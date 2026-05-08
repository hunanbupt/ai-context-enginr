package com.yupi.template.model.dto.course;

import lombok.Data;

import java.io.Serializable;

/**
 * 上传课程文档请求
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
public class UploadCourseDocumentRequest implements Serializable {

    /**
     * 所属知识库ID
     */
    private String kbId;

    private static final long serialVersionUID = 1L;
}