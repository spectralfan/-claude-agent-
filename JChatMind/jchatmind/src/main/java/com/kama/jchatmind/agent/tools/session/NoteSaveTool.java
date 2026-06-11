package com.kama.jchatmind.agent.tools.session;

import com.kama.jchatmind.agent.tools.Tool;
import com.kama.jchatmind.agent.tools.ToolType;
import com.kama.jchatmind.session.store.NoteStore;

/**
 * Agent 主动事实记录工具 — 允许 Agent 将重要决策或事实持久化到 notes.md。
 *
 * <p>Worker 和 Reviewer 均可调用此工具记录跨 run 共享的上下文。</p>
 */
public class NoteSaveTool implements Tool {

    private final NoteStore noteStore;
    private final String sessionId;
    private final String runId;

    public NoteSaveTool(NoteStore noteStore, String sessionId, String runId) {
        this.noteStore = noteStore;
        this.sessionId = sessionId;
        this.runId = runId;
    }

    @Override
    public String getName() {
        return "save_note";
    }

    @Override
    public String getDescription() {
        return "记录一条重要事实或决策到会话笔记中，供后续的 Agent 参考。"
                + "当你在编码过程中发现了重要的架构决策、配置细节或用户偏好时，使用此工具记录下来。";
    }

    @Override
    public ToolType getType() {
        return ToolType.OPTIONAL;
    }

    public String execute(String content) {
        if (content == null || content.isBlank()) {
            return "错误：内容不能为空";
        }
        noteStore.appendNote(sessionId, content.trim(), runId);
        return "笔记已保存";
    }
}