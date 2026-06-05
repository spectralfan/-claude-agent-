package com.kama.jchatmind.memory.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Memory Hub 通用工具：JSON 序列化、pgvector 字面量、token 估算、SHA-256。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemorySupport {

    private final ObjectMapper objectMapper;

    /** 对象序列化为 JSON 字符串；null 返回 null。 */
    public String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Memory Hub 序列化 JSON 失败: {}", e.getMessage());
            return null;
        }
    }

    /** float[] 转 pgvector 字面量，形如 [0.1,0.2,...]。 */
    public String toVectorLiteral(float[] vector) {
        if (vector == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            sb.append(vector[i]);
            if (i < vector.length - 1) {
                sb.append(',');
            }
        }
        return sb.append(']').toString();
    }

    /**
     * 粗略 token 估算（保守偏大，避免超出上下文窗口）。
     * 中英文混合时按约每 2 个字符 1 token 估算。
     */
    public int estimateTokens(String text) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        return (int) Math.ceil(text.length() / 2.0);
    }

    /** 内容 SHA-256，用于向量化去重。 */
    public String sha256(String content) {
        if (content == null) {
            content = "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
