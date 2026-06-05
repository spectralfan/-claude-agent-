package com.kama.jchatmind.coding.model.dto;

import lombok.Data;

@Data
public class RunMavenApiRequest {
    private String goal;
    private String testPattern;
    private String sessionId;
    private String agentId;
}
