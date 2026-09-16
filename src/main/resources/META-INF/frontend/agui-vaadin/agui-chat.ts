/**
 * <agui-chat>：AG-UI 对话组件。
 *
 * 浏览器直接以 SSE 消费 agent 事件流，Markdown 在客户端增量渲染；
 * Flow 服务端只在运行生命周期节点收到 DOM 事件。状态由 agui-reducer 归约，
 * 本文件只负责渲染与交互。
 */
import { LitElement, css, html, nothing, type PropertyValues } from 'lit';
import { customElement, property, query, state } from 'lit/decorators.js';
import { repeat } from 'lit/directives/repeat.js';
import { unsafeHTML } from 'lit/directives/unsafe-html.js';

import { findToolResult, indexAfterLastUser, initialState, reduce, toWireMessages } from './agui-reducer.js';
import { prettyJson, renderMarkdown } from './agui-markdown.js';
import { runAgent } from './agui-sse.js';
import type {
  AgUiEvent,
  ChatState,
  ContextItem,
  I18n,
  Message,
  RunAgentInput,
  ToolCall,
  ToolDefinition,
  UiMessage,
} from './agui-types.js';

/** 与 AgUiChatI18n 的默认值保持一致 */
const DEFAULT_I18N: I18n = {
  placeholder: 'Send a message…',
  send: 'Send',
  stop: 'Stop',
  regenerate: 'Regenerate',
  copy: 'Copy',
  copied: 'Copied',
  thinking: 'Thinking',
  toolCall: 'Tool call',
  toolResult: 'Result',
  assistantName: 'Assistant',
  userName: 'You',
  errorPrefix: 'Something went wrong',
  cancelled: 'Stopped',
  helpfulYes: 'Helpful',
  helpfulNo: 'Not helpful',
};

const STICK_THRESHOLD_PX = 24;

