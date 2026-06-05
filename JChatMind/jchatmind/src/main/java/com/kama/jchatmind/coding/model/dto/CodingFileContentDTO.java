package com.kama.jchatmind.coding.model.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CodingFileContentDTO {
    private String relativePath;
    private String content;
    private long size;
    private boolean truncated;
    /** 由扩展名推断的语言标识，供前端展示 */
    private String language;
}
