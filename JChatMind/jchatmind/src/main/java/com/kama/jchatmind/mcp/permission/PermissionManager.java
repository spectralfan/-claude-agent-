package com.kama.jchatmind.mcp.permission;

import com.kama.jchatmind.coding.context.CodingSessionContext;
import com.kama.jchatmind.session.event.EventBus;
import com.kama.jchatmind.session.event.PermissionDeniedEvent;
import com.kama.jchatmind.session.event.PermissionGrantedEvent;
import com.kama.jchatmind.session.event.PermissionRequestedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * KamaClaude 风格权限管理器。
 * 4 层策略评估：deny_patterns → outside_cwd → session cache → persistent cache → allow_patterns → tool default
 * 支持 Future 挂起异步审批、session 级 + persistent 缓存、超时、断线保护。
 */
@Component
public class PermissionManager {

    private static final Logger log = LoggerFactory.getLogger(PermissionManager.class);

    // OUTSIDE_CWD 启发式规则
    private static final List<Pattern> OUTSIDE_CWD_HEURISTICS = List.of(
            Pattern.compile("(^|\\s)/[^\\s]"),
            Pattern.compile("(^|\\s)~"),
            Pattern.compile("(^|\\s)\\.\\.(/|$|\\s)"),
            Pattern.compile("\\$\\{?HOME\\b"),
            Pattern.compile("\\$\\{?PWD\\b"),
            Pattern.compile("(^|\\s|;|&&|\\|\\|)cd(\\s|$)")
    );

    private static final Map<String, ToolPolicy> DEFAULT_POLICIES;

    static {
        Map<String, ToolPolicy> m = new LinkedHashMap<>();
        m.put("execute_command", new ToolPolicy(ToolPolicy.Default.ASK, List.of(), List.of()));
        m.put("write_coding_file", new ToolPolicy(ToolPolicy.Default.ASK, List.of(), List.of()));
        m.put("append_coding_file", new ToolPolicy(ToolPolicy.Default.ASK, List.of(), List.of()));
        m.put("read_coding_file", ToolPolicy.allowDefault());
        m.put("list_coding_directory_tree", ToolPolicy.allowDefault());
        m.put("task_create", ToolPolicy.allowDefault());
        m.put("task_update", ToolPolicy.allowDefault());
        m.put("task_list", ToolPolicy.allowDefault());
        m.put("task_get", ToolPolicy.allowDefault());
        m.put("spawn_agent", ToolPolicy.allowDefault());
        m.put("read_coding_files", ToolPolicy.allowDefault());
        m.put("agent_result", ToolPolicy.allowDefault());
        m.put("save_note", ToolPolicy.allowDefault());
        DEFAULT_POLICIES = Collections.unmodifiableMap(m);
    }

    private volatile String permissionMode = "ask";
    private final EventBus eventBus;

    private final Map<String, CompletableFuture<String>> pending = new ConcurrentHashMap<>();
    private final Map<String, String> sessionAlways = new ConcurrentHashMap<>();
    private final Map<String, String> persistentAlways = new ConcurrentHashMap<>();
    private Path policyFile;

    public PermissionManager(EventBus eventBus) {
        this.eventBus = eventBus;
        this.policyFile = Path.of(".jchatmind", "policy.json");
        loadPolicyFile();
    }

    /** 4 层静态评估 */
    public String evaluate(String toolName, String toolInput) {
        ToolPolicy policy = DEFAULT_POLICIES.get(toolName);
        String command = "execute_command".equals(toolName) ? toolInput : "";

        if (policy != null) {
            for (String pat : policy.getDenyPatterns()) {
                if (Pattern.compile(pat, Pattern.CASE_INSENSITIVE).matcher(command).find())
                    return "auto_deny";
            }
        }

        if (!command.isEmpty() && matchesOutsideCwd(command)) return "ask";

        CodingSessionContext.Context ctx = CodingSessionContext.get();
        String sessionId = ctx != null ? ctx.sessionId() : null;
        if (sessionId != null) {
            String key = sessionId + ":" + toolName;
            String cached = sessionAlways.get(key);
            if (cached != null) return "auto_" + cached;
        }

        String pcache = persistentAlways.get(toolName);
        if (pcache != null) return "auto_" + pcache;

        if (policy != null) {
            for (String pat : policy.getAllowPatterns()) {
                if (Pattern.compile(pat, Pattern.CASE_INSENSITIVE).matcher(command).find())
                    return "auto_allow";
            }
            return switch (policy.getDefaultDecision()) {
                case ALLOW -> "auto_allow";
                case DENY -> "auto_deny";
                case ASK -> "ask";
            };
        }
        return "ask";
    }

