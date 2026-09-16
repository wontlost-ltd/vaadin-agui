package com.wontlost.agui.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.wontlost.agui.AgUiJson;

class RunAgentInputTest {

    @Test
    @DisplayName("解析 TypeScript 客户端发出的完整请求体")
    void parsesWireFormat() {
        String body = """
                {
                  "threadId": "t-1",
                  "runId": "r-1",
                  "state": {"counter": 2},
                  "messages": [
                    {"id": "u1", "role": "user", "content": "hi"},
                    {"id": "a1", "role": "assistant", "content": null,
                     "toolCalls": [{"id": "c1", "type": "function", "function": {"name": "lookup", "arguments": "{\\"q\\":\\"x\\"}"}}]},
                    {"id": "t1", "role": "tool", "toolCallId": "c1", "content": "42"},
                    {"id": "a2", "role": "assistant", "content": "The answer is 42"}
                  ],
                  "tools": [{"name": "confirm", "description": "Ask the user", "parameters": {"type": "object"}}],
                  "context": [{"description": "tenant", "value": "acme"}],
                  "forwardedProps": {"page": "/orders"}
                }
                """;
        RunAgentInput input = AgUiJson.read(body, RunAgentInput.class);

        assertThat(input.threadId()).isEqualTo("t-1");
        assertThat(input.state().get("counter").asInt()).isEqualTo(2);
        assertThat(input.messages()).hasSize(4);
        assertThat(input.messages().get(1).role()).isEqualTo(Message.Role.ASSISTANT);
        assertThat(input.messages().get(1).toolCalls()).singleElement()
                .satisfies(c -> {
                    assertThat(c.id()).isEqualTo("c1");
                    assertThat(c.function().name()).isEqualTo("lookup");
                    assertThat(c.function().arguments()).isEqualTo("{\"q\":\"x\"}");
                });
        assertThat(input.messages().get(2).toolCallId()).isEqualTo("c1");
        assertThat(input.tools()).singleElement().extracting(ToolDefinition::name).isEqualTo("confirm");
        assertThat(input.context()).singleElement().extracting(ContextItem::value).isEqualTo("acme");
        assertThat(input.forwardedProps().get("page").asString()).isEqualTo("/orders");
    }

    @Test
    @DisplayName("缺省的列表字段读出来是空列表而不是 null")
    void listsDefaultToEmpty() {
        RunAgentInput input = AgUiJson.read("{\"threadId\":\"t\"}", RunAgentInput.class);
        assertThat(input.messages()).isEmpty();
        assertThat(input.tools()).isEmpty();
        assertThat(input.context()).isEmpty();
        assertThat(input.runId()).isNull();
    }
}
