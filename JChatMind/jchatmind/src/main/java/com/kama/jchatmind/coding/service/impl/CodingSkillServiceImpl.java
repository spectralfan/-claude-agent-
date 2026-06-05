package com.kama.jchatmind.coding.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.coding.model.dto.CodingSkillDTO;
import com.kama.jchatmind.coding.service.CodingSkillService;
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
public class CodingSkillServiceImpl implements CodingSkillService {

    private static final String SKILL_PATTERN = "classpath:coding-skills/*.json";

    private final ObjectMapper objectMapper;
    private Map<String, CodingSkillDTO> skillsById = Map.of();

    @PostConstruct
    void loadSkills() {
        Map<String, CodingSkillDTO> loaded = new LinkedHashMap<>();
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources(SKILL_PATTERN);
            for (Resource resource : resources) {
                try (InputStream in = resource.getInputStream()) {
                    CodingSkillDTO skill = objectMapper.readValue(in, CodingSkillDTO.class);
                    if (skill.getId() == null || skill.getId().isBlank()) {
                        log.warn("跳过无 id 的 Skill: {}", resource.getFilename());
                        continue;
                    }
                    loaded.put(skill.getId(), skill);
                }
            }
        } catch (Exception e) {
            log.warn("加载 Coding Skills 失败: {}", e.getMessage());
        }
        skillsById = Collections.unmodifiableMap(loaded);
        log.info("已加载 {} 个 Coding Skill", skillsById.size());
    }

    @Override
    public List<CodingSkillDTO> listSkills() {
        return new ArrayList<>(skillsById.values());
    }

    @Override
    public Optional<CodingSkillDTO> findById(String skillId) {
        if (skillId == null || skillId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(skillsById.get(skillId));
    }
}
