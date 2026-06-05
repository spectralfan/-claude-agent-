package com.kama.jchatmind.memory.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.config.ChatClientRegistry;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

/**
 * LLM-as-judge：对清晰率与召回有用性进行 0/1 打分。
 * score = -1 表示判分不可用（模型缺失或调用失败），上层据此计入 N/A。
 */
public class LlmJudge {

    private final ChatClientRegistry chatClientRegistry;
    private final ObjectMapper objectMapper;
    private final String model;

    public LlmJudge(ChatClientRegistry chatClientRegistry, ObjectMapper objectMapper, String model) {
        this.chatClientRegistry = chatClientRegistry;
        this.objectMapper = objectMapper;
        this.model = model;
    }

    public record Judgement(int score, String reason) {
        public boolean available() {
            return score >= 0;
        }
    }

    /** 判断整理摘要是否清晰、准确、无歧义（1 清晰 / 0 不清晰）。 */
    public Judgement judgeClarity(String summary) {
        String sys = "你是严格的评审员。判断给定的对话记忆摘要是否清晰、准确、无歧义、信息完整。"
                + "只输出 JSON：{\"score\": 1或0, \"reason\": \"简短理由\"}。1 表示清晰合格，0 表示不清晰。";
        String user = "记忆摘要：\n" + safe(summary);
        return judge(sys, user);
    }

    /** 判断召回的记忆是否有助于回答该问题（1 有帮助 / 0 无帮助）。 */
    public Judgement judgeHelpfulness(String query, List<String> recalledContents, List<String> expectedPoints) {
        StringBuilder mem = new StringBuilder();
        if (recalledContents != null) {
            for (int i = 0; i < recalledContents.size(); i++) {
                mem.append(i + 1).append(". ").append(safe(recalledContents.get(i))).append('\n');
            }
        }
        String sys = "你是严格的评审员。给定一个问题、系统召回的若干条记忆、以及该问题的期望答案要点，"
                + "判断这些召回记忆是否足以支撑回答出期望要点（即对当前回答是否有实质帮助）。"
                + "只输出 JSON：{\"score\": 1或0, \"reason\": \"简短理由\"}。1 表示有帮助，0 表示无帮助或缺关键信息。";
        String user = "问题：" + safe(query) + "\n\n召回的记忆：\n" + mem
                + "\n期望答案要点：\n" + (expectedPoints == null ? "" : String.join("；", expectedPoints));
        return judge(sys, user);
    }

    private Judgement judge(String sys, String user) {
        ChatClient client = chatClientRegistry.get(model);
        if (client == null) {
            return new Judgement(-1, "judge model not found: " + model);
        }
        try {
            String content = client.prompt().system(sys).user(user).call().content();
            return parse(content);
        } catch (Exception e) {
            return new Judgement(-1, "judge call failed: " + e.getMessage());
        }
    }

    private Judgement parse(String content) {
        if (content == null || content.isBlank()) {
            return new Judgement(-1, "empty judge response");
        }
        try {
            int start = content.indexOf('{');
            int end = content.lastIndexOf('}');
            String json = (start >= 0 && end > start) ? content.substring(start, end + 1) : content;
            JsonNode node = objectMapper.readTree(json);
            int score = node.path("score").asInt(-1);
            String reason = node.path("reason").asText("");
            if (score != 0 && score != 1) {
                return new Judgement(-1, "unpar_score: " + content);
            }
            return new Judgement(score, reason);
        } catch (Exception e) {
            return new Judgement(-1, "parse failed: " + content);
        }
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
