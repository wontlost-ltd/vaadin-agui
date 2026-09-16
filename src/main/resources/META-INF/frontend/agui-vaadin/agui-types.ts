/**
 * AG-UI 协议类型。字段名与协议规范逐字一致，服务端的 Java record 与此一一对应。
 */

export type Role = 'developer' | 'system' | 'assistant' | 'user' | 'tool';

export interface FunctionCall {
  name: string;
  /** JSON 字符串；流式期间可能不完整 */
  arguments: string;
}

export interface ToolCall {
  id: string;
  type: 'function';
  function: FunctionCall;
}

export interface Message {
  id: string;
  role: Role;
  content?: string | null;
  name?: string;
  toolCalls?: ToolCall[];
  toolCallId?: string;
}

/** 组件内部在协议消息上附加的两个渲染字段，发送前剥离 */
export interface UiMessage extends Message {
  thinking?: string;
  complete?: boolean;
}

export interface ToolDefinition {
  name: string;
  description: string;
  parameters: unknown;
}

export interface ContextItem {
  description: string;
  value: string;
}

export interface RunAgentInput {
  threadId: string;
  runId: string;
  state: unknown;
  messages: Message[];
  tools: ToolDefinition[];
  context: ContextItem[];
  forwardedProps: unknown;
}

export interface JsonPatchOp {
  op: 'add' | 'replace' | 'remove' | 'test' | 'move' | 'copy';
  path: string;
  value?: unknown;
  from?: string;
}

export type AgUiEvent =
  | { type: 'RUN_STARTED'; threadId: string; runId: string }
  | { type: 'RUN_FINISHED'; threadId: string; runId: string; result?: unknown }
  | { type: 'RUN_ERROR'; message: string; code?: string }
  | { type: 'STEP_STARTED'; stepName: string }
  | { type: 'STEP_FINISHED'; stepName: string }
  | { type: 'TEXT_MESSAGE_START'; messageId: string; role?: Role }
  | { type: 'TEXT_MESSAGE_CONTENT'; messageId: string; delta: string }
  | { type: 'TEXT_MESSAGE_END'; messageId: string }
  | { type: 'THINKING_START'; title?: string }
  | { type: 'THINKING_END' }
  | { type: 'THINKING_TEXT_MESSAGE_START' }
  | { type: 'THINKING_TEXT_MESSAGE_CONTENT'; delta: string }
  | { type: 'THINKING_TEXT_MESSAGE_END' }
  | { type: 'TOOL_CALL_START'; toolCallId: string; toolCallName: string; parentMessageId?: string }
  | { type: 'TOOL_CALL_ARGS'; toolCallId: string; delta: string }
  | { type: 'TOOL_CALL_END'; toolCallId: string }
  | { type: 'TOOL_CALL_RESULT'; messageId: string; toolCallId: string; content: string; role?: 'tool' }
  | { type: 'STATE_SNAPSHOT'; snapshot: unknown }
  | { type: 'STATE_DELTA'; delta: JsonPatchOp[] }
  | { type: 'MESSAGES_SNAPSHOT'; messages: Message[] }
  | { type: 'RAW'; event: unknown; source?: string }
  | { type: 'CUSTOM'; name: string; value: unknown };

export type ToolStatus = 'streaming' | 'called' | 'done';

export interface ChatState {
  messages: UiMessage[];
  running: boolean;
  runId: string | null;
  step: string | null;
  agentState: unknown;
  error: { message: string; code: string | null } | null;
  toolStatus: Record<string, ToolStatus>;
}

export interface I18n {
  placeholder: string;
  send: string;
  stop: string;
  regenerate: string;
  copy: string;
  copied: string;
  thinking: string;
  toolCall: string;
  toolResult: string;
  assistantName: string;
  userName: string;
  errorPrefix: string;
  cancelled: string;
  helpfulYes: string;
  helpfulNo: string;
}