@customElement('agui-chat')
export class AgUiChatElement extends LitElement {
  static override styles = css`
    :host {
      display: flex;
      flex-direction: column;
      box-sizing: border-box;
      min-height: 0;
      height: 100%;
      font-family: var(--lumo-font-family, system-ui, sans-serif);
      font-size: var(--lumo-font-size-m, 1rem);
      line-height: var(--lumo-line-height-m, 1.5);
      color: var(--lumo-body-text-color, inherit);
      --_gap: var(--agui-gap, var(--lumo-space-m, 1rem));
      --_radius: var(--agui-bubble-radius, var(--lumo-border-radius-l, 0.75em));
      --_user-bg: var(--agui-user-bubble-background, var(--lumo-primary-color-10pct, rgba(0, 0, 0, 0.06)));
      --_assistant-bg: var(--agui-assistant-bubble-background, var(--lumo-contrast-5pct, rgba(0, 0, 0, 0.04)));
      --_muted: var(--agui-muted-color, var(--lumo-secondary-text-color, #6b7280));
      --_border: var(--agui-border-color, var(--lumo-contrast-10pct, rgba(0, 0, 0, 0.1)));
      --_code-bg: var(--agui-code-background, var(--lumo-contrast-10pct, rgba(0, 0, 0, 0.08)));
      --_error: var(--agui-error-color, var(--lumo-error-text-color, #b91c1c));
      --_primary: var(--agui-primary-color, var(--lumo-primary-color, #2563eb));
    }
    :host([hidden]) {
      display: none;
    }
    :host([theme~='compact']) {
      --_gap: var(--lumo-space-s, 0.5rem);
      font-size: var(--lumo-font-size-s, 0.875rem);
    }
    :host([theme~='flat']) {
      --_user-bg: transparent;
      --_assistant-bg: transparent;
    }
    .messages {
      flex: 1 1 auto;
      min-height: 0;
      overflow-y: auto;
      padding: var(--_gap);
      display: flex;
      flex-direction: column;
      gap: var(--_gap);
      scroll-behavior: smooth;
    }
    .message {
      display: flex;
      flex-direction: column;
      gap: 0.25em;
      max-width: min(100%, 48rem);
    }
    .message[data-role='user'] {
      align-self: flex-end;
      align-items: flex-end;
    }
    .message[data-role='assistant'] {
      align-self: flex-start;
    }
    .author {
      font-size: var(--lumo-font-size-xs, 0.75rem);
      color: var(--_muted);
      padding: 0 0.25em;
    }
    .bubble {
      padding: 0.6em 0.9em;
      border-radius: var(--_radius);
      background: var(--_assistant-bg);
      overflow-wrap: anywhere;
    }
    .message[data-role='user'] .bubble {
      background: var(--_user-bg);
      white-space: pre-wrap;
    }
    :host([theme~='flat']) .bubble {
      padding: 0.25em 0;
    }
    .bubble :is(p, ul, ol, pre, blockquote, table) {
      margin: 0.4em 0;
    }
    .bubble :is(p, ul, ol, pre, blockquote, table):first-child {
      margin-top: 0;
    }
    .bubble :is(p, ul, ol, pre, blockquote, table):last-child {
      margin-bottom: 0;
    }
    .bubble pre {
      background: var(--_code-bg);
      border-radius: calc(var(--_radius) / 2);
      padding: 0.6em 0.8em;
      overflow-x: auto;
      font-size: 0.9em;
    }
    .bubble code {
      font-family: var(--lumo-font-family-mono, ui-monospace, monospace);
      font-size: 0.9em;
    }
    .bubble :not(pre) > code {
      background: var(--_code-bg);
      border-radius: 0.25em;
      padding: 0.1em 0.3em;
    }
    .bubble table {
      border-collapse: collapse;
    }
    .bubble :is(th, td) {
      border: 1px solid var(--_border);
      padding: 0.25em 0.5em;
    }
    .bubble a {
      color: var(--_primary);
    }
    .streaming::after {
      content: '▍';
      color: var(--_muted);
      animation: agui-blink 1s steps(2) infinite;
    }
    @keyframes agui-blink {
      to {
        visibility: hidden;
      }
    }
    details.thinking,
    details.tool {
      border: 1px solid var(--_border);
      border-radius: calc(var(--_radius) / 2);
      padding: 0.4em 0.7em;
      font-size: 0.9em;
      background: transparent;
    }
    details summary {
      cursor: pointer;
      color: var(--_muted);
      display: flex;
      gap: 0.5em;
      align-items: center;
    }
    details.thinking .body {
      white-space: pre-wrap;
      color: var(--_muted);
      margin-top: 0.4em;
    }
    .tool .name {
      font-family: var(--lumo-font-family-mono, ui-monospace, monospace);
      color: var(--lumo-body-text-color, inherit);
    }
    .tool .status {
      font-size: var(--lumo-font-size-xs, 0.75rem);
      padding: 0 0.5em;
      border-radius: 1em;
      border: 1px solid var(--_border);
    }
    .tool .status[data-status='done'] {
      border-color: var(--lumo-success-color, #16a34a);
      color: var(--lumo-success-text-color, #15803d);
    }
    .tool pre {
      background: var(--_code-bg);
      border-radius: calc(var(--_radius) / 2);
      padding: 0.5em 0.7em;
      overflow-x: auto;
      margin: 0.4em 0 0;
      font-size: 0.85em;
    }
    .tool .label {
      color: var(--_muted);
      font-size: var(--lumo-font-size-xs, 0.75rem);
      margin-top: 0.5em;
    }
    .actions {
      display: flex;
      gap: 0.25em;
      opacity: 0;
      transition: opacity 120ms;
    }
    .message:hover .actions,
    .message:focus-within .actions {
      opacity: 1;
    }
    .actions button,
    .chip,
    .send,
    .stop {
      font: inherit;
      cursor: pointer;
      border-radius: var(--lumo-border-radius-m, 0.5em);
      border: 1px solid var(--_border);
      background: transparent;
      color: inherit;
    }
    .actions button {
      font-size: var(--lumo-font-size-xs, 0.75rem);
      padding: 0.1em 0.5em;
      color: var(--_muted);
    }
    .actions button:hover {
      color: inherit;
    }
    .error {
      color: var(--_error);
      border: 1px solid var(--_error);
      border-radius: var(--_radius);
      padding: 0.5em 0.9em;
      align-self: stretch;
    }
    .suggestions {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5em;
      justify-content: center;
      margin: auto 0;
    }
    .chip {
      padding: 0.4em 0.9em;
    }
    .chip:hover {
      background: var(--_assistant-bg);
    }
    .composer {
      flex: 0 0 auto;
      display: flex;
      gap: 0.5em;
      align-items: flex-end;
      padding: var(--_gap);
      border-top: 1px solid var(--_border);
    }
    textarea {
      flex: 1 1 auto;
      resize: none;
      font: inherit;
      color: inherit;
      background: var(--lumo-base-color, #fff);
      border: 1px solid var(--_border);
      border-radius: var(--lumo-border-radius-m, 0.5em);
      padding: 0.5em 0.75em;
      min-height: 2.5em;
      max-height: 12em;
      line-height: 1.4;
      box-sizing: border-box;
    }
    textarea:focus {
      outline: 2px solid var(--_primary);
      outline-offset: -1px;
    }
    .send {
      background: var(--_primary);
      color: var(--lumo-primary-contrast-color, #fff);
      border-color: transparent;
      padding: 0.55em 1em;
    }
    .send:disabled {
      opacity: 0.5;
      cursor: default;
    }
    .stop {
      padding: 0.55em 1em;
    }
    .sr-only {
      position: absolute;
      width: 1px;
      height: 1px;
      overflow: hidden;
      clip: rect(0 0 0 0);
    }
  `;

