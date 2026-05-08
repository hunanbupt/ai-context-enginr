package com.yupi.template.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 课程文档视图对象
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
public class CourseDocumentVO implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 文档唯一标识
     */
    private String docId;

    /**
     * 所属知识库ID
     */
    private String kbId;

    /**
     * 原始文件名
     */
    private String fileName;

    /**
     * 文件类型
     */
    private String fileType;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 解析状态
     */
    private String parseStatus;

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