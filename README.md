# AG-UI for Vaadin

AG-UI protocol chat component for Vaadin Flow. The browser streams agent events straight from an
SSE endpoint and renders Markdown, tool calls and thinking blocks itself; the Flow server hears
about a run only when it starts, finishes, errors, or asks the UI to execute a tool.

Vaadin 25 ships its own AI integration (`AIOrchestrator`, preview). It streams plain text tokens
through Vaadin push, one request at a time, with state bound to the HTTP session and to component
instances. This add-on takes the other route — see [ADR-0001](docs/adr/0001-agui-protocol-for-in-app-ai.md):

- **Structured events, not text.** The ~16 standard [AG-UI](https://docs.ag-ui.com) event types:
  text, thinking, tool calls with arguments and results, state snapshots and deltas, steps, errors.
- **No `@Push`.** Tokens never touch the UI lock. Each `threadId` runs independently; stopping is
  aborting a fetch; regenerating is truncating and re-running.
- **Stateless agents.** Every run carries the whole conversation. Persist by `threadId` if you want
  to; nothing lives in the session or in a component instance.
- **Any AG-UI agent.** The component speaks the protocol, so it also works against LangGraph,
  Pydantic AI, Mastra or CopilotKit backends — and any AG-UI frontend works against the Spring
  endpoint shipped here.

## Installation

```xml
<dependency>
    <groupId>com.wontlost</groupId>
    <artifactId>agui-vaadin</artifactId>
    <version>0.1.0</version>
</dependency>
```

```kotlin
implementation("com.wontlost:agui-vaadin:0.1.0")
```

### Compatibility

| Dependency | Version | Notes |
|---|---|---|
| Vaadin Platform | 25.2.8+ | 25.x series, Lumo and Aura |
| Java | 21+ | Vaadin 25 baseline |
| Jackson | 3 | the one Vaadin 25 and Spring Boot 4 ship; `provided` |
| Spring Boot | 4.0+ | optional: SSE endpoint + auto-configuration |
| Spring AI | 2.0+ | optional: `SpringAiAgent` adapter |
| Lit / marked / DOMPurify | `^3.3.3` / `^15.0.12` / `^3.2.6` | pulled in via `@NpmPackage` |
| Node | 24+ | frontend build only |

Spring and Spring AI are **optional** dependencies. Without them you get the Flow component and the
event model; point the component at any AG-UI endpoint you already have.

## Quick start (Spring Boot 4 + Spring AI)

Declare an agent bean. Its bean name becomes the endpoint: `POST /agui/assistant`.

```java
@Bean
AgUiAgent assistant(ChatModel chatModel) {
    return SpringAiAgent.builder(chatModel)
            .systemPrompt("You are the order desk assistant.")
            .tools(new OrderTools())          // Spring AI @Tool methods, visible in the UI as cards
            .build();
}
```

Drop the component into a view:

```java
AgUiChat chat = new AgUiChat("/agui/assistant");
chat.setSizeFull();
chat.setSuggestions("Where is order A-1001?", "What can you do?");
chat.setContext(List.of(new ContextItem("tenant", tenantId)));
chat.addRunFinishedListener(e -> conversations.save(e.getThreadId(), e.getMessages()));
add(chat);
```

That is the whole integration. No `@Push`, no feature flag, no orchestrator.

## What the server can do

| Call | Effect |
|---|---|
| `prompt(text)` | send as the user and run, e.g. from a button |
| `stop()` | abort the current run; partial output stays |
| `regenerate()` | drop everything after the last user message and run again |
| `clear()` | new `threadId`, empty conversation |
| `setMessages(list)` | restore a saved conversation |
| `setFrontendTools(list)` | declare tools the UI executes; the agent calling one ends the run and fires `ToolCallEvent` |
| `submitToolResult(id, json)` | answer a frontend tool call; the component re-runs automatically |
| `setContext(list)` | per-run ambient context (tenant, locale, page state) |
| `setRequestHeaders(map)` | extra headers for the SSE request, e.g. a bearer token |

Events: `RunStarted`, `RunFinished` (with the conversation snapshot and a `cancelled` flag),
`RunError`, `ToolCall`, `StateChanged`, `MessageSent`, `Feedback`.

## Frontend tools: the UI as a tool

```java
chat.setFrontendTools(List.of(new ToolDefinition("confirm", "Ask the user before continuing",
        schemaJson)));
chat.addToolCallListener(e -> {
    ConfirmDialog dialog = new ConfirmDialog("Continue?", e.getArgumentsAsJson().get("question").asString(),
            "Yes", ok -> chat.submitToolResult(e.getToolCallId(), "{\"confirmed\":true}"),
            "No", no -> chat.submitToolResult(e.getToolCallId(), "{\"confirmed\":false}"));
    dialog.open();
});
```

With `SpringAiAgent` the frontend tool is registered as a `returnDirect` callback: the model's own
tool loop ends the turn as soon as it calls the tool, and continues on the next run with the result.

## Writing an agent without an AI framework

```java
@Bean
AgUiAgent scripted() {
    return (input, emitter) -> {
        emitter.emit(new AgUiEvent.RunStarted(input.threadId(), input.runId()));
        emitter.emit(new AgUiEvent.TextMessageStart("m1"));
        emitter.emit(new AgUiEvent.TextMessageContent("m1", "Hello **" + input.messages().getLast().content() + "**"));
        emitter.emit(new AgUiEvent.TextMessageEnd("m1"));
        emitter.emit(new AgUiEvent.RunFinished(input.threadId(), input.runId()));
        emitter.complete();
    };
}
```

`AgUiAgent` is the only SPI. The endpoint runs it on a task executor (virtual threads when Boot
has them), so blocking is fine; check `emitter.isCancelled()` in long loops.

## Theming

Custom properties with Lumo fallbacks; Aura works through the same fallbacks.

```css
agui-chat {
  --agui-user-bubble-background: var(--lumo-primary-color-10pct);
  --agui-assistant-bubble-background: transparent;
  --agui-bubble-radius: 0.5rem;
}
```

Variants: `chat.addThemeVariants(AgUiChatVariant.COMPACT, AgUiChatVariant.FLAT)`.
Parts: `messages`, `message`, `bubble`, `tool-call`, `thinking`, `actions`, `composer`, `suggestions`, `error`.

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `agui.path` | `/agui` | endpoint prefix; agents live at `<path>/<beanName>` |
| `agui.timeout` | `10m` | SSE timeout per run |

The endpoint is a normal Spring MVC route: put it behind whatever security you use, and make sure
reverse proxies do not buffer `text/event-stream`.

## Example: Atlas Order Desk

```bash
mvn install -DskipTests
mvn -f examples/agui-starter/pom.xml spring-boot:run          # http://localhost:8090
```

A mock logistics desk with six switchable agents, each built to show one thing. No API key is
needed: a scripted `ChatModel` replays Spring AI's internal tool loop, so `SpringAiAgent` runs the
exact code path a real model would.

| Agent | Built with | Shows |
|---|---|---|
| Order Desk | `SpringAiAgent` + `@Tool` methods | backend tools run inside the model loop and still appear as cards; a refund changes the grid on the right |
| Approvals | hand-written agent + frontend tools | `focusOrder` highlights the grid row, `confirm` opens a Flow dialog, the run resumes with the answer |
| Analyst | `STEP_*`, `STATE_SNAPSHOT`, `STATE_DELTA` | KPI cards and a plan checklist driven by state events, not chat text |
| Writer | long Markdown + thinking block | stop mid-stream, regenerate, copy |
| Chaos | steps + `RUN_ERROR` | errors are events: partial text stays, a banner shows, regenerate recovers |
| External runtime | a bare controller writing SSE frames by hand | the component only speaks the protocol; no add-on class is involved |

Also on the page: a conversation sidebar (in-memory store, restored with `setMessages`), a
parallel lane that streams a second chat at the same time, an English/中文 toggle, `compact` and
`flat` variants, and a server-side event log that proves Flow hears about each run once.
[e2e/README.md](e2e/README.md) describes the Playwright suite that locks all of this down.

## Licence

Apache 2.0.
