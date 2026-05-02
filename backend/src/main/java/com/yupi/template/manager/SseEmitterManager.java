package com.yupi.template.manager;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.yupi.template.constant.ArticleConstant.SSE_RECONNECT_TIME_MS;
import static com.yupi.template.constant.ArticleConstant.SSE_TIMEOUT_MS;

/**
 * SSE Emitter 管理器
 * 支持消息缓冲（断线重连后回放遗漏的消息）和流式内容累积
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Component
@Slf4j
public class SseEmitterManager {

    /**
     * 每个 taskId 对应一个活跃的 SseEmitter
     */
    private final Map<String, SseEmitter> emitterMap = new ConcurrentHashMap<>();

    /**
     * 消息缓冲区：暂存已发送的消息，供重连客户端回放
     */
    private final Map<String, List<String>> messageBufferMap = new ConcurrentHashMap<>();

    /**
     * 流式内容累积器：按 taskId 存储当前正在流式输出的累积文本
     */
    private final Map<String, StringBuilder> streamingAccumulatorMap = new ConcurrentHashMap<>();

    private static final int MAX_BUFFER_SIZE = 300;

    /**
     * 创建 SseEmitter 并回放缓冲消息
     * 如果已有旧连接，先将其关闭并清理
     */
    public SseEmitter createEmitter(String taskId) {
        // 关闭旧连接（页面刷新场景），避免旧回调误删新 emitter
        SseEmitter oldEmitter = emitterMap.remove(taskId);
        if (oldEmitter != null) {
            try {
                oldEmitter.complete();
            } catch (Exception e) {
                log.debug("关闭旧 SSE 连接异常, taskId={}", taskId, e);
            }
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        emitter.onTimeout(() -> {
            log.warn("SSE 连接超时, taskId={}", taskId);
            emitterMap.remove(taskId, emitter);
        });
        emitter.onCompletion(() -> {
            log.info("SSE 连接完成, taskId={}", taskId);
            emitterMap.remove(taskId, emitter);
        });
        emitter.onError((e) -> {
            log.error("SSE 连接错误, taskId={}", taskId, e);
            emitterMap.remove(taskId, emitter);
        });

        emitterMap.put(taskId, emitter);
        log.info("SSE 连接已创建, taskId={}", taskId);

        // 回放缓冲消息
        replayBufferedMessages(taskId, emitter);

        return emitter;
    }

    /**
     * 发送消息并缓冲
     */
    public void send(String taskId, String message) {
        // 缓冲消息（用于断线重连回放）
        bufferMessage(taskId, message);

        SseEmitter emitter = emitterMap.get(taskId);
        if (emitter == null) {
            log.debug("SSE 无活跃连接, taskId={}, 消息已缓冲", taskId);
            return;
        }

        try {
            emitter.send(SseEmitter.event()
                    .data(message)
                    .reconnectTime(SSE_RECONNECT_TIME_MS));
        } catch (IOException e) {
            log.error("SSE 消息发送失败, taskId={}", taskId, e);
            emitterMap.remove(taskId, emitter);
        }
    }

    /**
     * 累积流式内容（用于断线重连后发送快照）
     */
    public void accumulateStreaming(String taskId, String content) {
        streamingAccumulatorMap
                .computeIfAbsent(taskId, k -> new StringBuilder())
                .append(content);
    }

    /**
     * 获取当前累积的流式内容
     */
    public String getAccumulatedStreaming(String taskId) {
        StringBuilder sb = streamingAccumulatorMap.get(taskId);
        return sb != null ? sb.toString() : null;
    }

    /**
     * 清除流式内容累积器（流式阶段完成后调用）
     */
    public void clearStreamingAccumulator(String taskId) {
        streamingAccumulatorMap.remove(taskId);
    }

    /**
     * 完成连接并清理所有资源
     */
    public void complete(String taskId) {
        SseEmitter emitter = emitterMap.remove(taskId);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.warn("SSE 连接完成失败, taskId={}", taskId, e);
            }
        }
        messageBufferMap.remove(taskId);
        streamingAccumulatorMap.remove(taskId);
        log.info("SSE 连接已全部完成, taskId={}", taskId);
    }

    /**
     * 检查是否存在活跃连接
     */
    public boolean exists(String taskId) {
        return emitterMap.containsKey(taskId);
    }

    private void bufferMessage(String taskId, String message) {
        List<String> buffer = messageBufferMap.computeIfAbsent(taskId, k -> new ArrayList<>());
        if (buffer.size() >= MAX_BUFFER_SIZE) {
            buffer.remove(0);
        }
        buffer.add(message);
    }

    private void replayBufferedMessages(String taskId, SseEmitter emitter) {
        List<String> buffer = messageBufferMap.get(taskId);
        if (buffer == null || buffer.isEmpty()) {
            return;
        }
        log.info("SSE 回放缓冲消息, taskId={}, 消息数={}", taskId, buffer.size());
        for (String message : buffer) {
            try {
                emitter.send(SseEmitter.event()
                        .data(message)
                        .reconnectTime(SSE_RECONNECT_TIME_MS));
            } catch (IOException e) {
                log.warn("SSE 回放消息发送失败, taskId={}", taskId, e);
                return;
            }
        }
    }
}
