package com.wontlost.agui.event;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.wontlost.agui.model.Message;

import tools.jackson.databind.JsonNode;

/**
 * AG-UI 协议的全部标准事件。
 * <p>
 * 事件通过 {@code type} 字段区分，名称与协议规范逐字一致，因此任何 AG-UI 兼容前端
 * （包括本包自带的 Web Component 和 CopilotKit）都能直接消费。
 * 每个事件都是不可变 record；{@code timestamp} 为可选字段，省略时序列化不输出。
 * <p>
 * 设计上刻意不提供"文本 token 字符串"这类退化形式：UI 侧是对事件流做归约，
 * 结构化事件是唯一的数据模型，见 ADR-0001。
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = AgUiEvent.RunStarted.class, name = "RUN_STARTED"),
        @JsonSubTypes.Type(value = AgUiEvent.RunFinished.class, name = "RUN_FINISHED"),
        @JsonSubTypes.Type(value = AgUiEvent.RunError.class, name = "RUN_ERROR"),
        @JsonSubTypes.Type(value = AgUiEvent.StepStarted.class, name = "STEP_STARTED"),
        @JsonSubTypes.Type(value = AgUiEvent.StepFinished.class, name = "STEP_FINISHED"),
        @JsonSubTypes.Type(value = AgUiEvent.TextMessageStart.class, name = "TEXT_MESSAGE_START"),
        @JsonSubTypes.Type(value = AgUiEvent.TextMessageContent.class, name = "TEXT_MESSAGE_CONTENT"),
        @JsonSubTypes.Type(value = AgUiEvent.TextMessageEnd.class, name = "TEXT_MESSAGE_END"),
        @JsonSubTypes.Type(value = AgUiEvent.ThinkingStart.class, name = "THINKING_START"),
        @JsonSubTypes.Type(value = AgUiEvent.ThinkingEnd.class, name = "THINKING_END"),
        @JsonSubTypes.Type(value = AgUiEvent.ThinkingTextMessageStart.class, name = "THINKING_TEXT_MESSAGE_START"),
        @JsonSubTypes.Type(value = AgUiEvent.ThinkingTextMessageContent.class, name = "THINKING_TEXT_MESSAGE_CONTENT"),
        @JsonSubTypes.Type(value = AgUiEvent.ThinkingTextMessageEnd.class, name = "THINKING_TEXT_MESSAGE_END"),
        @JsonSubTypes.Type(value = AgUiEvent.ToolCallStart.class, name = "TOOL_CALL_START"),
        @JsonSubTypes.Type(value = AgUiEvent.ToolCallArgs.class, name = "TOOL_CALL_ARGS"),
        @JsonSubTypes.Type(value = AgUiEvent.ToolCallEnd.class, name = "TOOL_CALL_END"),
        @JsonSubTypes.Type(value = AgUiEvent.ToolCallResult.class, name = "TOOL_CALL_RESULT"),
        @JsonSubTypes.Type(value = AgUiEvent.StateSnapshot.class, name = "STATE_SNAPSHOT"),
        @JsonSubTypes.Type(value = AgUiEvent.StateDelta.class, name = "STATE_DELTA"),
        @JsonSubTypes.Type(value = AgUiEvent.MessagesSnapshot.class, name = "MESSAGES_SNAPSHOT"),
        @JsonSubTypes.Type(value = AgUiEvent.Raw.class, name = "RAW"),
        @JsonSubTypes.Type(value = AgUiEvent.Custom.class, name = "CUSTOM")
})
public sealed interface AgUiEvent {

    /** 事件产生时刻，Unix 毫秒；可为 null。 */
    Long timestamp();

    // ---- 运行生命周期 -------------------------------------------------

    record RunStarted(String threadId, String runId, Long timestamp) implements AgUiEvent {
        public RunStarted(String threadId, String runId) {
            this(threadId, runId, null);
        }
    }

    record RunFinished(String threadId, String runId, JsonNode result, Long timestamp) implements AgUiEvent {
        public RunFinished(String threadId, String runId) {
            this(threadId, runId, null, null);
        }
    }

    record RunError(String message, String code, Long timestamp) implements AgUiEvent {
        public RunError(String message) {
            this(message, null, null);
        }
    }

    record StepStarted(String stepName, Long timestamp) implements AgUiEvent {
        public StepStarted(String stepName) {
            this(stepName, null);
        }
    }

    record StepFinished(String stepName, Long timestamp) implements AgUiEvent {
        public StepFinished(String stepName) {
            this(stepName, null);
        }
    }

    // ---- 文本消息 -----------------------------------------------------

    record TextMessageStart(String messageId, String role, Long timestamp) implements AgUiEvent {
        public TextMessageStart(String messageId) {
            this(messageId, "assistant", null);
        }
    }

    record TextMessageContent(String messageId, String delta, Long timestamp) implements AgUiEvent {
        public TextMessageContent(String messageId, String delta) {
            this(messageId, delta, null);
        }
    }

    record TextMessageEnd(String messageId, Long timestamp) implements AgUiEvent {
        public TextMessageEnd(String messageId) {
            this(messageId, null);
        }
    }

    // ---- 思考过程 -----------------------------------------------------

    record ThinkingStart(String title, Long timestamp) implements AgUiEvent {
        public ThinkingStart() {
            this(null, null);
        }
    }

    record ThinkingEnd(Long timestamp) implements AgUiEvent {
        public ThinkingEnd() {
            this((Long) null);
        }
    }

    record ThinkingTextMessageStart(Long timestamp) implements AgUiEvent {
        public ThinkingTextMessageStart() {
            this((Long) null);
        }
    }

    record ThinkingTextMessageContent(String delta, Long timestamp) implements AgUiEvent {
        public ThinkingTextMessageContent(String delta) {
            this(delta, null);
        }
    }

    record ThinkingTextMessageEnd(Long timestamp) implements AgUiEvent {
        public ThinkingTextMessageEnd() {
            this((Long) null);
        }
    }

    // ---- 工具调用 -----------------------------------------------------

    record ToolCallStart(String toolCallId, String toolCallName, String parentMessageId, Long timestamp)
            implements AgUiEvent {
        public ToolCallStart(String toolCallId, String toolCallName) {
            this(toolCallId, toolCallName, null, null);
        }
    }

    record ToolCallArgs(String toolCallId, String delta, Long timestamp) implements AgUiEvent {
        public ToolCallArgs(String toolCallId, String delta) {
            this(toolCallId, delta, null);
        }
    }

    record ToolCallEnd(String toolCallId, Long timestamp) implements AgUiEvent {
        public ToolCallEnd(String toolCallId) {
            this(toolCallId, null);
        }
    }

    record ToolCallResult(String messageId, String toolCallId, String content, String role, Long timestamp)
            implements AgUiEvent {
        public ToolCallResult(String messageId, String toolCallId, String content) {
            this(messageId, toolCallId, content, "tool", null);
        }
    }

    // ---- 状态同步 -----------------------------------------------------

    record StateSnapshot(JsonNode snapshot, Long timestamp) implements AgUiEvent {
        public StateSnapshot(JsonNode snapshot) {
            this(snapshot, null);
        }
    }

    /** {@code delta} 为 RFC 6902 JSON Patch 数组。 */
    record StateDelta(JsonNode delta, Long timestamp) implements AgUiEvent {
        public StateDelta(JsonNode delta) {
            this(delta, null);
        }
    }

    record MessagesSnapshot(List<Message> messages, Long timestamp) implements AgUiEvent {
        public MessagesSnapshot(List<Message> messages) {
            this(messages, null);
        }
    }

    // ---- 扩展 ---------------------------------------------------------

    record Raw(JsonNode event, String source, Long timestamp) implements AgUiEvent {
        public Raw(JsonNode event) {
            this(event, null, null);
        }
    }

    record Custom(String name, JsonNode value, Long timestamp) implements AgUiEvent {
        public Custom(String name, JsonNode value) {
            this(name, value, null);
        }
    }
}
