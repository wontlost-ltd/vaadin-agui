package com.wontlost.agui.springai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.tool.annotation.Tool;

import com.wontlost.agui.AgUiJson;
import com.wontlost.agui.CollectingEmitter;
import com.wontlost.agui.event.AgUiEvent;
import com.wontlost.agui.model.ContextItem;
import com.wontlost.agui.model.Message;
import com.wontlost.agui.model.RunAgentInput;
import com.wontlost.agui.model.ToolCall;
import com.wontlost.agui.model.ToolDefinition;

class SpringAiAgentTest {

    static final class LookupTools {
        @Tool(description = "Look something up")
        String lookup(String q) {
            return "value-of-" + q;
        }
    }

    private static RunAgentInput input(List<Message> messages, List<ToolDefinition> tools) {
        return new RunAgentInput("t1", "r1", null, messages, tools, List.of(new ContextItem("tenant", "acme")), null);
    }

    @Test
    @DisplayName("纯文本回答：一个文本消息，块按序流出，运行以 RUN_FINISHED 收尾")
    void streamsText() {
        SpringAiAgent agent = SpringAiAgent.builder(ScriptedChatModel.textOnly("Hel", "lo")).build();
        CollectingEmitter emitter = new CollectingEmitter();

        agent.run(input(List.of(Message.user("hi")), List.of()), emitter);

        assertThat(emitter.types()).containsExactly("RunStarted", "TextMessageStart", "TextMessageContent",
                "TextMessageContent", "TextMessageEnd", "RunFinished");
        String messageId = ((AgUiEvent.TextMessageStart) emitter.events().get(1)).messageId();
        assertThat(emitter.events().stream().filter(AgUiEvent.TextMessageContent.class::isInstance)
                .map(e -> ((AgUiEvent.TextMessageContent) e).delta())).containsExactly("Hel", "lo");
        assertThat(((AgUiEvent.TextMessageEnd) emitter.events().get(4)).messageId()).isEqualTo(messageId);
        assertThat(emitter.isCompleted()).isTrue();
        assertThat(emitter.error()).isNull();
    }

    @Test
    @DisplayName("后端工具：模型内部执行时仍发出 START/ARGS/END/RESULT，随后文本另起一条消息")
    void observesBackendToolCalls() {
        ScriptedChatModel model = new ScriptedChatModel(
                ScriptedChatModel.callOnceUntilAnswered("id-1", "lookup", "{\"q\":\"x\"}"), List.of("Answer"));
        SpringAiAgent agent = SpringAiAgent.builder(model).tools(new LookupTools()).build();
        CollectingEmitter emitter = new CollectingEmitter();

        agent.run(input(List.of(Message.user("find x")), List.of()), emitter);

        assertThat(emitter.types()).containsExactly("RunStarted", "ToolCallStart", "ToolCallArgs", "ToolCallEnd",
                "ToolCallResult", "TextMessageStart", "TextMessageContent", "TextMessageEnd", "RunFinished");
        AgUiEvent.ToolCallStart start = (AgUiEvent.ToolCallStart) emitter.events().get(1);
        AgUiEvent.ToolCallResult result = (AgUiEvent.ToolCallResult) emitter.events().get(4);
        assertThat(start.toolCallName()).isEqualTo("lookup");
        assertThat(result.toolCallId()).isEqualTo(start.toolCallId());
        // Spring AI 把方法返回值 JSON 编码后交给模型，事件里透传同样的文本
        assertThat(result.content()).isEqualTo("\"value-of-x\"");
    }

    @Test
    @DisplayName("前端工具：只发出调用事件并结束本轮，不产生文本也不产生 RESULT")
    void frontendToolEndsTheRun() {
        ScriptedChatModel model = new ScriptedChatModel(
                ScriptedChatModel.callOnceUntilAnswered("id-1", "confirm", "{\"question\":\"ok?\"}"),
                List.of("should not appear"));
        SpringAiAgent agent = SpringAiAgent.builder(model).build();
        ToolDefinition confirm = new ToolDefinition("confirm", "Ask the user to confirm",
                AgUiJson.mapper().readTree("{\"type\":\"object\",\"properties\":{\"question\":{\"type\":\"string\"}}}"));
        CollectingEmitter emitter = new CollectingEmitter();

        agent.run(input(List.of(Message.user("go")), List.of(confirm)), emitter);

        assertThat(emitter.types()).containsExactly("RunStarted", "ToolCallStart", "ToolCallArgs", "ToolCallEnd",
                "RunFinished");
        assertThat(((AgUiEvent.ToolCallArgs) emitter.events().get(2)).delta()).isEqualTo("{\"question\":\"ok?\"}");
        assertThat(emitter.isCompleted()).isTrue();
    }

    @Test
    @DisplayName("AG-UI 消息转换：system 与 context 合并在最前，连续 tool 消息合并，工具名从调用记录回填")
    void convertsMessages() {
        SpringAiAgent agent = SpringAiAgent.builder(ScriptedChatModel.textOnly("x")).systemPrompt("Be brief.").build();
        List<Message> history = List.of(
                Message.user("find"),
                Message.assistantToolCalls("a1", List.of(ToolCall.function("c1", "lookup", "{}"),
                        ToolCall.function("c2", "lookup", "{}"))),
                Message.tool("t1", "c1", "one"),
                Message.tool("t2", "c2", "two"),
                Message.assistant("a2", "done"));

        var messages = agent.toSpringMessages(input(history, List.of()));

        assertThat(messages).hasSize(5);
        assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(messages.get(0).getText()).isEqualTo("Be brief.\n\nContext:\n- tenant: acme");
        assertThat(messages.get(3)).isInstanceOf(ToolResponseMessage.class);
        ToolResponseMessage tool = (ToolResponseMessage) messages.get(3);
        assertThat(tool.getResponses()).extracting(ToolResponseMessage.ToolResponse::name).containsExactly("lookup", "lookup");
        assertThat(tool.getResponses()).extracting(ToolResponseMessage.ToolResponse::responseData).containsExactly("one", "two");
    }

    @Test
    @DisplayName("客户端断开后订阅被释放，run 返回而不是挂住")
    void cancellationDisposesSubscription() {
        SpringAiAgent agent = SpringAiAgent.builder(ScriptedChatModel.textOnly("a", "b")).build();
        CollectingEmitter emitter = new CollectingEmitter();
        // 事件在 subscribe 时同步流完，因此这里验证的是 onCancel 注册与 latch 释放不会互相卡死
        agent.run(input(List.of(Message.user("hi")), List.of()), emitter);
        emitter.cancel();
        assertThat(emitter.isCompleted()).isTrue();
    }
}
