package com.wontlost.agui.springai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import com.wontlost.agui.AgUiJson;
import com.wontlost.agui.agent.AgUiAgent;
import com.wontlost.agui.agent.AgUiEmitter;
import com.wontlost.agui.event.AgUiEvent;
import com.wontlost.agui.model.ContextItem;
import com.wontlost.agui.model.Message;
import com.wontlost.agui.model.RunAgentInput;
import com.wontlost.agui.model.ToolCall;

import reactor.core.Disposable;
import tools.jackson.databind.node.ObjectNode;

/**
 * 把 Spring AI {@link ChatModel} 的流式输出转换成 AG-UI 事件流。
 * <p>
 * Spring AI 2.0 的模型实现在内部执行工具并自动回问 LLM，调用方看不到中间步骤。
 * 这里不重写那个循环，而是顺着它做两件事：
 * <ul>
 * <li>每次运行把后端 {@link ToolCallback} 包一层观察器，执行前后发出
 * {@code TOOL_CALL_START / ARGS / END / RESULT}，UI 因此能看到每一次工具调用；</li>
 * <li>前端工具（由 {@link RunAgentInput#tools()} 声明）注册为 {@code returnDirect} 的回调：
 * 模型"执行"它时只发出 {@code TOOL_CALL_*} 事件，本轮随即结束，由 UI 执行并在下一次运行中
 * 以 tool 消息回传结果。</li>
 * </ul>
 * 限制：同一轮里模型同时调用前端与后端工具时，Spring AI 只在全部回调都 returnDirect 时才直接返回，
 * 混合情况下前端工具的占位结果（空串）会被送回 LLM。把前端工具的描述写清楚"调用后等待用户"
 * 通常足以避免模型混合调用。
 * <p>
 * 对话记忆不在这里维护：AG-UI 每次运行都携带完整消息，agent 是无状态的。
 */
public final class SpringAiAgent implements AgUiAgent {

    /** 运行结束前发出的自定义事件名，值为 {@code {promptTokens, completionTokens, model}}。 */
    public static final String USAGE_EVENT = "usage";

    private final ChatModel chatModel;
    private final String systemPrompt;
    private final List<ToolCallback> toolCallbacks;
    private final ToolCallingChatOptions baseOptions;

    private SpringAiAgent(Builder b) {
        this.chatModel = b.chatModel;
        this.systemPrompt = b.systemPrompt;
        this.toolCallbacks = List.copyOf(b.toolCallbacks);
        this.baseOptions = b.baseOptions;
    }

    public static Builder builder(ChatModel chatModel) {
        return new Builder(chatModel);
    }

    @Override
    public void run(RunAgentInput input, AgUiEmitter emitter) {
        String threadId = input.threadId();
        String runId = input.runId() != null ? input.runId() : UUID.randomUUID().toString();
        emitter.emit(new AgUiEvent.RunStarted(threadId, runId));

        StreamState state = new StreamState(emitter);
        Prompt prompt = new Prompt(toSpringMessages(input), options(input, state));

        CountDownLatch done = new CountDownLatch(1);
        Disposable subscription = chatModel.stream(prompt).subscribe(
                state::onChunk,
                error -> {
                    state.endText();
                    emitter.error(error);
                    done.countDown();
                },
                () -> {
                    state.endText();
                    state.emitUsage();
                    emitter.emit(new AgUiEvent.RunFinished(threadId, runId));
                    emitter.complete();
                    done.countDown();
                });
        emitter.onCancel(() -> {
            subscription.dispose();
            done.countDown();
        });
        try {
            done.await();
        } catch (InterruptedException e) {
            subscription.dispose();
            Thread.currentThread().interrupt();
        }
    }

    // ---- 提示词与选项 -------------------------------------------------

    private ToolCallingChatOptions options(RunAgentInput input, StreamState state) {
        List<ToolCallback> callbacks = new ArrayList<>();
        for (ToolCallback backend : toolCallbacks) {
            callbacks.add(new ObservedToolCallback(backend, state));
        }
        for (com.wontlost.agui.model.ToolDefinition frontend : input.tools()) {
            callbacks.add(new FrontendToolCallback(frontend, state));
        }
        ToolCallingChatOptions.Builder<?> builder = baseOptions != null
                ? baseOptions.mutate()
                : ToolCallingChatOptions.builder();
        return builder.toolCallbacks(callbacks).build();
    }

