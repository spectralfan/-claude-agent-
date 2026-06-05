package com.kama.jchatmind.memory.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.config.ChatClientRegistry;
import com.kama.jchatmind.memory.agent.MemoryAgent;
import com.kama.jchatmind.memory.config.MemoryProperties;
import com.kama.jchatmind.memory.mapper.MemoryEntryMapper;
import com.kama.jchatmind.memory.model.dto.MemoryConsolidationDTO;
import com.kama.jchatmind.memory.model.dto.MemorySaveDTO;
import com.kama.jchatmind.memory.model.entity.MemoryEntry;
import com.kama.jchatmind.memory.model.enums.MemoryRole;
import com.kama.jchatmind.memory.model.enums.MemoryType;
import com.kama.jchatmind.memory.service.MemorySelector;
import com.kama.jchatmind.memory.service.MemoryService;
import com.kama.jchatmind.service.RagService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 记忆系统离线评测哈纳斯（opt-in）。
 *
 * 运行：./mvnw.cmd -Dmemeval=true -Dtest=MemoryEvaluationIT test
 * 结果：控制台汇总 + target/memory-eval/report.json
 */
@Slf4j
@SpringBootTest
@EnabledIfSystemProperty(named = "memeval", matches = "true")
public class MemoryEvaluationIT {

    /** 召回时的 token 预算（足够大以反映分层覆盖，而非预算截断） */
    private static final int EVAL_BUDGET = 4000;
    private static final int MAX_RECALL_FOR_JUDGE = 12;

    @Autowired private MemoryService memoryService;
    @Autowired private MemorySelector memorySelector;
    @Autowired private MemoryAgent memoryAgent;
    @Autowired private MemoryEntryMapper memoryEntryMapper;
    @Autowired private ChatClientRegistry chatClientRegistry;
    @Autowired private MemoryProperties memoryProperties;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RagService ragService;

