package com.yupi.template.model.enums;

import lombok.Getter;

/**
 * 知识库状态枚举
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Getter
public enum KnowledgeBaseStatusEnum {

    NORMAL("NORMAL", "正常"),
    DISABLED("DISABLED", "禁用");

    /**
     * 状态值
     */
    private final String value;

    /**
     * 状态描述
     */
    private final String description;

    KnowledgeBaseStatusEnum(String value, String description) {
        this.value = value;
        this.description = description;
    }

    /**
     * 根据值获取枚举
     *
     * @param value 状态值
     * @return 枚举实例
     */
    public static KnowledgeBaseStatusEnum getByValue(String value) {
        if (value == null) {
            return null;
        }
        for (KnowledgeBaseStatusEnum statusEnum : values()) {
            if (statusEnum.getValue().equals(value)) {
                return statusEnum;
            }
        }
        return null;
    }
}