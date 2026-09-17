package com.wontlost.agui.springai;

import java.util.List;
import java.util.function.Function;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;

import reactor.core.publisher.Flux;

/**
 * 脚本化的 ChatModel：不联网，但复刻 Spring AI 模型实现的内部工具循环
 * （先返回工具调用 → 用 ToolCallingManager 执行 → returnDirect 则直接返回，否则带着历史再问一轮）。
 * 这样对适配器而言它与真实模型行为一致，测试和 demo 都用它。
 */
public final class ScriptedChatModel implements ChatModel {

    private final ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();
    private final Function<Prompt, List<AssistantMessage.ToolCall>> toolCallScript;
    private final List<String> textChunks;

    /**
     * @param toolCallScript 给定提示词，决定本轮是否发起工具调用（返回空列表表示不调用）
     * @param textChunks     最终回答按块流出
     */
    public ScriptedChatModel(Function<Prompt, List<AssistantMessage.ToolCall>> toolCallScript,
            List<String> textChunks) {
        this.toolCallScript = toolCallScript;
        this.textChunks = textChunks;
    }

    public static ScriptedChatModel textOnly(String... chunks) {
        return new ScriptedChatModel(p -> List.of(), List.of(chunks));
    }

    private Usage usageOnLastChunk;
    private String modelName;

    /** 让最后一块带上用量与模型名，模拟真实提供商的流式响应。 */
    public ScriptedChatModel withUsage(int promptTokens, int completionTokens, String model) {
        this.usageOnLastChunk = new DefaultUsage(promptTokens, completionTokens);
        this.modelName = model;
        return this;
    }

    private ChatResponseMetadata metadataFor(String chunk) {
        if (usageOnLastChunk == null) {
            return ChatResponseMetadata.builder().build();
        }
        boolean last = textChunks.indexOf(chunk) == textChunks.size() - 1;
        ChatResponseMetadata.Builder b = ChatResponseMetadata.builder().model(modelName);
        return (last ? b.usage(usageOnLastChunk) : b).build();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        return stream(prompt).reduce((a, b) -> b).block();
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        List<AssistantMessage.ToolCall> calls = toolCallScript.apply(prompt);
        if (!calls.isEmpty() && prompt.getOptions() instanceof ToolCallingChatOptions) {
            ChatResponse toolCallResponse = new ChatResponse(List.of(new Generation(
                    AssistantMessage.builder().content("").toolCalls(calls).build())));
            return Flux.defer(() -> {
                ToolExecutionResult result = toolCallingManager.executeToolCalls(prompt, toolCallResponse);
                if (result.returnDirect()) {
                    return Flux.just(new ChatResponse(ToolExecutionResult.buildGenerations(result)));
                }
                return stream(new Prompt(result.conversationHistory(), prompt.getOptions()));
            });
        }
        return Flux.fromIterable(textChunks)
                .map(chunk -> new ChatResponse(List.of(new Generation(new AssistantMessage(chunk))), metadataFor(chunk)));
    }

    /** 常用脚本：历史里还没有工具响应时发起一次指定工具调用。 */
    public static Function<Prompt, List<AssistantMessage.ToolCall>> callOnceUntilAnswered(String id, String name,
            String arguments) {
        return prompt -> prompt.getInstructions().stream().anyMatch(m -> m instanceof ToolResponseMessage)
                ? List.of()
                : List.of(new AssistantMessage.ToolCall(id, "function", name, arguments));
    }
}