    @Test
    void evaluate() throws Exception {
        EvalDataset dataset = loadDataset();
        boolean ollama = checkOllama();
        LlmJudge judge = new LlmJudge(chatClientRegistry, objectMapper, memoryProperties.getConsolidationModel());

        EvalReport report = new EvalReport();
        report.setGeneratedAt(LocalDateTime.now().toString());
        report.setOllamaAvailable(ollama);
        report.setJudgeModel(memoryProperties.getConsolidationModel());
        report.setTokenBudget(EVAL_BUDGET);
        if (!ollama) {
            report.getNotes().add("Ollama(bge-m3) 不可用：ARCHIVE 向量召回与语义重复率不参与计算；召回仅覆盖 WORKING/RECENT。");
        }
        report.getNotes().add("tag 关联边基于记忆实际存储的 memory_tags（来自数据集标注）。");
        report.getNotes().add("测试会话以 memeval- 前缀写入，结束后清理记忆条目；t_memory_session 行保留不影响生产。");

        List<Double> allRecall = new ArrayList<>();
        List<Double> allDup = new ArrayList<>();
        List<Double> allEdgeP = new ArrayList<>();
        List<Double> allEdgeR = new ArrayList<>();
        List<Integer> allClarity = new ArrayList<>();
        List<Integer> allHelp = new ArrayList<>();

        for (EvalDataset.Session ds : dataset.getSessions()) {
            String sessionId = "memeval-" + UUID.randomUUID();
            EvalReport.SessionReport sr = new EvalReport.SessionReport();
            sr.setSessionId(sessionId);
            sr.setDatasetId(ds.getId());

            Map<String, String> refToId = ingest(ds, sessionId);
            sr.setStoredEntries(refToId.size());

            runConsolidation(sessionId, report);

            // 清晰率：对生成的归档摘要判分
            for (MemoryEntry summary : fetchSummaries(sessionId)) {
                EvalReport.ClarityReport cr = new EvalReport.ClarityReport();
                cr.setSummaryPreview(summary.getContent());
                LlmJudge.Judgement j = judge.judgeClarity(summary.getContent());
                cr.setClarityScore(j.score());
                cr.setReason(j.reason());
                if (j.available()) {
                    allClarity.add(j.score());
                }
                sr.getSummaries().add(cr);
            }

            // 召回率 / 有用性 / 重复率（重复率在单个召回集内度量，按 query 平均）
            List<Double> sessionDupRates = new ArrayList<>();
            for (EvalDataset.EvalQuery q : ds.getQueries()) {
                List<MemoryEntry> recalled = memorySelector.selectMemories(sessionId, q.getQuery(), EVAL_BUDGET);
                Set<String> recalledIds = new HashSet<>();
                List<String> recalledContents = new ArrayList<>();
                for (MemoryEntry e : recalled) {
                    recalledIds.add(e.getId());
                    recalledContents.add(e.getContent());
                }
                sessionDupRates.add(MemoryMetrics.duplication(recalledContents).rate());

                Set<String> goldIds = mapRefs(q.getGoldRecallRefs(), refToId);

                EvalReport.QueryReport qr = new EvalReport.QueryReport();
                qr.setQuery(q.getQuery());
                qr.setGoldCount(goldIds.size());
                qr.setRecalledCount(recalledIds.size());
                qr.setRecall(MemoryMetrics.recall(recalledIds, goldIds));
                qr.setPrecision(MemoryMetrics.precision(recalledIds, goldIds));
                if (!Double.isNaN(qr.getRecall())) {
                    allRecall.add(qr.getRecall());
                }

                List<String> forJudge = recalledContents.size() > MAX_RECALL_FOR_JUDGE
                        ? recalledContents.subList(0, MAX_RECALL_FOR_JUDGE)
                        : recalledContents;
                LlmJudge.Judgement hj = judge.judgeHelpfulness(q.getQuery(), forJudge, q.getExpectedPoints());
                qr.setHelpfulnessScore(hj.score());
                qr.setHelpfulnessReason(hj.reason());
                if (hj.available()) {
                    allHelp.add(hj.score());
                }
                sr.getQueries().add(qr);
            }

            double sessionDup = MemoryMetrics.mean(sessionDupRates);
            sr.setDuplicationRate(sessionDup);
            if (!Double.isNaN(sessionDup)) {
                allDup.add(sessionDup);
            }

            // tag 关联边精确率
            Map<String, List<String>> idToTags = new HashMap<>();
            for (Map.Entry<String, String> e : refToId.entrySet()) {
                MemoryEntry stored = memoryEntryMapper.selectById(e.getValue());
                idToTags.put(e.getValue(), stored != null ? stored.getMemoryTags() : List.of());
            }
            Set<String> systemEdges = MemoryMetrics.buildTagEdges(idToTags);
            Set<String> goldEdges = buildGoldEdges(ds.getGoldRelatedPairs(), refToId);
            MemoryMetrics.EdgeMetrics em = MemoryMetrics.edgeMetrics(systemEdges, goldEdges);
            sr.setSystemEdges(em.systemEdges());
            sr.setGoldEdges(em.goldEdges());
            sr.setCorrectEdges(em.correctEdges());
            sr.setEdgePrecision(em.precision());
            sr.setEdgeRecall(em.recall());
            if (!Double.isNaN(em.precision())) {
                allEdgeP.add(em.precision());
            }
            if (!Double.isNaN(em.recall())) {
                allEdgeR.add(em.recall());
            }

            report.getSessions().add(sr);
            cleanup(sessionId, refToId);
        }

        // 汇总
        EvalReport.Headline h = report.getHeadline();
        h.setRecallRate(MemoryMetrics.mean(allRecall));
        h.setDuplicationRate(MemoryMetrics.mean(allDup));
        h.setEdgePrecision(MemoryMetrics.mean(allEdgeP));
        h.setEdgeRecall(MemoryMetrics.mean(allEdgeR));
        h.setClarityRate(meanInt(allClarity));
        h.setHelpfulnessRate(meanInt(allHelp));
        h.setClarityJudged(allClarity.size());
        h.setHelpfulnessJudged(allHelp.size());
        h.setQueriesEvaluated(allRecall.size());

        Path out = Path.of("target", "memory-eval", "report.json");
        report.writeJson(objectMapper, out);
        String console = report.renderConsole();
        log.info(console);
        System.out.println(console);
        System.out.println("报告已写入: " + out.toAbsolutePath());

        assertFalse(report.getSessions().isEmpty(), "评测应至少覆盖一个 session");
    }

