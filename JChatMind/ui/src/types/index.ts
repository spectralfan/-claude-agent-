export type MessageType = "user" | "assistant" | "system" | "tool";

export interface KnowledgeBase {
  knowledgeBaseId: string;
  name: string;
  description: string;
}

export interface ToolCall {
  id: string;
  type: string;
  name: string;
  arguments: string;
}

export interface ToolResponse {
  id: string;
  name: string;
  responseData: string;
}

export interface ChatMessageVOMetadata {
  toolCalls?: ToolCall[];
  toolResponse?: ToolResponse;
}

export interface ChatMessageVO {
  id: string;
  sessionId: string;
  role: MessageType;
  content: string;
  metadata?: ChatMessageVOMetadata;
}

export type SseMessageType =
  | "AI_GENERATED_CONTENT"
  | "AI_PLANNING"
  | "AI_THINKING"
  | "AI_EXECUTING"
  | "AI_OBSERVING"
  | "AI_DONE"
  | "CODING_STARTED"
  | "CODING_APPROVAL_REQUIRED"
  | "CODING_COMMAND_OUTPUT"
  | "CODING_FILE_CHANGED"
  | "CODING_SCAFFOLD_DONE"
  | "CODING_COMPLETED"
  | "CODING_FAILED"
  | "CODING_SUBTASK_STARTED"
  | "CODING_SUBTASK_COMPLETED"
  | "CODING_SUBTASK_FAILED";

export interface SseMessagePayload {
  message: ChatMessageVO;
  statusText: string;
  done: boolean;
  // AI Coding 模块字段
  taskId?: string;
  subTaskId?: string;
  actionType?: string;
  detail?: string;
  workspace?: string;
  command?: string;
  exitCode?: number;
  output?: string;
  summary?: string;
  stepsUsed?: number;
  relativePath?: string;
  changeType?: string;
  oldContent?: string;
  newContent?: string;
}

export interface SseMessageMetadata {
  chatMessageId: string;
}

export interface SseMessage {
  type: SseMessageType;
  payload: SseMessagePayload;
  metadata: SseMessageMetadata;
}
