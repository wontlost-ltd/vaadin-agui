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

/* 消息操作图标：16 格线稿，描边随 currentColor；文字放在 title / aria-label 里 */
const ICON_COPY = html`<svg viewBox="0 0 16 16" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><rect x="5.5" y="5.5" width="8" height="8" rx="1.5" /><path d="M10.5 5.5v-2a1 1 0 0 0-1-1h-6a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h2" /></svg>`;
const ICON_CHECK = html`<svg viewBox="0 0 16 16" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M3 8.5l3 3 7-7" /></svg>`;
const ICON_REFRESH = html`<svg viewBox="0 0 16 16" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M13 8a5 5 0 1 1-1.5-3.6" /><path d="M13 2.5v3h-3" /></svg>`;
const ICON_THUMB_UP = html`<svg viewBox="0 0 16 16" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M5 7v6H2.5V7z" /><path d="M5 7.5l3-5c1.2 0 1.8.8 1.6 2L9.2 6.5h3.3c.9 0 1.5.8 1.3 1.6l-1 4.2c-.2.7-.8 1.2-1.5 1.2H5" /></svg>`;
const ICON_THUMB_DOWN = html`<svg viewBox="0 0 16 16" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M11 9V3h2.5v6z" /><path d="M11 8.5l-3 5c-1.2 0-1.8-.8-1.6-2l.4-2H3.5c-.9 0-1.5-.8-1.3-1.6l1-4.2C3.4 3 4 2.5 4.7 2.5H11" /></svg>`;

