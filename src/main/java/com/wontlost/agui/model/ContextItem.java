package com.wontlost.agui.model;

import java.io.Serializable;

/**
 * 随每次运行附带给 agent 的上下文片段，例如当前租户、locale、页面状态。
 * 这是 AG-UI 中把"每轮环境信息"与系统提示词分离的标准方式。
 */
public record ContextItem(String description, String value) implements Serializable {
}
