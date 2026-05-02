package com.yupi.template.controller;

import com.mybatisflex.core.paginate.Page;
import com.yupi.template.common.BaseResponse;
import com.yupi.template.common.DeleteRequest;
import com.yupi.template.common.ResultUtils;
import com.yupi.template.exception.ErrorCode;
import com.yupi.template.exception.ThrowUtils;
import com.yupi.template.manager.SseEmitterManager;
import com.yupi.template.model.dto.article.ArticleAiModifyOutlineRequest;
import com.yupi.template.model.dto.article.ArticleConfirmOutlineRequest;
import com.yupi.template.model.dto.article.ArticleConfirmTitleRequest;
import com.yupi.template.model.dto.article.ArticleCreateRequest;
import com.yupi.template.model.dto.article.ArticleGoBackRequest;
import com.yupi.template.model.dto.article.ArticleQueryRequest;
import com.yupi.template.model.dto.article.ArticleState;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.yupi.template.model.entity.User;
import com.yupi.template.model.enums.ArticlePhaseEnum;
import com.yupi.template.model.enums.ArticleStyleEnum;
import com.yupi.template.model.enums.SseMessageTypeEnum;
import com.yupi.template.model.vo.AgentExecutionStats;
import com.yupi.template.model.vo.ArticleVO;
import com.yupi.template.service.AgentLogService;
import com.yupi.template.service.ArticleAsyncService;
import com.yupi.template.service.ArticleService;
import com.yupi.template.service.UserService;
import com.yupi.template.utils.GsonUtils;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 文章接口
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@RestController
@RequestMapping("/article")
@Slf4j
public class ArticleController {

    @Resource
    private ArticleService articleService;

    @Resource
    private ArticleAsyncService articleAsyncService;

    @Resource
    private SseEmitterManager sseEmitterManager;

    @Resource
    private UserService userService;

    @Resource
    private AgentLogService agentLogService;

    /**
     * 创建文章任务
     */
    @PostMapping("/create")
    @Operation(summary = "创建文章任务")
    public BaseResponse<String> createArticle(@RequestBody ArticleCreateRequest request, HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getTopic() == null || request.getTopic().trim().isEmpty(), 
                ErrorCode.PARAMS_ERROR, "选题不能为空");
        // 校验风格参数（允许为空）
        ThrowUtils.throwIf(!ArticleStyleEnum.isValid(request.getStyle()),
                ErrorCode.PARAMS_ERROR, "无效的文章风格");

        User loginUser = userService.getLoginUser(httpServletRequest);

        // 检查并消耗配额 + 创建文章任务（在同一事务中）
        String taskId = articleService.createArticleTaskWithQuotaCheck(
                request.getTopic(), 
                request.getStyle(), 
                request.getEnabledImageMethods(),
                loginUser
        );

        // 异步执行阶段1：生成标题方案
        articleAsyncService.executePhase1(
                taskId, 
                request.getTopic(),
                request.getStyle()
        );

