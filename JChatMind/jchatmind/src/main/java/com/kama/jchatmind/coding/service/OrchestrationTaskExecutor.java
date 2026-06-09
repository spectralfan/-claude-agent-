package com.kama.jchatmind.coding.service;

import com.kama.jchatmind.coding.model.dto.OrchestrationTaskDTO;

public interface OrchestrationTaskExecutor {

    void execute(OrchestrationTaskDTO task, Runnable onFinally);
}