@customElement('agui-chat')
export class AgUiChatElement extends LitElement {
  static override styles = css`
    /*
     * 设计基调：克制的仪表盘。助手不用灰气泡而用左侧细线，用户消息是紧凑胶囊；
     * 工具调用是带 LED 的仪表条；所有颜色经 --agui-* 暴露，回退到 Lumo 变量，
     * Aura 下同样能落到合理值。
     */
    :host {
      display: flex;
      flex-direction: column;
      box-sizing: border-box;
      min-height: 0;
      height: 100%;
      font-family: var(--agui-font-family, var(--lumo-font-family, system-ui, sans-serif));
      font-size: var(--agui-font-size, var(--lumo-font-size-m, 1rem));
      line-height: 1.55;
      color: var(--agui-text-color, var(--lumo-body-text-color, #1f2933));

      --_gap: var(--agui-gap, var(--lumo-space-m, 1rem));
      --_radius: var(--agui-bubble-radius, 14px);
      --_mono: var(--agui-font-family-mono, var(--lumo-font-family-mono, ui-monospace, "SF Mono", Menlo, monospace));
      --_accent: var(--agui-accent-color, var(--lumo-primary-color, #2563eb));
      --_accent-soft: var(--agui-accent-soft, var(--lumo-primary-color-10pct, rgba(37, 99, 235, 0.1)));
      --_muted: var(--agui-muted-color, var(--lumo-secondary-text-color, #6b7280));
      --_faint: var(--agui-faint-color, var(--lumo-tertiary-text-color, #9aa3ad));
      --_hairline: var(--agui-hairline, var(--lumo-contrast-10pct, rgba(0, 0, 0, 0.1)));
      --_surface: var(--agui-surface, var(--lumo-base-color, #fff));
      --_surface-2: var(--agui-surface-2, var(--lumo-contrast-5pct, rgba(0, 0, 0, 0.04)));
      --_code-bg: var(--agui-code-background, var(--lumo-contrast-5pct, rgba(0, 0, 0, 0.05)));
      --_user-bg: var(--agui-user-bubble-background, var(--_accent-soft));
      --_assistant-rule: var(--agui-assistant-rule, var(--_accent));
      --_ok: var(--agui-success-color, var(--lumo-success-color, #16a34a));
      --_warn: var(--agui-pending-color, #e0a626);
      --_error: var(--agui-error-color, var(--lumo-error-text-color, #dc2626));
      --_focus: var(--agui-focus-ring, var(--_accent));
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
      --_assistant-rule: transparent;
    }
    *,
    *::before,
    *::after {
      box-sizing: border-box;
    }
    button {
      font: inherit;
      color: inherit;
      background: none;
      border: 0;
      padding: 0;
      cursor: pointer;
    }
    button:focus-visible,
    textarea:focus-visible,
    summary:focus-visible {
      outline: 2px solid var(--_focus);
      outline-offset: 2px;
    }

    /* ---- 消息流 ------------------------------------------------------ */
    .messages {
      flex: 1 1 auto;
      min-height: 0;
      overflow-y: auto;
      padding: var(--_gap) calc(var(--_gap) * 1.25);
      display: flex;
      flex-direction: column;
      gap: calc(var(--_gap) * 1.1);
      scroll-behavior: smooth;
      overscroll-behavior: contain;
    }
    .message {
      display: flex;
      flex-direction: column;
      gap: 0.35em;
      max-width: min(100%, 52rem);
      animation: agui-rise 220ms cubic-bezier(0.2, 0.7, 0.2, 1) both;
    }
    @keyframes agui-rise {
      from {
        opacity: 0;
        transform: translateY(4px);
      }
      to {
        opacity: 1;
        transform: none;
      }
    }
    @media (prefers-reduced-motion: reduce) {
      .message,
      .streaming::after,
      .led[data-status='streaming'],
      details.thinking[open] .body::after {
        animation: none;
      }
    }
    .message[data-role='user'] {
      align-self: flex-end;
      align-items: flex-end;
      max-width: min(85%, 40rem);
    }
    .author {
      font-family: var(--_mono);
      font-size: 0.68rem;
      letter-spacing: 0.08em;
      text-transform: uppercase;
      color: var(--_faint);
      padding: 0 0.15em;
    }
    .bubble {
      position: relative;
      padding: 0.15em 0 0.15em 1em;
      border-left: 2px solid var(--_assistant-rule);
      overflow-wrap: anywhere;
    }
    .message[data-role='user'] .bubble {
      border-left: 0;
      background: var(--_user-bg);
      border-radius: var(--_radius) var(--_radius) 4px var(--_radius);
      padding: 0.55em 0.95em;
      white-space: pre-wrap;
    }
    :host([theme~='flat']) .message[data-role='user'] .bubble {
      padding: 0.15em 0;
    }

    /* Markdown 排版 */
    .bubble :is(p, ul, ol, pre, blockquote, table, h1, h2, h3, h4) {
      margin: 0.45em 0;
    }
    .bubble > :first-child {
      margin-top: 0;
    }
    .bubble > :last-child {
      margin-bottom: 0;
    }
    .bubble :is(h1, h2, h3, h4) {
      line-height: 1.25;
      letter-spacing: -0.01em;
    }
    .bubble h1 {
      font-size: 1.35em;
    }
    .bubble h2 {
      font-size: 1.2em;
    }
    .bubble h3 {
      font-size: 1.05em;
    }
    .bubble ul,
    .bubble ol {
      padding-left: 1.4em;
    }
    .bubble li + li {
      margin-top: 0.2em;
    }
    .bubble blockquote {
      margin-left: 0;
      padding: 0.2em 0 0.2em 0.9em;
      border-left: 2px solid var(--_hairline);
      color: var(--_muted);
    }
    .bubble pre {
      background: var(--_code-bg);
      border: 1px solid var(--_hairline);
      border-radius: 8px;
      padding: 0.7em 0.9em;
      overflow-x: auto;
      font-size: 0.86em;
      line-height: 1.5;
    }
    .bubble code {
      font-family: var(--_mono);
      font-size: 0.88em;
    }
    .bubble :not(pre) > code {
      background: var(--_code-bg);
      border: 1px solid var(--_hairline);
      border-radius: 4px;
      padding: 0.05em 0.35em;
    }
    .bubble table {
      border-collapse: collapse;
      font-size: 0.92em;
      font-variant-numeric: tabular-nums;
    }
    .bubble th {
      font-family: var(--_mono);
      font-size: 0.7em;
      letter-spacing: 0.06em;
      text-transform: uppercase;
      color: var(--_muted);
      text-align: left;
      font-weight: 600;
    }
    .bubble :is(th, td) {
      border-bottom: 1px solid var(--_hairline);
      padding: 0.35em 0.7em 0.35em 0;
    }
    .bubble tr:last-child td {
      border-bottom: 0;
    }
    .bubble a {
      color: var(--_accent);
      text-decoration: none;
      border-bottom: 1px solid color-mix(in srgb, var(--_accent) 40%, transparent);
    }
    .bubble a:hover {
      border-bottom-color: var(--_accent);
    }
    .bubble hr {
      border: 0;
      border-top: 1px solid var(--_hairline);
    }

    /* 流式光标：琥珀色块 */
    .streaming > :last-child::after,
    .streaming:empty::after {
      content: '';
      display: inline-block;
      width: 0.5em;
      height: 1em;
      margin-left: 0.15em;
      vertical-align: -0.15em;
      background: var(--_warn);
      border-radius: 1px;
      animation: agui-blink 900ms steps(2) infinite;
    }
    @keyframes agui-blink {
      to {
        visibility: hidden;
      }
    }

    /* ---- 思考块与工具条 ---------------------------------------------- */
    details.thinking,
    details.tool {
      border: 1px solid var(--_hairline);
      border-radius: 10px;
      background: var(--_surface-2);
      font-size: 0.86em;
      overflow: hidden;
    }
    details summary {
      list-style: none;
      cursor: pointer;
      display: flex;
      gap: 0.6em;
      align-items: center;
      padding: 0.45em 0.8em;
      color: var(--_muted);
      user-select: none;
    }
    details summary::-webkit-details-marker {
      display: none;
    }
    details summary::after {
      content: '';
      margin-left: auto;
      width: 0.45em;
      height: 0.45em;
      border-right: 1.5px solid var(--_faint);
      border-bottom: 1.5px solid var(--_faint);
      transform: rotate(-45deg);
      transition: transform 160ms;
    }
    details[open] summary::after {
      transform: rotate(45deg);
    }
    details.thinking .body {
      white-space: pre-wrap;
      color: var(--_muted);
      font-style: italic;
      padding: 0 0.9em 0.7em;
      position: relative;
    }
    details.thinking[open] .body::after {
      content: '';
      position: absolute;
      inset: 0;
      pointer-events: none;
      background: linear-gradient(100deg, transparent 20%, color-mix(in srgb, var(--_surface) 50%, transparent) 50%, transparent 80%);
      background-size: 250% 100%;
      animation: agui-shimmer 1.8s linear infinite;
      opacity: 0;
    }
    .message:not(.complete) details.thinking[open] .body::after {
      opacity: 1;
    }
    @keyframes agui-shimmer {
      from {
        background-position: 150% 0;
      }
      to {
        background-position: -50% 0;
      }
    }
    .led {
      flex: 0 0 auto;
      width: 0.55em;
      height: 0.55em;
      border-radius: 50%;
      background: var(--_warn);
      box-shadow: 0 0 0 3px color-mix(in srgb, var(--_warn) 22%, transparent);
    }
    .led[data-status='streaming'] {
      animation: agui-pulse 1.1s ease-in-out infinite;
    }
    .led[data-status='done'] {
      background: var(--_ok);
      box-shadow: 0 0 0 3px color-mix(in srgb, var(--_ok) 22%, transparent);
    }
    @keyframes agui-pulse {
      50% {
        box-shadow: 0 0 0 6px color-mix(in srgb, var(--_warn) 8%, transparent);
      }
    }
    .tool .kind {
      font-family: var(--_mono);
      font-size: 0.72em;
      letter-spacing: 0.08em;
      text-transform: uppercase;
    }
    .tool .name {
      font-family: var(--_mono);
      color: var(--agui-text-color, var(--lumo-body-text-color, inherit));
      font-weight: 600;
    }
    .tool .status {
      font-family: var(--_mono);
      font-size: 0.72em;
      letter-spacing: 0.06em;
      color: var(--_faint);
    }
    .tool .status[data-status='done'] {
      color: var(--_ok);
    }
    .tool .body {
      padding: 0 0.8em 0.7em;
      display: grid;
      gap: 0.45em;
    }
    .tool .label {
      font-family: var(--_mono);
      font-size: 0.68em;
      letter-spacing: 0.08em;
      text-transform: uppercase;
      color: var(--_faint);
    }
    .tool pre {
      margin: 0;
      background: var(--_code-bg);
      border: 1px solid var(--_hairline);
      border-radius: 6px;
      padding: 0.5em 0.7em;
      overflow-x: auto;
      font-family: var(--_mono);
      font-size: 0.88em;
      line-height: 1.45;
      white-space: pre-wrap;
      overflow-wrap: anywhere;
    }

    /* ---- 操作与错误 ---------------------------------------------------- */
    .actions {
      display: flex;
      gap: 0.15em;
      margin-left: 1em;
      opacity: 0;
      transform: translateY(-2px);
      transition: opacity 140ms, transform 140ms;
    }
    .message:hover .actions,
    .message:focus-within .actions {
      opacity: 1;
      transform: none;
    }
    .actions button {
      display: inline-grid;
      place-items: center;
      width: 1.9em;
      height: 1.9em;
      padding: 0;
      border-radius: 6px;
      color: var(--_muted);
    }
    .actions button svg {
      width: 1em;
      height: 1em;
      display: block;
    }
    .actions button:hover {
      background: var(--_surface-2);
      color: inherit;
    }
    .actions button[data-state='copied'] {
      color: var(--_accent);
    }
    .error {
      align-self: stretch;
      display: flex;
      gap: 0.6em;
      align-items: flex-start;
      padding: 0.6em 0.9em;
      border-radius: 10px;
      border: 1px solid color-mix(in srgb, var(--_error) 35%, transparent);
      background: color-mix(in srgb, var(--_error) 7%, transparent);
      color: var(--_error);
      font-size: 0.92em;
    }
    .error::before {
      content: '';
      flex: 0 0 auto;
      width: 0.55em;
      height: 0.55em;
      margin-top: 0.5em;
      border-radius: 50%;
      background: var(--_error);
      box-shadow: 0 0 0 3px color-mix(in srgb, var(--_error) 22%, transparent);
    }
    .error .error-text {
      flex: 1 1 auto;
      padding-top: 0.1em;
    }
    .error .retry {
      flex: 0 0 auto;
      font-size: 0.82em;
      padding: 0.25em 0.7em;
      border-radius: 6px;
      border: 1px solid color-mix(in srgb, var(--_error) 45%, transparent);
      color: var(--_error);
    }
    .error .retry:hover {
      background: color-mix(in srgb, var(--_error) 12%, transparent);
    }

    /* ---- 开场建议 ------------------------------------------------------ */
    .suggestions {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5em;
      justify-content: center;
      align-content: center;
      margin: auto 0;
      padding: 2em 0;
    }
    .chip {
      padding: 0.5em 0.95em;
      border: 1px solid var(--_hairline);
      border-radius: 999px;
      color: var(--_muted);
      background: var(--_surface);
      transition: border-color 140ms, color 140ms, transform 140ms;
    }
    .chip:hover {
      border-color: var(--_accent);
      color: inherit;
      transform: translateY(-1px);
    }

    /* ---- 输入区 -------------------------------------------------------- */
    .composer {
      flex: 0 0 auto;
      padding: calc(var(--_gap) * 0.75) calc(var(--_gap) * 1.25) var(--_gap);
      border-top: 1px solid var(--_hairline);
      background: linear-gradient(to bottom, transparent, var(--_surface-2));
    }
    .field {
      display: flex;
      align-items: flex-end;
      gap: 0.5em;
      padding: 0.4em 0.4em 0.4em 0.9em;
      border: 1px solid var(--_hairline);
      border-radius: 16px;
      background: var(--_surface);
      transition: border-color 140ms, box-shadow 140ms;
    }
    .field:focus-within {
      border-color: var(--_accent);
      box-shadow: 0 0 0 3px var(--_accent-soft);
    }
    textarea {
      flex: 1 1 auto;
      resize: none;
      font: inherit;
      line-height: 1.45;
      color: inherit;
      background: transparent;
      border: 0;
      outline: 0;
      padding: 0.35em 0;
      min-height: 1.45em;
      max-height: 12em;
    }
    textarea::placeholder {
      color: var(--_faint);
    }
    .send,
    .stop {
      flex: 0 0 auto;
      width: 2.2em;
      height: 2.2em;
      border-radius: 50%;
      display: grid;
      place-items: center;
      transition: background 140ms, transform 140ms, opacity 140ms;
    }
    .send {
      background: var(--_accent);
      color: var(--agui-accent-contrast, var(--lumo-primary-contrast-color, #fff));
    }
    .send:hover:not(:disabled) {
      transform: translateY(-1px);
    }
    .send:disabled {
      opacity: 0.35;
      cursor: default;
    }
    .send svg,
    .stop svg {
      width: 1em;
      height: 1em;
    }
    .stop {
      background: var(--_surface-2);
      color: var(--_warn);
      box-shadow: 0 0 0 2px color-mix(in srgb, var(--_warn) 45%, transparent);
      animation: agui-ring 1.4s ease-in-out infinite;
    }
    @keyframes agui-ring {
      50% {
        box-shadow: 0 0 0 5px color-mix(in srgb, var(--_warn) 12%, transparent);
      }
    }
    .hint {
      font-family: var(--_mono);
      font-size: 0.66rem;
      letter-spacing: 0.06em;
      color: var(--_faint);
      padding: 0.45em 0.6em 0;
      display: flex;
      justify-content: space-between;
    }
    .hint kbd {
      font: inherit;
      border: 1px solid var(--_hairline);
      border-radius: 3px;
      padding: 0 0.3em;
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
    // 状态跟随对话：清空后监听方（例如状态面板）也应回到初始状态
    this._fire('agui-state-changed', { state: this.agentState ?? {} });
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
      this._fire('agui-state-changed', { state: this.agentState ?? {} });
    } else if (changed.has('agentState') && !this.running) {
      this._apply({ ...this._latest, agentState: this.agentState });
      this._fire('agui-state-changed', { state: this.agentState ?? {} });
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
          ? html`<div class="error" role="alert" part="error">
              <span class="error-text">${t.errorPrefix}: ${chat.error.message}</span>
              ${indexAfterLastUser(chat.messages) > 0 && !chat.running
                ? html`<button type="button" class="retry" @click=${this.regenerate}>${t.regenerate}</button>`
                : nothing}
            </div>`
          : nothing}
      </div>
      <div class="composer" part="composer">
        <label class="sr-only" for="agui-input">${t.placeholder}</label>
        <div class="field">
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
            ? html`<button class="stop" type="button" title=${t.stop} aria-label=${t.stop} @click=${this.stop}>
                <svg viewBox="0 0 16 16" aria-hidden="true"><rect x="3" y="3" width="10" height="10" rx="2" fill="currentColor" /></svg>
              </button>`
            : html`<button
                class="send"
                type="button"
                title=${t.send}
                aria-label=${t.send}
                ?disabled=${this.disabled || !this._draft.trim()}
                @click=${this._submit}
              >
                <svg viewBox="0 0 16 16" aria-hidden="true"><path d="M8 13V3m0 0L3.5 7.5M8 3l4.5 4.5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" /></svg>
              </button>`}
        </div>
        <div class="hint" aria-hidden="true">
          <span>${this.running ? t.thinking + '…' : ''}</span>
          <span><kbd>Enter</kbd> ${t.send} · <kbd>Shift</kbd>+<kbd>Enter</kbd> ↵</span>
        </div>
      </div>
    `;
  }

  private _renderMessage(m: UiMessage, t: I18n, chat: ChatState, isLastAssistant: boolean) {
    const streaming = chat.running && m.role === 'assistant' && !m.complete;
    const author = m.role === 'user' ? t.userName : t.assistantName;
    return html`
      <div class="message ${m.complete ? 'complete' : ''}" data-role=${m.role} part="message">
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
              <button
                type="button"
                data-action="copy"
                data-state=${this._copiedId === m.id ? 'copied' : 'idle'}
                title=${this._copiedId === m.id ? t.copied : t.copy}
                aria-label=${this._copiedId === m.id ? t.copied : t.copy}
                @click=${() => this._copy(m)}
              >
                ${this._copiedId === m.id ? ICON_CHECK : ICON_COPY}
              </button>
              <button type="button" data-action="helpful" title=${t.helpfulYes} aria-label=${t.helpfulYes} @click=${() => this._feedback(m, true)}>
                ${ICON_THUMB_UP}
              </button>
              <button type="button" data-action="not-helpful" title=${t.helpfulNo} aria-label=${t.helpfulNo} @click=${() => this._feedback(m, false)}>
                ${ICON_THUMB_DOWN}
              </button>
              ${isLastAssistant && !chat.running
                ? html`<button type="button" data-action="regenerate" title=${t.regenerate} aria-label=${t.regenerate} @click=${this.regenerate}>
                    ${ICON_REFRESH}
                  </button>`
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
          <span class="led" data-status=${status}></span>
          <span class="kind">${t.toolCall}</span>
          <span class="name">${c.function.name}</span>
          <span class="status" data-status=${status}>${status}</span>
        </summary>
        <div class="body">
          <pre>${prettyJson(c.function.arguments)}</pre>
          ${result
            ? html`<div class="label">${t.toolResult}</div>
                <pre>${prettyJson(result.content)}</pre>`
            : nothing}
        </div>
      </details>
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'agui-chat': AgUiChatElement;
  }
}
