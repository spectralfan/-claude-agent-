package com.kama.jchatmind.coding.service.impl;

import com.kama.jchatmind.coding.config.CodingProperties;
import com.kama.jchatmind.coding.service.CodingVerificationService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CodingVerificationServiceImpl implements CodingVerificationService {

    private record VerificationRecord(String command, int exitCode, LocalDateTime at) {
    }

    private final CodingProperties codingProperties;
    private final Map<String, VerificationRecord> store = new ConcurrentHashMap<>();

    public CodingVerificationServiceImpl(CodingProperties codingProperties) {
        this.codingProperties = codingProperties;
    }

    @Override
    public void recordSuccess(String taskId, String command, int exitCode) {
        if (taskId == null || exitCode != 0) {
            return;
        }
        store.put(taskId, new VerificationRecord(
                command != null ? command : "(unknown)",
                exitCode,
                LocalDateTime.now()
        ));
    }

    @Override
    public void invalidate(String taskId) {
        if (taskId != null) {
            store.remove(taskId);
        }
    }

    @Override
    public Optional<String> validateBeforeComplete(String taskId) {
        if (!codingProperties.getDelivery().isRequireVerification()) {
            return Optional.empty();
        }
        VerificationRecord record = store.get(taskId);
        if (record == null || record.exitCode() != 0) {
            return Optional.of(
                    "错误：尚未记录成功的验证命令（exit 0）。请先执行 compile/test/pytest/npm test 等验证后再调用 mark_coding_complete。");
        }
        return Optional.empty();
    }
}
