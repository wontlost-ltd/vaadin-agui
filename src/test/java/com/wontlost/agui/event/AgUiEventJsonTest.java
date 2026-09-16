package com.wontlost.agui.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.wontlost.agui.AgUiJson;
import com.wontlost.agui.model.Message;

import tools.jackson.databind.JsonNode;

class AgUiEventJsonTest {

    static Stream<Arguments> events() {
        JsonNode obj = AgUiJson.mapper().readTree("{\"a\":1}");
        return Stream.of(
                Arguments.of("RUN_STARTED", new AgUiEvent.RunStarted("t1", "r1")),
                Arguments.of("RUN_FINISHED", new AgUiEvent.RunFinished("t1", "r1")),
                Arguments.of("RUN_ERROR", new AgUiEvent.RunError("boom", "E1", null)),
                Arguments.of("STEP_STARTED", new AgUiEvent.StepStarted("plan")),
                Arguments.of("STEP_FINISHED", new AgUiEvent.StepFinished("plan")),
                Arguments.of("TEXT_MESSAGE_START", new AgUiEvent.TextMessageStart("m1")),
                Arguments.of("TEXT_MESSAGE_CONTENT", new AgUiEvent.TextMessageContent("m1", "he\nllo")),
                Arguments.of("TEXT_MESSAGE_END", new AgUiEvent.TextMessageEnd("m1")),
                Arguments.of("THINKING_START", new AgUiEvent.ThinkingStart()),
                Arguments.of("THINKING_END", new AgUiEvent.ThinkingEnd()),
                Arguments.of("THINKING_TEXT_MESSAGE_START", new AgUiEvent.ThinkingTextMessageStart()),
                Arguments.of("THINKING_TEXT_MESSAGE_CONTENT", new AgUiEvent.ThinkingTextMessageContent("hmm")),
                Arguments.of("THINKING_TEXT_MESSAGE_END", new AgUiEvent.ThinkingTextMessageEnd()),
                Arguments.of("TOOL_CALL_START", new AgUiEvent.ToolCallStart("c1", "lookup")),
                Arguments.of("TOOL_CALL_ARGS", new AgUiEvent.ToolCallArgs("c1", "{\"q\":")),
                Arguments.of("TOOL_CALL_END", new AgUiEvent.ToolCallEnd("c1")),
                Arguments.of("TOOL_CALL_RESULT", new AgUiEvent.ToolCallResult("m2", "c1", "42")),
                Arguments.of("STATE_SNAPSHOT", new AgUiEvent.StateSnapshot(obj)),
                Arguments.of("STATE_DELTA", new AgUiEvent.StateDelta(AgUiJson.mapper().readTree("[]"))),
                Arguments.of("MESSAGES_SNAPSHOT", new AgUiEvent.MessagesSnapshot(List.of(Message.assistant("m", "hi")))),
                Arguments.of("RAW", new AgUiEvent.Raw(obj)),
                Arguments.of("CUSTOM", new AgUiEvent.Custom("ping", obj)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("events")
    @DisplayName("每种事件的 type 名与协议一致，且能无损往返")
    void roundTrip(String type, AgUiEvent event) {
        String json = AgUiJson.write(event);
        assertThat(json).startsWith("{\"type\":\"" + type + "\"");
        assertThat(json).doesNotContain("\n");
        assertThat(json).doesNotContain("\"timestamp\"");
        assertThat(AgUiJson.read(json, AgUiEvent.class)).isEqualTo(event);
    }

    @Test
    @DisplayName("前端或第三方 agent 带来的未知字段被忽略")
    void unknownFieldsIgnored() {
        AgUiEvent event = AgUiJson.read("{\"type\":\"RUN_STARTED\",\"threadId\":\"t\",\"runId\":\"r\",\"extra\":true}",
                AgUiEvent.class);
        assertThat(event).isEqualTo(new AgUiEvent.RunStarted("t", "r"));
    }

    @Test
    @DisplayName("Role 以小写字符串上线，与 TypeScript 参考实现一致")
    void rolesAreLowercase() {
        String json = AgUiJson.write(new AgUiEvent.MessagesSnapshot(List.of(Message.user("x"))));
        assertThat(json).contains("\"role\":\"user\"");
        assertThat(json).doesNotContain("toolCalls");
    }
}
