package com.wontlost.agui.model;

import java.io.Serializable;

import tools.jackson.databind.JsonNode;

/**
 * 前端声明给 agent 的工具。{@code parameters} 为 JSON Schema。
 * <p>
 * 这类工具由 UI 一侧执行：agent 只发出 TOOL_CALL 事件，结果由前端或 Flow 服务端
 * 通过 {@code tool} 角色消息回传，见 {@code AgUiChat#submitToolResult}。
 */
public record ToolDefinition(String name, String description, JsonNode parameters) implements Serializable {
}