    /** 请求审批（可能阻塞） */
    public boolean requestApproval(String toolUseId, String toolName, String toolInput, String paramPreview) {
        if ("auto".equals(permissionMode)) return true;

        CodingSessionContext.Context ctx = CodingSessionContext.get();
        String sessionId = ctx != null ? ctx.sessionId() : "unknown";

        String decision = evaluate(toolName, toolInput);
        if (!"ask".equals(decision)) return decision.contains("allow");

        eventBus.publish(new PermissionRequestedEvent(
                toolUseId, toolName, paramPreview, sessionId, Instant.now().toString()));

        CompletableFuture<String> future = new CompletableFuture<>();
        pending.put(toolUseId, future);
        try {
            String raw = future.get(60, TimeUnit.SECONDS);
            return applyResponse(raw, sessionId, toolName);
        } catch (Exception e) {
            pending.remove(toolUseId);
            return false;
        }
    }

    /** 前端审批响应 */
    public void respond(String toolUseId, String decision) {
        CompletableFuture<String> future = pending.remove(toolUseId);
        if (future != null && !future.isDone()) future.complete(decision);
    }

    /** 断线时取消所有待审批请求 */
    public void cancelSession(String sessionId) {
        List<String> toCancel = new ArrayList<>(pending.keySet());
        for (String uid : toCancel) {
            CompletableFuture<String> f = pending.remove(uid);
            if (f != null && !f.isDone()) f.complete("deny_once");
        }
    }

    public String getMode() { return permissionMode; }
    public void setMode(String mode) {
        if ("auto".equals(mode) || "ask".equals(mode)) {
            this.permissionMode = mode;
            log.info("Permission mode set to: {}", mode);
        }
    }

    static boolean matchesOutsideCwd(String command) {
        return OUTSIDE_CWD_HEURISTICS.stream().anyMatch(p -> p.matcher(command).find());
    }

    private boolean applyResponse(String decision, String sessionId, String toolName) {
        boolean allow = "allow_once".equals(decision) || "always_allow".equals(decision);
        if ("always_allow".equals(decision)) {
            sessionAlways.put(sessionId + ":" + toolName, "allow");
            persistentAlways.put(toolName, "allow");
            savePolicyFile();
        } else if ("always_deny".equals(decision)) {
            sessionAlways.put(sessionId + ":" + toolName, "deny");
            persistentAlways.put(toolName, "deny");
            savePolicyFile();
        }
        return allow;
    }

    private void loadPolicyFile() {
        if (policyFile == null || !Files.exists(policyFile)) return;
        try {
            String json = Files.readString(policyFile);
            String stripped = json.replaceAll("[{}\"]", "").trim();
            if (!stripped.isEmpty()) {
                for (String pair : stripped.split(",")) {
                    String[] kv = pair.split("\\s*:\\s*");
                    if (kv.length == 2)
                        persistentAlways.put(kv[0].trim(), kv[1].trim());
                }
            }
        } catch (Exception ignored) {}
    }

    private void savePolicyFile() {
        if (policyFile == null) return;
        try {
            Files.createDirectories(policyFile.getParent());
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (var e : persistentAlways.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(e.getKey()).append("\":\"").append(e.getValue()).append("\"");
                first = false;
            }
            sb.append("}");
            Files.writeString(policyFile, sb.toString());
        } catch (Exception ignored) {}
    }
}