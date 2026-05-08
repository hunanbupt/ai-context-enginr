package com.yupi.template.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Embedding / 文本相似度工具类
 * 第一版使用关键词重叠度计算文本相关度
 * 后续可替换为 DashScope Embedding + 余弦相似度
 *
 */
@Component
@Slf4j
public class EmbeddingUtils {

    /**
     * 计算 query 与 content 的文本相关度
     * 第一版：基于关键词命中率 + 字符覆盖率
     *
     * @param query   检索查询文本
     * @param content 待匹配的文本内容
     * @return 相关度分数（0~1）
     */
    public double calculateTextSimilarity(String query, String content) {
        // 空值处理
        if (query == null || query.trim().isEmpty() || content == null || content.trim().isEmpty()) {
            return 0;
        }

        String q = query.trim().toLowerCase();
        String c = content.trim().toLowerCase();

        // 1. 完全匹配或包含关系，给予高分
        if (c.contains(q)) {
            return 0.95;
        }
        if (q.contains(c)) {
            return 0.90;
        }

        // 2. 对 query 按空格和中文字符拆分为关键词
        Set<String> keywords = extractKeywords(q);

        // 3. 计算关键词命中率
        int hitCount = 0;
        for (String keyword : keywords) {
            if (c.contains(keyword)) {
                hitCount++;
            }
        }

        double keywordScore = keywords.isEmpty() ? 0 : (double) hitCount / keywords.size();

        // 4. 字符级覆盖率
        double charCoverage = calculateCharCoverage(q, c);

        // 5. 综合得分：关键词命中权重 0.6 + 字符覆盖权重 0.4
        double score = keywordScore * 0.6 + charCoverage * 0.4;

        return Math.min(score, 1.0);
    }

    /**
     * 从查询文本中提取关键词
     * 按空格分词，同时提取连续的中文字符段
     */
    private Set<String> extractKeywords(String query) {
        Set<String> keywords = new HashSet<>();

        // 按空格分词
        String[] words = query.split("\\s+");
        for (String word : words) {
            if (!word.isEmpty()) {
                keywords.add(word);
            }
        }

        // 额外提取中文字符作为独立关键词（处理"人工智能发展历程"这种无空格中文）
        for (char ch : query.toCharArray()) {
            if (isChinese(ch)) {
                keywords.add(String.valueOf(ch));
            }
        }

        return keywords;
    }

    /**
     * 计算 query 字符在 content 中的覆盖率
     */
    private double calculateCharCoverage(String query, String content) {
        int totalChars = 0;
        int coveredChars = 0;

        for (char ch : query.toCharArray()) {
            if (!Character.isWhitespace(ch)) {
                totalChars++;
                if (content.indexOf(ch) >= 0) {
                    coveredChars++;
                }
            }
        }

        return totalChars == 0 ? 0 : (double) coveredChars / totalChars;
    }

    /**
     * 判断字符是否为中文
     */
    private boolean isChinese(char ch) {
        return Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN;
    }

    // TODO: 后续版本替换为 DashScope Embedding API
    // public List<Float> embed(String text) {
    //     // 调用 DashScope Embedding API 获取向量
    // }
    //
    // public double cosineSimilarity(List<Float> vec1, List<Float> vec2) {
    //     // 计算余弦相似度
    // }
}
