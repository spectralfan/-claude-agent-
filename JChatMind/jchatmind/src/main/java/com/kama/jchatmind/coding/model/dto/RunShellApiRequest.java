package com.kama.jchatmind.coding.model.dto;

import lombok.Data;

@Data
public class RunShellApiRequest {
    private String command;
    private String sessionId;
}
