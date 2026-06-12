import { get, post, patch, del, BASE_URL } from "./http.ts";
import type { ChatMessageVO, MessageType } from "../types";

// 绫诲瀷瀹氫箟
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
 * 鑾峰彇鎵€鏈?agents
 */
export async function getAgents(): Promise<GetAgentsResponse> {
  return get<GetAgentsResponse>("/agents");
}

/**
 * 鍒涘缓 agent
 */
export async function createAgent(
  request: CreateAgentRequest,
): Promise<CreateAgentResponse> {
  return post<CreateAgentResponse>("/agents", request);
}

/**
 * 鍒犻櫎 agent
 */
export async function deleteAgent(agentId: string): Promise<void> {
  return del<void>(`/agents/${agentId}`);
}

/**
 * 鏇存柊 agent
 */
export async function updateAgent(
  agentId: string,
  request: UpdateAgentRequest,
): Promise<void> {
  return patch<void>(`/agents/${agentId}`, request);
}

/**
 * 鍒涘缓鑱婂ぉ浼氳瘽
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
 * 鑱婂ぉ浼氳瘽鐩稿叧绫诲瀷鍜屾帴鍙? */
export interface ChatSessionVO {
  id: string;
  agentId: string;
  title?: string;
  type?: string;
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
 * 鑾峰彇鎵€鏈夎亰澶╀細璇? */
export async function getChatSessions(type?: string): Promise<GetChatSessionsResponse> {
  return get<GetChatSessionsResponse>("/chat-sessions", type ? { type } : undefined);
}

/**
 * 鑾峰彇鍗曚釜鑱婂ぉ浼氳瘽
 */
export async function getChatSession(
  chatSessionId: string,
): Promise<GetChatSessionResponse> {
  return get<GetChatSessionResponse>(`/chat-sessions/${chatSessionId}`);
}

/**
 * 鏍规嵁 agentId 鑾峰彇鑱婂ぉ浼氳瘽
 */
export async function getChatSessionsByAgentId(
  agentId: string,
): Promise<GetChatSessionsResponse> {
  return get<GetChatSessionsResponse>(`/chat-sessions/agent/${agentId}`);
}

/**
 * 鏇存柊鑱婂ぉ浼氳瘽
 */
export async function updateChatSession(
  chatSessionId: string,
  request: UpdateChatSessionRequest,
): Promise<void> {
  return patch<void>(`/chat-sessions/${chatSessionId}`, request);
}

/**
 * 鍒犻櫎鑱婂ぉ浼氳瘽
 */
export async function deleteChatSession(chatSessionId: string): Promise<void> {
  return del<void>(`/chat-sessions/${chatSessionId}`);
}

/**
 * 鑱婂ぉ娑堟伅鐩稿叧绫诲瀷鍜屾帴鍙? */
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
 * 鏍规嵁 sessionId 鑾峰彇鑱婂ぉ娑堟伅
 */
export async function getChatMessagesBySessionId(
  sessionId: string,
): Promise<GetChatMessagesResponse> {
  return get<GetChatMessagesResponse>(`/chat-messages/session/${sessionId}`);
}

/**
 * 鍒涘缓鑱婂ぉ娑堟伅
 */
export async function createChatMessage(
  request: CreateChatMessageRequest,
): Promise<CreateChatMessageResponse> {
  return post<CreateChatMessageResponse>("/chat-messages", request);
}

/**
 * 鏇存柊鑱婂ぉ娑堟伅
 */
export async function updateChatMessage(
  chatMessageId: string,
  request: UpdateChatMessageRequest,
): Promise<void> {
  return patch<void>(`/chat-messages/${chatMessageId}`, request);
}

/**
 * 鍒犻櫎鑱婂ぉ娑堟伅
 */
export async function deleteChatMessage(chatMessageId: string): Promise<void> {
  return del<void>(`/chat-messages/${chatMessageId}`);
}

/**
 * 鐭ヨ瘑搴撶浉鍏崇被鍨嬪拰鎺ュ彛
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
 * 鑾峰彇鎵€鏈夌煡璇嗗簱
 */
export async function getKnowledgeBases(): Promise<GetKnowledgeBasesResponse> {
  return get<GetKnowledgeBasesResponse>("/knowledge-bases");
}

/**
 * 鍒涘缓鐭ヨ瘑搴? */
export async function createKnowledgeBase(
  request: CreateKnowledgeBaseRequest,
): Promise<CreateKnowledgeBaseResponse> {
  return post<CreateKnowledgeBaseResponse>("/knowledge-bases", request);
}

/**
 * 鍒犻櫎鐭ヨ瘑搴? */
export async function deleteKnowledgeBase(
  knowledgeBaseId: string,
): Promise<void> {
  return del<void>(`/knowledge-bases/${knowledgeBaseId}`);
}

/**
 * 鏇存柊鐭ヨ瘑搴? */
export async function updateKnowledgeBase(
  knowledgeBaseId: string,
  request: UpdateKnowledgeBaseRequest,
): Promise<void> {
  return patch<void>(`/knowledge-bases/${knowledgeBaseId}`, request);
}

/**
 * 鏂囨。鐩稿叧绫诲瀷鍜屾帴鍙? */
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
 * 鏍规嵁鐭ヨ瘑搴?ID 鑾峰彇鏂囨。鍒楄〃
 */
export async function getDocumentsByKbId(
  kbId: string,
): Promise<GetDocumentsResponse> {
  return get<GetDocumentsResponse>(`/documents/kb/${kbId}`);
}

/**
 * 涓婁紶鏂囨。
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
    throw new Error(apiResponse.message || "涓婁紶澶辫触");
  }

  return apiResponse.data;
}

/**
 * 鍒犻櫎鏂囨。
 */
export async function deleteDocument(documentId: string): Promise<void> {
  return del<void>(`/documents/${documentId}`);
}

/**
 * 宸ュ叿鐩稿叧绫诲瀷鍜屾帴鍙? */
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
 * 鑾峰彇鍙€夊伐鍏峰垪琛? */
export async function getOptionalTools(): Promise<GetOptionalToolsResponse> {
  const tools = await get<ToolVO[]>("/tools");
  return { tools };
}

/**
 * AI Coding 妯″潡鐩稿叧绫诲瀷鍜屾帴鍙? */
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
 * 鑾峰彇鍙€夌殑鏈湴宸ヤ綔鍖猴紙IDEA/Maven 宸ョ▼锛夊垪琛? */
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
  /** 缃戦〉閫夋嫨鐨勬湰鍦板伐绋嬫牴锛堥』鍦ㄦ湇鍔＄鐧藉悕鍗曞唴锛?*/
  workspaceRoot: string;
  /** 鐩稿宸ョ▼鏍圭殑瀛愯矾寰勶紝澶氭ā鍧楁椂鍙～瀛愭ā鍧楀悕 */
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
 * 鏌ヨ浼氳瘽涓嬪綋鍓嶆椿鍔ㄧ殑 Coding 浠诲姟
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
 * 鍒涘缓 Coding 浠诲姟
 */
export async function createCodingTask(
  request: CreateCodingTaskRequest,
): Promise<CodingTaskVO> {
  return post<CodingTaskVO>("/coding/tasks", request);
}

/**
 * 鏌ヨ Coding 浠诲姟
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
 * 鎵ц Maven 鍛戒护锛堝彈鎺х櫧鍚嶅崟 + 瀹℃壒锛? */
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
  role?: string;
  title: string;
  goal?: string;
  constraints?: string;
  contextFiles?: string[];
  dependsOn?: string[];
  workerAgentId?: string;
  status: string;
  resultSummary?: string;
  errorMessage?: string;
  depth?: number;
  spawnedFromTaskId?: string;
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
 * 鎵瑰噯寰呭鎵瑰懡浠ゅ苟鎵ц
 */
export async function approveCodingTask(
  taskId: string,
): Promise<CommandExecutionResult> {
  return post<CommandExecutionResult>(`/coding/tasks/${taskId}/approve`);
}

/**
 * 鎷掔粷寰呭鎵瑰懡浠? */
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