        return ResultUtils.success(taskId);
    }

    /**
     * SSE 进度推送
     * 支持断线重连：新连接建立时会先回放缓冲消息，再发送当前状态快照（含流式内容）
     */
    @GetMapping("/progress/{taskId}")
    @Operation(summary = "获取文章生成进度(SSE)")
    public SseEmitter getProgress(@PathVariable String taskId, HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(taskId == null || taskId.trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "任务ID不能为空");

        // 校验权限（内部会检查任务是否存在以及用户是否有权限访问）
        User loginUser = userService.getLoginUser(httpServletRequest);
        ArticleVO articleVO = articleService.getArticleDetail(taskId, loginUser);

        // 创建 SSE Emitter（内部会回放缓冲消息）
        SseEmitter emitter = sseEmitterManager.createEmitter(taskId);

        // 发送状态快照，帮助前端恢复到当前阶段
        sendStateSnapshot(taskId, articleVO, emitter);

        log.info("SSE 连接已建立, taskId={}, phase={}", taskId, articleVO.getPhase());
        return emitter;
    }

    /**
     * 向新连接的客户端发送当前状态快照
     * 包含：已完成的数据（标题方案、大纲）+ 流式累积内容
     */
    private void sendStateSnapshot(String taskId, ArticleVO articleVO, SseEmitter emitter) {
        try {
            String phase = articleVO.getPhase();
            Map<String, Object> data = new HashMap<>();

            // 如果标题方案已生成，发送快照
            if (articleVO.getTitleOptions() != null && !articleVO.getTitleOptions().isEmpty()) {
                data.put("type", SseMessageTypeEnum.TITLES_GENERATED.getValue());
                data.put("titleOptions", articleVO.getTitleOptions());
                emitter.send(SseEmitter.event().data(GsonUtils.toJson(data)));
            }

            // 如果大纲已生成，发送快照
            if (articleVO.getOutline() != null && !articleVO.getOutline().isEmpty()) {
                data.clear();
                data.put("type", SseMessageTypeEnum.OUTLINE_GENERATED.getValue());
                data.put("outline", articleVO.getOutline());
                emitter.send(SseEmitter.event().data(GsonUtils.toJson(data)));
            }

            // 如果正在流式生成中，发送已累积的流式内容快照
            String accumulated = sseEmitterManager.getAccumulatedStreaming(taskId);
            if (accumulated != null && !accumulated.isEmpty()) {
                data.clear();
                data.put("type", SseMessageTypeEnum.STREAMING_SNAPSHOT.getValue());
                data.put("content", accumulated);
                data.put("phase", phase);
                emitter.send(SseEmitter.event().data(GsonUtils.toJson(data)));
            }
        } catch (IOException e) {
            log.warn("SSE 状态快照发送失败, taskId={}", taskId, e);
        }
    }

    /**
     * 获取文章详情
     */
    @GetMapping("/{taskId}")
    @Operation(summary = "获取文章详情")
    public BaseResponse<ArticleVO> getArticle(@PathVariable String taskId, HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(taskId == null || taskId.trim().isEmpty(), 
                ErrorCode.PARAMS_ERROR, "任务ID不能为空");

        User loginUser = userService.getLoginUser(httpServletRequest);
        ArticleVO articleVO = articleService.getArticleDetail(taskId, loginUser);

        return ResultUtils.success(articleVO);
    }

    /**
     * 分页查询文章列表
     */
    @PostMapping("/list")
    @Operation(summary = "分页查询文章列表")
    public BaseResponse<Page<ArticleVO>> listArticle(@RequestBody ArticleQueryRequest request,
                                                       HttpServletRequest httpServletRequest) {
        User loginUser = userService.getLoginUser(httpServletRequest);
        Page<ArticleVO> articleVOPage = articleService.listArticleByPage(request, loginUser);
        
        return ResultUtils.success(articleVOPage);
    }

    /**
     * 删除文章
     */
    @PostMapping("/delete")
    @Operation(summary = "删除文章")
    public BaseResponse<Boolean> deleteArticle(@RequestBody DeleteRequest deleteRequest,
                                                 HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null, 
                ErrorCode.PARAMS_ERROR);
        
        User loginUser = userService.getLoginUser(httpServletRequest);
        boolean result = articleService.deleteArticle(deleteRequest.getId(), loginUser);
        
        return ResultUtils.success(result);
    }

    /**
     * 确认标题并输入补充描述
     */
    @PostMapping("/confirm-title")
    @Operation(summary = "确认标题并输入补充描述")
    public BaseResponse<Void> confirmTitle(@RequestBody ArticleConfirmTitleRequest request,
                                            HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getTaskId() == null || request.getTaskId().trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "任务ID不能为空");
        ThrowUtils.throwIf(request.getSelectedMainTitle() == null || request.getSelectedMainTitle().trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "主标题不能为空");
        ThrowUtils.throwIf(request.getSelectedSubTitle() == null || request.getSelectedSubTitle().trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "副标题不能为空");

        User loginUser = userService.getLoginUser(httpServletRequest);

        // 确认标题
        articleService.confirmTitle(
                request.getTaskId(),
                request.getSelectedMainTitle(),
                request.getSelectedSubTitle(),
                request.getUserDescription(),
                loginUser
        );

        // 异步执行阶段2：生成大纲
        articleAsyncService.executePhase2(request.getTaskId());

        return ResultUtils.success(null);
    }

    /**
     * 确认大纲
     */
    @PostMapping("/confirm-outline")
    @Operation(summary = "确认大纲")
    public BaseResponse<Void> confirmOutline(@RequestBody ArticleConfirmOutlineRequest request,
                                              HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getTaskId() == null || request.getTaskId().trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "任务ID不能为空");
        ThrowUtils.throwIf(request.getOutline() == null || request.getOutline().isEmpty(),
                ErrorCode.PARAMS_ERROR, "大纲不能为空");

        User loginUser = userService.getLoginUser(httpServletRequest);

        // 确认大纲
        articleService.confirmOutline(
                request.getTaskId(),
                request.getOutline(),
                loginUser
        );

        // 异步执行阶段3：生成正文+配图
        articleAsyncService.executePhase3(request.getTaskId());

        return ResultUtils.success(null);
    }

    /**
     * AI 修改大纲
     */
    @PostMapping("/ai-modify-outline")
    @Operation(summary = "AI 修改大纲")
    public BaseResponse<List<ArticleState.OutlineSection>> aiModifyOutline(
            @RequestBody ArticleAiModifyOutlineRequest request,
            HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getTaskId() == null || request.getTaskId().trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "任务ID不能为空");
        ThrowUtils.throwIf(request.getModifySuggestion() == null || request.getModifySuggestion().trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "修改建议不能为空");

        User loginUser = userService.getLoginUser(httpServletRequest);

        // AI 修改大纲
        List<ArticleState.OutlineSection> modifiedOutline = articleService.aiModifyOutline(
                request.getTaskId(),
                request.getModifySuggestion(),
                loginUser
        );

        return ResultUtils.success(modifiedOutline);
    }

    /**
     * 回退到之前的决策阶段
     * 例如：大纲生成完毕后，用户可以回退到标题选择阶段，重新选择标题
     */
    @PostMapping("/go-back")
    @Operation(summary = "回退到之前的决策阶段")
    public BaseResponse<Void> goBack(@RequestBody ArticleGoBackRequest request,
                                     HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getTaskId() == null || request.getTaskId().trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "任务ID不能为空");
        ThrowUtils.throwIf(request.getTargetPhase() == null || request.getTargetPhase().trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "目标阶段不能为空");

        ArticlePhaseEnum targetPhase = ArticlePhaseEnum.getByValue(request.getTargetPhase());
        ThrowUtils.throwIf(targetPhase == null,
                ErrorCode.PARAMS_ERROR, "无效的目标阶段: " + request.getTargetPhase());

        User loginUser = userService.getLoginUser(httpServletRequest);
        articleService.goBackPhase(request.getTaskId(), targetPhase, loginUser);

        // 通过 SSE 通知前端阶段已回退
        Map<String, Object> data = new HashMap<>();
        data.put("type", SseMessageTypeEnum.PHASE_ROLLED_BACK.getValue());
        data.put("taskId", request.getTaskId());
        data.put("phase", targetPhase.getValue());
        sseEmitterManager.send(request.getTaskId(), GsonUtils.toJson(data));

        log.info("文章阶段回退成功, taskId={}, targetPhase={}", request.getTaskId(), targetPhase.getValue());
        return ResultUtils.success(null);
    }

    /**
     * 获取任务执行日志
     */
    @GetMapping("/execution-logs/{taskId}")
    @Operation(summary = "获取任务执行日志")
    public BaseResponse<AgentExecutionStats> getExecutionLogs(@PathVariable String taskId) {
        ThrowUtils.throwIf(taskId == null || taskId.trim().isEmpty(), 
                ErrorCode.PARAMS_ERROR, "任务ID不能为空");
        
        AgentExecutionStats stats = agentLogService.getExecutionStats(taskId);
        return ResultUtils.success(stats);
    }
}
