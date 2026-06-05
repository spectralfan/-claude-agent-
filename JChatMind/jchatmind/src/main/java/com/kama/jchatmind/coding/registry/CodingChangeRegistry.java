package com.kama.jchatmind.coding.registry;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CodingChangeRegistry {

    private final Map<String, List<String>> changedFilesByTask = new ConcurrentHashMap<>();

    public void recordChange(String taskId, String relativePath, String changeType) {
        if (taskId == null || relativePath == null || relativePath.isBlank()) {
            return;
        }
        changedFilesByTask
                .computeIfAbsent(taskId, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(relativePath + " (" + (changeType != null ? changeType : "modified") + ")");
    }

    public List<String> getChangedFiles(String taskId) {
        List<String> list = changedFilesByTask.get(taskId);
        if (list == null) {
            return List.of();
        }
        synchronized (list) {
            return List.copyOf(list);
        }
    }

    public void clear(String taskId) {
        changedFilesByTask.remove(taskId);
    }
}
