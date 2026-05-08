package com.yupi.template.model.dto.course;

import lombok.Data;

import java.io.Serializable;

/**
 * 创建知识库请求
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
public class CreateKnowledgeBaseRequest implements Serializable {

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

    private static final long serialVersionUID = 1L;
}