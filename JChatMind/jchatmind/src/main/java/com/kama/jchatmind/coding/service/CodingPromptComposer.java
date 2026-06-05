package com.kama.jchatmind.coding.service;

import com.kama.jchatmind.coding.model.entity.CodingTask;

public interface CodingPromptComposer {

    String composeSystemPrompt(String basePrompt, String taskSessionId);

    String composeForTask(CodingTask task, String basePrompt);
}
