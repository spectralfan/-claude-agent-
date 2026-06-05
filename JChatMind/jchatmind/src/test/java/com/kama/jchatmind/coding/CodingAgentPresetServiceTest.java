package com.kama.jchatmind.coding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.coding.config.CodingAgentPresetProperties;
import com.kama.jchatmind.coding.config.CodingProperties;
import com.kama.jchatmind.coding.model.dto.CodingAgentPresetDefinition;
import com.kama.jchatmind.coding.service.AgentPresetBootstrapService;
import com.kama.jchatmind.coding.service.impl.CodingAgentPresetServiceImpl;
import com.kama.jchatmind.converter.AgentConverter;
import com.kama.jchatmind.mapper.AgentMapper;
import com.kama.jchatmind.model.entity.Agent;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CodingAgentPresetServiceTest {

    @Test
    void ensurePresetAgent_shouldInsertWhenMissing() throws Exception {
        AgentMapper agentMapper = mock(AgentMapper.class);
        AgentConverter agentConverter = mock(AgentConverter.class);
        when(agentMapper.selectByName("Claude Code Coding Agent")).thenReturn(null);
        when(agentMapper.insert(any())).thenAnswer(inv -> {
            Agent a = inv.getArgument(0);
            a.setId("new-agent-id");
            return 1;
        });
        when(agentConverter.toEntity(any())).thenReturn(Agent.builder().name("Claude Code Coding Agent").build());

        CodingAgentPresetProperties props = new CodingAgentPresetProperties();
        props.setResource("classpath:coding-agent-preset.json");
        ObjectMapper mapper = new ObjectMapper();
        AgentPresetBootstrapService bootstrap = new AgentPresetBootstrapService(
                new DefaultResourceLoader(), mapper, agentMapper, agentConverter, new CodingProperties());

        CodingAgentPresetServiceImpl service = new CodingAgentPresetServiceImpl(props, bootstrap);

        service.ensurePresetAgent();

        verify(agentMapper).insert(any());
        assertTrue(service.findPreset().isPresent());
        assertEquals("new-agent-id", service.findPreset().get().getAgentId());
    }

    @Test
    void presetDefinition_shouldContainCoreTools() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (var in = getClass().getClassLoader().getResourceAsStream("coding-agent-preset.json")) {
            CodingAgentPresetDefinition def = mapper.readValue(in, CodingAgentPresetDefinition.class);
            assertTrue(def.getAllowedTools().contains("coding_file_tools"));
            assertTrue(def.getAllowedTools().contains("mark_coding_complete"));
            assertTrue(def.getAllowedTools().contains("run_terminal_cmd"));
        }
    }
}
