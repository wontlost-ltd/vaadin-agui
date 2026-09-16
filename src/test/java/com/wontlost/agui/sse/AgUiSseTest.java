package com.wontlost.agui.sse;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.wontlost.agui.event.AgUiEvent;

class AgUiSseTest {

    @Test
    @DisplayName("一帧恒为单行 data 加空行，换行在 JSON 内被转义")
    void frameIsSingleDataLine() {
        String frame = AgUiSse.frame(new AgUiEvent.TextMessageContent("m", "line1\nline2"));
        assertThat(frame).isEqualTo("data: {\"type\":\"TEXT_MESSAGE_CONTENT\",\"messageId\":\"m\",\"delta\":\"line1\\nline2\"}\n\n");
        assertThat(frame.lines().toList()).hasSize(2).first().asString().startsWith("data: ");
    }
}
