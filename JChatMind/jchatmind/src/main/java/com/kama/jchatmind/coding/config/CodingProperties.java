package com.kama.jchatmind.coding.config;

import com.kama.jchatmind.coding.model.enums.CodingApprovalMode;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Coding 模块配置，前缀 coding。
 */
@Data
@Component
@ConfigurationProperties(prefix = "coding")
public class CodingProperties {

    private Workspace workspace = new Workspace();
    private Agent agent = new Agent();
    private Skills skills = new Skills();
    private Approval approval = new Approval();
    private ProjectRules projectRules = new ProjectRules();
    private Maven maven = new Maven();
    private Command command = new Command();
    private Scaffold scaffold = new Scaffold();
    private Delivery delivery = new Delivery();
    private Stack stack = new Stack();
    private Verify verify = new Verify();

    @Data
    public static class Delivery {
        /** mark_coding_complete 前须有一次 exit 0 的验证记录 */
        private boolean requireVerification = true;
    }

    @Data
    public static class Workspace {
        /** 未指定或未命中白名单时的默认根目录 */
        private String root = "./workspace";
        /** 网页预览单文件最大字符数 */
        private int previewMaxChars = 512_000;
        /**
         * 工作区 HTML 静态预览推荐端口（勿与 JChatMind 后端 8080、MCP proxy 3000、Vite 5173 冲突）。
         * 注入 MCP shell 环境变量 JCHATMIND_PREVIEW_PORT。
         */
        private int previewPort = 5500;
        /** SSE 推送 diff 内容最大字符数 */
        private int sseDiffMaxChars = 16_000;
        /** 文件树忽略的目录名 */
        private List<String> ignoreDirs = new ArrayList<>(List.of(
                ".git", "target", "node_modules", ".idea", "out", "dist", "build", ".gradle", ".tasks"
        ));
        /** @file 引用注入单文件最大字符数 */
        private int atFileMaxChars = 12_000;
        /** list_coding_directory_tree / list_workspace_dir 默认深度 */
        private int listDirDefaultDepth = 2;
        /** 目录树列举最大深度 */
        private int listDirMaxDepth = 5;
        /** read_coding_files 单次最多文件数 */
        private int batchReadMaxFiles = 10;
        /** read_coding_files 单文件最大字符数 */
        private int batchReadMaxCharsPerFile = 32_000;
        /**
         * 允许用户在网页上选择的工作区列表（本地 IDEA/Maven 工程根目录）。
         * 为空时仅可使用 {@link #root}。
         */
        private List<AllowedRoot> allowedRoots = new ArrayList<>();
    }

    @Data
    public static class AllowedRoot {
        /** 下拉展示名称 */
        private String name;
        /** 工程根目录绝对或相对路径（须含 pom.xml 才能正常 mvn） */
        private String path;
    }

    @Data
    public static class Agent {
        private int maxLoopSteps = 35;
        private int memoryWindow = 80;
        /** 是否使用按 tool 轮次裁剪的 ChatMemory；false 时回退 MessageWindowChatMemory */
        private boolean toolAwareMemory = true;
    }

    @Data
    public static class Skills {
        /** 是否启用 Skill 注入 */
        private boolean enabled = true;
    }

    @Data
    public static class Approval {
        private boolean enabled = true;
        /** 默认审批模式：development ≈ Claude Code 开发体验 */
        private CodingApprovalMode defaultMode = CodingApprovalMode.DEVELOPMENT;
    }

    @Data
    public static class ProjectRules {
        private boolean enabled = true;
        private List<String> files = new ArrayList<>(List.of("JCHATMIND.md", "CLAUDE.md", "AGENTS.md"));
        private int maxChars = 2000;
    }

    @Data
    public static class Maven {
        private int timeoutSeconds = 300;
        private int outputMaxChars = 2000;
    }

    @Data
    public static class Command {
        /** MCP / 命令输出 SSE 与工具返回的最大字符数 */
        private int outputMaxChars = 2000;
    }

    @Data
    public static class Stack {
        /** 创建任务或对话时根据 pom.xml / pyproject.toml / package.json 等自动识别技术栈 */
        private boolean autoDetect = true;
    }

    @Data
    public static class Verify {
        /** mcp | sandbox | auto（auto=MCP 优先，失败可降级 sandbox） */
        private String backend = "auto";
        /** backend=auto 时 MCP 不可用是否降级 SandboxCommandRunner */
        private boolean fallbackEnabled = true;
    }

    @Data
    public static class Scaffold {
        /** classpath 下模板根路径，如 coding-templates */
        private String templatesPath = "coding-templates";
        /** 非空目录是否仍允许脚手架（默认否） */
        private boolean allowOnNonEmpty = false;
    }
}
