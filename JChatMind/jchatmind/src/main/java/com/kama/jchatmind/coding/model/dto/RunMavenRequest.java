package com.kama.jchatmind.coding.model.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RunMavenRequest {

    private String taskId;

    private String sessionId;

    private String agentId;

    private MavenGoal goal;

    /** 仅 goal=TEST_SINGLE 时使用，格式建议 com.foo.BarTest */
    private String testPattern;
}