    /**
     * AG-UI 消息 → Spring AI 消息。system prompt 与 context 合并成一条 SystemMessage 放在最前；
     * 连续的 tool 消息合并成一条 ToolResponseMessage。
     */
    List<org.springframework.ai.chat.messages.Message> toSpringMessages(RunAgentInput input) {
        List<org.springframework.ai.chat.messages.Message> out = new ArrayList<>();
        String system = systemText(input.context());
        if (!system.isEmpty()) {
            out.add(new SystemMessage(system));
        }
        Map<String, String> toolNames = new java.util.HashMap<>();
        List<ToolResponseMessage.ToolResponse> pendingTool = new ArrayList<>();
        for (Message m : input.messages()) {
            if (m.role() != Message.Role.TOOL && !pendingTool.isEmpty()) {
                out.add(ToolResponseMessage.builder().responses(List.copyOf(pendingTool)).build());
                pendingTool.clear();
            }
            switch (m.role()) {
                case USER -> out.add(new UserMessage(m.content() == null ? "" : m.content()));
                case SYSTEM, DEVELOPER -> out.add(new SystemMessage(m.content() == null ? "" : m.content()));
                case ASSISTANT -> {
                    List<AssistantMessage.ToolCall> calls = new ArrayList<>();
                    if (m.toolCalls() != null) {
                        for (ToolCall c : m.toolCalls()) {
                            toolNames.put(c.id(), c.function().name());
                            calls.add(new AssistantMessage.ToolCall(c.id(), "function", c.function().name(),
                                    c.function().arguments() == null ? "{}" : c.function().arguments()));
                        }
                    }
                    out.add(AssistantMessage.builder()
                            .content(m.content() == null ? "" : m.content())
                            .toolCalls(calls)
                            .build());
                }
                case TOOL -> pendingTool.add(new ToolResponseMessage.ToolResponse(m.toolCallId(),
                        toolNames.getOrDefault(m.toolCallId(), ""), m.content() == null ? "" : m.content()));
            }
        }
        if (!pendingTool.isEmpty()) {
            out.add(ToolResponseMessage.builder().responses(List.copyOf(pendingTool)).build());
        }
        return out;
    }

