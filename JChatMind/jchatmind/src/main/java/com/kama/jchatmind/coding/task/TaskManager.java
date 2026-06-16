package com.kama.jchatmind.coding.task;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 文件系统任务管理器（对齐 KamaClaude TaskManager）。
 * 任务以 JSON 文件存储在 .tasks/task_*.json 中。
 */
public class TaskManager {

    private final Path tasksDir;
    private int nextId;

    public TaskManager(Path tasksDir) {
        this.tasksDir = tasksDir;
        try {
            Files.createDirectories(tasksDir);
        } catch (IOException e) {
            throw new RuntimeException("无法创建 .tasks 目录: " + tasksDir, e);
        }
        this.nextId = maxId() + 1;
    }

    private int maxId() {
        try (Stream<Path> files = Files.list(tasksDir)) {
            return files
                    .filter(f -> f.getFileName().toString().matches("task_\\d+\\.json"))
                    .mapToInt(f -> {
                        String name = f.getFileName().toString();
                        return Integer.parseInt(name.substring(5, name.length() - 5));
                    })
                    .max().orElse(0);
        } catch (IOException e) {
            return 0;
        }
    }

    private Path taskPath(int id) {
        return tasksDir.resolve("task_" + id + ".json");
    }

    /** 创建新任务 */
    public AgentTask create(String subject, String description, List<Integer> blockedBy) {
        for (int depId : blockedBy != null ? blockedBy : List.<Integer>of()) {
            if (!Files.exists(taskPath(depId))) {
                throw new IllegalArgumentException("blocked_by task " + depId + " not found");
            }
        }
        String now = Instant.now().toString();
        AgentTask task = new AgentTask(nextId, subject, description, "pending",
                blockedBy != null ? blockedBy : List.of(), now, now);
        save(task);
        nextId++;
        return task;
    }

    /** 读取任务 */
    public AgentTask get(int taskId) {
        return load(taskId);
    }

    /** 更新任务状态或依赖 */
    public AgentTask update(int taskId, String status,
                            List<Integer> addBlockedBy, List<Integer> removeBlockedBy) {
        AgentTask task = load(taskId);
        if (status != null) {
            if (!status.equals("pending") && !status.equals("in_progress")
                    && !status.equals("completed")) {
                throw new IllegalArgumentException("invalid status: " + status);
            }
            task.setStatus(status);
            if ("completed".equals(status)) {
                clearDependency(taskId);
            }
        }
        if (addBlockedBy != null && !addBlockedBy.isEmpty()) {
            var merged = new java.util.LinkedHashSet<>(task.getBlockedBy());
            merged.addAll(addBlockedBy);
            task.getBlockedBy().clear();
            task.getBlockedBy().addAll(merged);
        }
        if (removeBlockedBy != null && !removeBlockedBy.isEmpty()) {
            task.getBlockedBy().removeAll(removeBlockedBy);
        }
        task.setUpdatedAt(Instant.now().toString());
        save(task);
        return task;
    }

    /** 列出所有任务，按 ID 升序 */
    public List<AgentTask> listAll() {
        List<AgentTask> tasks = new ArrayList<>();
        try (Stream<Path> files = Files.list(tasksDir)) {
            files.filter(f -> f.getFileName().toString().matches("task_\\d+\\.json"))
                    .sorted(Comparator.comparing(f -> {
                        String name = f.getFileName().toString();
                        return Integer.parseInt(name.substring(5, name.length() - 5));
                    }))
                    .forEach(f -> {
                        try {
                            int id = Integer.parseInt(f.getFileName().toString()
                                    .substring(5, f.getFileName().toString().length() - 5));
                            tasks.add(load(id));
                        } catch (Exception ignored) {}
                    });
        } catch (IOException ignored) {}
        return tasks;
    }

    /** 格式化任务列表 */
    public String formatList() {
        List<AgentTask> tasks = listAll();
        if (tasks.isEmpty()) return "No tasks.";
        StringBuilder sb = new StringBuilder();
        for (AgentTask t : tasks) {
            sb.append(t.formatLine()).append("\n");
        }
        return sb.toString().trim();
    }

    // ---- 内部 ----

    private void save(AgentTask task) {
        String json = String.format(
                "{\"id\":%d,\"subject\":\"%s\",\"description\":\"%s\",\"status\":\"%s\","
                        + "\"blocked_by\":%s,\"created_at\":\"%s\",\"updated_at\":\"%s\"}",
                task.getId(),
                escape(task.getSubject()),
                escape(task.getDescription()),
                task.getStatus(),
                blockedByJson(task.getBlockedBy()),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
        try {
            Files.writeString(taskPath(task.getId()), json);
        } catch (IOException e) {
            throw new RuntimeException("写入任务文件失败: " + task.getId(), e);
        }
    }

    private AgentTask load(int id) {
        Path path = taskPath(id);
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("task " + id + " not found");
        }
        try {
            String raw = Files.readString(path);
            return parse(raw);
        } catch (IOException e) {
            throw new RuntimeException("读取任务文件失败: " + id, e);
        }
    }

    /** 将 completed 任务从所有其他任务的 blocked_by 中移除 */
    private void clearDependency(int completedId) {
        for (AgentTask t : listAll()) {
            if (t.getId() != completedId && t.getBlockedBy().contains(completedId)) {
                t.getBlockedBy().remove(Integer.valueOf(completedId));
                t.setUpdatedAt(Instant.now().toString());
                save(t);
            }
        }
    }

    // 简易 JSON 解析（不引入 Jackson 依赖）
    private static AgentTask parse(String json) {
        int id = extractInt(json, "id");
        String subject = extractStr(json, "subject");
        String description = extractStr(json, "description");
        String status = extractStr(json, "status");
        List<Integer> blockedBy = extractIntList(json, "blocked_by");
        String createdAt = extractStr(json, "created_at");
        String updatedAt = extractStr(json, "updated_at");
        return new AgentTask(id, subject, description, status, blockedBy, createdAt, updatedAt);
    }

    private static int extractInt(String json, String key) {
        String search = "\"" + key + "\":";
        int start = json.indexOf(search) + search.length();
        int end = json.indexOf(",", start);
        if (end < 0) end = json.indexOf("}", start);
        String val = json.substring(start, end).trim();
        return Integer.parseInt(val);
    }

    private static String extractStr(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start < 0) return "";
        start += search.length();
        int end = start;
        while (end < json.length()) {
            if (json.charAt(end) == '"' && (end == 0 || json.charAt(end - 1) != '\\')) {
                break;
            }
            end++;
        }
        return json.substring(start, end).replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static List<Integer> extractIntList(String json, String key) {
        String search = "\"" + key + "\":[";
        int start = json.indexOf(search);
        if (start < 0) return List.of();
        start += search.length();
        int end = json.indexOf("]", start);
        if (end < 0) return List.of();
        String inner = json.substring(start, end).trim();
        if (inner.isEmpty()) return List.of();
        String[] parts = inner.split(",");
        return java.util.Arrays.stream(parts)
                .map(String::trim)
                .map(Integer::parseInt)
                .toList();
    }

    private static String blockedByJson(List<Integer> list) {
        if (list == null || list.isEmpty()) return "[]";
        return "[" + list.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("") + "]";
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}