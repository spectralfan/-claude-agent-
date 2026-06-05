package com.kama.jchatmind.coding.model.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StackVerifyCommandDTO {
    private String label;
    /** maven | shell */
    private String type;
    /** maven 类型时使用 */
    private String goal;
    /** shell 类型时使用，如 pytest -q */
    private String command;
}
