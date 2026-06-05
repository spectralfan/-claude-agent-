package com.kama.jchatmind.memory.eval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * 记忆评测合成数据集模型（从 classpath:memeval/dataset.json 反序列化）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EvalDataset {

    private List<Session> sessions;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Session {
        private String id;
        private List<Turn> turns;
        private List<EvalQuery> queries;
        /** 会话级 gold「真正相关」记忆对，用于第 5 项 tag 关联边精确率 */
        private List<List<String>> goldRelatedPairs;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Turn {
        /** 数据集内唯一引用名，用于 gold 标注映射到实际记忆 id */
        private String ref;
        private String role;
        private String content;
        private List<String> tags;
        /** 显式重要性；>=阈值会落到 RECENT 层 */
        private Integer importance;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EvalQuery {
        private String query;
        /** 该 query 应当被召回的记忆 ref 集（recall 真值） */
        private List<String> goldRecallRefs;
        /** 期望答案要点，供有用性判分参考 */
        private List<String> expectedPoints;
    }
}
