package com.wontlost.agui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.ComponentUtil;

class AgUiChatCodeActionTest {

    @Test
    void setCodeActionsWritesPropertyList() {
        AgUiChat chat = new AgUiChat("/agui/x");
        chat.setCodeActions(AgUiCodeAction.COPY, AgUiCodeAction.INSERT, AgUiCodeAction.REPLACE);
        String json = chat.getElement().getPropertyRaw("codeActions").toString();
        assertTrue(json.contains("copy") && json.contains("insert") && json.contains("replace"), json);
    }

    @Test
    void codeActionEventCarriesDetail() {
        AgUiChat chat = new AgUiChat("/agui/x");
        List<AgUiChat.CodeActionEvent> seen = new ArrayList<>();
        chat.addCodeActionListener(seen::add);
        ComponentUtil.fireEvent(chat, new AgUiChat.CodeActionEvent(chat, true, "insert", "Rule a.", "aster", "m-1"));
        assertEquals(1, seen.size());
        AgUiChat.CodeActionEvent e = seen.get(0);
        assertEquals("insert", e.getAction());
        assertEquals("Rule a.", e.getCode());
        assertEquals("aster", e.getLanguage());
        assertEquals("m-1", e.getMessageId());
    }

    @Test
    void i18nHasCodeActionLabels() {
        AgUiChatI18n i18n = new AgUiChatI18n();
        assertEquals("Copy code", i18n.getCopyCode());
        assertEquals("Insert", i18n.getInsertCode());
        assertEquals("Replace", i18n.getReplaceCode());
    }
}
