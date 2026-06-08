import React, { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  Alert,
  Button,
  Card,
  Input,
  Modal,
  Select,
  Space,
  Steps,
  Switch,
  Tag,
  Typography,
  message as antdMessage,
} from "antd";
import {
  CodeOutlined,
  PlayCircleOutlined,
  ReloadOutlined,
  SettingOutlined,
  ThunderboltOutlined,
} from "@ant-design/icons";
import { useAgents } from "../../hooks/useAgents.ts";
import {
  approveCodingTask,
  createChatMessage,
  createChatSession,
  createCodingTask,
  detectCodingWorkspace,
  getChatMessagesBySessionId,
  getActiveCodingTask,
  getCodingWorkspaces,
  getCodingStacks,
  getCodingAgentPreset,
  getCodingOrchestratorPreset,
  getChatSession,
  getCodingTask,
  getCodingTaskSummary,
  getCodingRuntimeStatus,
  runShellCommand,
  rejectCodingTask,
  runMavenCommand,
  type CodingApprovalModeType,
  type CodingRuntimeStatusVO,
  type CodingStackVO,
  type StackVerifyCommandVO,
  type CodingTaskSummaryVO,
  type CodingWorkspaceOption,
  type CodingTaskVO,
  type CodingTaskStatusType,
  type MavenGoalType,
} from "../../api/api.ts";
import type { ChatMessageVO, SseMessage, SseMessageType } from "../../types";
import AgentChatHistory from "./agentChatView/AgentChatHistory.tsx";
import CodingChatInput from "../coding/CodingChatInput.tsx";
import CodingFileTree from "../coding/CodingFileTree.tsx";
import CodingFilePreview, {
  type FileDiffState,
} from "../coding/CodingFilePreview.tsx";
import CodingTerminalPanel, {
  type TerminalLogEntry,
} from "../coding/CodingTerminalPanel.tsx";
import CodingCompletionCard from "../coding/CodingCompletionCard.tsx";
import CodingSubtaskPanel from "../coding/CodingSubtaskPanel.tsx";
import { useSessionSse } from "../../hooks/useSessionSse.ts";

type CodingAgentRole = "orchestrator" | "worker";

const { Title, Text } = Typography;

const GOAL_OPTIONS: { value: MavenGoalType; label: string }[] = [
  { value: "compile", label: "compile" },
  { value: "test", label: "test" },
  { value: "test_single", label: "test_single" },
  { value: "package_skip_tests", label: "package -DskipTests" },
  { value: "clean_compile", label: "clean compile" },
  { value: "clean_test", label: "clean test" },
];

const STATUS_COLOR: Record<CodingTaskStatusType, string> = {
  PENDING: "default",
  RUNNING: "processing",
  WAITING_APPROVAL: "warning",
  COMPLETED: "success",
  FAILED: "error",
  TIMEOUT: "error",
  REJECTED: "default",
};

const APPROVAL_MODE_OPTIONS: { value: CodingApprovalModeType; label: string }[] = [
  { value: "development", label: "开发模式（compile/test 自动，推荐）" },
  { value: "trusted", label: "信任模式（全部 Maven 自动）" },
  { value: "strict", label: "严格模式（仅 compile 自动）" },
];

