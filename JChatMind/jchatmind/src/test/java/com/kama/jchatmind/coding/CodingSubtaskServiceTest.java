package com.kama.jchatmind.coding;

import com.kama.jchatmind.coding.model.dto.CodingSubtaskDTO;
import com.kama.jchatmind.coding.model.enums.CodingSubtaskStatus;
import com.kama.jchatmind.coding.service.impl.CodingSubtaskServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CodingSubtaskServiceTest {

    private CodingSubtaskServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CodingSubtaskServiceImpl();
    }

    @Test
    void createAndQuery_shouldTrackLifecycle() {
        CodingSubtaskDTO created = service.create(
                "session-1", "task-1", "worker-1", "实现登录", "添加 JWT 登录接口并写单测");
        assertNotNull(created.getId());
        assertEquals(CodingSubtaskStatus.PENDING.getCode(), created.getStatus());

        service.markRunning(created.getId());
        assertEquals(CodingSubtaskStatus.RUNNING.getCode(),
                service.findById(created.getId()).orElseThrow().getStatus());

        service.markCompleted(created.getId(), "登录接口已完成");
        CodingSubtaskDTO done = service.findById(created.getId()).orElseThrow();
        assertEquals(CodingSubtaskStatus.COMPLETED.getCode(), done.getStatus());
        assertEquals("登录接口已完成", done.getResultSummary());
        assertNotNull(done.getFinishedAt());

        List<CodingSubtaskDTO> list = service.listByParentSession("session-1");
        assertEquals(1, list.size());
        assertEquals(created.getId(), list.get(0).getId());
    }

    @Test
    void markFailed_shouldStoreError() {
        CodingSubtaskDTO created = service.create(
                "s2", "t2", "w2", "失败用例", "goal");
        service.markFailed(created.getId(), "编译失败");
        CodingSubtaskDTO failed = service.findById(created.getId()).orElseThrow();
        assertEquals(CodingSubtaskStatus.FAILED.getCode(), failed.getStatus());
        assertEquals("编译失败", failed.getErrorMessage());
    }
}
