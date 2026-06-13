package com.kama.jchatmind.coding.service;

import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.model.dto.AgentDTO;

public interface CodingPromptComposer {

    String composeSystemPrompt(String basePrompt, String taskSessionId, AgentDTO agentConfig);

    String composeForTask(CodingTask task, String basePrompt, AgentDTO agentConfig);

    /** 子 Agent 角色 Prompt：不含父 chat 历史，仅角色模板 + goal + constraints + contextFiles */
}
