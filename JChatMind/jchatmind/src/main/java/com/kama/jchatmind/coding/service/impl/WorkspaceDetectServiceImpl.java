package com.kama.jchatmind.coding.service.impl;

import com.kama.jchatmind.coding.model.dto.CodingStackDTO;
import com.kama.jchatmind.coding.model.dto.WorkspaceDetectResultDTO;
import com.kama.jchatmind.coding.service.CodingStackService;
import com.kama.jchatmind.coding.service.CodingWorkspaceService;
import com.kama.jchatmind.coding.service.WorkspaceDetectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class WorkspaceDetectServiceImpl implements WorkspaceDetectService {

    private final CodingWorkspaceService codingWorkspaceService;
    private final CodingStackService codingStackService;

    @Override
    public WorkspaceDetectResultDTO detect(String workspaceRoot, String workspacePath) {
        Path root = codingWorkspaceService.resolveAllowedRoot(workspaceRoot);
        String sub = (workspacePath == null || workspacePath.isBlank()) ? "." : workspacePath.trim();
        Path target = root.resolve(sub).normalize();
        if (!codingWorkspaceService.isPathSafe(root, target)) {
            throw new IllegalArgumentException("工作区路径不安全: " + sub);
        }
        boolean empty = isEffectivelyEmpty(target);
        List<CodingStackDTO> stacks = codingStackService.listStacks();
        for (CodingStackDTO stack : stacks) {
            if (stack.getDetectFiles() == null) {
                continue;
            }
            for (String detectFile : stack.getDetectFiles()) {
                Path marker = target.resolve(detectFile).normalize();
                if (codingWorkspaceService.isPathSafe(target, marker) && Files.exists(marker)) {
                    return WorkspaceDetectResultDTO.builder()
                            .stackId(stack.getId())
                            .displayName(stack.getDisplayName())
                            .language(stack.getLanguage())
                            .matchedFile(detectFile)
                            .emptyWorkspace(empty)
                            .build();
                }
            }
        }
        return WorkspaceDetectResultDTO.builder()
                .emptyWorkspace(empty)
                .build();
    }

    private boolean isEffectivelyEmpty(Path dir) {
        if (!Files.exists(dir)) {
            return true;
        }
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.noneMatch(this::countsAsContent);
        } catch (IOException e) {
            return false;
        }
    }

    private boolean countsAsContent(Path entry) {
        String name = entry.getFileName().toString();
        return !name.equals(".") && !name.equals("..");
    }
}
