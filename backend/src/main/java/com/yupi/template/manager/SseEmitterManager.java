package com.yupi.template.manager;

import com.yupi.template.utils.GsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static com.yupi.template.constant.ArticleConstant.SSE_RECONNECT_TIME_MS;
import static com.yupi.template.constant.ArticleConstant.SSE_TIMEOUT_MS;

/**
 * SSE emitter manager with sequence-based replay and streaming snapshots.
 * 核心数据结构（SseEmitterManager）
 * 字段	类型	说明
 * emitterMap	Map<String, SseEmitter>	当前活跃的 SSE 连接
 * messageBufferMap	Map<String, List<BufferedMessage>>	缓冲已发送的消息，最多 300 条
 * streamingAccumulatorMap	Map<String, StringBuilder>	流式内容的累积文本
 * seqMap	Map<String, AtomicLong>	每个 taskId 的自增序号
 * taskLockMap	Map<String, Object>	每个 taskId 的锁对象
 */
@Component
@Slf4j
public class SseEmitterManager {

    private final Map<String, SseEmitter> emitterMap = new ConcurrentHashMap<>();

    private final Map<String, List<BufferedMessage>> messageBufferMap = new ConcurrentHashMap<>();

    private final Map<String, StringBuilder> streamingAccumulatorMap = new ConcurrentHashMap<>();
//SSE 核心：创建/激活/发送/回放/完成 emitter，管理缓冲区、seq、流式累积器
    private final Map<String, AtomicLong> seqMap = new ConcurrentHashMap<>();

    private final Map<String, Object> taskLockMap = new ConcurrentHashMap<>();

    private static final int MAX_BUFFER_SIZE = 300;

    public record BufferedMessage(long seq, String content) {
    }

    public void runWithTaskLock(String taskId, Runnable runnable) {
        synchronized (getTaskLock(taskId)) {
            runnable.run();
        }
    }

