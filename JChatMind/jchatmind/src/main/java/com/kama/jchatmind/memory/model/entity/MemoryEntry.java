package com.kama.jchatmind.memory.model.entity;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 记忆条目实体，对应表 t_memory_entry。
 * JSON 列（tool_calls / metadata）以 String 形式承载，由上层 Converter 序列化。
 */
@Data
@Builder
public class MemoryEntry {

    private String id;

    private String sessionId;

    /** WORKING / RECENT / ARCHIVE */
    private String memoryType;

    /** user / assistant / system / tool */
    private String role;

    private String content;

    private String summary;

    private Integer importance;

    /** PostgreSQL TEXT[] */
    private List<String> memoryTags;

    /** JSON string */
    private String toolCalls;

    /** JSON string */
    private String metadata;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime archivedAt;
}
