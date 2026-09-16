package com.wontlost.agui.agent;

import com.wontlost.agui.event.AgUiEvent;

/**
 * agent 向客户端发事件的出口。传输细节（SSE、测试用的内存收集器）由实现决定。
 * <p>
 * 线程安全：{@link #emit(AgUiEvent)} 可从任意线程调用。客户端断开后
 * {@link #isCancelled()} 为 true，此后的 {@code emit} 被静默丢弃；长时间运行的 agent
 * 应定期检查它或注册 {@link #onCancel(Runnable)} 以尽早停止上游工作。
 */
public interface AgUiEmitter {

    void emit(AgUiEvent event);

    /** 运行正常结束。幂等，重复调用无副作用。 */
    void complete();

    /**
     * 运行异常结束。实现会先发出 {@code RUN_ERROR} 再关闭流，因此调用方不必自己发错误事件。
     * 幂等，且在 {@link #complete()} 之后调用无效。
     */
    void error(Throwable cause);

    boolean isCancelled();

    /** 注册取消回调；如果注册时已取消，回调立即在当前线程执行。 */
    void onCancel(Runnable callback);
}
