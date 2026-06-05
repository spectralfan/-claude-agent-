package com.kama.jchatmind.coding.service;

import com.kama.jchatmind.coding.model.entity.CodingTask;

import java.util.List;

public interface WorkspaceScaffoldService {

    /**
     * 若工作区为空且允许脚手架，从 classpath 模板复制文件。
     *
     * @return 复制的相对路径列表；未执行则返回空列表
     */
    List<String> scaffoldIfNeeded(CodingTask task, String stackId, boolean scaffoldOnCreate);
}