  // ---- 服务端同步的属性 -------------------------------------------------

  @property({ type: String, attribute: 'agent-url' }) agentUrl = '';
  @property({ type: String, attribute: 'thread-id' }) threadId = '';
  @property({ type: Object }) headers: Record<string, string> = {};
  /** 初始历史。服务端设置时整个对话状态被重置，运行中不会被覆盖。 */
  @property({ type: Array }) messages: Message[] = [];
  @property({ type: Array }) suggestions: string[] = [];
  @property({ type: Array }) tools: ToolDefinition[] = [];
  @property({ type: Array }) context: ContextItem[] = [];
  @property({ type: Object }) forwardedProps: unknown = null;
  @property({ type: Object }) agentState: unknown = {};
  @property({ type: Object }) i18n: I18n = DEFAULT_I18N;
  @property({ type: Boolean, reflect: true }) running = false;
  @property({ type: Boolean, reflect: true }) disabled = false;

  // ---- 内部状态 -----------------------------------------------------------

  /** 渲染用快照；_latest 才是权威状态，二者通过 rAF 合并同步以限制 Markdown 重渲染频率 */
  @state() private _chat: ChatState = initialState();
  @state() private _draft = '';
  @state() private _copiedId: string | null = null;

  @query('.messages') private _list!: HTMLElement;
  @query('textarea') private _textarea!: HTMLTextAreaElement;

  private _latest: ChatState = initialState();
  private _raf = 0;
  private _abort: AbortController | null = null;
  private _stickToBottom = true;
  private _pendingFrontendTools = new Map<string, ToolCall>();

  // ---- 公开方法（服务端通过 callJsFunction 调用） ---------------------------

  /** 以用户身份发送并运行。 */
  send(text: string): void {
    const trimmed = (text ?? '').trim();
    if (!trimmed || this.running || this.disabled) {
      return;
    }
    const user: UiMessage = { id: crypto.randomUUID(), role: 'user', content: trimmed, complete: true };
    this._apply({ ...this._latest, messages: [...this._latest.messages, user], error: null });
    this._fire('agui-message-sent', { text: trimmed });
    void this._run();
  }

  stop(): void {
    this._abort?.abort();
  }

  /** 丢弃最后一条 user 消息之后的一切并重跑。 */
  regenerate(): void {
    if (this.running) {
      return;
    }
    const keep = indexAfterLastUser(this._latest.messages);
    if (keep === 0) {
      return;
    }
    this._pendingFrontendTools.clear();
    this._apply({ ...this._latest, messages: this._latest.messages.slice(0, keep), error: null });
    void this._run();
  }

  clear(): void {
    this.stop();
    this._pendingFrontendTools.clear();
    this._apply(initialState([], this.agentState));
  }

  /** 回传前端工具结果；全部待处理工具都有结果后自动续跑。 */
  submitToolResult(toolCallId: string, content: string): void {
    if (!this._pendingFrontendTools.has(toolCallId)) {
      return;
    }
    this._pendingFrontendTools.delete(toolCallId);
    this._apply(
      reduce(this._latest, {
        type: 'TOOL_CALL_RESULT',
        messageId: crypto.randomUUID(),
        toolCallId,
        content: content ?? '',
      }),
    );
    if (this._pendingFrontendTools.size === 0) {
      void this._run();
    }
  }

  // ---- 生命周期 -----------------------------------------------------------

  protected override willUpdate(changed: PropertyValues<this>): void {
    if (changed.has('messages') && !this.running) {
      this._apply(initialState(this.messages ?? [], this.agentState));
    } else if (changed.has('agentState') && !this.running) {
      this._apply({ ...this._latest, agentState: this.agentState });
    }
    if (changed.has('i18n') && !this.i18n) {
      this.i18n = DEFAULT_I18N;
    }
  }

  protected override updated(): void {
    if (this._stickToBottom && this._list) {
      this._list.scrollTop = this._list.scrollHeight;
    }
  }

  override disconnectedCallback(): void {
    super.disconnectedCallback();
    this.stop();
    cancelAnimationFrame(this._raf);
  }

