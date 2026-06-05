package com.kama.jchatmind.coding.model.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CodingRuntimeStatusDTO {
    private boolean mcpEnabled;
    private boolean springMcpClientEnabled;
    private boolean deliveryRequireVerification;
}
