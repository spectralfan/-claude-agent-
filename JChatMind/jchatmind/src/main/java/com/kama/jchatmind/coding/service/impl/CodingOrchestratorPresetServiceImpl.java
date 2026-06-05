package com.kama.jchatmind.coding.service.impl;

import com.kama.jchatmind.coding.config.CodingOrchestratorPresetProperties;
import com.kama.jchatmind.coding.model.dto.CodingAgentPresetDTO;
import com.kama.jchatmind.coding.service.AgentPresetBootstrapService;
import com.kama.jchatmind.coding.service.CodingOrchestratorPresetService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CodingOrchestratorPresetServiceImpl implements CodingOrchestratorPresetService {

    private final CodingOrchestratorPresetProperties properties;
    private final AgentPresetBootstrapService bootstrapService;

    @Override
    public void ensurePresetAgent() {
        if (!properties.isEnabled()) {
            return;
        }
        bootstrapService.ensurePreset(properties.getResource(), "Coding Orchestrator");
    }

    @Override
    public Optional<CodingAgentPresetDTO> findPreset() {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        return bootstrapService.findPreset("coding-orchestrator", properties.getResource());
    }
}
