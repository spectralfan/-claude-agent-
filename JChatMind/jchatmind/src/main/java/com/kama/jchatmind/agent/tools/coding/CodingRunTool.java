package com.kama.jchatmind.agent.tools.coding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.agent.tools.Tool;
import com.kama.jchatmind.agent.tools.ToolType;
import com.kama.jchatmind.coding.context.CodingSessionContext;
import com.kama.jchatmind.coding.model.dto.CommandExecutionResult;
import com.kama.jchatmind.coding.model.dto.MavenGoal;
import com.kama.jchatmind.coding.model.dto.RunMavenRequest;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.model.enums.CodingActionType;
import com.kama.jchatmind.coding.service.CodingApprovalPolicy;
import com.kama.jchatmind.coding.service.CodingCommandService;
import com.kama.jchatmind.coding.service.CodingTaskService;
import com.kama.jchatmind.message.SseMessage;
import com.kama.jchatmind.realtime.RealtimeNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class CodingRunTool implements Tool {

    private static final Pattern TEST_PATTERN = Pattern.compile("[A-Za-z0-9_.$*]+");

    private final CodingTaskService codingTaskService;
    private final CodingCommandService codingCommandService;
    private final CodingApprovalPolicy codingApprovalPolicy;
    private final RealtimeNotifier realtimeNotifier;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "maven_command";
    }

    @Override
    public String getDescription() {
        return "执行 Maven 命令进行编译和测试（严格白名单与审批控制）";
    }

    @Override
    public ToolType getType() {
        return ToolType.OPTIONAL;
    }

    /**
     * Agent 对话调用：从 CodingSessionContext 解析活动任务。
     */
    @org.springframework.ai.tool.annotation.Tool(
            name = "maven_command",
            description = "执行受控 Maven 命令。goal 支持 compile/test/test_single/package_skip_tests/clean_compile/clean_test。development 模式下 test 等可自动执行。"
    )
    public String runMavenCommand(String goal, String testPattern) {
        CodingSessionContext.Context ctx = CodingSessionContext.get();
        if (ctx == null) {
            return "错误：无 Coding 会话上下文，无法执行 Maven";
        }
        CodingTask task = codingTaskService.getActiveTask(ctx.sessionId());
        if (task == null) {
            return "错误：当前会话无活动 Coding 任务，请先在 AI Coding 页创建任务";
        }
        return runMavenForTask(task.getId(), goal, testPattern, ctx.sessionId(), ctx.agentId());
    }

    /**
     * REST API / 手动触发：显式指定 taskId。
     */
    public String runMavenForTask(String taskId, String goal, String testPattern,
                                  String sessionId, String agentId) {
        try {
            MavenGoal mavenGoal = parseGoal(goal);
            if (mavenGoal == MavenGoal.TEST_SINGLE
                    && (testPattern == null || !TEST_PATTERN.matcher(testPattern).matches())) {
                return "test_single 模式下 testPattern 非法，只允许字母数字下划线点号和星号";
            }

            RunMavenRequest request = RunMavenRequest.builder()
                    .taskId(taskId)
                    .sessionId(sessionId)
                    .agentId(agentId)
                    .goal(mavenGoal)
                    .testPattern(testPattern)
                    .build();

            CodingTask task = codingTaskService.getTaskEntity(taskId);
            String emitSession = task.getSessionId();

            if (codingApprovalPolicy.needApproval(task, mavenGoal)) {
                try {
                    String payload = objectMapper.writeValueAsString(request);
                    String command = previewCommand(mavenGoal, testPattern);
                    codingTaskService.markWaitingApproval(
                            taskId, command, CodingActionType.MAVEN_COMMAND.getCode(), payload
                    );
                    realtimeNotifier.tryPublish(emitSession, SseMessage.builder()
                            .type(SseMessage.Type.CODING_APPROVAL_REQUIRED)
                            .payload(SseMessage.Payload.builder()
                                    .taskId(taskId)
                                    .actionType(CodingActionType.MAVEN_COMMAND.getCode())
                                    .detail(command)
                                    .workspace(task.getWorkspacePath())
                                    .statusText("需要审批后才能执行命令")
                                    .build())
                            .build());
                    return "命令需要审批，已进入待审批状态: " + command;
                } catch (Exception e) {
                    return "准备审批请求失败: " + e.getMessage();
                }
            }

            CommandExecutionResult result = codingCommandService.executeMaven(request);
            realtimeNotifier.tryPublish(emitSession, SseMessage.builder()
                    .type(SseMessage.Type.CODING_COMMAND_OUTPUT)
                    .payload(SseMessage.Payload.builder()
                            .taskId(taskId)
                            .command(previewCommand(mavenGoal, testPattern))
                            .exitCode(result.getExitCode())
                            .output(result.getOutput())
                            .done(result.getExitCode() == 0 && !result.isTimeout())
                            .build())
                    .build());
            return result.getOutput();
        } catch (IllegalArgumentException e) {
            return "错误：" + e.getMessage();
        }
    }

    private MavenGoal parseGoal(String goal) {
        if (goal == null) {
            throw new IllegalArgumentException("goal 不能为空");
        }
        String normalized = goal.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "compile" -> MavenGoal.COMPILE;
            case "test" -> MavenGoal.TEST;
            case "test_single", "single_test" -> MavenGoal.TEST_SINGLE;
            case "package_skip_tests", "package" -> MavenGoal.PACKAGE_SKIP_TESTS;
            case "clean_compile" -> MavenGoal.CLEAN_COMPILE;
            case "clean_test" -> MavenGoal.CLEAN_TEST;
            default -> throw new IllegalArgumentException("不支持的 goal: " + goal);
        };
    }

    private String previewCommand(MavenGoal goal, String testPattern) {
        return switch (goal) {
            case COMPILE -> "mvn compile";
            case TEST -> "mvn test";
            case TEST_SINGLE -> "mvn test -Dtest=" + testPattern;
            case PACKAGE_SKIP_TESTS -> "mvn package -DskipTests";
            case CLEAN_COMPILE -> "mvn clean compile";
            case CLEAN_TEST -> "mvn clean test";
        };
    }
}
