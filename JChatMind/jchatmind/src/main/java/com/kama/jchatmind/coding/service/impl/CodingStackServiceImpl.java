package com.kama.jchatmind.coding.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.coding.model.dto.CodingStackDTO;
import com.kama.jchatmind.coding.service.CodingStackService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodingStackServiceImpl implements CodingStackService {

    private static final String STACK_PATTERN = "classpath:coding-stacks/*.json";

    private final ObjectMapper objectMapper;
    private Map<String, CodingStackDTO> stacksById = Map.of();

    @PostConstruct
    void loadStacks() {
        Map<String, CodingStackDTO> loaded = new LinkedHashMap<>();
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources(STACK_PATTERN);
            for (Resource resource : resources) {
                try (InputStream in = resource.getInputStream()) {
                    CodingStackDTO stack = objectMapper.readValue(in, CodingStackDTO.class);
                    if (stack.getId() == null || stack.getId().isBlank()) {
                        log.warn("跳过无 id 的 Stack: {}", resource.getFilename());
                        continue;
                    }
                    loaded.put(stack.getId(), stack);
                }
            }
        } catch (Exception e) {
            log.warn("加载 Coding Stacks 失败: {}", e.getMessage());
        }
        stacksById = Collections.unmodifiableMap(loaded);
        log.info("已加载 {} 个 Coding Stack", stacksById.size());
    }

    @Override
    public List<CodingStackDTO> listStacks() {
        return new ArrayList<>(stacksById.values());
    }

    @Override
    public Optional<CodingStackDTO> findById(String stackId) {
        if (stackId == null || stackId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(stacksById.get(stackId));
    }
}
