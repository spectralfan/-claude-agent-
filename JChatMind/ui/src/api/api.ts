import { get, post, patch, del, BASE_URL } from "./http.ts";
import type { ChatMessageVO, MessageType } from "../types";

// 类型定义
export interface ChatOptions {
  temperature?: number;
  topP?: number;
  messageLength?: number;
}

export type ModelType = "deepseek-chat" | "glm-4.6";

export interface CreateAgentRequest {
  name: string;
  description?: string;
  systemPrompt?: string;
  model: ModelType;
  allowedTools?: string[];
  allowedKbs?: string[];
  chatOptions?: ChatOptions;
}

export interface UpdateAgentRequest {
  name?: string;
  description?: string;
  systemPrompt?: string;
  model?: ModelType;
  allowedTools?: string[];
  allowedKbs?: string[];
  chatOptions?: ChatOptions;
}

export interface CreateAgentResponse {
  agentId: string;
}

export interface AgentVO {
  id: string;
  name: string;
  description?: string;
  systemPrompt?: string;
  model: ModelType;
  allowedTools?: string[];
  allowedKbs?: string[];
  chatOptions?: ChatOptions;
  createdAt?: string;
  updatedAt?: string;
}

export interface GetAgentsResponse {
  agents: AgentVO[];
}

/**
 * 获取所有 agents
 */
export async function getAgents(): Promise<GetAgentsResponse> {
  return get<GetAgentsResponse>("/agents");
}

/**
 * 创建 agent
 */
export async function createAgent(
  request: CreateAgentRequest,
): Promise<CreateAgentResponse> {
  return post<CreateAgentResponse>("/agents", request);
}

/**
 * 删除 agent
 */
export async function deleteAgent(agentId: string): Promise<void> {
  return del<void>(`/agents/${agentId}`);
}

/**
 * 更新 agent
 */
export async function updateAgent(
  agentId: string,
  request: UpdateAgentRequest,
): Promise<void> {
  return patch<void>(`/agents/${agentId}`, request);
}

/**
 * 创建聊天会话
 */
export interface CreateChatSessionRequest {
  agentId: string;
  title?: string;
  workspaceRoot?: string;
  workspacePath?: string;
  approvalMode?: CodingApprovalModeType;
  scaffoldOnCreate?: boolean;
}

export interface CreateChatSessionResponse {
  chatSessionId: string;
}

export async function createChatSession(
  request: CreateChatSessionRequest,
): Promise<CreateChatSessionResponse> {
  return post<CreateChatSessionResponse>("/chat-sessions", request);
}

/**
 * 聊天会话相关类型和接口
 */
export interface ChatSessionVO {
  id: string;
  agentId: string;
  title?: string;
}

export interface GetChatSessionsResponse {
  chatSessions: ChatSessionVO[];
}

export interface GetChatSessionResponse {
  chatSession: ChatSessionVO;
}

export interface UpdateChatSessionRequest {
  title?: string;
}

/**
 * 获取所有聊天会话
 */
export async function getChatSessions(): Promise<GetChatSessionsResponse> {
  return get<GetChatSessionsResponse>("/chat-sessions");
}

/**
 * 获取单个聊天会话
 */
export async function getChatSession(
  chatSessionId: string,
): Promise<GetChatSessionResponse> {
  return get<GetChatSessionResponse>(`/chat-sessions/${chatSessionId}`);
}

/**
 * 根据 agentId 获取聊天会话
 */
export async function getChatSessionsByAgentId(
  agentId: string,
): Promise<GetChatSessionsResponse> {
  return get<GetChatSessionsResponse>(`/chat-sessions/agent/${agentId}`);
}

/**
 * 更新聊天会话
 */
export async function updateChatSession(
  chatSessionId: string,
  request: UpdateChatSessionRequest,
): Promise<void> {
  return patch<void>(`/chat-sessions/${chatSessionId}`, request);
}

/**
 * 删除聊天会话
 */
export async function deleteChatSession(chatSessionId: string): Promise<void> {
  return del<void>(`/chat-sessions/${chatSessionId}`);
}

/**
 * 聊天消息相关类型和接口
 */
