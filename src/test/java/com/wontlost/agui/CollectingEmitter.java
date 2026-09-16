package com.wontlost.agui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.wontlost.agui.agent.AgUiEmitter;
import com.wontlost.agui.event.AgUiEvent;

/** 测试用的内存 emitter：记录事件序列与终态。 */
public final class CollectingEmitter implements AgUiEmitter {

    private final List<AgUiEvent> events = Collections.synchronizedList(new ArrayList<>());
    private final List<Runnable> cancelCallbacks = new ArrayList<>();
    private volatile boolean completed;
    private volatile boolean cancelled;
    private volatile Throwable error;

    @Override
    public void emit(AgUiEvent event) {
        events.add(event);
    }

    @Override
    public void complete() {
        completed = true;
    }

    @Override
    public void error(Throwable cause) {
        error = cause;
        emit(new AgUiEvent.RunError(String.valueOf(cause.getMessage())));
        completed = true;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void onCancel(Runnable callback) {
        cancelCallbacks.add(callback);
    }

    public void cancel() {
        cancelled = true;
        cancelCallbacks.forEach(Runnable::run);
    }

    public List<AgUiEvent> events() {
        return List.copyOf(events);
    }

    public List<String> types() {
        return events().stream().map(e -> e.getClass().getSimpleName()).toList();
    }

    public boolean isCompleted() {
        return completed;
    }

    public Throwable error() {
        return error;
    }
}
