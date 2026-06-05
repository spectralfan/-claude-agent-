package com.kama.jchatmind.coding.controller;

import com.kama.jchatmind.coding.model.dto.CodingAgentPresetDTO;
import com.kama.jchatmind.coding.service.CodingAgentPresetService;
import com.kama.jchatmind.coding.service.CodingOrchestratorPresetService;
import com.kama.jchatmind.model.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/coding/agents")
@RequiredArgsConstructor
public class CodingAgentController {

    private final CodingAgentPresetService codingAgentPresetService;
    private final CodingOrchestratorPresetService codingOrchestratorPresetService;

    @GetMapping("/preset")
    public ApiResponse<CodingAgentPresetDTO> getPreset() {
        return ApiResponse.success(codingAgentPresetService.findPreset().orElse(null));
    }

    @GetMapping("/orchestrator-preset")
    public ApiResponse<CodingAgentPresetDTO> getOrchestratorPreset() {
        return ApiResponse.success(codingOrchestratorPresetService.findPreset().orElse(null));
    }
}