  // ---- 运行 ---------------------------------------------------------------

  private async _run(): Promise<void> {
    if (this.running || !this.agentUrl) {
      return;
    }
    const threadId = this.threadId || crypto.randomUUID();
    const runId = crypto.randomUUID();
    const input: RunAgentInput = {
      threadId,
      runId,
      state: this._latest.agentState ?? {},
      messages: toWireMessages(this._latest.messages),
      tools: this.tools ?? [],
      context: this.context ?? [],
      forwardedProps: this.forwardedProps ?? {},
    };

    this._abort = new AbortController();
    this.running = true;
    this._stickToBottom = true;
    this._apply({ ...this._latest, running: true, runId, error: null });
    this._fire('agui-run-started', { threadId, runId });

    let cancelled = false;
    try {
      await runAgent({
        url: this.agentUrl,
        input,
        headers: this.headers,
        signal: this._abort.signal,
        onEvent: (event) => this._onEvent(event),
      });
    } catch (err) {
      if ((err as Error).name === 'AbortError') {
        cancelled = true;
      } else {
        const message = (err as Error).message ?? String(err);
        this._apply(reduce(this._latest, { type: 'RUN_ERROR', message }));
        this._fire('agui-run-error', { message, code: null });
      }
    } finally {
      this._abort = null;
      this._apply({ ...this._latest, running: false, step: null });
      this.running = false;
      this._flush();
      this._fire('agui-run-finished', {
        threadId,
        runId,
        cancelled,
        messages: toWireMessages(this._latest.messages),
      });
      // 前端工具在运行结束后才通知服务端，保证参数已经完整
      for (const call of this._pendingFrontendTools.values()) {
        this._fire('agui-tool-call', {
          toolCallId: call.id,
          name: call.function.name,
          arguments: call.function.arguments || '{}',
        });
      }
    }
  }

  private _onEvent(event: AgUiEvent): void {
    this._apply(reduce(this._latest, event));
    switch (event.type) {
      case 'RUN_ERROR':
        this._fire('agui-run-error', { message: event.message, code: event.code ?? null });
        break;
      case 'TOOL_CALL_END': {
        const call = this._findToolCall(event.toolCallId);
        if (call && this.tools?.some((t) => t.name === call.function.name)) {
          this._pendingFrontendTools.set(call.id, call);
        }
        break;
      }
      case 'STATE_SNAPSHOT':
      case 'STATE_DELTA':
        this._fire('agui-state-changed', { state: this._latest.agentState });
        break;
      case 'CUSTOM':
        this._fire('agui-custom', { name: event.name, value: event.value });
        break;
      default:
        break;
    }
  }

  private _findToolCall(toolCallId: string): ToolCall | undefined {
    for (let i = this._latest.messages.length - 1; i >= 0; i -= 1) {
      const found = this._latest.messages[i].toolCalls?.find((c) => c.id === toolCallId);
      if (found) {
        return found;
      }
    }
    return undefined;
  }

  /** 更新权威状态，并把渲染合并到下一帧。 */
  private _apply(next: ChatState): void {
    this._latest = next;
    if (!this._raf) {
      this._raf = requestAnimationFrame(() => {
        this._raf = 0;
        this._chat = this._latest;
      });
    }
  }

  private _flush(): void {
    cancelAnimationFrame(this._raf);
    this._raf = 0;
    this._chat = this._latest;
  }

  private _fire(type: string, detail: unknown): void {
    this.dispatchEvent(new CustomEvent(type, { detail, bubbles: true, composed: true }));
  }

  // ---- 交互 ---------------------------------------------------------------

  private _onScroll(): void {
    const el = this._list;
    this._stickToBottom = el.scrollHeight - el.scrollTop - el.clientHeight < STICK_THRESHOLD_PX;
  }

  private _onInput(e: Event): void {
    const ta = e.target as HTMLTextAreaElement;
    this._draft = ta.value;
    ta.style.height = 'auto';
    ta.style.height = `${ta.scrollHeight}px`;
  }

