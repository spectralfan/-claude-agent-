package com.kama.jchatmind.coding.bootstrap;

import com.kama.jchatmind.coding.service.CodingAgentPresetService;
import com.kama.jchatmind.coding.service.CodingOrchestratorPresetService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
@RequiredArgsConstructor
public class CodingAgentPresetInitializer implements ApplicationRunner {

    private final CodingAgentPresetService codingAgentPresetService;
    private final CodingOrchestratorPresetService codingOrchestratorPresetService;

    @Override
    public void run(ApplicationArguments args) {
        codingAgentPresetService.ensurePresetAgent();
        codingOrchestratorPresetService.ensurePresetAgent();
    }
}
