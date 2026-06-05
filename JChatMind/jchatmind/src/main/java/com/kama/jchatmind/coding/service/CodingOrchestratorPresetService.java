package com.kama.jchatmind.coding.service;

import com.kama.jchatmind.coding.model.dto.CodingAgentPresetDTO;

import java.util.Optional;

public interface CodingOrchestratorPresetService {

    void ensurePresetAgent();

    Optional<CodingAgentPresetDTO> findPreset();
}
