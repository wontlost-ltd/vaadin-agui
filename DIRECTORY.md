# AgUiVaadin

**AG-UI protocol chat component for Vaadin Flow. Structured agent events, streamed straight to the
browser, no `@Push` required.**

Vaadin 25's built-in AI integration streams plain text tokens through Vaadin push, one request at a
time, with state bound to the HTTP session. That is fine for a demo chatbot and limiting for
everything after it: tool calls are invisible, there is no stop button, two tabs fight over one
orchestrator, and every token takes the UI lock.

AgUiVaadin speaks [AG-UI](https://docs.ag-ui.com), the open event protocol used by LangGraph,
Pydantic AI, Mastra and CopilotKit. The component opens an SSE stream from the browser, reduces the
event stream into a conversation, and renders Markdown, tool-call cards, thinking blocks and state
updates as they arrive. The Flow server is told when a run starts, finishes or fails, and receives
the full conversation snapshot to persist however it likes.

```java
AgUiChat chat = new AgUiChat("/agui/assistant");
chat.setSizeFull();
add(chat);
```

## Spring AI in one bean

```java
@Bean
AgUiAgent assistant(ChatModel chatModel) {
    return SpringAiAgent.builder(chatModel)
            .systemPrompt("You are the order desk assistant.")
            .tools(new OrderTools())
            .build();
}
```

The bean name is the endpoint. Backend `@Tool` methods show up in the UI as cards with arguments
and results while the model's own tool loop runs them. Tools the UI should execute are declared on
the component; the agent calling one ends the turn, Flow gets a `ToolCallEvent`, and the component
resumes once you hand back the result.

## What you get

- Streaming Markdown with code blocks, tables and sanitised HTML
- Tool-call cards: name, status, arguments, result
- Thinking blocks, step indicators, error banners
- Stop, regenerate, copy, thumbs up/down, suggestion chips
- Server-driven `prompt()`, `stop()`, `regenerate()`, `clear()`, `setMessages()`
- Per-run context (tenant, locale, page state) and shared agent state via snapshots and JSON Patch
- Lumo and Aura, `compact` and `flat` variants, CSS parts and custom properties
- Works against any AG-UI endpoint, not only the Spring one shipped here

## Compatibility

Vaadin 25.2+, Java 21+, Jackson 3. Spring Boot 4 and Spring AI 2 are optional dependencies.
Apache 2.0.
