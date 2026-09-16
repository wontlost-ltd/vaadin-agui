package com.wontlost.agui.spring;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.wontlost.agui.agent.AgUiAgent;
import com.wontlost.agui.model.RunAgentInput;

/**
 * AG-UI HTTP 端点：{@code POST <agui.path>/{agentId}}，请求体为 {@link RunAgentInput}，
 * 响应为 SSE 事件流。
 * <p>
 * agentId 就是 {@link AgUiAgent} 的 Spring bean 名，这样多 agent 不需要任何注册代码：
 * 声明一个 bean 就多一个端点。agent 在工作线程上执行，因此同步实现也不会占用 Servlet 线程。
 */
@RestController
public class AgUiController {

    private static final Logger LOG = LoggerFactory.getLogger(AgUiController.class);

    private final Map<String, AgUiAgent> agents;
    private final AsyncTaskExecutor executor;
    private final AgUiProperties properties;

    public AgUiController(Map<String, AgUiAgent> agents, AsyncTaskExecutor executor, AgUiProperties properties) {
        this.agents = agents;
        this.executor = executor;
        this.properties = properties;
    }

    @PostMapping(path = "${agui.path:/agui}/{agentId}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter run(@PathVariable String agentId, @RequestBody RunAgentInput input) {
        AgUiAgent agent = agents.get(agentId);
        if (agent == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No AgUiAgent bean named '" + agentId + "'");
        }
        AgUiSseEmitter emitter = new AgUiSseEmitter(new SseEmitter(properties.timeout().toMillis()));
        executor.execute(() -> {
            try {
                agent.run(input, emitter);
            } catch (RuntimeException e) {
                LOG.warn("agent '{}' 运行失败 (thread={}, run={})", agentId, input.threadId(), input.runId(), e);
                emitter.error(e);
            }
        });
        return emitter.sseEmitter();
    }
}
