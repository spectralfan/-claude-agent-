package com.kama.jchatmind.agent.profile;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AgentProfileService {

    private final AgentProfileLoader loader;

    public AgentProfileService(AgentProfileLoader loader) {
        this.loader = loader;
    }

    public Optional<AgentProfile> getProfile(String name) {
        return Optional.ofNullable(loader.getProfile(name));
    }

    public AgentProfile getSchedulerProfile() {
        return loader.getProfile("scheduler");
    }

    public AgentProfile getWorkerProfile() {
        return loader.getProfile("worker");
    }

    public AgentProfile getReviewerProfile() {
        return loader.getProfile("reviewer");
    }

    public List<String> getAllowedTools(String profileName) {
        AgentProfile profile = loader.getProfile(profileName);
        return profile != null ? profile.getAllowedTools() : List.of();
    }

    public boolean isValidRole(String roleName) {
        return loader.hasProfile(roleName);
    }
}