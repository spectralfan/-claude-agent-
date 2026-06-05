package com.kama.jchatmind.coding.service;

import com.kama.jchatmind.coding.model.dto.CommandExecutionResult;

import java.nio.file.Path;
import java.util.List;

public interface SandboxCommandRunner {

    CommandExecutionResult run(List<String> command, Path workspace, int timeoutSeconds, int outputMaxChars);
}
