package com.kama.jchatmind.coding.service.impl;

import com.kama.jchatmind.coding.model.dto.CodingTaskMetadata;
import com.kama.jchatmind.coding.model.dto.CodingTaskSummaryDTO;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.model.enums.CodingTaskStatus;
import com.kama.jchatmind.coding.registry.CodingChangeRegistry;
import com.kama.jchatmind.coding.service.CodingStackService;
import com.kama.jchatmind.coding.service.CodingTaskService;
import com.kama.jchatmind.coding.service.CodingTaskSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CodingTaskSummaryServiceImpl implements CodingTaskSummaryService {

    private final CodingTaskService codingTaskService;
    private final CodingChangeRegistry changeRegistry;
    private final CodingStackService codingStackService;

    @Override
    public CodingTaskSummaryDTO getSummary(String taskId) {
        CodingTask task = codingTaskService.getTaskEntity(taskId);
        CodingTaskMetadata metadata = CodingTaskMetadata.fromJson(task.getMetadata());
        boolean completed = CodingTaskStatus.COMPLETED.getCode().equals(task.getStatus());
        String runInstructions = codingStackService.findById(metadata.getStackId())
                .map(stack -> "技术栈 " + stack.getDisplayName() + "："
                        + (stack.getDoneCriteria() != null ? stack.getDoneCriteria() : "验证通过即可运行"))
                .orElse("请查看 Agent 最终回复中的运行说明");
        return CodingTaskSummaryDTO.builder()
                .taskId(taskId)
                .status(task.getStatus())
                .stackId(metadata.getStackId())
                .language(metadata.getLanguage())
                .resultSummary(task.getResultSummary())
                .lastCommand(task.getCommand())
                .lastCommandOutput(task.getResultSummary())
                .completed(completed)
                .changedFiles(changeRegistry.getChangedFiles(taskId))
                .runInstructions(runInstructions)
                .build();
    }
}
