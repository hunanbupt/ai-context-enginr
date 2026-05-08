package com.yupi.template.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档切片器
 * 将长文本按固定大小 + 重叠策略切分为多个 chunk
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Component
@Slf4j
public class DocumentChunker {

    /**
     * 默认切片大小（字符数）
     */
    public static final int DEFAULT_CHUNK_SIZE = 600;

    /**
     * 默认重叠大小（字符数）
     */
    public static final int DEFAULT_OVERLAP = 100;

    /**
     * 将文本切分为多个 chunk（使用默认参数）
     */
    public List<String> split(String text) {
        return split(text, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    /**
     * 将文本切分为多个 chunk
     *
     * @param text      原始文本
     * @param chunkSize 每个 chunk 的大小（字符数）
     * @param overlap   相邻 chunk 之间的重叠大小（字符数）
     * @return chunk 列表
     */
    public List<String> split(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();

        // 清洗多余空白字符（保留单个空格和换行）
        String cleaned = text.replaceAll("\\s+", " ").trim();

        if (cleaned.isEmpty()) {
            return chunks;
        }

        // 文本长度小于 chunkSize，直接作为一个 chunk
        if (cleaned.length() <= chunkSize) {
            chunks.add(cleaned);
            return chunks;
        }

        int step = chunkSize - overlap;
        // 防止 step <= 0 导致死循环
        if (step <= 0) {
            chunks.add(cleaned);
            return chunks;
        }

        int start = 0;
        while (start < cleaned.length()) {
            int end = Math.min(start + chunkSize, cleaned.length());
            String chunk = cleaned.substring(start, end).trim();

            // 过滤空 chunk
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }

            start += step;

            // 如果剩余部分不足 step，且还有内容，截取最后一段
            if (start >= cleaned.length() - step && start < cleaned.length()) {
                String lastChunk = cleaned.substring(start).trim();
                if (!lastChunk.isEmpty() && lastChunk.length() >= 10) {
                    // 只添加长度 >= 10 的尾部片段，避免无意义的小片段
                    chunks.add(lastChunk);
                }
                break;
            }
        }

        log.debug("文本切片完成, 原文长度={}, 切片数={}, chunkSize={}, overlap={}",
                text.length(), chunks.size(), chunkSize, overlap);
        return chunks;
    }

}
