package com.wontlost.agui.model;

import java.util.List;

import tools.jackson.databind.JsonNode;

/**
 * 一次 agent 运行的完整输入，即 AG-UI 端点的 POST 请求体。
 * <p>
 * {@code messages} 是前端持有的整段对话；服务端 agent 不需要额外的会话记忆，
 * 但可以按 {@code threadId} 自行持久化。
 */
public record RunAgentInput(
        String threadId,
        String runId,
        JsonNode state,
        List<Message> messages,
        List<ToolDefinition> tools,
        List<ContextItem> context,
        JsonNode forwardedProps) {

    public List<Message> messages() {
        return messages == null ? List.of() : messages;
    }

    public List<ToolDefinition> tools() {
        return tools == null ? List.of() : tools;
    }

    public List<ContextItem> context() {
        return context == null ? List.of() : context;
    }
}
