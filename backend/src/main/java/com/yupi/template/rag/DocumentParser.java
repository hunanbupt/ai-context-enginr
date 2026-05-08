package com.yupi.template.rag;

import com.yupi.template.exception.ErrorCode;
import com.yupi.template.exception.ThrowUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 文档解析器
 * 根据文件类型解析文档内容为纯文本
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Component
@Slf4j
public class DocumentParser {

    /**
     * 解析文档内容
     *
     * @param file     上传的文件
     * @param fileType 文件类型（txt/md）
     * @return 纯文本内容
     */
    public String parse(MultipartFile file, String fileType) {
        ThrowUtils.throwIf(file == null || file.isEmpty(), ErrorCode.PARAMS_ERROR, "文件不能为空");

        String lowerType = fileType.toLowerCase();

        // txt 和 md 都按 UTF-8 纯文本读取
        if ("txt".equals(lowerType) || "md".equals(lowerType)) {
            return readAsPlainText(file);
        }

        // TODO: 后续版本支持 pdf/docx
        throw new RuntimeException("暂不支持的文件类型: " + fileType);
    }

    /**
     * 按 UTF-8 读取文本文件内容
     */
    private String readAsPlainText(MultipartFile file) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            String content = sb.toString();
            ThrowUtils.throwIf(content.trim().isEmpty(), ErrorCode.PARAMS_ERROR, "文件内容为空");
            return content;
        } catch (Exception e) {
            log.error("文本文件读取失败, fileName={}", file.getOriginalFilename(), e);
            throw new RuntimeException("文件读取失败: " + e.getMessage(), e);
        }
    }

}
