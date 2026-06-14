package com.kama.jchatmind.agent.tools;

import org.springframework.stereotype.Component;

@Component
public class DirectAnswerTool implements Tool {

    @Override
    public String getName() {
        return "direct_answer";
    }

    @Override
    public String getDescription() {
        return "当用户的请求不需要执行操作时调用此工具，用以直接返回自然语言回答。";
    }

    @Override
    public ToolType getType() {
        return ToolType.FIXED;
    }

    @org.springframework.ai.tool.annotation.Tool(
            name = "direct_answer",
            description = "用于直接回答用户问题，适用于无需生成任务计划或调用其他工具的场景。"
    )
    public void directAnswer() {}
}