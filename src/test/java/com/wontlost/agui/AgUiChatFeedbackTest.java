package com.wontlost.agui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.dom.DomEvent;
import com.vaadin.flow.internal.nodefeature.ElementListenerMap;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class AgUiChatFeedbackTest {

    private static final String LEGACY_FILTER = "event.detail.value != null";

    private static String feedbackJson(AgUiChat chat) {
        Object raw = chat.getElement().getPropertyRaw("feedback");
        return raw == null ? "" : raw.toString();
    }

    /** 按浏览器的方式投递 agui-feedback：过滤表达式的结果由客户端算好随事件数据回传。 */
    private static void clientFeedback(AgUiChat chat, String messageId, boolean positive, String value) {
        ObjectNode data = JsonMapper.shared().createObjectNode();
        data.put("event.detail.messageId", messageId);
        data.put("event.detail.positive", positive);
        data.put("event.detail.value", value);
        data.put(LEGACY_FILTER, value != null);
        chat.getElement().getNode().getFeature(ElementListenerMap.class)
                .fireEvent(new DomEvent(chat.getElement(), "agui-feedback", data));
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
    void legacyListenerIsNotCalledOnClear() {
        AgUiChat chat = new AgUiChat("/agui/x");
        List<AgUiChat.FeedbackEvent> votes = new ArrayList<>();
        chat.addFeedbackListener(votes::add);

        clientFeedback(chat, "m-1", true, "up");
        clientFeedback(chat, "m-1", true, null);

        assertEquals(1, votes.size());
        assertTrue(votes.get(0).isPositive());
    }

    @Test
    void changeListenerReceivesNullOnClearAndMapIsPruned() {
        AgUiChat chat = new AgUiChat("/agui/x");
        List<AgUiChat.FeedbackChangeEvent> changes = new ArrayList<>();
        chat.addFeedbackChangeListener(changes::add);

        clientFeedback(chat, "m-1", true, "up");
        assertEquals(Boolean.TRUE, chat.getFeedback("m-1"));
        clientFeedback(chat, "m-1", true, null);

        assertEquals(2, changes.size());
        assertEquals(Boolean.TRUE, changes.get(0).getHelpful());
        AgUiChat.FeedbackChangeEvent cleared = changes.get(1);
        assertEquals("m-1", cleared.getMessageId());
        assertNull(cleared.getHelpful());
        assertTrue(cleared.isCleared());
        assertNull(chat.getFeedback("m-1"));
        assertFalse(feedbackJson(chat).contains("m-1"), feedbackJson(chat));
    }

    @Test
    void switchingUpToDownCallsLegacyListenerWithFalse() {
        AgUiChat chat = new AgUiChat("/agui/x");
        List<AgUiChat.FeedbackEvent> votes = new ArrayList<>();
        List<AgUiChat.FeedbackChangeEvent> changes = new ArrayList<>();
        chat.addFeedbackListener(votes::add);
        chat.addFeedbackChangeListener(changes::add);

        clientFeedback(chat, "m-1", true, "up");
        clientFeedback(chat, "m-1", false, "down");

        assertEquals(2, votes.size());
        assertTrue(votes.get(0).isPositive());
        assertFalse(votes.get(1).isPositive());
        assertEquals("m-1", votes.get(1).getMessageId());
        assertEquals(Boolean.FALSE, changes.get(1).getHelpful());
        assertEquals(Boolean.FALSE, chat.getFeedback("m-1"));
    }
}
