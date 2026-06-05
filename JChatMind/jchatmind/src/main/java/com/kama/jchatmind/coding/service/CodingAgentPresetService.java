package com.kama.jchatmind.coding.service;

import com.kama.jchatmind.coding.model.dto.CodingAgentPresetDTO;

import java.util.Optional;

public interface CodingAgentPresetService {

    /** 启动时确保预设 Agent 存在 */
    void ensurePresetAgent();

    Optional<CodingAgentPresetDTO> findPreset();
}
