package com.kama.jchatmind.memory.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 评测报告：聚合五项指标 + 明细，可写 JSON 与控制台汇总。
 */
@Data
public class EvalReport {

    private String generatedAt;
    private boolean ollamaAvailable;
    private String judgeModel;
    private int tokenBudget;
    private List<String> notes = new ArrayList<>();

    private Headline headline = new Headline();
    private List<SessionReport> sessions = new ArrayList<>();

    /** 五项核心指标汇总。NaN 表示该项不可用（N/A）。 */
    @Data
    public static class Headline {
        private double clarityRate = Double.NaN;        // 1 清晰率
        private double recallRate = Double.NaN;         // 2 召回率
        private double duplicationRate = Double.NaN;    // 3 重复率
        private double helpfulnessRate = Double.NaN;    // 4 召回有用性
        private double edgePrecision = Double.NaN;      // 5 关联边精确率
        private double edgeRecall = Double.NaN;         // 5 附：关联边召回率
        private int clarityJudged;
        private int helpfulnessJudged;
        private int queriesEvaluated;
    }

    @Data
    public static class SessionReport {
        private String sessionId;
        private String datasetId;
        private int storedEntries;
        private List<QueryReport> queries = new ArrayList<>();
        private double duplicationRate = Double.NaN;
        private int systemEdges;
        private int goldEdges;
        private int correctEdges;
        private double edgePrecision = Double.NaN;
        private double edgeRecall = Double.NaN;
        private List<ClarityReport> summaries = new ArrayList<>();
    }

    @Data
    public static class QueryReport {
        private String query;
        private int goldCount;
        private int recalledCount;
        private double recall = Double.NaN;
        private double precision = Double.NaN;
        private int helpfulnessScore = -1;
        private String helpfulnessReason;
    }

    @Data
    public static class ClarityReport {
        private String summaryPreview;
        private int clarityScore = -1;
        private String reason;
    }

    public void writeJson(ObjectMapper mapper, Path path) throws Exception {
        Files.createDirectories(path.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), this);
    }

    public String renderConsole() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n==================== 记忆系统评测报告 ====================\n");
        sb.append("时间: ").append(generatedAt).append('\n');
        sb.append("Ollama(bge-m3) 可用: ").append(ollamaAvailable)
                .append("   判分模型: ").append(judgeModel)
                .append("   Token预算: ").append(tokenBudget).append('\n');
        sb.append("----------------------------------------------------------\n");
        sb.append(String.format("1. 清晰率           : %s  (判分 %d 条摘要)%n",
                pct(headline.clarityRate), headline.clarityJudged));
        sb.append(String.format("2. 召回率           : %s  (%d 条 query)%n",
                pct(headline.recallRate), headline.queriesEvaluated));
        sb.append(String.format("3. 重复率           : %s%n", pct(headline.duplicationRate)));
        sb.append(String.format("4. 召回有用性       : %s  (判分 %d 条 query)%n",
                pct(headline.helpfulnessRate), headline.helpfulnessJudged));
        sb.append(String.format("5. 关联边精确率     : %s  (附 边召回率: %s)%n",
                pct(headline.edgePrecision), pct(headline.edgeRecall)));
        sb.append("----------------------------------------------------------\n");
        for (SessionReport s : sessions) {
            sb.append("[session ").append(s.datasetId).append("] 记忆条目=").append(s.storedEntries)
                    .append(" 重复率=").append(pct(s.duplicationRate))
                    .append(" 边: 系统=").append(s.systemEdges)
                    .append(" gold=").append(s.goldEdges)
                    .append(" 正确=").append(s.correctEdges)
                    .append(" 精确率=").append(pct(s.edgePrecision)).append('\n');
            for (QueryReport q : s.queries) {
                sb.append("    Q: ").append(truncate(q.query, 40))
                        .append(" | recall=").append(pct(q.recall))
                        .append(" precision=").append(pct(q.precision))
                        .append(" 有用=").append(scoreText(q.helpfulnessScore)).append('\n');
            }
            for (ClarityReport c : s.summaries) {
                sb.append("    摘要清晰=").append(scoreText(c.clarityScore))
                        .append(" | ").append(truncate(c.summaryPreview, 50)).append('\n');
            }
        }
        if (!notes.isEmpty()) {
            sb.append("----------------------------------------------------------\n");
            sb.append("备注:\n");
            for (String n : notes) {
                sb.append("  - ").append(n).append('\n');
            }
        }
        sb.append("==========================================================\n");
        return sb.toString();
    }

    private static String pct(double v) {
        return Double.isNaN(v) ? "N/A" : String.format("%.1f%%", v * 100);
    }

    private static String scoreText(int score) {
        return switch (score) {
            case 1 -> "是";
            case 0 -> "否";
            default -> "N/A";
        };
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replace('\n', ' ');
        return t.length() <= max ? t : t.substring(0, max) + "...";
    }
}
