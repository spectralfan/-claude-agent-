package com.kama.jchatmind.coding.service;

import java.util.Optional;

public interface CodingVerificationService {

    void recordSuccess(String taskId, String command, int exitCode);

    void invalidate(String taskId);

    Optional<String> validateBeforeComplete(String taskId);
}
