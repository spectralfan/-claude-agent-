package com.kama.jchatmind.coding.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "coding.reviewer-preset")
public class CodingReviewerPresetProperties {

    private boolean enabled = true;
    private String resource = "classpath:coding-reviewer-preset.json";
}
