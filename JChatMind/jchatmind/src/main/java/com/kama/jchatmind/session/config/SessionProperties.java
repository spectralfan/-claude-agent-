package com.kama.jchatmind.session.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Session 存储配置。
 *
 * <p>控制 thread.jsonl / notes.md 等会话文件的存储根目录。</p>
 */
@Component
@ConfigurationProperties(prefix = "session")
public class SessionProperties {

    /** 会话文件存储根目录，默认 .jchatmind/sessions */
    private String storeRoot = ".jchatmind/sessions";

    public String getStoreRoot() {
        return storeRoot;
    }

    public void setStoreRoot(String storeRoot) {
        this.storeRoot = storeRoot;
    }
}