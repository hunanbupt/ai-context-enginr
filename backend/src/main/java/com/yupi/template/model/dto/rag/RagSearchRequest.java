package com.yupi.template.model.dto.rag;

import lombok.Data;

import java.io.Serializable;

/**
 * RAG 检索请求
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
public class RagSearchRequest implements Serializable {

    /**
     * 知识库ID
     */
    private String kbId;

    /**
     * 检索查询文本
     */
    private String query;

    /**
     * 返回结果数量
     */
    private Integer topK;

    private static final long serialVersionUID = 1L;
}