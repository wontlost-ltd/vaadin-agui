# Changelog

## Unreleased

- Vaadin Platform 25.2.8 → 25.3.0.

## 0.1.0 (2026-09-17)

- `AgUiChat` — Flow component wrapping `<agui-chat>`: agent URL, thread id, initial messages,
  suggestions, per-run context, frontend tool declarations, forwarded props, shared state, i18n,
  `compact` / `flat` variants. Server-driven `prompt`, `stop`, `regenerate`, `clear`,
  `submitToolResult`. Events: run started / finished / error, tool call, state changed, message sent,
  feedback.
- `<agui-chat>` — Lit component: POST + SSE client with abort, AG-UI event reducer (text, thinking,
  tool calls, results, state snapshot and JSON Patch delta, messages snapshot), client-side
  Markdown via marked + DOMPurify, tool-call cards, thinking blocks, sticky scrolling, copy and
  feedback actions, frontend tool round-trips.
- `StateChangedEvent` also fires when the conversation is cleared or restored, so state listeners follow the conversation.
- `AgUiEvent` — sealed Jackson 3 model of the 22 AG-UI event types, names verbatim from the spec;
  `Message`, `ToolCall`, `ToolDefinition`, `ContextItem`, `RunAgentInput`.
- `AgUiAgent` / `AgUiEmitter` — the framework-agnostic SPI.
- Spring (optional): `AgUiController` at `POST ${agui.path}/{beanName}` returning SSE, agents run
  on the application task executor, Boot 4 auto-configuration, `agui.path` / `agui.timeout`.
- Spring AI (optional): `SpringAiAgent` emits a `CUSTOM usage` event (`promptTokens`, `completionTokens`,
  `model`) before `RUN_FINISHED` whenever the model reports usage, so metering can bill exact numbers. Wraps backend `ToolCallback`s per run so the model's
  internal tool loop still emits `TOOL_CALL_*` events; registers frontend tools as `returnDirect`
  callbacks so the turn ends when one is called. Spring AI 2.0 removed
  `internalToolExecutionEnabled`, so this is the only way to keep visibility without reimplementing
  the loop.
- Example "Atlas Order Desk" (kept in the private agui-examples repository, live at https://agui.wontlost.com): six switchable agents on mock data — Spring AI
  order desk with `@Tool` methods, human-in-the-loop approvals with frontend tools and a Flow
  `ConfirmDialog`, analyst driving KPI cards and a plan through `STATE_SNAPSHOT`/`STATE_DELTA`,
  long-Markdown writer, warehouse agent over a mock API with on-demand `RUN_ERROR`, and an external runtime that writes SSE
  frames by hand. Conversation sidebar with `setMessages` restore, parallel lane, English/中文,
  `compact`/`flat` variants, server-side event log, reset button. Scripted `ChatModel` replays
  Spring AI's internal tool loop so no API key is needed.
- E2E: 24 Playwright cases × Chromium + Firefox covering streaming, tool calls (backend and
  frontend), shared state, lifecycle, conversations, protocol compatibility and error recovery.
- Component design pass: assistant messages use a left rule instead of a grey bubble, user messages
  are compact pills, tool calls are instrument strips with an LED status dot and collapsed
  arguments/result, thinking blocks shimmer while streaming, the composer is a rounded field with
  an icon send button and a pulsing stop button, an amber streaming caret, refined Markdown
  typography (tables, code, quotes). All colours go through `--agui-*` custom properties with Lumo
  fallbacks so Aura works unchanged. The error banner offers an inline retry (`regenerate()`).
- Example favicon: amber mark (peak + LED dot) as SVG, PNG-in-ICO (16/32/48), apple-touch-icon and a
  512px `icons/icon.png`, linked from `AppShellSettings`.
- Message actions are icon buttons (copy → check while copied, thumbs up/down, regenerate); the i18n
  text moves to `title` / `aria-label`, and each button carries `data-action` for tests and styling.
- Example redesigned as a dark "control room": Lumo dark with an amber/green signal palette,
  IBM Plex Sans/Mono, LED run indicator, instrument-style KPI tiles, plan checklist, status dots in
  the grid, terminal-style event log, staggered column reveal. All styling lives in `atlas.css`.
- Example light/dark: a sun/moon toggle in the header (`#theme`) switches Lumo's `theme="dark"` on
  the UI element; the default follows `prefers-color-scheme` and the choice is remembered in a
  cookie. `atlas.css` carries a paper "daylight" palette on `html` and the night palette under
  `[theme~='dark']`; rules only reference tokens, so the component follows through its `--agui-*`
  fallbacks. Two E2E cases (system default, toggle + reload persistence) in both browsers.
