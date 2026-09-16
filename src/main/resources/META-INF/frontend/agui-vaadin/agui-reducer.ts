/**
 * AG-UI 事件归约器：纯函数，把事件流折叠成对话状态。
 *
 * messages 与协议线上格式一致，可直接作为下一次运行的输入；
 * UiMessage 额外带 thinking 与 complete 两个渲染字段，发送前由 toWireMessages 剥离。
 */
import type { AgUiEvent, ChatState, JsonPatchOp, Message, ToolCall, UiMessage } from './agui-types.js';

export function initialState(messages: Message[] = [], agentState: unknown = {}): ChatState {
  return {
    messages: messages.map((m) => ({ ...m, complete: true })),
    running: false,
    runId: null,
    step: null,
    agentState,
    error: null,
    toolStatus: {},
  };
}

export function reduce(state: ChatState, event: AgUiEvent): ChatState {
  switch (event.type) {
    case 'RUN_STARTED':
      return { ...state, running: true, runId: event.runId, error: null, step: null };

    case 'RUN_FINISHED':
      return { ...state, running: false, step: null };

    case 'RUN_ERROR':
      return { ...state, running: false, error: { message: event.message, code: event.code ?? null } };

    case 'STEP_STARTED':
      return { ...state, step: event.stepName };

    case 'STEP_FINISHED':
      return { ...state, step: null };

    case 'TEXT_MESSAGE_START': {
      // 思考块先于文本到达时，宿主是一条只有 thinking、还没有内容的 assistant 消息；
      // 文本应落进同一条消息，而不是另起一个气泡
      const last = state.messages[state.messages.length - 1];
      if (last && last.role === 'assistant' && !last.complete && !last.content && !last.toolCalls) {
        const merged = [...state.messages];
        merged[merged.length - 1] = { ...last, id: event.messageId, content: '' };
        return withMessages(state, merged);
      }
      return withMessages(state, [
        ...state.messages,
        { id: event.messageId, role: event.role ?? 'assistant', content: '', complete: false },
      ]);
    }

    case 'TEXT_MESSAGE_CONTENT':
      return updateMessage(state, event.messageId, (m) => ({ ...m, content: (m.content ?? '') + event.delta }));

    case 'TEXT_MESSAGE_END':
      return updateMessage(state, event.messageId, (m) => ({ ...m, complete: true }));

    case 'THINKING_START':
    case 'THINKING_TEXT_MESSAGE_START':
      return withMessages(state, ensureThinkingHost(state.messages));

    case 'THINKING_TEXT_MESSAGE_CONTENT': {
      const messages = ensureThinkingHost(state.messages);
      const last = messages[messages.length - 1];
      messages[messages.length - 1] = { ...last, thinking: (last.thinking ?? '') + event.delta };
      return withMessages(state, messages);
    }

    case 'THINKING_TEXT_MESSAGE_END':
    case 'THINKING_END':
      return state;

    case 'TOOL_CALL_START': {
      const call: ToolCall = {
        id: event.toolCallId,
        type: 'function',
        function: { name: event.toolCallName, arguments: '' },
      };
      const messages = [...state.messages];
      const hostIndex = findToolHost(messages, event.parentMessageId);
      if (hostIndex >= 0) {
        const host = messages[hostIndex];
        messages[hostIndex] = { ...host, toolCalls: [...(host.toolCalls ?? []), call] };
      } else {
        messages.push({
          id: event.parentMessageId ?? `msg-${event.toolCallId}`,
          role: 'assistant',
          content: null,
          toolCalls: [call],
          complete: false,
        });
      }
      return {
        ...withMessages(state, messages),
        toolStatus: { ...state.toolStatus, [event.toolCallId]: 'streaming' },
      };
    }

    case 'TOOL_CALL_ARGS':
      return updateToolCall(state, event.toolCallId, (c) => ({
        ...c,
        function: { ...c.function, arguments: (c.function.arguments ?? '') + event.delta },
      }));

    case 'TOOL_CALL_END':
      return { ...state, toolStatus: { ...state.toolStatus, [event.toolCallId]: 'called' } };

    case 'TOOL_CALL_RESULT':
      return {
        ...withMessages(state, [
          ...state.messages,
          { id: event.messageId, role: 'tool', toolCallId: event.toolCallId, content: event.content, complete: true },
        ]),
        toolStatus: { ...state.toolStatus, [event.toolCallId]: 'done' },
      };

    case 'STATE_SNAPSHOT':
      return { ...state, agentState: event.snapshot ?? {} };

    case 'STATE_DELTA':
      return { ...state, agentState: applyJsonPatch(state.agentState, event.delta ?? []) };

    case 'MESSAGES_SNAPSHOT':
      return withMessages(state, (event.messages ?? []).map((m) => ({ ...m, complete: true })));

    default:
      // RAW、CUSTOM 及未来新增事件不改变对话状态，由组件按需转发
      return state;
  }
}

/** 去掉渲染专用字段，得到可发送给 agent 的消息数组。 */
export function toWireMessages(messages: UiMessage[]): Message[] {
  return messages.map(({ thinking: _thinking, complete: _complete, ...wire }) => wire);
}

