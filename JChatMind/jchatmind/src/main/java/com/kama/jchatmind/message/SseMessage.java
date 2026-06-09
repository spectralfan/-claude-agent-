package com.kama.jchatmind.message;

import com.kama.jchatmind.model.vo.ChatMessageVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class SseMessage {

    private Type type;
    private Payload payload;
    private Metadata metadata;

    @Data
    @AllArgsConstructor
    @Builder
    public static class Payload {
        private ChatMessageVO message;
        private String statusText;
        private Boolean done;
        private String taskId;
        /** 异步子任务 ID */
        private String subTaskId;
        private String actionType;
        private String detail;
        private String workspace;
        private String command;
        private Integer exitCode;
        private String output;
        private String summary;
        private Integer stepsUsed;
        /** 相对工作区路径 */
        private String relativePath;
        /** created | modified */
        private String changeType;
        private String oldContent;
        private String newContent;
        /** 编排任务角色 WORKER | REVIEWER */
        private String role;
        /** 依赖任务 ID，逗号分隔 */
        private String dependsOn;
    }

    @Data
    @AllArgsConstructor
    @Builder
    public static class Metadata {
        private String chatMessageId;
    }

    // 自定义消息类型
    // 1. AI 生成
    // 2. AI 规划中
    // 3. AI 推理中
    // 4. AI 工具调用中
    // 5. AI 观察工具结果
    // 6. AI 完成
    public enum Type {
        AI_GENERATED_CONTENT,
        AI_PLANNING,
        AI_THINKING,
        AI_EXECUTING,
        AI_OBSERVING,
        AI_DONE,
        CODING_STARTED,
        CODING_APPROVAL_REQUIRED,
        CODING_COMMAND_OUTPUT,
        CODING_FILE_CHANGED,
        CODING_SCAFFOLD_DONE,
        CODING_COMPLETED,
        CODING_FAILED,
        CODING_SUBTASK_STARTED,
        CODING_SUBTASK_COMPLETED,
        CODING_SUBTASK_FAILED
    }
}
