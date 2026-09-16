package com.wontlost.agui.model;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * AG-UI 对话消息。
 * <p>
 * 与协议规范一致：{@code toolCalls} 只在 assistant 消息上出现，
 * {@code toolCallId} 只在 tool 消息上出现，其余情况为 null 并在序列化时省略。
 * 实现 Serializable 是因为 {@code AgUiChat} 会把对话镜像存在组件字段里，会话持久化时随 UI 一起序列化。
 */
public record Message(
        String id,
        Role role,
        String content,
        String name,
        List<ToolCall> toolCalls,
        String toolCallId) implements Serializable {

    public static Message user(String content) {
        return new Message(UUID.randomUUID().toString(), Role.USER, content, null, null, null);
    }

    public static Message assistant(String id, String content) {
        return new Message(id, Role.ASSISTANT, content, null, null, null);
    }

    public static Message assistantToolCalls(String id, List<ToolCall> toolCalls) {
        return new Message(id, Role.ASSISTANT, null, null, toolCalls, null);
    }

    public static Message system(String content) {
        return new Message(UUID.randomUUID().toString(), Role.SYSTEM, content, null, null, null);
    }

    public static Message tool(String id, String toolCallId, String content) {
        return new Message(id, Role.TOOL, content, null, null, toolCallId);
    }

    public enum Role {
        DEVELOPER("developer"),
        SYSTEM("system"),
        ASSISTANT("assistant"),
        USER("user"),
        TOOL("tool");

        private final String wire;

        Role(String wire) {
            this.wire = wire;
        }

        /** 线上格式为小写字符串，与 TypeScript 参考实现一致。 */
        @JsonValue
        public String wire() {
            return wire;
        }
    }
}
