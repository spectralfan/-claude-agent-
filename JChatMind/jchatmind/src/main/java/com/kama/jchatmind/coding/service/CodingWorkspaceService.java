package com.kama.jchatmind.coding.service;

import com.kama.jchatmind.coding.model.dto.CodingFileContentDTO;
import com.kama.jchatmind.coding.model.dto.CodingWorkspaceOptionDTO;
import com.kama.jchatmind.coding.model.dto.FileNode;
import com.kama.jchatmind.coding.model.entity.CodingTask;

import java.nio.file.Path;
import java.util.List;

public interface CodingWorkspaceService {

    Path getWorkspaceRoot();

    /** 解析并校验用户选择的工作区根（须在白名单内） */
    Path resolveAllowedRoot(String workspaceRoot);

    Path resolveSafePath(String relativePath);

    /** 按任务绑定的工作区根 + 子路径解析 Maven 等工作目录 */
    Path resolveForTask(CodingTask task);

    boolean isPathSafe(Path base, Path target);

    List<FileNode> getProjectTree(String relativePath);

    /** 列出任务工作区下某目录的直接子项（供网页文件树懒加载） */
    List<FileNode> listDirectoryForTask(CodingTask task, String relativePath);

    /**
     * 递归列举任务工作区目录树（一次调用替代逐层 list），深度受配置限制。
     */
    String listDirectoryTreeForTask(CodingTask task, String relativePath, int maxDepth);

    /** 读取任务工作区内的文本文件（供网页预览） */
    CodingFileContentDTO readFileForTask(CodingTask task, String relativePath);

    /** 供前端下拉：可选的本地工作区列表 */
    List<CodingWorkspaceOptionDTO> listWorkspaceOptions();
}
