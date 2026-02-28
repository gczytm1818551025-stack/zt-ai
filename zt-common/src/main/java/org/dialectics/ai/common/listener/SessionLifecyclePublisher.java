package org.dialectics.ai.common.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 会话生命周期事件发布器
 * <p>
 * 观察者模式：管理监听器并发布生命周期事件
 * <p>
 * 设计原则：
 * 1. 线程安全：使用 CopyOnWriteArrayList 保证并发安全
 * 2. 优先级控制：按优先级顺序调用监听器
 * 3. 容错性：一个监听器异常不影响其他监听器
 * 4. 异步支持：支持同步和异步发布
 */
@Slf4j
@Component
public class SessionLifecyclePublisher {

    /**
     * 监听器列表（线程安全）
     */
    private final List<SessionLifecycleListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * 注册监听器
     *
     * @param listener 监听器
     */
    public void registerListener(SessionLifecycleListener listener) {
        if (listener == null) {
            log.warn("尝试注册 null 监听器");
            return;
        }

        // 检查是否已存在
        for (SessionLifecycleListener existing : listeners) {
            if (existing.getName().equals(listener.getName())) {
                log.warn("监听器已存在，跳过注册: name={}", listener.getName());
                return;
            }
        }

        listeners.add(listener);
        log.info("注册会话生命周期监听器: name={}, priority={}",
                listener.getName(), listener.getPriority());

        // 按优先级排序
        listeners.sort(Comparator.comparingInt(SessionLifecycleListener::getPriority));
    }

    /**
     * 注销监听器
     *
     * @param listenerName 监听器名称
     */
    public void unregisterListener(String listenerName) {
        boolean removed = listeners.removeIf(listener ->
                listener.getName().equals(listenerName));

        if (removed) {
            log.info("注销会话生命周期监听器: name={}", listenerName);
        } else {
            log.warn("监听器不存在，无法注销: name={}", listenerName);
        }
    }

    /**
     * 发布会话生命周期事件（同步）
     *
     * @param event 生命周期事件
     */
    public void publishEvent(SessionLifecycleEvent event) {
        if (event == null) {
            log.warn("尝试发布 null 事件");
            return;
        }

        log.debug("发布会话生命周期事件: sessionId={}, eventType={}",
                event.getSessionId(), event.getEventType());

        // 按优先级顺序通知监听器
        for (SessionLifecycleListener listener : listeners) {
            try {
                // 检查是否启用
                if (!listener.isEnabled()) {
                    log.debug("监听器已禁用，跳过: name={}", listener.getName());
                    continue;
                }

                // 检查是否支持该事件类型
                if (!listener.supports(event.getEventType())) {
                    log.debug("监听器不支持该事件类型，跳过: name={}, eventType={}",
                            listener.getName(), event.getEventType());
                    continue;
                }

                long startTime = System.currentTimeMillis();
                listener.onSessionLifecycleChange(event);
                long duration = System.currentTimeMillis() - startTime;

                log.debug("监听器处理完成: name={}, duration={}ms",
                        listener.getName(), duration);

            } catch (Exception e) {
                log.error("监听器处理异常: name={}, eventType={}, error={}",
                        listener.getName(), event.getEventType(), e.getMessage(), e);
                // 继续处理下一个监听器
            }
        }

        log.debug("会话生命周期事件发布完成: sessionId={}, eventType={}, listeners={}",
                event.getSessionId(), event.getEventType(), listeners.size());
    }

    /**
     * 异步发布会话生命周期事件
     *
     * @param event 生命周期事件
     */
    public void publishEventAsync(SessionLifecycleEvent event) {
        new Thread(() -> publishEvent(event), "session-lifecycle-publisher").start();
    }

    /**
     * 获取监听器数量
     *
     * @return 监听器数量
     */
    public int getListenerCount() {
        return listeners.size();
    }

    /**
     * 获取启用的监听器数量
     *
     * @return 启用的监听器数量
     */
    public int getEnabledListenerCount() {
        return (int) listeners.stream()
                .filter(SessionLifecycleListener::isEnabled)
                .count();
    }

    /**
     * 获取监听器列表
     *
     * @return 监听器列表副本
     */
    public List<SessionLifecycleListener> getListeners() {
        return new ArrayList<>(listeners);
    }

    /**
     * 清空所有监听器
     */
    public void clearListeners() {
        listeners.clear();
        log.info("已清空所有会话生命周期监听器");
    }
}