const CodingView: React.FC = () => {
  const { sessionId: routeSessionId } = useParams<{ sessionId?: string }>();
  const navigate = useNavigate();
  const { agents } = useAgents();

  const [agentRole, setAgentRole] = useState<CodingAgentRole>("orchestrator");

  const [agentId, setAgentId] = useState<string>("");
  const [workspaceOptions, setWorkspaceOptions] = useState<CodingWorkspaceOption[]>([]);
  const [workspaceRoot, setWorkspaceRoot] = useState<string>("");
  const [workspacePath, setWorkspacePath] = useState<string>(".");
  const [stackOptions, setStackOptions] = useState<CodingStackVO[]>([]);
  const [scaffoldOnCreate, setScaffoldOnCreate] = useState(false);
  const [detectHint, setDetectHint] = useState<string>("");
  const [wizardStep, setWizardStep] = useState(0);
  const [approvalMode, setApprovalMode] =
    useState<CodingApprovalModeType>("development");
  const [sessionId, setSessionId] = useState<string>("");
  const [task, setTask] = useState<CodingTaskVO | null>(null);
  const [taskSummary, setTaskSummary] = useState<CodingTaskSummaryVO | null>(
    null,
  );

  const [goal, setGoal] = useState<MavenGoalType>("compile");
  const [testPattern, setTestPattern] = useState<string>("");
  const [showSetup, setShowSetup] = useState(false);
  const [showVerifyPanel, setShowVerifyPanel] = useState(false);
  const [runtimeStatus, setRuntimeStatus] =
    useState<CodingRuntimeStatusVO | null>(null);
  const [subtaskRefreshToken, setSubtaskRefreshToken] = useState(0);

  const [creating, setCreating] = useState(false);
  const [running, setRunning] = useState(false);
  const [approving, setApproving] = useState(false);

  const [logs, setLogs] = useState<TerminalLogEntry[]>([]);
  const [terminalCollapsed, setTerminalCollapsed] = useState(false);
  const [messages, setMessages] = useState<ChatMessageVO[]>([]);
  const [displayAgentStatus, setDisplayAgentStatus] = useState(false);
  const [agentStatusText, setAgentStatusText] = useState("");
  const [agentStatusType, setAgentStatusType] = useState<
    SseMessageType | undefined
  >(undefined);
  const [chatSending, setChatSending] = useState(false);
  const [approvalCommand, setApprovalCommand] = useState<string>("");

  const [selectedFilePath, setSelectedFilePath] = useState<string>();
  const [fileDiff, setFileDiff] = useState<FileDiffState | null>(null);
  const [treeRefreshToken, setTreeRefreshToken] = useState(0);

  const logEndRef = useRef<HTMLDivElement | null>(null);

  const appendLog = useCallback((type: string, text: string) => {
    const time = new Date().toLocaleTimeString();
    setLogs((prev) => [...prev, { time, type, text }]);
    setTerminalCollapsed(false);
  }, []);

  useEffect(() => {
    logEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [logs]);

  useEffect(() => {
    getCodingWorkspaces()
      .then((list) => {
        setWorkspaceOptions(list);
        const def = list.find((w) => w.defaultOption) ?? list[0];
        if (def) setWorkspaceRoot(def.path);
      })
      .catch(() => {
        antdMessage.warning(
          "无法加载工作区列表，请检查 coding.workspace.allowed-roots",
        );
      });
    getCodingStacks()
      .then(setStackOptions)
      .catch(() => undefined);
  }, []);

  useEffect(() => {
    const loader =
      agentRole === "orchestrator"
        ? getCodingOrchestratorPreset
        : getCodingAgentPreset;
    loader()
      .then((preset) => {
        if (preset?.agentId) {
          setAgentId(preset.agentId);
        }
      })
      .catch(() => undefined);
  }, [agentRole]);

  useEffect(() => {
    if (!routeSessionId) {
      return;
    }
    setSessionId(routeSessionId);
    let cancelled = false;
    (async () => {
      try {
        const sessionResp = await getChatSession(routeSessionId);
        if (cancelled) return;
        setAgentId(sessionResp.chatSession.agentId);
        const active = await getActiveCodingTask(routeSessionId);
        if (cancelled) return;
        if (active) {
          setTask(active);
        } else if (workspaceRoot) {
          setTask(null);
        }
        const msgResp = await getChatMessagesBySessionId(routeSessionId);
        if (!cancelled) {
          setMessages(msgResp.chatMessages);
        }
      } catch {
        if (!cancelled) {
          antdMessage.warning("无法恢复 Coding 会话，请重新创建任务");
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [routeSessionId]);

  useEffect(() => {
    if (!workspaceRoot) {
      setDetectHint("");
      return;
    }
    const timer = setTimeout(() => {
      detectCodingWorkspace(workspaceRoot, workspacePath.trim() || ".")
        .then((result) => {
          if (result.stackId) {
            setDetectHint(
              `预览：检测到 ${result.matchedFile} → ${result.displayName ?? result.stackId}（进入对话后自动绑定）`,
            );
          } else if (result.emptyWorkspace) {
            setDetectHint("工作区为空：可在对话中描述要创建的项目，或勾选「从零脚手架」");
          } else {
            setDetectHint("未识别到特征文件：Agent 将在对话中自行判断技术栈与验证方式");
          }
        })
        .catch(() => setDetectHint(""));
    }, 400);
    return () => clearTimeout(timer);
  }, [workspaceRoot, workspacePath]);

  const loadMessages = useCallback(async () => {
    if (!sessionId) return;
    const resp = await getChatMessagesBySessionId(sessionId);
    setMessages(resp.chatMessages);
  }, [sessionId]);

  useEffect(() => {
    if (!task?.id) return;
    getCodingRuntimeStatus()
      .then(setRuntimeStatus)
      .catch(() => undefined);
  }, [task?.id]);

  const currentStack = stackOptions.find((s) => s.id === task?.stackId);
  const verifyCommands: StackVerifyCommandVO[] =
    currentStack?.verifyCommands ??
    (task?.stackId === "java-maven" || !task?.stackId
      ? [
          { label: "mvn compile", type: "maven", goal: "compile" },
          { label: "mvn test", type: "maven", goal: "test" },
        ]
      : []);

  useEffect(() => {
    if (sessionId) loadMessages().catch(() => undefined);
  }, [sessionId, loadMessages]);

  const refreshTaskBySession = useCallback(async () => {
    if (!sessionId) return;
    try {
      const active = await getActiveCodingTask(sessionId);
      if (active) setTask(active);
    } catch {
      // ignore
    }
  }, [sessionId]);

  const refreshTask = useCallback(async () => {
    if (task?.id) {
      try {
        setTask(await getCodingTask(task.id));
      } catch {
        // ignore
      }
    } else {
      await refreshTaskBySession();
    }
  }, [task?.id, refreshTaskBySession]);

  const handleSseEvent = useCallback(
    (msg: SseMessage) => {
      const p = msg.payload ?? ({} as SseMessage["payload"]);
      switch (msg.type) {
        case "AI_GENERATED_CONTENT":
          if (p.message) setMessages((prev) => [...prev, p.message]);
          break;
        case "AI_PLANNING":
          setDisplayAgentStatus(true);
          setAgentStatusText(p.statusText);
          setAgentStatusType("AI_PLANNING");
          break;
        case "AI_THINKING":
          setDisplayAgentStatus(true);
          setAgentStatusText(p.statusText);
          setAgentStatusType("AI_THINKING");
          break;
        case "AI_EXECUTING":
          setDisplayAgentStatus(true);
          setAgentStatusText(p.statusText);
          setAgentStatusType("AI_EXECUTING");
          break;
        case "AI_OBSERVING":
          setDisplayAgentStatus(true);
          setAgentStatusText(p.statusText);
          setAgentStatusType("AI_OBSERVING");
          break;
        case "AI_DONE":
          setDisplayAgentStatus(false);
          setAgentStatusText("");
          setAgentStatusType(undefined);
          loadMessages().catch(() => undefined);
          refreshTask().catch(() => undefined);
          break;
        case "CODING_STARTED":
          appendLog("started", p.statusText || "任务已创建");
          refreshTaskBySession().catch(() => undefined);
          break;
        case "CODING_APPROVAL_REQUIRED":
          appendLog("approval", `需要审批: ${p.detail ?? ""}`);
          setApprovalCommand(p.detail ?? "");
          break;
        case "CODING_COMMAND_OUTPUT":
          appendLog(
            "output",
            `[$ ${p.command ?? ""}] exit=${p.exitCode ?? "?"}\n${p.output ?? ""}`,
          );
          setApprovalCommand("");
          refreshTaskBySession();
          break;
        case "CODING_FILE_CHANGED": {
          const path = p.relativePath ?? p.detail ?? "";
          appendLog("file", p.statusText || `文件变更: ${path}`);
          setTreeRefreshToken((t) => t + 1);
          if (path && path !== ".") {
            setSelectedFilePath(path);
            setFileDiff({
              relativePath: path,
              changeType: p.changeType,
              oldContent: p.oldContent,
              newContent: p.newContent,
            });
          }
          break;
        }
        case "CODING_SCAFFOLD_DONE":
          appendLog("scaffold", p.statusText || "脚手架已就绪");
          setTreeRefreshToken((t) => t + 1);
          break;
        case "CODING_COMPLETED":
          appendLog("completed", p.summary || p.statusText || "任务完成");
          setApprovalCommand("");
          refreshTaskBySession().then(() => {
            if (p.taskId) {
              getCodingTaskSummary(p.taskId)
                .then(setTaskSummary)
                .catch(() => undefined);
            }
          });
          break;
        case "CODING_FAILED":
          appendLog("failed", `${p.statusText ?? "失败"} ${p.detail ?? ""}`);
          setApprovalCommand("");
          refreshTaskBySession();
          break;
        case "CODING_SUBTASK_STARTED":
          appendLog(
            "subtask",
            `[子任务启动] ${p.statusText ?? ""} (${p.subTaskId ?? ""})`,
          );
          setSubtaskRefreshToken((t) => t + 1);
          break;
        case "CODING_SUBTASK_COMPLETED":
          appendLog(
            "subtask",
            `[子任务完成] ${p.statusText ?? ""}: ${p.summary ?? ""}`,
          );
          setSubtaskRefreshToken((t) => t + 1);
          refreshTaskBySession();
          break;
        case "CODING_SUBTASK_FAILED":
          appendLog(
            "subtask",
            `[子任务失败] ${p.statusText ?? ""}: ${p.output ?? p.detail ?? ""}`,
          );
          setSubtaskRefreshToken((t) => t + 1);
          break;
        default:
          break;
      }
    },
    [appendLog, loadMessages, refreshTask, refreshTaskBySession],
  );

  useSessionSse(sessionId || undefined, handleSseEvent);

  const handleCreateTask = async () => {
    if (!agentId) {
      antdMessage.warning("请先选择一个智能体助手");
      return;
    }
    if (!workspaceRoot) {
      antdMessage.warning("请选择本地工作区");
      return;
    }
    if (!workspacePath.trim()) {
      antdMessage.warning("请填写工程内子路径");
      return;
    }
    setCreating(true);
    try {
      const session = await createChatSession({
        agentId,
        title: `Coding @ ${workspacePath}`,
        workspaceRoot,
        workspacePath: workspacePath.trim(),
        approvalMode,
        scaffoldOnCreate,
      });
      setSessionId(session.chatSessionId);
      navigate(`/coding/${session.chatSessionId}`, { replace: true });
      setTask(null);
      setTaskSummary(null);
      setLogs([]);
      setSelectedFilePath(undefined);
      setFileDiff(null);
      setShowSetup(false);
      appendLog("started", "会话已就绪，请直接描述开发任务");
      antdMessage.success("进入工作台，发送首条消息即可自动开始开发");
    } catch (e) {
      antdMessage.error(e instanceof Error ? e.message : "创建会话失败");
    } finally {
      setCreating(false);
    }
  };

  const handleChatSend = async (value: string | { text: string }) => {
    const message = typeof value === "string" ? value : value.text;
    if (!message?.trim() || !sessionId || !agentId) return;
    setChatSending(true);
    try {
      await createChatMessage({
        agentId,
        sessionId,
        role: "user",
        content: message.trim(),
      });
      await loadMessages();
    } catch (e) {
      antdMessage.error(e instanceof Error ? e.message : "发送失败");
    } finally {
      setChatSending(false);
    }
  };

  const handleRunVerify = async (cmd: StackVerifyCommandVO) => {
    if (!task?.id) return;
    setRunning(true);
    try {
      if (cmd.type === "maven" && cmd.goal) {
        const resp = await runMavenCommand(task.id, {
          goal: cmd.goal,
          sessionId,
          agentId,
        });
        appendLog("run", `${cmd.label}: ${resp}`);
      } else if (cmd.type === "shell" && cmd.command) {
        const result = await runShellCommand(task.id, {
          command: cmd.command,
          sessionId,
        });
        appendLog(
          "run",
          `${cmd.label} exit=${result.exitCode}\n${result.output}`,
        );
      }
      await refreshTask();
    } catch (e) {
      antdMessage.error(e instanceof Error ? e.message : "验证命令执行失败");
    } finally {
      setRunning(false);
    }
  };

  const handleRunMaven = async () => {
    if (!task?.id) return;
    if (goal === "test_single" && !testPattern.trim()) {
      antdMessage.warning("test_single 需要填写 testPattern");
      return;
    }
    setRunning(true);
    try {
      const resp = await runMavenCommand(task.id, {
        goal,
        testPattern: testPattern.trim() || undefined,
        sessionId,
        agentId,
      });
      appendLog("run", `执行 ${goal}: ${resp}`);
      await refreshTask();
    } catch (e) {
      antdMessage.error(e instanceof Error ? e.message : "执行失败");
    } finally {
      setRunning(false);
    }
  };

  const handleApprove = async () => {
    if (!task?.id) return;
    setApproving(true);
    try {
      const result = await approveCodingTask(task.id);
      appendLog("output", `批准执行 exit=${result.exitCode}\n${result.output}`);
      setApprovalCommand("");
      await refreshTask();
    } catch (e) {
      antdMessage.error(e instanceof Error ? e.message : "批准失败");
    } finally {
      setApproving(false);
    }
  };

  const handleReject = async () => {
    if (!task?.id) return;
    setApproving(true);
    try {
      await rejectCodingTask(task.id, "用户在界面拒绝执行");
      appendLog("failed", "审批被拒绝");
      setApprovalCommand("");
      await refreshTask();
    } catch (e) {
      antdMessage.error(e instanceof Error ? e.message : "拒绝失败");
    } finally {
      setApproving(false);
    }
  };

  const handleSelectFile = (path: string) => {
    setSelectedFilePath(path);
    setFileDiff(null);
  };

  if (!sessionId) {
    return (
      <div className="flex flex-col h-full p-6 overflow-y-auto">
        <div className="max-w-2xl w-full mx-auto flex flex-col gap-4">
          <Card>
            <div className="flex items-start gap-4">
              <div className="w-14 h-14 rounded-lg bg-gradient-to-br from-emerald-200 to-cyan-200 flex items-center justify-center text-2xl shrink-0">
                <CodeOutlined />
              </div>
              <div>
                <Title level={3} className="mb-1">
                  AI Coding 工作台
                </Title>
                <Text type="secondary">
                  仿 Claude Code：文件树 · 代码预览 · Agent 对话 · 终端
                </Text>
              </div>
            </div>
          </Card>
          <Card title="开始 AI Coding">
            <Steps
              size="small"
              current={wizardStep}
              className="mb-4"
              items={[
                { title: "工作区" },
                { title: "Agent" },
              ]}
            />
            <Space direction="vertical" size="middle" className="w-full">
              {wizardStep === 0 && (
                <>
                  <Select
                    placeholder="本地工程根目录"
                    value={workspaceRoot || undefined}
                    onChange={setWorkspaceRoot}
                    options={workspaceOptions.map((w) => ({
                      value: w.path,
                      label: w.label,
                    }))}
                    className="w-full"
                  />
                  <Input
                    value={workspacePath}
                    onChange={(e) => setWorkspacePath(e.target.value)}
                    placeholder="子路径，如 . 或 api-module"
                  />
                  {detectHint && (
                    <Alert type="info" showIcon message={detectHint} />
                  )}
                  <Alert
                    type="success"
                    showIcon
                    message="技术栈无需预先选择，进入对话后 Agent 会根据项目文件自动识别；也可在消息中说明「用 Python + uv」等偏好。"
                  />
                  <div className="flex items-center justify-between">
                    <Text type="secondary">空目录脚手架（从零创建，可选）</Text>
                    <Switch
                      checked={scaffoldOnCreate}
                      onChange={setScaffoldOnCreate}
                    />
                  </div>
                  <Button type="primary" onClick={() => setWizardStep(1)}>
                    下一步
                  </Button>
                </>
              )}
              {wizardStep === 1 && (
                <>
                  <Select
                    value={agentRole}
                    onChange={(v) => setAgentRole(v as CodingAgentRole)}
                    options={[
                      {
                        value: "orchestrator",
                        label: "Orchestrator（默认，自动委派子任务）",
                      },
                      {
                        value: "worker",
                        label: "Worker（单 Agent 直连开发）",
                      },
                    ]}
                    className="w-full"
                  />
                  <Select
                    placeholder="智能体（预设已自动选择，可手动覆盖）"
                    value={agentId || undefined}
                    onChange={setAgentId}
                    options={agents.map((a) => ({
                      value: a.id,
                      label: a.name,
                    }))}
                    className="w-full"
                  />
                  <Select
                    value={approvalMode}
                    onChange={(v) =>
                      setApprovalMode(v as CodingApprovalModeType)
                    }
                    options={APPROVAL_MODE_OPTIONS}
                    className="w-full"
                  />
                  <Space>
                    <Button onClick={() => setWizardStep(0)}>上一步</Button>
                    <Button
                      type="primary"
                      icon={<PlayCircleOutlined />}
                      loading={creating}
                      onClick={handleCreateTask}
                    >
                      开始开发
                    </Button>
                  </Space>
                </>
              )}
            </Space>
          </Card>
        </div>
      </div>
    );
  }

  return (
    <div className="flex flex-col h-full min-h-0 bg-white">
      {/* 顶栏 */}
      <div className="shrink-0 flex items-center justify-between gap-3 px-4 py-2 border-b border-gray-200 bg-gray-50">
        <div className="flex items-center gap-3 min-w-0">
          <CodeOutlined className="text-emerald-600 text-lg" />
          <div className="min-w-0">
            <Text strong className="block truncate">
              {task
                ? `${task.workspaceRoot?.split(/[/\\]/).pop() ?? "工程"} / ${task.workspacePath}`
                : workspaceRoot
                  ? `${workspaceRoot.split(/[/\\]/).pop()} / ${workspacePath}`
                  : "AI Coding"}
            </Text>
            <Text type="secondary" className="text-xs">
              {task
                ? `任务 ${task.id.slice(0, 8)}…`
                : "发送任务描述后自动创建 Coding 任务"}
            </Text>
          </div>
          {task && <Tag color={STATUS_COLOR[task.status]}>{task.status}</Tag>}
          {task?.stackId && <Tag color="geekblue">{task.stackId}</Tag>}
          {task?.language && <Tag>{task.language}</Tag>}
          {task?.skillId && <Tag color="blue">Skill: {task.skillId}</Tag>}
          {task?.approvalMode && <Tag>{task.approvalMode}</Tag>}
          {runtimeStatus && (
            <Tag color={runtimeStatus.mcpEnabled ? "green" : "orange"}>
              MCP {runtimeStatus.mcpEnabled ? "已启用" : "未启用"}
            </Tag>
          )}
          {runtimeStatus?.deliveryRequireVerification && (
            <Tag color="purple">须验证后交付</Tag>
          )}
        </div>
        <Space wrap>
          <Button
            size="small"
            icon={<ReloadOutlined />}
            onClick={() => setTreeRefreshToken((t) => t + 1)}
          >
            刷新文件树
          </Button>
          <Button
            size="small"
            icon={<SettingOutlined />}
            onClick={() => setShowVerifyPanel((v) => !v)}
          >
            验证
          </Button>
          <Button size="small" onClick={() => setShowSetup(true)}>
            新建任务
          </Button>
        </Space>
      </div>

      {showVerifyPanel && (
        <div className="shrink-0 px-4 py-2 border-b border-gray-100 bg-amber-50/50 flex flex-wrap items-center gap-2">
          <Text type="secondary" className="text-xs mr-1">
            {currentStack?.verifyWorkflow ?? "栈感知验证命令"}
          </Text>
          {verifyCommands.map((cmd) => (
            <Button
              key={cmd.label}
              size="small"
              icon={<ThunderboltOutlined />}
              loading={running}
              onClick={() => handleRunVerify(cmd)}
            >
              {cmd.label}
            </Button>
          ))}
          {verifyCommands.length === 0 && (
            <Text type="secondary" className="text-xs">
              当前栈未配置 verifyCommands
            </Text>
          )}
          {task?.stackId === "java-maven" && (
            <>
              <Select
                size="small"
                value={goal}
                onChange={(v) => setGoal(v as MavenGoalType)}
                options={GOAL_OPTIONS}
                style={{ minWidth: 160 }}
              />
              {goal === "test_single" && (
                <Input
                  size="small"
                  value={testPattern}
                  onChange={(e) => setTestPattern(e.target.value)}
                  placeholder="testPattern"
                  style={{ width: 200 }}
                />
              )}
              <Button
                size="small"
                loading={running}
                onClick={handleRunMaven}
              >
                高级 Maven
              </Button>
            </>
          )}
        </div>
      )}

      {/* 三栏主区域 */}
      <div className="flex-1 flex min-h-0">
        <div className="w-56 shrink-0 min-h-0">
          {task ? (
            <CodingFileTree
              taskId={task.id}
              selectedPath={selectedFilePath}
              onSelectFile={handleSelectFile}
              refreshToken={treeRefreshToken}
            />
          ) : (
            <div className="p-3 text-xs text-gray-400">
              发送首条任务消息后显示文件树
            </div>
          )}
        </div>
        <div className="flex-1 min-w-0 min-h-0">
          {task ? (
            <CodingFilePreview
              taskId={task.id}
              filePath={selectedFilePath}
              diff={fileDiff}
              stackLanguage={task.language}
            />
          ) : (
            <div className="flex items-center justify-center h-full text-gray-400 text-sm">
              描述你的开发目标，Agent 将自动创建任务并开始编排
            </div>
          )}
        </div>
        <div className="w-[380px] shrink-0 flex flex-col min-h-0 border-l border-gray-200">
          <div className="px-3 py-2 border-b border-gray-100 shrink-0">
            <Text strong className="text-sm">
              Agent 对话
            </Text>
            <Alert
              type="info"
              showIcon={false}
              banner
              className="mt-2 py-1"
              message="Claude Code 模式：选定工作区后直接对话；技术栈自动识别，可用 @文件 引用"
            />
          </div>
          <CodingSubtaskPanel
            sessionId={sessionId}
            refreshToken={subtaskRefreshToken}
          />
          {taskSummary && task?.status === "COMPLETED" && (
            <CodingCompletionCard
              summary={taskSummary}
              onOpenFile={(path) => {
                setSelectedFilePath(path);
                setFileDiff(null);
              }}
            />
          )}
          <div className="flex-1 min-h-0 flex flex-col">
            <AgentChatHistory
              messages={messages}
              displayAgentStatus={displayAgentStatus}
              agentStatusText={agentStatusText}
              agentStatusType={agentStatusType}
            />
            <div className="border-t border-gray-200 p-3 bg-white shrink-0">
              <CodingChatInput
                onSend={handleChatSend}
                selectedFilePath={selectedFilePath}
                disabled={chatSending}
              />
              {chatSending && (
                <Text type="secondary" className="text-xs mt-1 block">
                  Agent 处理中…
                </Text>
              )}
            </div>
          </div>
        </div>
      </div>

      <CodingTerminalPanel
        logs={logs}
        collapsed={terminalCollapsed}
        onToggleCollapse={() => setTerminalCollapsed((v) => !v)}
        approvalCommand={approvalCommand || undefined}
        onApprove={handleApprove}
        onReject={handleReject}
        approving={approving}
      />
      <div ref={logEndRef} />

      <Modal
        open={showSetup}
        title="新建 Coding 任务"
        onCancel={() => setShowSetup(false)}
        footer={null}
        destroyOnClose
      >
        <Space direction="vertical" className="w-full mt-2">
          <Text type="warning" className="text-xs">
            新建任务将离开当前会话，请先保存未完成的工作。
          </Text>
          <Select
            placeholder="智能体"
            value={agentId || undefined}
            onChange={setAgentId}
            options={agents.map((a) => ({ value: a.id, label: a.name }))}
            className="w-full"
          />
          <Select
            placeholder="工作区"
            value={workspaceRoot || undefined}
            onChange={setWorkspaceRoot}
            options={workspaceOptions.map((w) => ({
              value: w.path,
              label: w.label,
            }))}
            className="w-full"
          />
          <Input
            value={workspacePath}
            onChange={(e) => setWorkspacePath(e.target.value)}
          />
          <Select
            value={approvalMode}
            onChange={(v) => setApprovalMode(v as CodingApprovalModeType)}
            options={APPROVAL_MODE_OPTIONS}
            className="w-full"
          />
          <Button
            type="primary"
            loading={creating}
            onClick={() => {
              setTask(null);
              setSessionId("");
              handleCreateTask();
            }}
          >
            创建并进入
          </Button>
        </Space>
      </Modal>
    </div>
  );
};

export default CodingView;
