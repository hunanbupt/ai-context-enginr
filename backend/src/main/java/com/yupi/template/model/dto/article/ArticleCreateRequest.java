package com.yupi.template.model.dto.article;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 创建文章请求
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
public class ArticleCreateRequest implements Serializable {

    /**
     * 选题
     */
    private String topic;

    /**
     * 文章风格：tech/emotional/educational/humorous，可为空
     */
    private String style;

    /**
     * 允许的配图方式列表（为空或 null 表示支持所有方式）
     * 可选值：PEXELS, NANO_BANANA, MERMAID, ICONIFY, EMOJI_PACK, SVG_DIAGRAM
     */
    private List<String> enabledImageMethods;

    /**
     * 是否启用 RAG（课程知识库增强），可为空
     */
    private Boolean ragEnabled;

    /**
     * 关联的课程知识库 ID，ragEnabled=true 时必填
     */
    private String kbId;

    private static final long serialVersionUID = 1L;
}
