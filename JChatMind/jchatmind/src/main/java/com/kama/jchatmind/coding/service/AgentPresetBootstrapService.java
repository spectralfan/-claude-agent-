package com.kama.jchatmind.coding.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.coding.config.CodingProperties;
import com.kama.jchatmind.coding.model.dto.CodingAgentPresetDTO;
import com.kama.jchatmind.coding.model.dto.CodingAgentPresetDefinition;
import com.kama.jchatmind.converter.AgentConverter;
import com.kama.jchatmind.mapper.AgentMapper;
import com.kama.jchatmind.model.dto.AgentDTO;
import com.kama.jchatmind.model.entity.Agent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class AgentPresetBootstrapService {

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final AgentMapper agentMapper;
    private final AgentConverter agentConverter;
    private final CodingProperties codingProperties;

    public AgentPresetBootstrapService(ResourceLoader resourceLoader,
                                       ObjectMapper objectMapper,
                                       AgentMapper agentMapper,
                                       AgentConverter agentConverter,
                                       CodingProperties codingProperties) {
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
        this.agentMapper = agentMapper;
        this.agentConverter = agentConverter;
        this.codingProperties = codingProperties;
    }

    private final Map<String, CodingAgentPresetDTO> cache = new ConcurrentHashMap<>();

    public void ensurePreset(String resourcePath, String logLabel) {
        try {
            CodingAgentPresetDefinition def = loadDefinition(resourcePath);
            Agent existing = agentMapper.selectByName(def.getName());
            if (existing != null) {
                syncAllowedToolsIfNeeded(existing, def);
                syncMessageLengthIfNeeded(existing);
                cachePreset(existing, def);
                log.info("{} 预设已存在: {} ({})", logLabel, existing.getName(), existing.getId());
                return;
            }
            AgentDTO dto = AgentDTO.builder()
                    .name(def.getName())
                    .description(def.getDescription())
                    .systemPrompt(def.getSystemPrompt())
                    .model(AgentDTO.ModelType.fromModelName(def.getModel()))
                    .allowedTools(def.getAllowedTools())
                    .allowedKbs(def.getAllowedKbs())
                    .chatOptions(codingChatOptions())
                    .build();
            Agent agent = agentConverter.toEntity(dto);
            LocalDateTime now = LocalDateTime.now();
            agent.setCreatedAt(now);
            agent.setUpdatedAt(now);
            agentMapper.insert(agent);
            cachePreset(agent, def);
            log.info("已创建 {} 预设: {} ({})", logLabel, agent.getName(), agent.getId());
        } catch (Exception e) {
            log.warn("初始化 {} 预设失败: {}", logLabel, e.getMessage());
        }
    }

    public Optional<CodingAgentPresetDTO> findPreset(String presetKey, String resourcePath) {
        CodingAgentPresetDTO cached = cache.get(presetKey);
        if (cached != null) {
            return Optional.of(cached);
        }
        try {
            CodingAgentPresetDefinition def = loadDefinition(resourcePath);
            Agent existing = agentMapper.selectByName(def.getName());
            if (existing == null) {
                return Optional.empty();
            }
            cachePreset(existing, def);
            return Optional.ofNullable(cache.get(presetKey));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private AgentDTO.ChatOptions codingChatOptions() {
        AgentDTO.ChatOptions defaults = AgentDTO.ChatOptions.defaultOptions();
        return AgentDTO.ChatOptions.builder()
                .temperature(defaults.getTemperature())
                .topP(defaults.getTopP())
                .messageLength(codingProperties.getAgent().getMemoryWindow())
                .build();
    }

    private void syncMessageLengthIfNeeded(Agent existing) {
        int target = codingProperties.getAgent().getMemoryWindow();
        try {
            AgentDTO dto = agentConverter.toDTO(existing);
            Integer current = dto.getChatOptions() != null ? dto.getChatOptions().getMessageLength() : null;
            if (current != null && current >= target) {
                return;
            }
            AgentDTO.ChatOptions options = dto.getChatOptions() != null
                    ? dto.getChatOptions()
                    : AgentDTO.ChatOptions.defaultOptions();
            options.setMessageLength(target);
            dto.setChatOptions(options);
            Agent updated = agentConverter.toEntity(dto);
            updated.setId(existing.getId());
            updated.setCreatedAt(existing.getCreatedAt());
            updated.setUpdatedAt(LocalDateTime.now());
            agentMapper.updateById(updated);
            log.info("已同步 {} 预设 messageLength → {}", existing.getName(), target);
        } catch (Exception e) {
            log.warn("同步 {} 预设 messageLength 失败: {}", existing.getName(), e.getMessage());
        }
    }

    private void syncAllowedToolsIfNeeded(Agent existing, CodingAgentPresetDefinition def) {
        if (def.getAllowedTools() == null || def.getAllowedTools().isEmpty()) {
            return;
        }
        try {
            AgentDTO dto = agentConverter.toDTO(existing);
            if (def.getAllowedTools().equals(dto.getAllowedTools())) {
                return;
            }
            dto.setAllowedTools(def.getAllowedTools());
            Agent updated = agentConverter.toEntity(dto);
            updated.setId(existing.getId());
            updated.setCreatedAt(existing.getCreatedAt());
            updated.setUpdatedAt(LocalDateTime.now());
            agentMapper.updateById(updated);
            log.info("已同步 {} 预设工具白名单", existing.getName());
        } catch (Exception e) {
            log.warn("同步 {} 预设工具白名单失败: {}", existing.getName(), e.getMessage());
        }
    }

    private void cachePreset(Agent agent, CodingAgentPresetDefinition def) {
        cache.put(def.getPresetKey(), CodingAgentPresetDTO.builder()
                .presetKey(def.getPresetKey())
                .agentId(agent.getId())
                .name(agent.getName())
                .description(agent.getDescription())
                .model(agent.getModel())
                .allowedTools(def.getAllowedTools())
                .build());
    }

    private CodingAgentPresetDefinition loadDefinition(String resourcePath) throws Exception {
        Resource resource = resourceLoader.getResource(resourcePath);
        try (InputStream in = resource.getInputStream()) {
            return objectMapper.readValue(in, CodingAgentPresetDefinition.class);
        }
    }
}