/** 最后一条 user 消息之后的第一个下标；没有 user 消息时返回 messages.length。 */
export function indexAfterLastUser(messages: Message[]): number {
  for (let i = messages.length - 1; i >= 0; i -= 1) {
    if (messages[i].role === 'user') {
      return i + 1;
    }
  }
  return messages.length;
}

/** 按 toolCallId 找到对应的 tool 结果消息。 */
export function findToolResult(messages: Message[], toolCallId: string): Message | undefined {
  return messages.find((m) => m.role === 'tool' && m.toolCallId === toolCallId);
}

// ---- 内部工具 -------------------------------------------------------

function withMessages(state: ChatState, messages: UiMessage[]): ChatState {
  return { ...state, messages };
}

function updateMessage(state: ChatState, id: string, fn: (m: UiMessage) => UiMessage): ChatState {
  const index = state.messages.findLastIndex((m) => m.id === id);
  if (index < 0) {
    return state;
  }
  const messages = [...state.messages];
  messages[index] = fn(messages[index]);
  return withMessages(state, messages);
}

function updateToolCall(state: ChatState, toolCallId: string, fn: (c: ToolCall) => ToolCall): ChatState {
  const messages = [...state.messages];
  for (let i = messages.length - 1; i >= 0; i -= 1) {
    const calls = messages[i].toolCalls;
    if (!calls) {
      continue;
    }
    const ci = calls.findIndex((c) => c.id === toolCallId);
    if (ci >= 0) {
      const next = [...calls];
      next[ci] = fn(next[ci]);
      messages[i] = { ...messages[i], toolCalls: next };
      return withMessages(state, messages);
    }
  }
  return state;
}

/** 工具调用挂到 parentMessageId 指定的消息；未指定时挂到最后一条未完成的 assistant 消息。 */
function findToolHost(messages: UiMessage[], parentMessageId?: string): number {
  if (parentMessageId) {
    return messages.findLastIndex((m) => m.id === parentMessageId);
  }
  const last = messages.length - 1;
  return last >= 0 && messages[last].role === 'assistant' && !messages[last].complete ? last : -1;
}

/** 思考文本挂到最后一条未完成的 assistant 消息；没有则新建一条空消息承载。 */
function ensureThinkingHost(messages: UiMessage[]): UiMessage[] {
  const next = [...messages];
  const last = next[next.length - 1];
  if (!last || last.role !== 'assistant' || last.complete) {
    next.push({ id: `thinking-${Date.now()}`, role: 'assistant', content: '', thinking: '', complete: false });
  }
  return next;
}

/**
 * 最小 RFC 6902 实现：add、replace、remove、test；路径支持对象键与数组下标。
 * move 与 copy 由 remove + add 组合表达，agent 端极少使用，故不实现。
 */
export function applyJsonPatch(target: unknown, patch: JsonPatchOp[]): unknown {
  let result: unknown = structuredClone(target ?? {});
  for (const op of patch) {
    const tokens = op.path
      .split('/')
      .slice(1)
      .map((t) => t.replace(/~1/g, '/').replace(/~0/g, '~'));
    if (tokens.length === 0) {
      if (op.op === 'replace' || op.op === 'add') {
        result = structuredClone(op.value);
      }
      continue;
    }
    const key = tokens[tokens.length - 1];
    let parent = result as Container;
    for (const t of tokens.slice(0, -1)) {
      if (child(parent, t) === undefined || child(parent, t) === null) {
        setChild(parent, t, /^\d+$/.test(t) ? [] : {});
      }
      parent = child(parent, t) as Container;
    }
    switch (op.op) {
      case 'add':
        if (Array.isArray(parent)) {
          parent.splice(key === '-' ? parent.length : Number(key), 0, op.value);
        } else {
          setChild(parent, key, op.value);
        }
        break;
      case 'replace':
        setChild(parent, key, op.value);
        break;
      case 'remove':
        if (Array.isArray(parent)) {
          parent.splice(Number(key), 1);
        } else {
          delete parent[guardKey(key)];
        }
        break;
      case 'test':
        if (JSON.stringify(child(parent, key)) !== JSON.stringify(op.value)) {
          throw new Error(`JSON Patch test failed at ${op.path}`);
        }
        break;
      default:
        throw new Error(`Unsupported JSON Patch op: ${op.op}`);
    }
  }
  return result;
}

type Container = Record<string, unknown> | unknown[];

/** agent 送来的补丁路径不可信：这些键会沿原型链写入全局对象，一律拒绝 */
const FORBIDDEN_KEYS = new Set(['__proto__', 'constructor', 'prototype']);

function guardKey(key: string): string {
  if (FORBIDDEN_KEYS.has(key)) {
    throw new Error(`JSON Patch path segment not allowed: ${key}`);
  }
  return key;
}

function child(parent: Container, key: string): unknown {
  guardKey(key);
  return Array.isArray(parent) ? parent[Number(key)] : parent[key];
}

function setChild(parent: Container, key: string, value: unknown): void {
  guardKey(key);
  if (Array.isArray(parent)) {
    parent[Number(key)] = value;
  } else {
    parent[key] = value;
  }
}
