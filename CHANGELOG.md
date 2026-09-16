# Changelog

## 0.1.0 (unreleased)

- `AgUiChat` — Flow component wrapping `<agui-chat>`: agent URL, thread id, initial messages,
  suggestions, per-run context, frontend tool declarations, forwarded props, shared state, i18n,
  `compact` / `flat` variants. Server-driven `prompt`, `stop`, `regenerate`, `clear`,
  `submitToolResult`. Events: run started / finished / error, tool call, state changed, message sent,
  feedback.
- `<agui-chat>` — Lit component: POST + SSE client with abort, AG-UI event reducer (text, thinking,
  tool calls, results, state snapshot and JSON Patch delta, messages snapshot), client-side
  Markdown via marked + DOMPurify, tool-call cards, thinking blocks, sticky scrolling, copy and
  feedback actions, frontend tool round-trips.
- `AgUiEvent` — sealed Jackson 3 model of the 22 AG-UI event types, names verbatim from the spec;
  `Message`, `ToolCall`, `ToolDefinition`, `ContextItem`, `RunAgentInput`.
- `AgUiAgent` / `AgUiEmitter` — the framework-agnostic SPI.
- Spring (optional): `AgUiController` at `POST ${agui.path}/{beanName}` returning SSE, agents run
  on the application task executor, Boot 4 auto-configuration, `agui.path` / `agui.timeout`.
- Spring AI (optional): `SpringAiAgent`. Wraps backend `ToolCallback`s per run so the model's
  internal tool loop still emits `TOOL_CALL_*` events; registers frontend tools as `returnDirect`
  callbacks so the turn ends when one is called. Spring AI 2.0 removed
  `internalToolExecutionEnabled`, so this is the only way to keep visibility without reimplementing
  the loop.
- Example `examples/agui-starter`: scripted `ChatModel` replaying Spring AI's tool loop without an
  API key, a hand-written agent with thinking and state, server-side probes for E2E.
