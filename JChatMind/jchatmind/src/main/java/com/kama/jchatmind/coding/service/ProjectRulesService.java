package com.kama.jchatmind.coding.service;

import com.kama.jchatmind.coding.model.entity.CodingTask;

import java.util.Optional;

public interface ProjectRulesService {

    Optional<String> getRules();

    /** 从 Coding 任务绑定的工作区读取 JCHATMIND.md / CLAUDE.md 等 */
    Optional<String> getRulesForTask(CodingTask task);
}
