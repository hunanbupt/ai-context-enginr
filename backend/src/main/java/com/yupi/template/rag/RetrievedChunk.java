package com.yupi.template.rag;

import lombok.Data;

import java.io.Serializable;

/**
 * RAG 内部检索结果对象
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 *
 */
@Data
public class RetrievedChunk implements Serializable {

    /**
     * 切片主键ID
     */
    private Long id;

    /**
     * 切片唯一标识
     */
    private String chunkId;

    /**
     * 所属文档ID
     */
    private String docId;

    /**
     * 所属知识库ID
     */
    private String kbId;

    /**
     * 切片序号
     */
    private Integer chunkIndex;

    /**
     * 切片文本内容
     */
    private String content;

    /**
     * 相关度分数（0~1，越高越相关）
     */
    private Double score;

    private static final long serialVersionUID = 1L;
}