    public SseEmitter createEmitter(String taskId, long lastSeq) {
        SseEmitter oldEmitter = emitterMap.remove(taskId);
        if (oldEmitter != null) {
            try {
                oldEmitter.complete();
            } catch (Exception e) {
                log.debug("Close old SSE connection failed, taskId={}", taskId, e);
            }
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        emitter.onTimeout(() -> {
            log.warn("SSE connection timeout, taskId={}", taskId);
            emitterMap.remove(taskId, emitter);
        });
        emitter.onCompletion(() -> {
            log.info("SSE connection completed, taskId={}", taskId);
            emitterMap.remove(taskId, emitter);
        });
        emitter.onError((e) -> {
            log.error("SSE connection error, taskId={}", taskId, e);
            emitterMap.remove(taskId, emitter);
        });

        replayBufferedMessages(taskId, emitter, lastSeq);
        log.info("SSE emitter created, taskId={}, lastSeq={}", taskId, lastSeq);
        return emitter;
    }

    public void activateEmitter(String taskId, SseEmitter emitter) {
        emitterMap.put(taskId, emitter);
        log.info("SSE emitter activated, taskId={}", taskId);
    }

    public void send(String taskId, String message) {
        synchronized (getTaskLock(taskId)) {
            long seq = getNextSeq(taskId);
            String enrichedMessage = injectSeq(message, seq);
            bufferMessage(taskId, new BufferedMessage(seq, enrichedMessage));

            SseEmitter emitter = emitterMap.get(taskId);
            if (emitter == null) {
                log.debug("No active SSE connection, message buffered, taskId={}", taskId);
                return;
            }

            try {
                emitter.send(SseEmitter.event()
                        .id(String.valueOf(seq))
                        .data(enrichedMessage)
                        .reconnectTime(SSE_RECONNECT_TIME_MS));
            } catch (IOException e) {
                log.error("SSE send failed, taskId={}", taskId, e);
                emitterMap.remove(taskId, emitter);
            }
        }
    }

    public void accumulateStreaming(String taskId, String content) {
        synchronized (getTaskLock(taskId)) {
            streamingAccumulatorMap
                    .computeIfAbsent(taskId, k -> new StringBuilder())
                    .append(content);
        }
    }

    public String getAccumulatedStreaming(String taskId) {
        synchronized (getTaskLock(taskId)) {
            StringBuilder sb = streamingAccumulatorMap.get(taskId);
            return sb != null ? sb.toString() : null;
        }
    }

    public void clearStreamingAccumulator(String taskId) {
        synchronized (getTaskLock(taskId)) {
            streamingAccumulatorMap.remove(taskId);
        }
    }

    public void sendDirect(String taskId, Map<String, Object> data) {
        sendDirect(taskId, data, emitterMap.get(taskId));
    }

    public void sendDirect(String taskId, Map<String, Object> data, SseEmitter emitter) {
        if (emitter == null) {
            log.debug("SSE direct emitter is null, taskId={}", taskId);
            return;
        }

        long seq = getNextSeq(taskId);
        data.put("seq", seq);

        try {
            emitter.send(SseEmitter.event()
                    .id(String.valueOf(seq))
                    .data(GsonUtils.toJson(data))
                    .reconnectTime(SSE_RECONNECT_TIME_MS));
        } catch (IOException e) {
            log.error("SSE direct send failed, taskId={}", taskId, e);
            emitterMap.remove(taskId, emitter);
        }
    }

    public void complete(String taskId) {
        synchronized (getTaskLock(taskId)) {
            SseEmitter emitter = emitterMap.remove(taskId);
            if (emitter != null) {
                try {
                    emitter.complete();
                } catch (Exception e) {
                    log.warn("SSE complete failed, taskId={}", taskId, e);
                }
            }
            streamingAccumulatorMap.remove(taskId);
        }
        log.info("SSE connection closed, replay buffer retained, taskId={}", taskId);
    }

    public boolean exists(String taskId) {
        return emitterMap.containsKey(taskId);
    }

    private long getNextSeq(String taskId) {
        return seqMap.computeIfAbsent(taskId, k -> new AtomicLong(0)).incrementAndGet();
    }

    private Object getTaskLock(String taskId) {
        return taskLockMap.computeIfAbsent(taskId, k -> new Object());
    }

    @SuppressWarnings("unchecked")
    private String injectSeq(String jsonMessage, long seq) {
        Map<String, Object> map = GsonUtils.fromJson(jsonMessage, Map.class);
        map.put("seq", seq);
        return GsonUtils.toJson(map);
    }

    private void bufferMessage(String taskId, BufferedMessage message) {
        List<BufferedMessage> buffer = messageBufferMap.computeIfAbsent(taskId, k -> new ArrayList<>());
        if (buffer.size() >= MAX_BUFFER_SIZE) {
            buffer.remove(0);
        }
        buffer.add(message);
    }

    private void replayBufferedMessages(String taskId, SseEmitter emitter, long lastSeq) {
        List<BufferedMessage> buffer = messageBufferMap.get(taskId);
        if (buffer == null || buffer.isEmpty()) {
            return;
        }

        long replayed = 0;
        long skipped = 0;
        for (BufferedMessage msg : buffer) {
            if (msg.seq() <= lastSeq) {
                skipped++;
                continue;
            }
            if (msg.content().contains("\"ALL_COMPLETE\"")) {
                skipped++;
                continue;
            }
            try {
                emitter.send(SseEmitter.event()
                        .id(String.valueOf(msg.seq()))
                        .data(msg.content())
                        .reconnectTime(SSE_RECONNECT_TIME_MS));
                replayed++;
            } catch (IOException e) {
                log.warn("SSE replay failed, taskId={}, seq={}", taskId, msg.seq(), e);
                return;
            }
        }
        log.info("SSE replay complete, taskId={}, replayed={}, skipped={}, lastSeq={}",
                taskId, replayed, skipped, lastSeq);
    }
}
