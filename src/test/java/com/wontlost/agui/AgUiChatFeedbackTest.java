package com.wontlost.agui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.ComponentEventBusUtil;
import com.vaadin.flow.component.ComponentUtil;

class AgUiChatFeedbackTest {

    private static String feedbackJson(AgUiChat chat) {
        Object raw = chat.getElement().getPropertyRaw("feedback");
        return raw == null ? "" : raw.toString();
    }

    @Test
    void setFeedbackWritesPropertyMap() {
        AgUiChat chat = new AgUiChat("/agui/x");
        chat.setFeedback("m-1", true);
        chat.setFeedback("m-2", false);
        String json = feedbackJson(chat);
        assertTrue(json.contains("\"m-1\":\"up\""), json);
        assertTrue(json.contains("\"m-2\":\"down\""), json);
        assertEquals(Boolean.TRUE, chat.getFeedback("m-1"));
        assertEquals(Boolean.FALSE, chat.getFeedback("m-2"));
    }

    @Test
    void setFeedbackNullClearsVote() {
        AgUiChat chat = new AgUiChat("/agui/x");
        chat.setFeedback("m-1", true);
        chat.setFeedback("m-1", null);
        assertNull(chat.getFeedback("m-1"));
        assertFalse(feedbackJson(chat).contains("m-1"), feedbackJson(chat));
    }

    @Test
    void clearResetsFeedback() {
        AgUiChat chat = new AgUiChat("/agui/x");
        chat.setFeedback("m-1", true);
        chat.clear();
        assertNull(chat.getFeedback("m-1"));
        assertFalse(feedbackJson(chat).contains("m-1"), feedbackJson(chat));
    }

    @Test
    void feedbackEventCarriesNewValue() {
        AgUiChat chat = new AgUiChat("/agui/x");
        List<AgUiChat.FeedbackEvent> seen = new ArrayList<>();
        chat.addFeedbackListener(seen::add);

        ComponentUtil.fireEvent(chat, new AgUiChat.FeedbackEvent(chat, true, "m-1", false, "down"));
        AgUiChat.FeedbackEvent down = seen.get(0);
        assertEquals("m-1", down.getMessageId());
        assertFalse(down.isPositive());
        assertEquals(Boolean.FALSE, down.getHelpful());
        assertFalse(down.isCleared());
        assertEquals(Boolean.FALSE, chat.getFeedback("m-1"));

        // 再点一次同一按钮：撤销
        ComponentUtil.fireEvent(chat, new AgUiChat.FeedbackEvent(chat, true, "m-1", false, null));
        AgUiChat.FeedbackEvent cleared = seen.get(1);
        assertFalse(cleared.isPositive());
        assertNull(cleared.getHelpful());
        assertTrue(cleared.isCleared());
        assertNull(chat.getFeedback("m-1"));
    }

    @Test
    void legacyConstructorMeansVoteNotCleared() {
        AgUiChat chat = new AgUiChat("/agui/x");
        AgUiChat.FeedbackEvent e = new AgUiChat.FeedbackEvent(chat, true, "m-1", true);
        assertTrue(e.isPositive());
        assertEquals(Boolean.TRUE, e.getHelpful());
        assertFalse(e.isCleared());
    }

    @Test
    void domEventBindsValueFromDetail() {
        Constructor<AgUiChat.FeedbackEvent> ctor = ComponentEventBusUtil.getEventConstructor(AgUiChat.FeedbackEvent.class);
        assertEquals(5, ctor.getParameterCount());
        assertEquals(List.of("event.detail.messageId", "event.detail.positive", "event.detail.value"),
                List.copyOf(ComponentEventBusUtil.getEventDataExpressions(AgUiChat.FeedbackEvent.class).keySet()));
    }
}
