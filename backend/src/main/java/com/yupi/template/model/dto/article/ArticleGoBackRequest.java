package com.yupi.template.model.dto.article;

import lombok.Data;

import java.io.Serializable;

/**
 * 回退阶段请求
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
public class ArticleGoBackRequest implements Serializable {

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 目标阶段值，如 TITLE_SELECTING、OUTLINE_EDITING
     */
    private String targetPhase;

    private static final long serialVersionUID = 1L;
}
