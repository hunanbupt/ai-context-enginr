package com.yupi.template.model.dto.rag;

import com.yupi.template.model.vo.RetrievedChunkVO;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * RAG 检索响应
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
public class RagSearchResponse implements Serializable {

    /**
     * 检索查询文本
     */
    private String query;

    /**
     * 检索结果切片列表
     */
    private List<RetrievedChunkVO> chunks;

    private static final long serialVersionUID = 1L;
}