    private String systemText(List<ContextItem> context) {
        StringBuilder sb = new StringBuilder();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            sb.append(systemPrompt.strip());
        }
        if (!context.isEmpty()) {
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append("Context:");
            for (ContextItem item : context) {
                sb.append("\n- ").append(item.description()).append(": ").append(item.value());
            }
        }
        return sb.toString();
    }

    // ---- 流状态 -------------------------------------------------------

    /**
     * 一次运行内的事件序列状态：当前打开的文本消息 id。
     * 文本块与工具调用在事件流里必须成对开闭，工具调用开始前先关掉正在流式的文本消息。
     * 模型的内部工具执行发生在 Reactor 线程上，与 onChunk 可能交错，因此方法同步。
     */
    static final class StreamState {
        private final AgUiEmitter emitter;
        private String textMessageId;
        private int promptTokens;
        private int completionTokens;
        private String model;

        StreamState(AgUiEmitter emitter) {
            this.emitter = emitter;
        }

        synchronized void onChunk(ChatResponse response) {
            recordUsage(response);
            for (Generation g : response.getResults()) {
                // returnDirect 产生的"生成"是前端工具的占位结果，不是模型文本
                if (ToolExecutionResult.FINISH_REASON.equals(g.getMetadata().getFinishReason())) {
                    continue;
                }
                String text = g.getOutput().getText();
                if (text == null || text.isEmpty()) {
                    continue;
                }
                if (textMessageId == null) {
                    textMessageId = "msg-" + UUID.randomUUID();
                    emitter.emit(new AgUiEvent.TextMessageStart(textMessageId));
                }
                emitter.emit(new AgUiEvent.TextMessageContent(textMessageId, text));
            }
        }

        /**
         * 记住模型报告的用量。流式响应里用量通常只在最后一块出现，且是整轮累计值，取"最大"即可；
         * 内部工具循环会有多次模型调用，把每次的累计值相加。
         */
        private void recordUsage(ChatResponse response) {
            ChatResponseMetadata metadata = response.getMetadata();
            if (metadata == null) {
                return;
            }
            if (metadata.getModel() != null && !metadata.getModel().isEmpty()) {
                model = metadata.getModel();
            }
            Usage usage = metadata.getUsage();
            if (usage == null) {
                return;
            }
            int prompt = usage.getPromptTokens() == null ? 0 : usage.getPromptTokens();
            int completion = usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens();
            if (prompt == 0 && completion == 0) {
                return;
            }
            promptTokens += prompt;
            completionTokens += completion;
        }

        /** 运行结束前发出 {@code CUSTOM usage}，计量与看板据此计费；模型没报用量就不发。 */
        synchronized void emitUsage() {
            if (promptTokens == 0 && completionTokens == 0) {
                return;
            }
            ObjectNode value = AgUiJson.mapper().createObjectNode();
            value.put("promptTokens", promptTokens);
            value.put("completionTokens", completionTokens);
            if (model != null) {
                value.put("model", model);
            }
            emitter.emit(new AgUiEvent.Custom(USAGE_EVENT, value));
        }

        synchronized void endText() {
            if (textMessageId != null) {
                emitter.emit(new AgUiEvent.TextMessageEnd(textMessageId));
                textMessageId = null;
            }
        }

        /** 发出一次完整的工具调用请求（START、ARGS、END），返回本包生成的 toolCallId。 */
        synchronized String toolCallRequested(String name, String arguments) {
            endText();
            String id = "call-" + UUID.randomUUID();
            emitter.emit(new AgUiEvent.ToolCallStart(id, name));
            emitter.emit(new AgUiEvent.ToolCallArgs(id, arguments == null ? "{}" : arguments));
            emitter.emit(new AgUiEvent.ToolCallEnd(id));
            return id;
        }

        synchronized void toolCallFinished(String toolCallId, String result) {
            emitter.emit(new AgUiEvent.ToolCallResult("msg-" + UUID.randomUUID(), toolCallId, result));
        }
    }

    /** 后端工具的观察器：执行前后发事件，行为完全委托。 */
    record ObservedToolCallback(ToolCallback delegate, StreamState state) implements ToolCallback {

        @Override
        public ToolDefinition getToolDefinition() {
            return delegate.getToolDefinition();
        }

        @Override
        public ToolMetadata getToolMetadata() {
            return delegate.getToolMetadata();
        }

        @Override
        public String call(String toolInput) {
            return call(toolInput, null);
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            String id = state.toolCallRequested(delegate.getToolDefinition().name(), toolInput);
            try {
                String result = toolContext == null ? delegate.call(toolInput) : delegate.call(toolInput, toolContext);
                state.toolCallFinished(id, result == null ? "" : result);
                return result;
            } catch (RuntimeException e) {
                state.toolCallFinished(id, "Error: " + e.getMessage());
                throw e;
            }
        }
    }

    /** 前端工具：模型"执行"它只会发出事件并结束本轮，真正的执行发生在浏览器或 Flow 服务端。 */
    record FrontendToolCallback(com.wontlost.agui.model.ToolDefinition definition, StreamState state)
            implements ToolCallback {

        @Override
        public ToolDefinition getToolDefinition() {
            String schema = definition.parameters() == null
                    ? "{\"type\":\"object\",\"properties\":{}}"
                    : AgUiJson.write(definition.parameters());
            return new DefaultToolDefinition(definition.name(),
                    definition.description() == null ? "" : definition.description(), schema);
        }

        @Override
        public ToolMetadata getToolMetadata() {
            return ToolMetadata.builder().returnDirect(true).build();
        }

        @Override
        public String call(String toolInput) {
            state.toolCallRequested(definition.name(), toolInput);
            return "";
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            return call(toolInput);
        }
    }

    // ---- Builder ------------------------------------------------------

    public static final class Builder {
        private final ChatModel chatModel;
        private String systemPrompt;
        private final List<ToolCallback> toolCallbacks = new ArrayList<>();
        private ToolCallingChatOptions baseOptions;

        private Builder(ChatModel chatModel) {
            this.chatModel = Objects.requireNonNull(chatModel, "chatModel");
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        /** 带 Spring AI {@code @Tool} 注解方法的对象。 */
        public Builder tools(Object... toolObjects) {
            toolCallbacks.addAll(List.of(ToolCallbacks.from(toolObjects)));
            return this;
        }

        public Builder toolCallbacks(ToolCallback... callbacks) {
            toolCallbacks.addAll(List.of(callbacks));
            return this;
        }

        /** 模型、温度等基础选项；工具回调由 agent 每次运行合并进去。 */
        public Builder options(ToolCallingChatOptions options) {
            this.baseOptions = options;
            return this;
        }

        public SpringAiAgent build() {
            return new SpringAiAgent(this);
        }
    }
}
