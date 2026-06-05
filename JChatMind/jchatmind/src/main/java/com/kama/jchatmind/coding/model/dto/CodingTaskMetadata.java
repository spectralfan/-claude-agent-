package com.kama.jchatmind.coding.model.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.coding.model.enums.CodingApprovalMode;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CodingTaskMetadata {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private String skillId;
    private String stackId;
    private String language;
    private CodingApprovalMode approvalMode;

    public static CodingTaskMetadata fromJson(String json) {
        if (json == null || json.isBlank()) {
            return CodingTaskMetadata.builder().build();
        }
        try {
            return MAPPER.readValue(json, CodingTaskMetadata.class);
        } catch (JsonProcessingException e) {
            return CodingTaskMetadata.builder().build();
        }
    }

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
