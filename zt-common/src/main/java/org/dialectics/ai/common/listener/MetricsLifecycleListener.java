package org.dialectics.ai.common.listener;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * 指标收集生命周期监听器
 * <p>
 * 观察者模式：收集会话生命周期相关指标
 * <p>
 * 职责：
 * 1. 记录各事件类型的计数
 * 2. 记录会话持续时间
 * 3. 提供监控数据
 */
@Slf4j
@Component
public class MetricsLifecycleListener implements SessionLifecycleListener {

    private final MeterRegistry meterRegistry;

    /**
     * 事件计数器（按类型分类）
     */
    private final Map<SessionLifecycleEvent.EventType, Counter> eventCounters;

    @Autowired
    public MetricsLifecycleListener(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.eventCounters = new EnumMap<>(SessionLifecycleEvent.EventType.class);
        initCounters();
    }

    /**
     * 初始化计数器
     */
    private void initCounters() {
        for (SessionLifecycleEvent.EventType eventType : SessionLifecycleEvent.EventType.values()) {
            Counter counter = Counter.builder("session.lifecycle.event")
                    .description("Session lifecycle event count")
                    .tag("event", eventType.name())
                    .tag("listener", "MetricsLifecycleListener")
                    .register(meterRegistry);
            eventCounters.put(eventType, counter);
        }
        log.info("会话生命周期指标监听器初始化完成");
    }

    @Override
    public void onSessionLifecycleChange(SessionLifecycleEvent event) {
        String sessionId = event.getSessionId();
        SessionLifecycleEvent.EventType eventType = event.getEventType();
        String requestId = event.getRequestId();

        log.debug("[{}] 记录会话生命周期指标: sessionId={}, type={}",
                requestId, sessionId, eventType);

        try {
            // 记录事件计数
            Counter counter = eventCounters.get(eventType);
            if (counter != null) {
                counter.increment();
            }

            // 如果有持续时间，记录耗时指标
            Long durationMs = event.getDurationMs();
            if (durationMs != null) {
                io.micrometer.core.instrument.Timer.builder("session.lifecycle.duration")
                        .description("Session lifecycle duration")
                        .tag("event", eventType.name())
                        .tag("listener", "MetricsLifecycleListener")
                        .register(meterRegistry)
                        .record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            }

            log.debug("[{}] 指标记录完成: sessionId={}, type={}, duration={}ms",
                    requestId, sessionId, eventType, durationMs);

        } catch (Exception e) {
            log.error("[{}] 记录指标异常: sessionId={}, type={}, error={}",
                    requestId, sessionId, eventType, e.getMessage(), e);
        }
    }

    @Override
    public String getName() {
        return "MetricsLifecycleListener";
    }

    @Override
    public int getPriority() {
        return 20; // 中等优先级
    }

    /**
     * 获取事件计数
     *
     * @param eventType 事件类型
     * @return 计数
     */
    public double getEventCount(SessionLifecycleEvent.EventType eventType) {
        Counter counter = eventCounters.get(eventType);
        return counter != null ? counter.count() : 0.0;
    }
}
