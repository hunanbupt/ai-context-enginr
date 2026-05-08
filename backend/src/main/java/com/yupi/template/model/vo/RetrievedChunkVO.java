package com.yupi.template.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 检索结果切片视图对象
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
public class RetrievedChunkVO implements Serializable {

    /**
     * id
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
     * 相似度分数
     */
    private Double score;

    private static final long serialVersionUID = 1L;
}