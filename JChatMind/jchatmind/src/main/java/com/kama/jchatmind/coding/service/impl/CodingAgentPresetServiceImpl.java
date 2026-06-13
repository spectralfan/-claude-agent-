package com.kama.jchatmind.coding.service.impl;

import com.kama.jchatmind.coding.config.CodingAgentPresetProperties;
import com.kama.jchatmind.coding.model.dto.CodingAgentPresetDTO;
import com.kama.jchatmind.coding.service.AgentPresetBootstrapService;
import com.kama.jchatmind.coding.service.CodingAgentPresetService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CodingAgentPresetServiceImpl implements CodingAgentPresetService {

    private final CodingAgentPresetProperties properties;
    private final AgentPresetBootstrapService bootstrapService;

    @Override
    public void ensurePresetAgent() {
        if (!properties.isEnabled()) {
            return;
        }
        bootstrapService.ensurePreset(properties.getResource(), "Coding Agent");
        bootstrapService.ensurePreset(properties.getMainAgentResource(), "Main Agent");
    }

    @Override
    public Optional<CodingAgentPresetDTO> findPreset() {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        return bootstrapService.findPreset("coding-agent", properties.getResource());
    }
}
