package com.kama.jchatmind.coding.model.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CommandExecutionResult {
    private int exitCode;
    private String output;
    private boolean timeout;
}
