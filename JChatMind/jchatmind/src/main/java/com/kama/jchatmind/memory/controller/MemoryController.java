package com.kama.jchatmind.memory.controller;

import com.kama.jchatmind.memory.config.MemoryProperties;
import com.kama.jchatmind.memory.model.dto.MemoryStatsDTO;
import com.kama.jchatmind.memory.service.MemoryService;
import com.kama.jchatmind.model.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/memory")
@RequiredArgsConstructor
public class MemoryController {

    private final MemoryService memoryService;
    private final MemoryProperties memoryProperties;

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", memoryProperties.isEnabled());
        body.put("workingWindowSize", memoryProperties.getWorkingWindowSize());
        body.put("recentMaxEntries", memoryProperties.getRecentMaxEntries());
        body.put("embeddingModel", memoryProperties.getEmbeddingModel());
        body.put("ollamaBaseUrl", memoryProperties.getOllamaBaseUrl());
        return ApiResponse.success(body);
    }

    @GetMapping("/stats/{sessionId}")
    public ApiResponse<MemoryStatsDTO> stats(@PathVariable String sessionId) {
        return ApiResponse.success(memoryService.getStats(sessionId));
    }
}
