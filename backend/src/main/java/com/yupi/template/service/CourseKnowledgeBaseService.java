package com.yupi.template.service;

import com.mybatisflex.core.service.IService;
import com.yupi.template.model.dto.course.CreateKnowledgeBaseRequest;
import com.yupi.template.model.entity.CourseKnowledgeBase;
import com.yupi.template.model.entity.User;
import com.yupi.template.model.vo.KnowledgeBaseVO;

import java.util.List;

/**
 * 课程知识库服务接口
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
public interface CourseKnowledgeBaseService extends IService<CourseKnowledgeBase> {

    /**
     * 创建课程知识库
     *
     * @param request   创建请求
     * @param loginUser 当前登录用户
     * @return kbId
     */
    String createKnowledgeBase(CreateKnowledgeBaseRequest request, User loginUser);

    /**
     * 查询当前登录用户的知识库列表
     *
     * @param loginUser 当前登录用户
     * @return 知识库列表
     */
    List<KnowledgeBaseVO> listMyKnowledgeBase(User loginUser);

}