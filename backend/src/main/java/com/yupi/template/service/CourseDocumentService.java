package com.yupi.template.service;

import com.mybatisflex.core.service.IService;
import com.yupi.template.model.entity.CourseDocument;
import com.yupi.template.model.entity.User;
import com.yupi.template.model.vo.CourseDocumentVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 课程文档服务接口
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
public interface CourseDocumentService extends IService<CourseDocument> {

    /**
     * 上传课程文档（解析 + 切片 + 持久化）
     *
     * @param kbId      知识库ID
     * @param file      上传的文件
     * @param loginUser 当前登录用户
     * @return 文档视图对象
     */
    CourseDocumentVO uploadDocument(String kbId, MultipartFile file, User loginUser);

    /**
     * 查询知识库下的文档列表
     *
     * @param kbId      知识库ID
     * @param loginUser 当前登录用户
     * @return 文档列表
     */
    List<CourseDocumentVO> listDocumentByKbId(String kbId, User loginUser);

}