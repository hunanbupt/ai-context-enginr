package com.yupi.template.model.enums;

import lombok.Getter;

/**
 * 文档解析状态枚举
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Getter
public enum DocumentParseStatusEnum {

    PENDING("PENDING", "待解析"),
    PARSING("PARSING", "解析中"),
    SUCCESS("SUCCESS", "解析成功"),
    FAILED("FAILED", "解析失败");

    /**
     * 状态值
     */
    private final String value;

    /**
     * 状态描述
     */
    private final String description;

    DocumentParseStatusEnum(String value, String description) {
        this.value = value;
        this.description = description;
    }

    /**
     * 根据值获取枚举
     *
     * @param value 状态值
     * @return 枚举实例
     */
    public static DocumentParseStatusEnum getByValue(String value) {
        if (value == null) {
            return null;
        }
        for (DocumentParseStatusEnum statusEnum : values()) {
            if (statusEnum.getValue().equals(value)) {
                return statusEnum;
            }
        }
        return null;
    }
}