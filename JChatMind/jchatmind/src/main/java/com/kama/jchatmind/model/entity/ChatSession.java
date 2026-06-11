package com.kama.jchatmind.model.entity;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatSession {
    private String id;
    private String agentId;
    private String title;
    /** CHAT / CODING */
    private String type;
    private String metadata;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Override
    public boolean equals(Object that) {
        if (this == that) return true;
        if (that == null) return false;
        if (getClass() != that.getClass()) return false;
        ChatSession other = (ChatSession) that;
        return eq(this.getId(), other.getId())
            && eq(this.getAgentId(), other.getAgentId())
            && eq(this.getTitle(), other.getTitle())
            && eq(this.getType(), other.getType())
            && eq(this.getMetadata(), other.getMetadata())
            && eq(this.getCreatedAt(), other.getCreatedAt())
            && eq(this.getUpdatedAt(), other.getUpdatedAt());
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + hash(getId());
        result = 31 * result + hash(getAgentId());
        result = 31 * result + hash(getTitle());
        result = 31 * result + hash(getType());
        result = 31 * result + hash(getMetadata());
        result = 31 * result + hash(getCreatedAt());
        result = 31 * result + hash(getUpdatedAt());
        return result;
    }

    private static boolean eq(Object a, Object b) { return a == null ? b == null : a.equals(b); }
    private static int hash(Object o) { return o != null ? o.hashCode() : 0; }

    @Override
    public String toString() {
        return "ChatSession{id=" + id + ", agentId=" + agentId + ", title=" + title
                + ", type=" + type + ", createdAt=" + createdAt + "}";
    }
}