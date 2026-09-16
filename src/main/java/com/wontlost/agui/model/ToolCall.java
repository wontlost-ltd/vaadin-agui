package com.wontlost.agui.model;

import java.io.Serializable;

/**
 * assistant 消息中携带的一次工具调用请求。
 * {@code type} 目前固定为 {@code function}，与 OpenAI 风格的线上格式保持一致。
 */
public record ToolCall(String id, String type, FunctionCall function) implements Serializable {

    public static ToolCall function(String id, String name, String arguments) {
        return new ToolCall(id, "function", new FunctionCall(name, arguments));
    }

    /** {@code arguments} 是 JSON 字符串，而不是已解析的对象，因为流式期间它可能不完整。 */
    public record FunctionCall(String name, String arguments) implements Serializable {
    }
}
