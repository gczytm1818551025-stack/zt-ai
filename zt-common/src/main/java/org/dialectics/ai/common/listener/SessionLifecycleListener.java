package org.dialectics.ai.common.listener;

/**
 * 会话生命周期监听器接口
 * <p>
 * 观察者模式：监听会话生命周期事件
 * <p>
 * 设计原则：
 * 1. 松耦合：监听器独立于被观察对象
 * 2. 可扩展：可以动态添加和移除监听器
 * 3. 单一职责：每个监听器关注特定的生命周期事件
 */
public interface SessionLifecycleListener {

    /**
     * 处理会话生命周期事件
     *
     * @param event 生命周期事件
     */
    void onSessionLifecycleChange(SessionLifecycleEvent event);

    /**
     * 获取监听器名称
     *
     * @return 监听器名称
     */
    String getName();

    /**
     * 获取监听器优先级（数字越小优先级越高）
     *
     * @return 优先级
     */
    default int getPriority() {
        return 100;
    }

    /**
     * 是否启用
     *
     * @return true-启用，false-禁用
     */
    default boolean isEnabled() {
        return true;
    }

    /**
     * 是否支持指定事件类型
     *
     * @param eventType 事件类型
     * @return true-支持，false-不支持
     */
    default boolean supports(SessionLifecycleEvent.EventType eventType) {
        return true;
    }
}
