package com.yupi.template.service.impl;

import com.mybatisflex.core.query.QueryWrapper;
import com.yupi.template.exception.ErrorCode;
import com.yupi.template.exception.ThrowUtils;
import com.yupi.template.model.entity.CourseDocumentChunk;
import com.yupi.template.model.entity.CourseKnowledgeBase;
import com.yupi.template.model.entity.User;
import com.yupi.template.rag.EmbeddingUtils;
import com.yupi.template.rag.RetrievedChunk;
import com.yupi.template.service.CourseDocumentChunkService;
import com.yupi.template.service.CourseKnowledgeBaseService;
import com.yupi.template.service.RagService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG 检索服务实现类
 * 第一版：基于关键词重叠度的文本相似度检索
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Service
@Slf4j
public class RagServiceImpl implements RagService {

    /**
     * RAG 上下文最大字符数，防止 Prompt 过长
     */
    private static final int MAX_CONTEXT_LENGTH = 4000;

    /**
     * topK 默认值
     */
    private static final int DEFAULT_TOP_K = 5;

    /**
     * topK 最大值
     */
    private static final int MAX_TOP_K = 10;

    @Resource
    private CourseKnowledgeBaseService courseKnowledgeBaseService;

    @Resource
    private CourseDocumentChunkService courseDocumentChunkService;

    @Resource
    private EmbeddingUtils embeddingUtils;

    @Override
    public List<RetrievedChunk> retrieve(String kbId, String query, Integer topK, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(kbId == null || kbId.trim().isEmpty(), ErrorCode.PARAMS_ERROR, "知识库ID不能为空");
        ThrowUtils.throwIf(query == null || query.trim().isEmpty(), ErrorCode.PARAMS_ERROR, "检索内容不能为空");

        // 2. topK 参数处理
        if (topK == null || topK <= 0) {
            topK = DEFAULT_TOP_K;
        }
        topK = Math.min(topK, MAX_TOP_K);

        // 3. 校验知识库存在且属于当前用户
        QueryWrapper kbQuery = QueryWrapper.create().eq("kbId", kbId);
        CourseKnowledgeBase kb = courseKnowledgeBaseService.getOne(kbQuery);
        ThrowUtils.throwIf(kb == null, ErrorCode.NOT_FOUND_ERROR, "知识库不存在");
        ThrowUtils.throwIf(!kb.getUserId().equals(loginUser.getId()),
                ErrorCode.NO_AUTH_ERROR, "无权检索该知识库");

        // 4. 查询该知识库下所有切片（isDelete 由 MyBatis-Flex 逻辑删除自动过滤）
        QueryWrapper chunkQuery = QueryWrapper.create().eq("kbId", kbId);
        List<CourseDocumentChunk> allChunks = courseDocumentChunkService.list(chunkQuery);

        // 5. 无切片时直接返回空
        if (allChunks == null || allChunks.isEmpty()) {
            log.info("知识库无可用切片, kbId={}", kbId);
            return List.of();
        }

        // 6. 计算每个 chunk 与 query 的相似度
        List<RetrievedChunk> scoredChunks = new ArrayList<>();
        for (CourseDocumentChunk chunk : allChunks) {
            double score = embeddingUtils.calculateTextSimilarity(query, chunk.getContent());
            if (score > 0 || allChunks.size() <= topK) {
                // 保留所有 score > 0 的结果；如果总分片数 <= topK，也保留 score=0 的兜底
                RetrievedChunk rc = new RetrievedChunk();
                rc.setId(chunk.getId());
                rc.setChunkId(chunk.getChunkId());
                rc.setDocId(chunk.getDocId());
                rc.setKbId(chunk.getKbId());
                rc.setChunkIndex(chunk.getChunkIndex());
                rc.setContent(chunk.getContent());
                rc.setScore(score);
                scoredChunks.add(rc);
            }
        }

        // 7. 按 score 降序排序，取 topK
        List<RetrievedChunk> result = scoredChunks.stream()
                .sorted(Comparator.comparingDouble(RetrievedChunk::getScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());

        log.info("RAG 检索完成, kbId={}, query={}, topK={}, totalChunks={}, resultCount={}",
                kbId, query.substring(0, Math.min(50, query.length())), topK, allChunks.size(), result.size());
        return result;
    }

    @Override
    public String buildRagContext(String kbId, String query, Integer topK, User loginUser) {
        // 1. 检索
        List<RetrievedChunk> chunks;
        try {
            chunks = retrieve(kbId, query, topK, loginUser);
        } catch (Exception e) {
            log.warn("RAG 检索失败，降级为空上下文, kbId={}, query={}", kbId, query, e);
            return "";
        }

        // 2. 无结果直接返回空
        if (chunks == null || chunks.isEmpty()) {
            return "";
        }

        // 3. 拼接上下文文本
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n【课程知识库参考资料】\n");
        sb.append("以下内容来自用户上传的课程资料，请在生成课程内容时优先参考：\n\n");

        int totalLength = sb.length();
        int includedCount = 0;

        for (int i = 0; i < chunks.size(); i++) {
            RetrievedChunk chunk = chunks.get(i);
            String entry = String.format("[资料%d | 相关度：%.2f]\n%s\n\n",
                    i + 1, chunk.getScore(), chunk.getContent());

            // 限制总长度，避免 Prompt 过长
            if (totalLength + entry.length() > MAX_CONTEXT_LENGTH) {
                log.info("RAG 上下文达到长度上限, kbId={}, includedCount={}, maxLength={}",
                        kbId, includedCount, MAX_CONTEXT_LENGTH);
                break;
            }

            sb.append(entry);
            totalLength += entry.length();
            includedCount++;
        }

        String ragContext = sb.toString();
        log.info("RAG 上下文构造完成, kbId={}, chunkCount={}, contextLength={}",
                kbId, includedCount, ragContext.length());
        return ragContext;
    }

    @Override
    public List<RetrievedChunk> retrieveByUserId(String kbId, String query, Integer topK, Long userId) {
        User user = new User();
        user.setId(userId);
        return retrieve(kbId, query, topK, user);
    }

    @Override
    public String buildRagContextByUserId(String kbId, String query, Integer topK, Long userId) {
        User user = new User();
        user.setId(userId);
        return buildRagContext(kbId, query, topK, user);
    }

}
