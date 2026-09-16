package com.wontlost.agui.agent;

import com.wontlost.agui.model.RunAgentInput;

/**
 * 服务端 agent 的唯一契约：接收一次运行输入，向 {@link AgUiEmitter} 发出 AG-UI 事件。
 * <p>
 * 与 AI 框架无关。实现可以同步阻塞直到运行结束（Spring 端点保证在工作线程上调用），
 * 也可以立即返回并在别的线程上继续发事件；无论哪种，都必须以
 * {@link AgUiEmitter#complete()} 或 {@link AgUiEmitter#error(Throwable)} 收尾，
 * 否则客户端连接会一直挂到超时。
 * <p>
 * 输入里的 {@code messages} 是前端持有的完整对话，agent 不需要额外的会话记忆。
 * 需要跨运行保存状态时按 {@code threadId} 自行持久化。
 */
@FunctionalInterface
public interface AgUiAgent {

    void run(RunAgentInput input, AgUiEmitter emitter);
}
