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

    // 硬拒命令模式
    private static final Set<Pattern> DENY_PATTERNS = Set.of(
            Pattern.compile("rm\\s+-rf", Pattern.CASE_INSENSITIVE),
            Pattern.compile("shutdown|reboot|halt", Pattern.CASE_INSENSITIVE),
            Pattern.compile("format\\s+[a-z]:", Pattern.CASE_INSENSITIVE)
    );

    // 自动放行命令模式（仅 execute_command）
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

    // 需要审批的工具
    private static final Set<String> ASK_TOOLS = Set.of(
            "execute_command", "write_coding_file", "append_coding_file"
    );

    private final EventBus eventBus;
    private final Map<String, CompletableFuture<String>> pending = new ConcurrentHashMap<>();

    public PermissionManager(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * 检查工具调用是否需要审批.
     * @return null 表示放行, 非 null 字符串表示拒绝原因
     */
    public String check(String toolName, String toolInput) {
        // 1. deny_patterns
        if ("execute_command".equals(toolName)) {
            for (Pattern p : DENY_PATTERNS) {
                if (p.matcher(toolInput).find()) {
                    return "命令被安全策略禁止: " + toolInput;
                }
            }
        }
        return null; // 放行
    }

    /**
     * 请求用户审批, 阻塞等待用户响应.
     * @return true=放行, false=拒绝
     */
    public boolean requestApproval(String toolUseId, String toolName, String toolInput, String paramPreview) {
        CodingSessionContext.Context ctx = CodingSessionContext.get();
        String sessionId = ctx != null ? ctx.sessionId() : "unknown";

        // 先检查策略
        String denied = check(toolName, toolInput);
        if (denied != null) {
            return false;
        }

        // execute_command: 读操作自动放行
        if ("execute_command".equals(toolName)) {
            for (Pattern p : ALLOW_PATTERNS) {
                if (p.matcher(toolInput).find()) {
                    return true;
                }
            }
        }

        // 不在审批列表中的工具自动放行
        if (!ASK_TOOLS.contains(toolName)) {
            return true;
        }

        // 发事件到前端
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