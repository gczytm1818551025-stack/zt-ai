package org.dialectics.ai.agent;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * 线程本地Agent执行上下文
 */
@Slf4j
public class AgentExecutionContext {
    @Getter
    private final AgentExecutionContext parent;
    private final Map<Enum<?>, Object> localAttributes = new HashMap<>();
    private final Map<Enum<?>, Object> globalAttributes;

    public AgentExecutionContext() {
        this.parent = null;
        this.globalAttributes = new HashMap<>();
    }

    public AgentExecutionContext(AgentExecutionContext parent) {
        this.parent = parent;
        this.globalAttributes = parent.globalAttributes;
    }

    public AgentExecutionContext createChild() {
        return new AgentExecutionContext(this);
    }

    public void set(Enum<?> key, Object value) {
        localAttributes.put(key, value);
    }

    public void set(Map<Enum<?>, Object> map) {
        localAttributes.putAll(map);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Enum<?> key) {
        return (T) localAttributes.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T computeIfAbsent(Enum<?> key, T defaultValue) {
        Object value = localAttributes.get(key);
        return value != null ? (T) value : defaultValue;
    }

    public void setGlobal(Enum<?> key, Object value) {
        globalAttributes.put(key, value);
    }

    public void setGlobal(Map<Enum<?>, Object> map) {
        globalAttributes.putAll(map);
    }

    @SuppressWarnings("unchecked")
    public <T> T getGlobal(Enum<?> key) {
        return (T) globalAttributes.get(key);
    }

}
