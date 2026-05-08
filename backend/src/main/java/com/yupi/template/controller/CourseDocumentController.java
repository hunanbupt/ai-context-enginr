package com.yupi.template.controller;

import com.yupi.template.common.BaseResponse;
import com.yupi.template.common.ResultUtils;
import com.yupi.template.exception.ErrorCode;
import com.yupi.template.exception.ThrowUtils;
import com.yupi.template.model.entity.User;
import com.yupi.template.model.vo.CourseDocumentVO;
import com.yupi.template.service.CourseDocumentService;
import com.yupi.template.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 课程文档接口
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@RestController
@RequestMapping("/course/document")
@Slf4j
public class CourseDocumentController {

    @Resource
    private CourseDocumentService courseDocumentService;

    @Resource
    private UserService userService;

    /**
     * 上传课程文档
     */
    @PostMapping("/upload")
    @Operation(summary = "上传课程文档")
    public BaseResponse<CourseDocumentVO> uploadDocument(@RequestParam("kbId") String kbId,
                                                          @RequestParam("file") MultipartFile file,
                                                          HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(kbId == null || kbId.trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "知识库ID不能为空");
        ThrowUtils.throwIf(file == null || file.isEmpty(),
                ErrorCode.PARAMS_ERROR, "文件不能为空");

        User loginUser = userService.getLoginUser(httpServletRequest);

        CourseDocumentVO vo = courseDocumentService.uploadDocument(kbId, file, loginUser);

        return ResultUtils.success(vo);
    }

    /**
     * 查询知识库下的文档列表
     */
    @GetMapping("/list")
    @Operation(summary = "查询知识库文档列表")
    public BaseResponse<List<CourseDocumentVO>> listDocumentByKbId(@RequestParam("kbId") String kbId,
                                                                    HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(kbId == null || kbId.trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "知识库ID不能为空");

        User loginUser = userService.getLoginUser(httpServletRequest);

        List<CourseDocumentVO> list = courseDocumentService.listDocumentByKbId(kbId, loginUser);

        return ResultUtils.success(list);
    }

}