  private _onKeydown(e: KeyboardEvent): void {
    if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
      e.preventDefault();
      this._submit();
    }
  }

  private _submit(): void {
    const text = this._draft;
    if (!text.trim()) {
      return;
    }
    this._draft = '';
    if (this._textarea) {
      this._textarea.value = '';
      this._textarea.style.height = 'auto';
    }
    this.send(text);
  }

  private async _copy(message: UiMessage): Promise<void> {
    try {
      await navigator.clipboard.writeText(message.content ?? '');
      this._copiedId = message.id;
      setTimeout(() => {
        if (this._copiedId === message.id) {
          this._copiedId = null;
        }
      }, 1500);
    } catch {
      // 剪贴板在非安全上下文不可用；静默失败，按钮无反馈即可
    }
  }

  private _feedback(message: UiMessage, positive: boolean): void {
    this._fire('agui-feedback', { messageId: message.id, positive });
  }

  // ---- 渲染 ---------------------------------------------------------------

  protected override render() {
    const t = this.i18n ?? DEFAULT_I18N;
    const chat = this._chat;
    const visible = chat.messages.filter((m) => m.role === 'user' || m.role === 'assistant');
    const lastAssistantId = [...visible].reverse().find((m) => m.role === 'assistant')?.id;

    return html`
      <div class="messages" part="messages" @scroll=${this._onScroll} aria-live="polite" aria-relevant="additions text">
        ${visible.length === 0 && this.suggestions?.length && !this.disabled
          ? html`<div class="suggestions" part="suggestions">
              ${this.suggestions.map(
                (s) => html`<button class="chip" type="button" @click=${() => this.send(s)}>${s}</button>`,
              )}
            </div>`
          : nothing}
        ${repeat(
          visible,
          (m) => m.id,
          (m) => this._renderMessage(m, t, chat, m.id === lastAssistantId),
        )}
        ${chat.error
          ? html`<div class="error" role="alert" part="error">${t.errorPrefix}: ${chat.error.message}</div>`
          : nothing}
      </div>
      <div class="composer" part="composer">
        <label class="sr-only" for="agui-input">${t.placeholder}</label>
        <textarea
          id="agui-input"
          rows="1"
          placeholder=${t.placeholder}
          .value=${this._draft}
          ?disabled=${this.disabled}
          @input=${this._onInput}
          @keydown=${this._onKeydown}
        ></textarea>
        ${this.running
          ? html`<button class="stop" type="button" @click=${this.stop}>${t.stop}</button>`
          : html`<button
              class="send"
              type="button"
              ?disabled=${this.disabled || !this._draft.trim()}
              @click=${this._submit}
            >
              ${t.send}
            </button>`}
      </div>
    `;
  }

  private _renderMessage(m: UiMessage, t: I18n, chat: ChatState, isLastAssistant: boolean) {
    const streaming = chat.running && m.role === 'assistant' && !m.complete;
    const author = m.role === 'user' ? t.userName : t.assistantName;
    return html`
      <div class="message" data-role=${m.role} part="message">
        <span class="author">${author}</span>
        ${m.thinking
          ? html`<details class="thinking" part="thinking" ?open=${streaming && !m.content}>
              <summary>${t.thinking}</summary>
              <div class="body">${m.thinking}</div>
            </details>`
          : nothing}
        ${m.toolCalls?.map((c) => this._renderToolCall(c, t, chat))}
        ${m.role === 'user'
          ? html`<div class="bubble" part="bubble">${m.content}</div>`
          : m.content || streaming
            ? html`<div class="bubble ${streaming ? 'streaming' : ''}" part="bubble">
                ${unsafeHTML(renderMarkdown(m.content))}
              </div>`
            : nothing}
        ${m.role === 'assistant' && m.complete && m.content
          ? html`<div class="actions" part="actions">
              <button type="button" @click=${() => this._copy(m)}>
                ${this._copiedId === m.id ? t.copied : t.copy}
              </button>
              <button type="button" title=${t.helpfulYes} @click=${() => this._feedback(m, true)}>👍</button>
              <button type="button" title=${t.helpfulNo} @click=${() => this._feedback(m, false)}>👎</button>
              ${isLastAssistant && !chat.running
                ? html`<button type="button" @click=${this.regenerate}>${t.regenerate}</button>`
                : nothing}
            </div>`
          : nothing}
      </div>
    `;
  }

  private _renderToolCall(c: ToolCall, t: I18n, chat: ChatState) {
    const status = chat.toolStatus[c.id] ?? 'streaming';
    const result = findToolResult(chat.messages, c.id);
    return html`
      <details class="tool" part="tool-call" data-status=${status}>
        <summary>
          <span>${t.toolCall}</span>
          <span class="name">${c.function.name}</span>
          <span class="status" data-status=${status}>${status}</span>
        </summary>
        <pre>${prettyJson(c.function.arguments)}</pre>
        ${result
          ? html`<div class="label">${t.toolResult}</div>
              <pre>${prettyJson(result.content)}</pre>`
          : nothing}
      </details>
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'agui-chat': AgUiChatElement;
  }
}
