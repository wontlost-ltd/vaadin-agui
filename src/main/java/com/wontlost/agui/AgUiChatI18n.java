package com.wontlost.agui;

import java.io.Serializable;

/**
 * 组件文案。遵循官方组件的 I18n 约定：链式 setter，整对象通过属性同步到客户端。
 * 默认值为英文，应用按 locale 替换。
 */
public class AgUiChatI18n implements Serializable {

    private String placeholder = "Send a message…";
    private String send = "Send";
    private String stop = "Stop";
    private String regenerate = "Regenerate";
    private String copy = "Copy";
    private String copied = "Copied";
    private String thinking = "Thinking";
    private String toolCall = "Tool call";
    private String toolResult = "Result";
    private String assistantName = "Assistant";
    private String userName = "You";
    private String errorPrefix = "Something went wrong";
    private String cancelled = "Stopped";
    private String helpfulYes = "Helpful";
    private String helpfulNo = "Not helpful";

    public String getPlaceholder() {
        return placeholder;
    }

    public AgUiChatI18n setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
        return this;
    }

    public String getSend() {
        return send;
    }

    public AgUiChatI18n setSend(String send) {
        this.send = send;
        return this;
    }

    public String getStop() {
        return stop;
    }

    public AgUiChatI18n setStop(String stop) {
        this.stop = stop;
        return this;
    }

    public String getRegenerate() {
        return regenerate;
    }

    public AgUiChatI18n setRegenerate(String regenerate) {
        this.regenerate = regenerate;
        return this;
    }

    public String getCopy() {
        return copy;
    }

    public AgUiChatI18n setCopy(String copy) {
        this.copy = copy;
        return this;
    }

    public String getCopied() {
        return copied;
    }

    public AgUiChatI18n setCopied(String copied) {
        this.copied = copied;
        return this;
    }

    public String getThinking() {
        return thinking;
    }

    public AgUiChatI18n setThinking(String thinking) {
        this.thinking = thinking;
        return this;
    }

    public String getToolCall() {
        return toolCall;
    }

    public AgUiChatI18n setToolCall(String toolCall) {
        this.toolCall = toolCall;
        return this;
    }

    public String getToolResult() {
        return toolResult;
    }

    public AgUiChatI18n setToolResult(String toolResult) {
        this.toolResult = toolResult;
        return this;
    }

    public String getAssistantName() {
        return assistantName;
    }

    public AgUiChatI18n setAssistantName(String assistantName) {
        this.assistantName = assistantName;
        return this;
    }

    public String getUserName() {
        return userName;
    }

    public AgUiChatI18n setUserName(String userName) {
        this.userName = userName;
        return this;
    }

    public String getErrorPrefix() {
        return errorPrefix;
    }

    public AgUiChatI18n setErrorPrefix(String errorPrefix) {
        this.errorPrefix = errorPrefix;
        return this;
    }

    public String getCancelled() {
        return cancelled;
    }

    public AgUiChatI18n setCancelled(String cancelled) {
        this.cancelled = cancelled;
        return this;
    }

    public String getHelpfulYes() {
        return helpfulYes;
    }

    public AgUiChatI18n setHelpfulYes(String helpfulYes) {
        this.helpfulYes = helpfulYes;
        return this;
    }

    public String getHelpfulNo() {
        return helpfulNo;
    }

    public AgUiChatI18n setHelpfulNo(String helpfulNo) {
        this.helpfulNo = helpfulNo;
        return this;
    }
}
