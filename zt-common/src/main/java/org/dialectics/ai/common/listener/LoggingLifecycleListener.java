package org.dialectics.ai.common.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 日志记录生命周期监听器
 * <p>
 * 观察者模式：记录会话生命周期事件的日志
 * <p>
 * 职责：
 * 1. 记录关键生命周期事件
 * 2. 记录事件详情和耗时
 * 3. 提供可观测性
 */
@Slf4j
@Component
public class LoggingLifecycleListener implements SessionLifecycleListener {

    @Override
    public void onSessionLifecycleChange(SessionLifecycleEvent event) {
        String sessionId = event.getSessionId();
        SessionLifecycleEvent.EventType eventType = event.getEventType();
        String requestId = event.getRequestId();

        switch (eventType) {
            case CREATED -> log.info("[{}] 会话创建: sessionId={}, userId={}",
                    requestId, sessionId, event.getUserId());
            case STARTED -> log.info("[{}] 会话开始: sessionId={}", requestId, sessionId);
            case ACTIVE -> log.debug("[{}] 会话活跃: sessionId={}", requestId, sessionId);
            case PAUSED -> log.info("[{}] 会话暂停: sessionId={}", requestId, sessionId);
            case RESUMED -> log.info("[{}] 会话恢复: sessionId={}", requestId, sessionId);
            case COMPLETED -> log.info("[{}] 会话完成: sessionId={}, duration={}ms, details={}",
                    requestId, sessionId, event.getDurationMs(), event.getDetails());
            case CANCELLED -> log.warn("[{}] 会话取消: sessionId={}, reason={}",
                    requestId, sessionId, event.getDetails());
            case TIMEOUT -> log.warn("[{}] 会话超时: sessionId={}, duration={}ms",
                    requestId, sessionId, event.getDurationMs());
            case DESTROYED -> log.info("[{}] 会话销毁: sessionId={}", requestId, sessionId);
            case ERROR -> log.error("[{}] 会话错误: sessionId={}, error={}",
                    requestId, sessionId, event.getDetails());
            default -> log.debug("[{}] 未知事件类型: sessionId={}, type={}",
                    requestId, sessionId, eventType);
        }
    }

    @Override
    public String getName() {
        return "LoggingLifecycleListener";
    }

    @Override
    public int getPriority() {
        return 10; // 高优先级，确保日志先记录
    }
}
