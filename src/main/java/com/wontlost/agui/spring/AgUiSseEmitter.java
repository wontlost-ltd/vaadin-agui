package com.wontlost.agui.spring;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.wontlost.agui.agent.AgUiEmitter;
import com.wontlost.agui.event.AgUiEvent;
import com.wontlost.agui.sse.AgUiSse;

/**
 * 把 Spring MVC 的 {@link SseEmitter} 适配成 {@link AgUiEmitter}。
 * <p>
 * 断连、超时与写入失败统一归为"已取消"：这三种情况下客户端都已经收不到后续事件，
 * 对 agent 而言没有区别。取消后的 {@code emit} 直接丢弃，不抛异常，这样 agent 代码
 * 不需要在每次发事件时都包 try/catch。
 */
public final class AgUiSseEmitter implements AgUiEmitter {

    private static final Logger LOG = LoggerFactory.getLogger(AgUiSseEmitter.class);

    private final SseEmitter sse;
    private final AtomicBoolean done = new AtomicBoolean();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final List<Runnable> cancelCallbacks = new ArrayList<>();

    public AgUiSseEmitter(SseEmitter sse) {
        this.sse = sse;
        sse.onTimeout(this::cancel);
        sse.onError(t -> cancel());
        // onCompletion 也会在我们自己 complete() 之后触发；那时 done 已为 true，cancel 不做事
        sse.onCompletion(() -> {
            if (!done.get()) {
                cancel();
            }
        });
    }

    public SseEmitter sseEmitter() {
        return sse;
    }

    @Override
    public void emit(AgUiEvent event) {
        if (done.get() || cancelled.get()) {
            return;
        }
        try {
            sse.send(SseEmitter.event().data(AgUiSse.json(event), MediaType.TEXT_PLAIN));
        } catch (IOException | IllegalStateException e) {
            LOG.debug("客户端已断开，停止推送: {}", e.getMessage());
            cancel();
        }
    }

    @Override
    public void complete() {
        if (done.compareAndSet(false, true)) {
            sse.complete();
        }
    }

    @Override
    public void error(Throwable cause) {
        if (done.get()) {
            return;
        }
        emit(new AgUiEvent.RunError(cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage()));
        complete();
    }

    @Override
    public boolean isCancelled() {
        return cancelled.get();
    }

    @Override
    public void onCancel(Runnable callback) {
        boolean runNow;
        synchronized (cancelCallbacks) {
            runNow = cancelled.get();
            if (!runNow) {
                cancelCallbacks.add(callback);
            }
        }
        if (runNow) {
            callback.run();
        }
    }

    private void cancel() {
        if (!cancelled.compareAndSet(false, true)) {
            return;
        }
        done.set(true);
        List<Runnable> callbacks;
        synchronized (cancelCallbacks) {
            callbacks = List.copyOf(cancelCallbacks);
            cancelCallbacks.clear();
        }
        callbacks.forEach(Runnable::run);
    }
}
