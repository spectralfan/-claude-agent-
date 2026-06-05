package com.kama.jchatmind.coding;

import com.kama.jchatmind.coding.config.CodingProperties;
import com.kama.jchatmind.coding.service.CodingVerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CodingVerificationServiceTest {

    private CodingVerificationService service;

    @BeforeEach
    void setUp() {
        CodingProperties props = new CodingProperties();
        props.getDelivery().setRequireVerification(true);
        service = new com.kama.jchatmind.coding.service.impl.CodingVerificationServiceImpl(props);
    }

    @Test
    void validateBeforeComplete_withoutRecord_shouldFail() {
        assertTrue(service.validateBeforeComplete("t1").isPresent());
    }

    @Test
    void validateBeforeComplete_afterSuccess_shouldPass() {
        service.recordSuccess("t1", "mvn test", 0);
        assertTrue(service.validateBeforeComplete("t1").isEmpty());
    }

    @Test
    void invalidate_shouldClearRecord() {
        service.recordSuccess("t1", "pytest -q", 0);
        service.invalidate("t1");
        assertTrue(service.validateBeforeComplete("t1").isPresent());
    }

    @Test
    void validateBeforeComplete_whenDisabled_shouldPass() {
        CodingProperties props = new CodingProperties();
        props.getDelivery().setRequireVerification(false);
        var disabled = new com.kama.jchatmind.coding.service.impl.CodingVerificationServiceImpl(props);
        assertTrue(disabled.validateBeforeComplete("t1").isEmpty());
    }
}