    private EvalDataset loadDataset() throws Exception {
        try (InputStream is = new ClassPathResource("memeval/dataset.json").getInputStream()) {
            return objectMapper.readValue(is, EvalDataset.class);
        }
    }

    private boolean checkOllama() {
        try {
            float[] v = ragService.embed("ping");
            return v != null && v.length > 0;
        } catch (Exception e) {
            log.warn("Ollama 探测失败: {}", e.getMessage());
            return false;
        }
    }

    private Map<String, String> ingest(EvalDataset.Session ds, String sessionId) {
        Map<String, String> refToId = new HashMap<>();
        for (EvalDataset.Turn t : ds.getTurns()) {
            MemorySaveDTO dto = MemorySaveDTO.builder()
                    .sessionId(sessionId)
                    .role(MemoryRole.fromCode(t.getRole()))
                    .content(t.getContent())
                    .memoryType(MemoryType.WORKING)
                    .tags(t.getTags())
                    .importance(t.getImportance())
                    .build();
            MemoryEntry saved = memoryService.save(dto);
            refToId.put(t.getRef(), saved.getId());
        }
        return refToId;
    }

    private void runConsolidation(String sessionId, EvalReport report) {
        try {
            MemoryConsolidationDTO dto = MemoryConsolidationDTO.builder()
                    .sessionId(sessionId)
                    .trigger("EVAL")
                    .build();
            memoryAgent.consolidate(dto);
        } catch (Exception e) {
            // Ollama 不可用时摘要文本已生成并落库，仅向量化失败
            report.getNotes().add("session " + sessionId + " 整理向量化失败(可能 Ollama 离线): " + e.getMessage());
            log.warn("consolidate 异常(摘要可能已生成): {}", e.getMessage());
        }
    }

    private List<MemoryEntry> fetchSummaries(String sessionId) {
        List<MemoryEntry> archive = memoryEntryMapper.selectRecentByType(sessionId, MemoryType.ARCHIVE.getCode(), 50);
        List<MemoryEntry> summaries = new ArrayList<>();
        for (MemoryEntry e : archive) {
            if (e.getMemoryTags() != null && e.getMemoryTags().contains("summary")) {
                summaries.add(e);
            }
        }
        return summaries;
    }

    private Set<String> mapRefs(List<String> refs, Map<String, String> refToId) {
        Set<String> ids = new HashSet<>();
        if (refs != null) {
            for (String r : refs) {
                String id = refToId.get(r);
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    private Set<String> buildGoldEdges(List<List<String>> pairs, Map<String, String> refToId) {
        Set<String> edges = new HashSet<>();
        if (pairs != null) {
            for (List<String> p : pairs) {
                if (p.size() == 2) {
                    String a = refToId.get(p.get(0));
                    String b = refToId.get(p.get(1));
                    if (a != null && b != null) {
                        edges.add(MemoryMetrics.pairKey(a, b));
                    }
                }
            }
        }
        return edges;
    }

    private void cleanup(String sessionId, Map<String, String> refToId) {
        try {
            for (String id : refToId.values()) {
                memoryEntryMapper.deleteById(id);
            }
            // 删除整理生成的归档摘要等剩余条目
            for (MemoryType type : MemoryType.values()) {
                for (MemoryEntry e : memoryEntryMapper.selectRecentByType(sessionId, type.getCode(), 200)) {
                    memoryEntryMapper.deleteById(e.getId());
                }
            }
        } catch (Exception e) {
            log.warn("清理测试记忆失败 session={}: {}", sessionId, e.getMessage());
        }
    }

    private double meanInt(List<Integer> scores) {
        if (scores.isEmpty()) {
            return Double.NaN;
        }
        double sum = 0;
        for (int s : scores) {
            sum += s;
        }
        return sum / scores.size();
    }
}
