package com.wontlost.agui.sse;

import com.wontlost.agui.AgUiJson;
import com.wontlost.agui.event.AgUiEvent;

/**
 * AG-UI 事件的 SSE 帧编码。
 * <p>
 * 协议只用 {@code data:} 字段，不用 {@code event:}，因为事件类型已经在 JSON 的
 * {@code type} 字段里；这样任何标准 SSE 客户端都只需处理一种帧。
 * Jackson 会转义字符串里的换行，所以一条事件恒为单行 JSON，一帧恒为一行 data 加空行。
 */
public final class AgUiSse {

    private AgUiSse() {
    }

    /** 事件 → JSON 文本（单行）。 */
    public static String json(AgUiEvent event) {
        return AgUiJson.write(event);
    }

    /** 事件 → 完整 SSE 帧，含结尾空行。 */
    public static String frame(AgUiEvent event) {
        return "data: " + json(event) + "\n\n";
    }
}
