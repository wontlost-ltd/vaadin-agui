package com.wontlost.agui;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.DomEvent;
import com.vaadin.flow.component.EventData;
import com.vaadin.flow.component.HasEnabled;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.HasStyle;
import com.vaadin.flow.component.HasTheme;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.NpmPackage;
import com.vaadin.flow.shared.Registration;
import com.wontlost.agui.model.ContextItem;
import com.wontlost.agui.model.Message;
import com.wontlost.agui.model.ToolDefinition;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * AG-UI 对话组件。
 * <p>
 * 浏览器端直接以 SSE 连接 AG-UI 端点消费事件流并渲染，Flow 服务端不参与逐 token 推送，
 * 因此不需要 {@code @Push}，也不占用 UI 锁。服务端只在运行生命周期节点（开始、结束、出错、
 * 前端工具调用、状态变更）收到事件，并通过 {@link #getMessages()} 得到对话快照。
 * <p>
 * 最小用法：
 * <pre>{@code
 * AgUiChat chat = new AgUiChat("/agui/assistant");
 * chat.setSizeFull();
 * add(chat);
 * }</pre>
 */
@Tag("agui-chat")
@JsModule("./agui-vaadin/agui-chat.ts")
@NpmPackage(value = "lit", version = "^3.3.3")
@NpmPackage(value = "marked", version = "^18.0.13")
@NpmPackage(value = "dompurify", version = "^3.2.6")
public class AgUiChat extends Component implements HasSize, HasStyle, HasTheme, HasEnabled {

    private static final TypeReference<List<Message>> MESSAGE_LIST = new TypeReference<>() {
    };

    private List<Message> messages = List.of();
    /** 服务端镜像的反馈投票（消息 id → 是否有帮助），与前端 feedback 属性保持一致。 */
    private final Map<String, Boolean> feedback = new LinkedHashMap<>();

    public AgUiChat(String agentUrl) {
        setAgentUrl(agentUrl);
        setThreadId(UUID.randomUUID().toString());
        setI18n(new AgUiChatI18n());
        // 客户端每次运行结束都会回传完整对话，服务端只保留镜像，不做二次归约
        addRunFinishedListener(e -> messages = e.getMessages());
        // 用户点击后同步镜像，避免重新挂载时用过期的属性值覆盖浏览器里的投票
        addFeedbackChangeListener(e -> putFeedback(e.getMessageId(), e.getHelpful()));
    }

    // ---- 连接与会话 ---------------------------------------------------

    public void setAgentUrl(String agentUrl) {
        getElement().setProperty("agentUrl", Objects.requireNonNull(agentUrl, "agentUrl"));
    }

    public String getAgentUrl() {
        return getElement().getProperty("agentUrl");
    }

    /** 对话线程标识，服务端 agent 用它持久化状态；默认随机 UUID。 */
    public void setThreadId(String threadId) {
        getElement().setProperty("threadId", Objects.requireNonNull(threadId, "threadId"));
    }

    public String getThreadId() {
        return getElement().getProperty("threadId");
    }

    /** 请求 AG-UI 端点时附带的额外 HTTP 头，例如租户标识或令牌。 */
    public void setRequestHeaders(Map<String, String> headers) {
        getElement().setPropertyMap("headers", headers);
    }

    // ---- 对话内容 -----------------------------------------------------

    /** 用已有历史初始化对话，例如从存储恢复。 */
    public void setMessages(List<Message> messages) {
        this.messages = List.copyOf(messages);
        getElement().setPropertyList("messages", this.messages);
    }

    /** 最近一次运行结束时客户端回传的对话快照。 */
    public List<Message> getMessages() {
        return messages;
    }

    /** 对话为空时展示的开场建议，点击即发送。 */
    public void setSuggestions(String... suggestions) {
        getElement().setPropertyList("suggestions", Arrays.asList(suggestions));
    }

    /** 每次运行附带给 agent 的上下文，例如租户、locale、页面状态。 */
    public void setContext(List<ContextItem> context) {
        getElement().setPropertyList("context", List.copyOf(context));
    }

    /**
     * 声明由 UI 一侧执行的工具。agent 调用这些工具时组件触发 {@link ToolCallEvent}，
     * 监听器完成后调用 {@link #submitToolResult(String, String)}，组件自动续跑。
     */
    public void setFrontendTools(List<ToolDefinition> tools) {
        getElement().setPropertyList("tools", List.copyOf(tools));
    }

    /** 透传给 agent 的自定义属性。 */
    public void setForwardedProps(ObjectNode props) {
        getElement().setPropertyJson("forwardedProps", props);
    }

    /** 初始共享状态。运行中 agent 通过 STATE_SNAPSHOT / STATE_DELTA 更新它。 */
    public void setState(ObjectNode state) {
        getElement().setPropertyJson("agentState", state);
    }

    /** 设置每个代码块工具栏上显示的动作（默认只有复制）。 */
    public void setCodeActions(AgUiCodeAction... actions) {
        getElement().setPropertyList("codeActions",
                Arrays.stream(actions).map(AgUiCodeAction::getValue).toList());
    }

    /**
     * 设置某条助手消息上的反馈投票，例如页面重载后恢复用户之前的选择。
     *
     * @param messageId 消息 id
     * @param helpful   {@code true} 有帮助，{@code false} 没帮助，{@code null} 清除投票
     */
    public void setFeedback(String messageId, Boolean helpful) {
        putFeedback(Objects.requireNonNull(messageId, "messageId"), helpful);
    }

    /** 返回某条消息当前的反馈投票；未投票时为 {@code null}。 */
    public Boolean getFeedback(String messageId) {
        return feedback.get(messageId);
    }

    private void putFeedback(String messageId, Boolean helpful) {
        if (helpful == null) {
            feedback.remove(messageId);
        } else {
            feedback.put(messageId, helpful);
        }
        Map<String, String> wire = new LinkedHashMap<>();
        feedback.forEach((id, up) -> wire.put(id, up ? "up" : "down"));
        getElement().setPropertyMap("feedback", wire);
    }

    public void setI18n(AgUiChatI18n i18n) {
        getElement().setPropertyBean("i18n", Objects.requireNonNull(i18n, "i18n"));
    }

    public void addThemeVariants(AgUiChatVariant... variants) {
        getThemeNames().addAll(Arrays.stream(variants).map(AgUiChatVariant::getVariantName).toList());
    }

    public void removeThemeVariants(AgUiChatVariant... variants) {
        getThemeNames().removeAll(Arrays.stream(variants).map(AgUiChatVariant::getVariantName).toList());
    }

    // ---- 服务端驱动的动作 ---------------------------------------------

    /** 以用户身份发送一条消息并开始运行，等价于用户在输入框提交。 */
    public void prompt(String text) {
        getElement().callJsFunction("send", text);
    }

    /** 中断当前运行，已生成的部分内容保留。 */
    public void stop() {
        getElement().callJsFunction("stop");
    }

    /** 丢弃最后一轮 assistant 输出并重跑。 */
    public void regenerate() {
        getElement().callJsFunction("regenerate");
    }

    /** 清空对话并开启新的 threadId。 */
    public void clear() {
        setThreadId(UUID.randomUUID().toString());
        messages = List.of();
        feedback.clear();
        getElement().setPropertyMap("feedback", Map.of());
        getElement().callJsFunction("clear");
    }

    /** 回传前端工具的执行结果；所有待处理工具都有结果后组件自动续跑。 */
    public void submitToolResult(String toolCallId, String content) {
        getElement().callJsFunction("submitToolResult", toolCallId, content);
    }

    // ---- 事件 ---------------------------------------------------------

    public Registration addRunStartedListener(ComponentEventListener<RunStartedEvent> listener) {
        return addListener(RunStartedEvent.class, listener);
    }

    public Registration addRunFinishedListener(ComponentEventListener<RunFinishedEvent> listener) {
        return addListener(RunFinishedEvent.class, listener);
    }

    public Registration addRunErrorListener(ComponentEventListener<RunErrorEvent> listener) {
        return addListener(RunErrorEvent.class, listener);
    }

    public Registration addToolCallListener(ComponentEventListener<ToolCallEvent> listener) {
        return addListener(ToolCallEvent.class, listener);
    }

    public Registration addStateChangedListener(ComponentEventListener<StateChangedEvent> listener) {
        return addListener(StateChangedEvent.class, listener);
    }

    public Registration addMessageSentListener(ComponentEventListener<MessageSentEvent> listener) {
        return addListener(MessageSentEvent.class, listener);
    }

    /** 只通知有效投票（含切换）；撤销投票不触发，需要时用 {@link #addFeedbackChangeListener}。 */
    public Registration addFeedbackListener(ComponentEventListener<FeedbackEvent> listener) {
        return addListener(FeedbackEvent.class, listener);
    }

    /** 投票、切换与撤销都会通知；只关心有效投票时用 {@link #addFeedbackListener}。 */
    public Registration addFeedbackChangeListener(ComponentEventListener<FeedbackChangeEvent> listener) {
        return addListener(FeedbackChangeEvent.class, listener);
    }

    public Registration addCodeActionListener(ComponentEventListener<CodeActionEvent> listener) {
        return addListener(CodeActionEvent.class, listener);
    }

    @DomEvent("agui-run-started")
    public static class RunStartedEvent extends ComponentEvent<AgUiChat> {
        private final String threadId;
        private final String runId;

        public RunStartedEvent(AgUiChat source, boolean fromClient,
                @EventData("event.detail.threadId") String threadId,
                @EventData("event.detail.runId") String runId) {
            super(source, fromClient);
            this.threadId = threadId;
            this.runId = runId;
        }

        public String getThreadId() {
            return threadId;
        }

        public String getRunId() {
            return runId;
        }
    }

    @DomEvent("agui-run-finished")
    public static class RunFinishedEvent extends ComponentEvent<AgUiChat> {
        private final String threadId;
        private final String runId;
        private final boolean cancelled;
        private final List<Message> messages;

        public RunFinishedEvent(AgUiChat source, boolean fromClient,
                @EventData("event.detail.threadId") String threadId,
                @EventData("event.detail.runId") String runId,
                @EventData("event.detail.cancelled") boolean cancelled,
                @EventData("event.detail.messages") ArrayNode messages) {
            super(source, fromClient);
            this.threadId = threadId;
            this.runId = runId;
            this.cancelled = cancelled;
            this.messages = AgUiJson.mapper().convertValue(messages, MESSAGE_LIST);
        }

        public String getThreadId() {
            return threadId;
        }

        public String getRunId() {
            return runId;
        }

        /** 用户点击停止导致的结束为 true。 */
        public boolean isCancelled() {
            return cancelled;
        }

        public List<Message> getMessages() {
            return messages;
        }
    }

    @DomEvent("agui-run-error")
    public static class RunErrorEvent extends ComponentEvent<AgUiChat> {
        private final String message;
        private final String code;

        public RunErrorEvent(AgUiChat source, boolean fromClient,
                @EventData("event.detail.message") String message,
                @EventData("event.detail.code") String code) {
            super(source, fromClient);
            this.message = message;
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public String getCode() {
            return code;
        }
    }

    /** agent 调用了一个由 {@link #setFrontendTools(List)} 声明的工具。 */
    @DomEvent("agui-tool-call")
    public static class ToolCallEvent extends ComponentEvent<AgUiChat> {
        private final String toolCallId;
        private final String toolName;
        private final String arguments;

        public ToolCallEvent(AgUiChat source, boolean fromClient,
                @EventData("event.detail.toolCallId") String toolCallId,
                @EventData("event.detail.name") String toolName,
                @EventData("event.detail.arguments") String arguments) {
            super(source, fromClient);
            this.toolCallId = toolCallId;
            this.toolName = toolName;
            this.arguments = arguments;
        }

        public String getToolCallId() {
            return toolCallId;
        }

        public String getToolName() {
            return toolName;
        }

        /** 原始 JSON 字符串参数。 */
        public String getArguments() {
            return arguments;
        }

        public ObjectNode getArgumentsAsJson() {
            return (ObjectNode) AgUiJson.mapper().readTree(arguments);
        }
    }

    @DomEvent("agui-state-changed")
    public static class StateChangedEvent extends ComponentEvent<AgUiChat> {
        private final ObjectNode state;

        public StateChangedEvent(AgUiChat source, boolean fromClient,
                @EventData("event.detail.state") ObjectNode state) {
            super(source, fromClient);
            this.state = state;
        }

        public ObjectNode getState() {
            return state;
        }
    }

    @DomEvent("agui-message-sent")
    public static class MessageSentEvent extends ComponentEvent<AgUiChat> {
        private final String text;

        public MessageSentEvent(AgUiChat source, boolean fromClient,
                @EventData("event.detail.text") String text) {
            super(source, fromClient);
            this.text = text;
        }

        public String getText() {
            return text;
        }
    }

    /**
     * 用户投出「有帮助 / 没帮助」时触发，包括从一票切换到另一票；撤销投票不触发。
     * 需要感知撤销的宿主请用 {@link #addFeedbackChangeListener}。
     */
    @DomEvent(value = "agui-feedback", filter = "event.detail.value != null")
    public static class FeedbackEvent extends ComponentEvent<AgUiChat> {
        private final String messageId;
        private final boolean positive;

        public FeedbackEvent(AgUiChat source, boolean fromClient,
                @EventData("event.detail.messageId") String messageId,
                @EventData("event.detail.positive") boolean positive) {
            super(source, fromClient);
            this.messageId = messageId;
            this.positive = positive;
        }

        public String getMessageId() {
            return messageId;
        }

        public boolean isPositive() {
            return positive;
        }
    }

    /** 某条消息的反馈投票发生任何变化时触发：投票、切换或撤销。 */
    @DomEvent("agui-feedback")
    public static class FeedbackChangeEvent extends ComponentEvent<AgUiChat> {
        private final String messageId;
        private final Boolean helpful;

        public FeedbackChangeEvent(AgUiChat source, boolean fromClient,
                @EventData("event.detail.messageId") String messageId,
                @EventData("event.detail.value") String value) {
            this(source, fromClient, messageId, value == null ? null : "up".equals(value));
        }

        public FeedbackChangeEvent(AgUiChat source, boolean fromClient, String messageId, Boolean helpful) {
            super(source, fromClient);
            this.messageId = messageId;
            this.helpful = helpful;
        }

        public String getMessageId() {
            return messageId;
        }

        /** 变化后的投票：{@code TRUE} 有帮助，{@code FALSE} 没帮助，{@code null} 已撤销。 */
        public Boolean getHelpful() {
            return helpful;
        }

        public boolean isCleared() {
            return helpful == null;
        }
    }

    /** 用户点击代码块上的「插入/替换/复制」时触发；宿主据此把代码写回编辑器等。 */
    @DomEvent("agui-code-action")
    public static class CodeActionEvent extends ComponentEvent<AgUiChat> {
        private final String action;
        private final String code;
        private final String language;
        private final String messageId;

        public CodeActionEvent(AgUiChat source, boolean fromClient,
                @EventData("event.detail.action") String action,
                @EventData("event.detail.code") String code,
                @EventData("event.detail.language") String language,
                @EventData("event.detail.messageId") String messageId) {
            super(source, fromClient);
            this.action = action;
            this.code = code;
            this.language = language;
            this.messageId = messageId;
        }

        public String getAction() {
            return action;
        }

        public String getCode() {
            return code;
        }

        public String getLanguage() {
            return language;
        }

        public String getMessageId() {
            return messageId;
        }
    }
}
