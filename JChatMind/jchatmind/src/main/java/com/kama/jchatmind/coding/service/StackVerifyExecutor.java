package com.kama.jchatmind.coding.service;

import com.kama.jchatmind.coding.model.dto.StackVerifyCommandDTO;
import com.kama.jchatmind.coding.model.entity.CodingTask;

import java.util.List;
import java.util.Optional;

public interface StackVerifyExecutor {

    String listVerifyCommands(CodingTask task);

    String runByLabel(CodingTask task, String label);

    List<StackVerifyCommandDTO> resolveCommands(CodingTask task);

    Optional<StackVerifyCommandDTO> findByLabel(CodingTask task, String label);

    Optional<StackVerifyCommandDTO> findByShellCommand(CodingTask task, String commandLine);
}