export interface MetaData {
  [key: string]: unknown;
}

export interface GetChatMessagesResponse {
  chatMessages: ChatMessageVO[];
}

export interface CreateChatMessageRequest {
  agentId: string;
  sessionId: string;
  role: MessageType;
  content: string;
  metadata?: MetaData;
}

export interface CreateChatMessageResponse {
  chatMessageId: string;
}

export interface UpdateChatMessageRequest {
  content?: string;
  metadata?: MetaData;
}

/**
 * 根据 sessionId 获取聊天消息
 */
export async function getChatMessagesBySessionId(
  sessionId: string,
): Promise<GetChatMessagesResponse> {
  return get<GetChatMessagesResponse>(`/chat-messages/session/${sessionId}`);
}

/**
 * 创建聊天消息
 */
export async function createChatMessage(
  request: CreateChatMessageRequest,
): Promise<CreateChatMessageResponse> {
  return post<CreateChatMessageResponse>("/chat-messages", request);
}

/**
 * 更新聊天消息
 */
export async function updateChatMessage(
  chatMessageId: string,
  request: UpdateChatMessageRequest,
): Promise<void> {
  return patch<void>(`/chat-messages/${chatMessageId}`, request);
}

/**
 * 删除聊天消息
 */
export async function deleteChatMessage(chatMessageId: string): Promise<void> {
  return del<void>(`/chat-messages/${chatMessageId}`);
}

/**
 * 知识库相关类型和接口
 */
export interface KnowledgeBaseVO {
  id: string;
  name: string;
  description?: string;
}

export interface CreateKnowledgeBaseRequest {
  name: string;
  description?: string;
}

export interface UpdateKnowledgeBaseRequest {
  name?: string;
  description?: string;
}

export interface GetKnowledgeBasesResponse {
  knowledgeBases: KnowledgeBaseVO[];
}

export interface CreateKnowledgeBaseResponse {
  knowledgeBaseId: string;
}

/**
 * 获取所有知识库
 */
export async function getKnowledgeBases(): Promise<GetKnowledgeBasesResponse> {
  return get<GetKnowledgeBasesResponse>("/knowledge-bases");
}

/**
 * 创建知识库
 */
export async function createKnowledgeBase(
  request: CreateKnowledgeBaseRequest,
): Promise<CreateKnowledgeBaseResponse> {
  return post<CreateKnowledgeBaseResponse>("/knowledge-bases", request);
}

/**
 * 删除知识库
 */
export async function deleteKnowledgeBase(
  knowledgeBaseId: string,
): Promise<void> {
  return del<void>(`/knowledge-bases/${knowledgeBaseId}`);
}

/**
 * 更新知识库
 */
export async function updateKnowledgeBase(
  knowledgeBaseId: string,
  request: UpdateKnowledgeBaseRequest,
): Promise<void> {
  return patch<void>(`/knowledge-bases/${knowledgeBaseId}`, request);
}

/**
 * 文档相关类型和接口
 */
export interface DocumentVO {
  id: string;
  kbId: string;
  filename: string;
  filetype: string;
  size: number;
}

export interface GetDocumentsResponse {
  documents: DocumentVO[];
}

export interface CreateDocumentResponse {
  documentId: string;
}

/**
 * 根据知识库 ID 获取文档列表
 */
export async function getDocumentsByKbId(
  kbId: string,
): Promise<GetDocumentsResponse> {
  return get<GetDocumentsResponse>(`/documents/kb/${kbId}`);
}

/**
 * 上传文档
 */
