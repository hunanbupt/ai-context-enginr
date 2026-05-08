package com.yupi.template.service;

import com.yupi.template.model.entity.User;
import com.yupi.template.rag.RetrievedChunk;

import java.util.List;

/**
 * RAG 检索服务接口
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
public interface RagService {

    /**
     * 从指定知识库中检索相关切片
     *
     * @param kbId      知识库ID
     * @param query     检索查询文本
     * @param topK      返回结果数量
     * @param loginUser 当前登录用户
     * @return 相关切片列表（按 score 降序）
     */
    List<RetrievedChunk> retrieve(String kbId, String query, Integer topK, User loginUser);

    /**
     * 检索并构造适合注入 Prompt 的上下文文本
     *
     * @param kbId      知识库ID
     * @param query     检索查询文本
     * @param topK      返回结果数量
     * @param loginUser 当前登录用户
     * @return RAG 上下文文本，检索失败或无结果时返回空字符串
     */
    String buildRagContext(String kbId, String query, Integer topK, User loginUser);

    /**
     * 从指定知识库中检索相关切片（仅需 userId 校验归属）
     *
     * @param kbId   知识库ID
     * @param query  检索查询文本
     * @param topK   返回结果数量
     * @param userId 用户ID
     * @return 相关切片列表（按 score 降序）
     */
    List<RetrievedChunk> retrieveByUserId(String kbId, String query, Integer topK, Long userId);

    /**
     * 检索并构造适合注入 Prompt 的上下文文本（仅需 userId 校验归属）
     *
     * @param kbId   知识库ID
     * @param query  检索查询文本
     * @param topK   返回结果数量
     * @param userId 用户ID
     * @return RAG 上下文文本，检索失败或无结果时返回空字符串
     */
    String buildRagContextByUserId(String kbId, String query, Integer topK, Long userId);

}
