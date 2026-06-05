package com.kama.jchatmind.coding.model.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FileNode {
    private String name;
    private String relativePath;
    private boolean directory;
}