export async function uploadDocument(
  kbId: string,
  file: File,
): Promise<CreateDocumentResponse> {
  const formData = new FormData();
  formData.append("kbId", kbId);
  formData.append("file", file);

  const response = await fetch(`${BASE_URL}/documents/upload`, {
    method: "POST",
    body: formData,
  });

  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`);
  }

  const apiResponse = await response.json();
  if (apiResponse.code !== 200) {
    throw new Error(apiResponse.message || "上传失败");
  }

  return apiResponse.data;
}

/**
 * 删除文档
 */
export async function deleteDocument(documentId: string): Promise<void> {
  return del<void>(`/documents/${documentId}`);
}

/**
 * 工具相关类型和接口
 */
export type ToolType = "FIXED" | "OPTIONAL";

export interface ToolVO {
  name: string;
  description: string;
  type: ToolType;
}

export interface GetOptionalToolsResponse {
  tools: ToolVO[];
}

/**
 * 获取可选工具列表
 */
export async function getOptionalTools(): Promise<GetOptionalToolsResponse> {
  const tools = await get<ToolVO[]>("/tools");
  return { tools };
}

/**
 * AI Coding 模块相关类型和接口
 */
export type MavenGoalType =
  | "compile"
  | "test"
  | "test_single"
  | "package_skip_tests"
  | "clean_compile"
  | "clean_test";

export type CodingTaskStatusType =
  | "PENDING"
  | "RUNNING"
  | "WAITING_APPROVAL"
  | "COMPLETED"
  | "FAILED"
  | "TIMEOUT"
  | "REJECTED";

/**
 * 获取可选的本地工作区（IDEA/Maven 工程）列表
 */
export async function getCodingWorkspaces(): Promise<CodingWorkspaceOption[]> {
  return get<CodingWorkspaceOption[]>("/coding/workspaces");
}

export async function getCodingSkills(): Promise<CodingSkillVO[]> {
  return get<CodingSkillVO[]>("/coding/skills");
}

export interface CodingStackVO {
  id: string;
  language: string;
  displayName: string;
  detectFiles?: string[];
  skillId?: string;
  verifyWorkflow?: string;
  doneCriteria?: string;
  verifyCommands?: StackVerifyCommandVO[];
}

export interface StackVerifyCommandVO {
  label: string;
  type: "maven" | "shell";
  goal?: MavenGoalType;
  command?: string;
}

export async function getCodingStacks(): Promise<CodingStackVO[]> {
  return get<CodingStackVO[]>("/coding/stacks");
}

export interface CodingAgentPresetVO {
  presetKey?: string;
  agentId: string;
  name: string;
  description?: string;
  model?: string;
  allowedTools?: string[];
}

export async function getCodingAgentPreset(): Promise<CodingAgentPresetVO | null> {
  const data = await get<CodingAgentPresetVO | null>("/coding/agents/preset");
  return data;
}

export async function getCodingOrchestratorPreset(): Promise<CodingAgentPresetVO | null> {
  return get<CodingAgentPresetVO | null>("/coding/agents/orchestrator-preset");
}

export interface WorkspaceDetectResultVO {
  stackId?: string;
  displayName?: string;
  language?: string;
  matchedFile?: string;
  emptyWorkspace: boolean;
}

export async function detectCodingWorkspace(
  workspaceRoot: string,
  workspacePath = ".",
): Promise<WorkspaceDetectResultVO> {
  return get<WorkspaceDetectResultVO>("/coding/workspaces/detect", {
    workspaceRoot,
    workspacePath,
  });
}

export interface CodingTaskVO {
  id: string;
  sessionId: string;
  agentId: string;
  status: CodingTaskStatusType;
  workspacePath: string;
  workspaceRoot?: string;
  command?: string;
  resultSummary?: string;
  skillId?: string;
  stackId?: string;
  language?: string;
  approvalMode?: CodingApprovalModeType;
  approvalReason?: string;
  startedAt?: string;
  finishedAt?: string;
}

export interface CommandExecutionResult {
  exitCode: number;
  output: string;
  timeout: boolean;
}

export interface CodingWorkspaceOption {
  label: string;
  path: string;
  defaultOption: boolean;
}

export interface CreateCodingTaskRequest {
  sessionId: string;
  agentId: string;
  /** 网页选择的本地工程根（须在服务端白名单内） */
  workspaceRoot: string;
  /** 相对工程根的子路径，多模块时可填子模块名 */
  workspacePath: string;
  stackId?: string;
  skillId?: string;
  approvalMode?: CodingApprovalModeType;
  scaffoldOnCreate?: boolean;
  autoDetectStack?: boolean;
}

export type CodingApprovalModeType = "strict" | "development" | "trusted";

export interface CodingSkillVO {
  id: string;
  name: string;
  description: string;
  prompt?: string;
  suggestedTools?: string[];
}

export interface RunMavenRequest {
  goal: MavenGoalType;
  testPattern?: string;
  sessionId: string;
  agentId: string;
}

/**
 * 查询会话下当前活动的 Coding 任务
 */
export async function getActiveCodingTask(
  sessionId: string,
): Promise<CodingTaskVO | null> {
  const data = await get<CodingTaskVO | null>(
    `/coding/tasks/session/${sessionId}/active`,
  );
  return data;
}

/**
 * 创建 Coding 任务
 */
export async function createCodingTask(
  request: CreateCodingTaskRequest,
): Promise<CodingTaskVO> {
  return post<CodingTaskVO>("/coding/tasks", request);
}

/**
 * 查询 Coding 任务
 */
export async function getCodingTask(
  taskId: string,
): Promise<CodingTaskVO> {
  return get<CodingTaskVO>(`/coding/tasks/${taskId}`);
}

export interface CodingTaskSummaryVO {
  taskId: string;
  status: CodingTaskStatusType;
  stackId?: string;
  language?: string;
  resultSummary?: string;
  lastCommand?: string;
  lastCommandOutput?: string;
  completed: boolean;
  changedFiles: string[];
  runInstructions?: string;
}

export async function getCodingTaskSummary(
  taskId: string,
): Promise<CodingTaskSummaryVO> {
  return get<CodingTaskSummaryVO>(`/coding/tasks/${taskId}/summary`);
}

/**
 * 执行 Maven 命令（受控白名单 + 审批）
 */
export async function runMavenCommand(
  taskId: string,
  request: RunMavenRequest,
): Promise<string> {
  return post<string>(`/coding/tasks/${taskId}/run-maven`, request);
}

export interface RunShellRequest {
  command: string;
  sessionId: string;
}

export async function runShellCommand(
  taskId: string,
  request: RunShellRequest,
): Promise<CommandExecutionResult> {
  return post<CommandExecutionResult>(
    `/coding/tasks/${taskId}/run-shell`,
    request,
  );
}

export interface CodingSubtaskVO {
  id: string;
  parentSessionId: string;
  parentTaskId: string;
  title: string;
  goal?: string;
  workerAgentId?: string;
  status: string;
  resultSummary?: string;
  errorMessage?: string;
  createdAt?: string;
  finishedAt?: string;
}

export async function getCodingSubtasks(
  sessionId: string,
): Promise<CodingSubtaskVO[]> {
  return get<CodingSubtaskVO[]>(
    `/coding/tasks/session/${sessionId}/subtasks`,
  );
}

export interface CodingRuntimeStatusVO {
  mcpEnabled: boolean;
  springMcpClientEnabled: boolean;
  deliveryRequireVerification: boolean;
}

export async function getCodingRuntimeStatus(): Promise<CodingRuntimeStatusVO> {
  return get<CodingRuntimeStatusVO>("/coding/runtime-status");
}

/**
 * 批准待审批命令并执行
 */
export async function approveCodingTask(
  taskId: string,
): Promise<CommandExecutionResult> {
  return post<CommandExecutionResult>(`/coding/tasks/${taskId}/approve`);
}

/**
 * 拒绝待审批命令
 */
export async function rejectCodingTask(
  taskId: string,
  reason: string,
): Promise<void> {
  return post<void>(`/coding/tasks/${taskId}/reject`, { reason });
}

export interface FileNodeVO {
  name: string;
  relativePath: string;
  directory: boolean;
}

export interface CodingFileContentVO {
  relativePath: string;
  content: string;
  size: number;
  truncated: boolean;
  language: string;
}

export async function getCodingTaskTree(
  taskId: string,
  path = ".",
): Promise<FileNodeVO[]> {
  return get<FileNodeVO[]>(`/coding/tasks/${taskId}/tree`, { path });
}

export async function getCodingTaskFile(
  taskId: string,
  path: string,
): Promise<CodingFileContentVO> {
  return get<CodingFileContentVO>(`/coding/tasks/${taskId}/file`, { path });
}
