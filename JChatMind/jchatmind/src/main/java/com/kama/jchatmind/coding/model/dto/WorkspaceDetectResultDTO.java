package com.kama.jchatmind.coding.model.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WorkspaceDetectResultDTO {
    private String stackId;
    private String displayName;
    private String language;
    private String matchedFile;
    private boolean emptyWorkspace;
}
