package com.yupi.template.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识库视图对象
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
public class KnowledgeBaseVO implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 知识库唯一标识
     */
    private String kbId;

    /**
     * 知识库名称
     */
    private String name;

    /**
     * 课程名称
     */
    private String courseName;

    /**
     * 知识库描述
     */
    private String description;

    /**
     * 状态
     */
    private String status;

    /**
     * 文档数量
     */
    private Integer documentCount;

    /**
     * 切片数量
     */
    private Integer chunkCount;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    private static final long serialVersionUID = 1L;
}