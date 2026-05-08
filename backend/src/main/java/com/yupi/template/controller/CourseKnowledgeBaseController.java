package com.yupi.template.controller;

import com.yupi.template.common.BaseResponse;
import com.yupi.template.common.ResultUtils;
import com.yupi.template.exception.ErrorCode;
import com.yupi.template.exception.ThrowUtils;
import com.yupi.template.model.dto.course.CreateKnowledgeBaseRequest;
import com.yupi.template.model.entity.User;
import com.yupi.template.model.vo.KnowledgeBaseVO;
import com.yupi.template.service.CourseKnowledgeBaseService;
import com.yupi.template.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 课程知识库接口
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@RestController
@RequestMapping("/course/kb")
@Slf4j
public class CourseKnowledgeBaseController {

    @Resource
    private CourseKnowledgeBaseService courseKnowledgeBaseService;

    @Resource
    private UserService userService;

    /**
     * 创建课程知识库
     */
    @PostMapping("/create")
    @Operation(summary = "创建课程知识库")
    public BaseResponse<String> createKnowledgeBase(@RequestBody CreateKnowledgeBaseRequest request,
                                                     HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getName() == null || request.getName().trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "知识库名称不能为空");

        User loginUser = userService.getLoginUser(httpServletRequest);

        String kbId = courseKnowledgeBaseService.createKnowledgeBase(request, loginUser);

        return ResultUtils.success(kbId);
    }

    /**
     * 查询我的知识库列表
     */
    @GetMapping("/my/list")
    @Operation(summary = "查询我的知识库列表")
    public BaseResponse<List<KnowledgeBaseVO>> listMyKnowledgeBase(HttpServletRequest httpServletRequest) {
        User loginUser = userService.getLoginUser(httpServletRequest);

        List<KnowledgeBaseVO> list = courseKnowledgeBaseService.listMyKnowledgeBase(loginUser);

        return ResultUtils.success(list);
    }

}