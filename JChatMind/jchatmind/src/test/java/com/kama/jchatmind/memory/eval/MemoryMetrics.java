package com.kama.jchatmind.memory.eval;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 确定性指标计算：召回率、重复率、tag 关联边精确率/召回率。纯函数，便于核对。
 */
public final class MemoryMetrics {

    private MemoryMetrics() {
    }

    /** 召回率：|recalled ∩ gold| / |gold|。gold 为空返回 NaN（不计入）。 */
    public static double recall(Set<String> recalledIds, Set<String> goldIds) {
        if (goldIds == null || goldIds.isEmpty()) {
            return Double.NaN;
        }
        long hit = goldIds.stream().filter(recalledIds::contains).count();
        return (double) hit / goldIds.size();
    }

    /** 精确率：|recalled ∩ gold| / |recalled|。recalled 为空返回 NaN。 */
    public static double precision(Set<String> recalledIds, Set<String> goldIds) {
        if (recalledIds == null || recalledIds.isEmpty()) {
            return Double.NaN;
        }
        long hit = recalledIds.stream().filter(goldIds::contains).count();
        return (double) hit / recalledIds.size();
    }

    public record Duplication(int total, int distinct, int duplicates, double rate) {
    }

    /**
     * 重复率：基于内容精确判重。duplicates = total - distinct，rate = duplicates / total。
     */
    public static Duplication duplication(List<String> contents) {
        int total = contents == null ? 0 : contents.size();
        if (total == 0) {
            return new Duplication(0, 0, 0, 0.0);
        }
        Set<String> distinct = new HashSet<>();
        for (String c : contents) {
            distinct.add(c == null ? "" : c.trim());
        }
        int dup = total - distinct.size();
        return new Duplication(total, distinct.size(), dup, (double) dup / total);
    }

    /**
     * 由「记忆 id -> tags」构建 tag 关联边：任意两条记忆只要共享 >=1 个 tag 即生成一条无向边。
     * 返回规范化后的边集合（key = "idA||idB"，按字典序）。
     */
    public static Set<String> buildTagEdges(Map<String, List<String>> idToTags) {
        List<String> ids = new ArrayList<>(idToTags.keySet());
        Set<String> edges = new HashSet<>();
        for (int i = 0; i < ids.size(); i++) {
            for (int j = i + 1; j < ids.size(); j++) {
                String a = ids.get(i);
                String b = ids.get(j);
                if (shareTag(idToTags.get(a), idToTags.get(b))) {
                    edges.add(pairKey(a, b));
                }
            }
        }
        return edges;
    }

    private static boolean shareTag(List<String> ta, List<String> tb) {
        if (ta == null || tb == null || ta.isEmpty() || tb.isEmpty()) {
            return false;
        }
        Set<String> set = new HashSet<>(ta);
        for (String t : tb) {
            if (set.contains(t)) {
                return true;
            }
        }
        return false;
    }

    /** 规范化无向边的 key。 */
    public static String pairKey(String a, String b) {
        TreeSet<String> sorted = new TreeSet<>();
        sorted.add(a);
        sorted.add(b);
        return String.join("||", sorted);
    }

    public record EdgeMetrics(int systemEdges, int goldEdges, int correctEdges,
                              double precision, double recall) {
    }

    /**
     * tag 关联边精确率 = |系统边 ∩ gold边| / |系统边|；召回率 = |系统边 ∩ gold边| / |gold边|。
     */
    public static EdgeMetrics edgeMetrics(Set<String> systemEdges, Set<String> goldEdges) {
        int sys = systemEdges.size();
        int gold = goldEdges.size();
        int correct = 0;
        for (String e : systemEdges) {
            if (goldEdges.contains(e)) {
                correct++;
            }
        }
        double precision = sys == 0 ? Double.NaN : (double) correct / sys;
        double recall = gold == 0 ? Double.NaN : (double) correct / gold;
        return new EdgeMetrics(sys, gold, correct, precision, recall);
    }

    /** 对非 NaN 值求平均，全 NaN 返回 NaN。 */
    public static double mean(List<Double> values) {
        double sum = 0;
        int n = 0;
        for (Double v : values) {
            if (v != null && !v.isNaN()) {
                sum += v;
                n++;
            }
        }
        return n == 0 ? Double.NaN : sum / n;
    }
}
