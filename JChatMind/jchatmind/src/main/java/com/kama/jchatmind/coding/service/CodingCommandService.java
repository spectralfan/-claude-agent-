package com.kama.jchatmind.coding.service;

import com.kama.jchatmind.coding.model.dto.CommandExecutionResult;
import com.kama.jchatmind.coding.model.dto.RunMavenRequest;

public interface CodingCommandService {

    CommandExecutionResult executeMaven(RunMavenRequest request);

    CommandExecutionResult executeShell(String taskId, String commandLine);
}
