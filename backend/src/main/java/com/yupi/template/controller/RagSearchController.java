package com.yupi.template.controller;

import com.yupi.template.common.BaseResponse;
import com.yupi.template.common.ResultUtils;
import com.yupi.template.exception.ErrorCode;
import com.yupi.template.exception.ThrowUtils;
import com.yupi.template.model.dto.rag.RagSearchRequest;
import com.yupi.template.model.dto.rag.RagSearchResponse;
import com.yupi.template.model.entity.User;
import com.yupi.template.model.vo.RetrievedChunkVO;
import com.yupi.template.rag.RetrievedChunk;
import com.yupi.template.service.RagService;
import com.yupi.template.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG 检索测试接口
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@RestController
@RequestMapping("/rag")
@Slf4j
public class RagSearchController {

    @Resource
    private RagService ragService;

    @Resource
    private UserService userService;

    /**
     * RAG 检索测试
     */
    @PostMapping("/search")
    @Operation(summary = "RAG 检索测试")
    public BaseResponse<RagSearchResponse> search(@RequestBody RagSearchRequest request,
                                                   HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getKbId() == null || request.getKbId().trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "知识库ID不能为空");
        ThrowUtils.throwIf(request.getQuery() == null || request.getQuery().trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "检索内容不能为空");

        User loginUser = userService.getLoginUser(httpServletRequest);

        List<RetrievedChunk> results = ragService.retrieve(
                request.getKbId(), request.getQuery(), request.getTopK(), loginUser);

        // 转换为 VO
        List<RetrievedChunkVO> chunks = results.stream()
                .map(this::toRetrievedChunkVO)
                .collect(Collectors.toList());

        RagSearchResponse response = new RagSearchResponse();
        response.setQuery(request.getQuery());
        response.setChunks(chunks);

        return ResultUtils.success(response);
    }

    /**
     * RetrievedChunk 转 RetrievedChunkVO
     */
    private RetrievedChunkVO toRetrievedChunkVO(RetrievedChunk chunk) {
        RetrievedChunkVO vo = new RetrievedChunkVO();
        vo.setId(chunk.getId());
        vo.setChunkId(chunk.getChunkId());
        vo.setDocId(chunk.getDocId());
        vo.setKbId(chunk.getKbId());
        vo.setChunkIndex(chunk.getChunkIndex());
        vo.setContent(chunk.getContent());
        vo.setScore(chunk.getScore());
        return vo;
    }

}
