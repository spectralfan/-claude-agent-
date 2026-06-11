package com.kama.jchatmind.coding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrchestrationSummaryExtractorTest {

    @Test
    void extractsDeliverySection() {
        String content = "已完成修改。\n\n## 交付摘要\n- 修改文件: Foo.java\n- 验证: mvn test OK";
        assertEquals("## 交付摘要\n- 修改文件: Foo.java\n- 验证: mvn test OK",
                OrchestrationSummaryExtractor.extractStructuredSummary(content));
    }

    @Test
    void extractsReviewSection() {
        String content = "审查完毕。\n## 审查结论\nVERDICT: FAIL\n## 发现\n- 缺测试";
        assertTrue(OrchestrationSummaryExtractor.isReviewFailed(
                OrchestrationSummaryExtractor.extractStructuredSummary(content)));
    }

    @Test
    void passesWhenVerdictPass() {
        String content = "## 审查结论\nVERDICT: PASS\n## 发现\n- 无";
        assertFalse(OrchestrationSummaryExtractor.isReviewFailed(content));
    }

    @Test
    void fallsBackToFullContentWhenNoMarker() {
        String content = "plain summary without marker";
        assertEquals(content, OrchestrationSummaryExtractor.extractStructuredSummary(content));
    }
}
