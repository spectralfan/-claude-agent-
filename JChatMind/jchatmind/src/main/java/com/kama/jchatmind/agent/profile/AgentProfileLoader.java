package com.kama.jchatmind.agent.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@Component
public class AgentProfileLoader {

    private static final Logger log = LoggerFactory.getLogger(AgentProfileLoader.class);
    private static final String LOCATION_PATTERN = "classpath:agent-profiles/*.yaml";

    private final Map<String, AgentProfile> profiles = new HashMap<>();
    private final ObjectMapper yamlMapper;

    public AgentProfileLoader() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }

    @PostConstruct
    public void loadProfiles() {
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources(LOCATION_PATTERN);
            for (Resource resource : resources) {
                try (InputStream is = resource.getInputStream()) {
                    AgentProfile profile = yamlMapper.readValue(is, AgentProfile.class);
                    if (profile.getName() != null && !profile.getName().isBlank()) {
                        profiles.put(profile.getName(), profile);
                        log.info("Loaded agent profile: {} ({})", profile.getName(), resource.getFilename());
                    } else {
                        log.warn("Skipped profile without name: {}", resource.getFilename());
                    }
                } catch (IOException e) {
                    log.warn("Failed to load agent profile: {}", resource.getFilename(), e);
                }
            }
            log.info("Loaded {} agent profiles: {}", profiles.size(), profiles.keySet());
        } catch (IOException e) {
            log.error("Failed to scan agent profiles", e);
        }
    }

    public AgentProfile getProfile(String name) { return profiles.get(name); }
    public boolean hasProfile(String name) { return profiles.containsKey(name); }
    public Map<String, AgentProfile> getAllProfiles() { return Map.copyOf(profiles); }
}