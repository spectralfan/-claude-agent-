package com.kama.jchatmind.mcp.permission;

import com.kama.jchatmind.coding.context.CodingSessionContext;
import com.kama.jchatmind.session.event.EventBus;
import com.kama.jchatmind.session.event.PermissionRequestedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Component
public class PermissionManager {

    private static final Logger log = LoggerFactory.getLogger(PermissionManager.class);

    private static final Set<Pattern> DENY_PATTERNS = Set.of(
            Pattern.compile("rm\\s+-rf", Pattern.CASE_INSENSITIVE),
            Pattern.compile("shutdown|reboot|halt", Pattern.CASE_INSENSITIVE),
            Pattern.compile("format\\s+[a-z]:", Pattern.CASE_INSENSITIVE)
    );

    private static final Set<Pattern> ALLOW_PATTERNS = Set.of(
            Pattern.compile("^ls\\b"),
            Pattern.compile("^cat\\b"),
            Pattern.compile("^pwd\\b"),
            Pattern.compile("^find\\b"),
            Pattern.compile("^head\\b"),
            Pattern.compile("^tail\\b"),
            Pattern.compile("^wc\\b"),
            Pattern.compile("^grep\\b"),
            Pattern.compile("^test\\b"),
            Pattern.compile("^node\\s+--check\\b"),
            Pattern.compile("^python3?\\s+--version\\b"),
            Pattern.compile("^git\\s+status\\b"),
            Pattern.compile("^git\\s+log\\b")
    );

    private static final Set<String> ASK_TOOLS = Set.of(
            "execute_command", "write_coding_file", "append_coding_file"
    );

    private volatile String permissionMode = "ask";
    private final EventBus eventBus;
    private final Map<String, CompletableFuture<String>> pending = new ConcurrentHashMap<>();

    public PermissionManager(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    public String getMode() { return permissionMode; }

    public void setMode(String mode) {
        if ("auto".equals(mode) || "ask".equals(mode)) {
            this.permissionMode = mode;
            log.info("Permission mode set to: {}", mode);
        }
    }

    public String check(String toolName, String toolInput) {
        if ("execute_command".equals(toolName)) {
            for (Pattern p : DENY_PATTERNS) {
                if (p.matcher(toolInput).find()) {
                    return "命令被安全策略禁止: " + toolInput;
                }
            }
        }
        return null;
    }

    public boolean requestApproval(String toolUseId, String toolName, String toolInput, String paramPreview) {
        if ("auto".equals(permissionMode)) return true;

        CodingSessionContext.Context ctx = CodingSessionContext.get();
        String sessionId = ctx != null ? ctx.sessionId() : "unknown";

        String denied = check(toolName, toolInput);
        if (denied != null) return false;

        if ("execute_command".equals(toolName)) {
            for (Pattern p : ALLOW_PATTERNS) {
                if (p.matcher(toolInput).find()) return true;
            }
        }

        if (!ASK_TOOLS.contains(toolName)) return true;

        eventBus.publish(new PermissionRequestedEvent(
                toolUseId, toolName, paramPreview, sessionId, Instant.now().toString()));

        CompletableFuture<String> future = new CompletableFuture<>();
        pending.put(toolUseId, future);

        try {
            String decision = future.get(60, TimeUnit.SECONDS);
            return "allow".equals(decision) || "always_allow".equals(decision);
        } catch (Exception e) {
            pending.remove(toolUseId);
            return false;
        }
    }

    public void respond(String toolUseId, String decision) {
        CompletableFuture<String> future = pending.remove(toolUseId);
        if (future != null && !future.isDone()) {
            future.complete(decision);
        }
    }
}