package com.kama.jchatmind.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DeepSeekApiKeyStartupValidator implements ApplicationRunner {

    @Value("${spring.ai.deepseek.api-key:}")
    private String apiKey;

    @Override
    public void run(ApplicationArguments args) {
        if (apiKey == null || apiKey.isBlank()) {
            log.error(
                    "DeepSeek API Key 未配置。请设置 DEEPSEEK_API_KEY（用户或系统环境变量），"
                            + "或复制 application-local.yaml.example 为 application-local.yaml；"
                            + "Windows 可用 scripts/run-backend.ps1 从系统环境变量注入。");
            return;
        }
        if (apiKey.contains("$") || apiKey.contains("{") || apiKey.contains("}")) {
            log.error(
                    "DeepSeek API Key 似未正确解析（含占位符字符）。"
                            + "请确认 JVM 能读取 DEEPSEEK_API_KEY，并在修改环境变量后完全重启 IDE。");
            return;
        }
        if (!apiKey.startsWith("sk-")) {
            log.warn("DeepSeek API Key 非常见 sk- 格式（length={}），若出现 401 请核对 Key。", apiKey.length());
        } else {
            log.info("DeepSeek API Key 已加载（length={}）", apiKey.length());
        }
    }
}
