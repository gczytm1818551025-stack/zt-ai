package org.dialectics.ai.agent.manager;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.dialectics.ai.agent.domain.vo.ReActEventVo;
import org.dialectics.ai.common.base.StreamManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BiConsumer;

/**
 * ReAct 流管理器
 * <p>
 * 功能：
 * 1. 管理每个会话的 Sinks.Many，支持重连
 * 2. 提供会话状态查询（进行中/已完成）
 * 3. 处理多订阅场景
 * <p>
 * 设计原理：
 * - 使用 ConcurrentHashMap 保证线程安全
 * - 使用 Sinks.many() 支持多订阅
 * - 使用 Redis 状态管理连接，避免 connectionCount 累积问题
 * - Redis TTL 自动清理过期会话
 * <p>
 * 优化点：
 * - 使用 LockSupport.parkNanos() 替代 Thread.sleep()，减少线程阻塞开销
 * - 序列号分配在锁内保证原子性
 * - 保持事件顺序：序列号严格递增
 */
@Slf4j
@Component
public class ReActStreamManager implements StreamManager<String, ReActEventVo> {
    private static final int MAX_RETRY_COUNT = 3;
    private static final long RETRY_INTERVAL_NS = 10_000_000L;
    private static final long SEND_TIMEOUT_MS = 5000;

    private static final Map<String, SessionState> SESSION_STATE_MAP = new ConcurrentHashMap<>();

    @Setter
    @Value("${zt-ai.react.backpressure-buffer-size:256}")
    private int backpressureBufferSize;

    @Setter
    private BiConsumer<String, Long> eventHistoryCleanupCallback;

    private record SessionState(
            Sinks.Many<ReActEventVo> manySink,
            AtomicLong activeSubscribers,
            AtomicBoolean flowCompleted,
            AtomicBoolean releasing,
            AtomicLong lastEventId,
            AtomicLong sequenceNumber,
            Object emitLock,
            AtomicLong lastEmittedSequence
    ) {
    }

    @Override
    public Flux<ReActEventVo> getStream(String sessionId) {
        SessionState existingState = SESSION_STATE_MAP.get(sessionId);

        if (existingState == null || existingState.flowCompleted.get()) {
            if (existingState != null && existingState.flowCompleted.get()) {
                log.info("[{}] 检测到旧会话已完成，创建新的 ReAct 流 Sink", sessionId);
                try {
                    existingState.manySink.tryEmitComplete();
                    log.debug("[{}] 旧 Sink 已关闭", sessionId);
                } catch (Exception e) {
                    log.warn("[{}] 关闭旧 Sink 时出错: {}", sessionId, e.getMessage());
                }
            } else {
                log.info("[{}] 创建新的 ReAct 流 Sink", sessionId);
            }
            Sinks.Many<ReActEventVo> newManySink = Sinks.many().multicast().onBackpressureBuffer(this.backpressureBufferSize);
            SessionState newState = new SessionState(
                    newManySink,
                    new AtomicLong(0),
                    new AtomicBoolean(false),
                    new AtomicBoolean(false),
                    new AtomicLong(0),
                    new AtomicLong(0),
                    new Object(),
                    new AtomicLong(-1)
            );
            SESSION_STATE_MAP.put(sessionId, newState);
        }

        final SessionState state = SESSION_STATE_MAP.get(sessionId);

        return state.manySink.asFlux()
                .doOnSubscribe(subscription -> {
                    long count = state.activeSubscribers.incrementAndGet();
                    log.debug("[{}] 订阅者增加: activeSubscribers={}", sessionId, count);
                })
                .doOnCancel(() -> {
                    long count = state.activeSubscribers.decrementAndGet();
                    log.debug("[{}] 订阅者减少: activeSubscribers={}, flowCompleted={}",
                            sessionId, count, state.flowCompleted.get());

                    if (count == 0 && state.flowCompleted.get() && !state.releasing.get()) {
                        log.info("[{}] 快速清理：无活跃订阅者且流程已完成，立即释放资源", sessionId);
                        release(sessionId);
                    }
                });
    }

    @Override
    public long countActiveSubscriber(String sessionId) {
        SessionState state = SESSION_STATE_MAP.get(sessionId);
        return state != null ? state.activeSubscribers.get() : 0;
    }

    public long getLastEventId(String sessionId) {
        SessionState state = SESSION_STATE_MAP.get(sessionId);
        return state != null ? state.lastEventId.get() : 0;
    }

    public long getLastEmittedSequence(String sessionId) {
        SessionState state = SESSION_STATE_MAP.get(sessionId);
        return state != null ? state.lastEmittedSequence.get() : -1;
    }

    @Override
    public boolean isActive(String sessionId) {
        SessionState state = SESSION_STATE_MAP.get(sessionId);
        if (state == null) {
            log.debug("[{}] 会话状态检查: state=null, active=false", sessionId);
            return false;
        }

        boolean hasSink = state.manySink != null;
        long activeSubscribers = state.activeSubscribers.get();
        boolean active = hasSink && activeSubscribers > 0 && !state.flowCompleted.get() && !state.releasing.get();
        log.debug("[{}] 会话状态检查: hasSink={}, activeSubscribers={}, flowCompleted={}, releasing={}, active={}",
                sessionId, hasSink, activeSubscribers, state.flowCompleted.get(), state.releasing.get(), active);
        return active;
    }

    public void markFlowCompleted(String sessionId) {
        SessionState state = SESSION_STATE_MAP.get(sessionId);
        if (state != null) {
            state.flowCompleted.set(true);
            log.info("[{}] 会话流程已标记为完成", sessionId);

            if (state.activeSubscribers.get() == 0) {
                log.info("[{}] 流程已完成且无活跃订阅者，立即释放资源", sessionId);
                release(sessionId);
            }
        }
    }

    @Override
    public void emit(String key, ReActEventVo latest) {
        tryEmit(key, latest);
    }

    /**
     * 安全发送事件
     * <p>
     * 优化策略：
     * 1. 使用 LockSupport.parkNanos() 替代 Thread.sleep()，减少线程阻塞开销
     * 2. 序列号分配在锁内保证原子性
     * 3. 发送失败不回退序列号，保证序列号严格递增
     * <p>
     * 顺序保证：
     * - 序列号在 synchronized 块内分配，保证严格递增
     * - 事件按序列号顺序推送
     *
     * @param sessionId 会话 ID
     * @param event     事件数据
     * @return true-发送成功，false-Sink 不存在或已取消
     */
    public boolean tryEmit(String sessionId, ReActEventVo event) {
        SessionState state = SESSION_STATE_MAP.get(sessionId);
        if (state == null || state.manySink == null) {
            log.debug("[{}] 会话状态为空，无法发送事件", sessionId);
            return false;
        }

        final long seq;
        final long startTime;
        synchronized (state.emitLock) {
            seq = state.sequenceNumber.incrementAndGet();
            event.setSequenceNumber(seq);
            event.setSessionId(sessionId);
            event.setTimestamp(System.currentTimeMillis());
            startTime = System.currentTimeMillis();
        }

        int retryCount = 0;
        while (retryCount < MAX_RETRY_COUNT) {
            Sinks.EmitResult result = state.manySink.tryEmitNext(event);

            if (result.isSuccess()) {
                synchronized (state.emitLock) {
                    state.lastEventId.incrementAndGet();
                    state.lastEmittedSequence.set(seq);
                }

                log.debug("[{}] 事件已发送: seq={}, type={}, stage={}, cost={}ms",
                        sessionId, seq, event.getType(), event.getStage(),
                        System.currentTimeMillis() - startTime);
                return true;
            }

            if (result == Sinks.EmitResult.FAIL_NON_SERIALIZED) {
                log.warn("[{}] 背压缓冲区满，等待重试: seq={}, retry={}",
                        sessionId, seq, retryCount + 1);
                LockSupport.parkNanos(RETRY_INTERVAL_NS);
            } else if (result == Sinks.EmitResult.FAIL_CANCELLED) {
                log.error("[{}] Sink已取消，无法发送事件: seq={}", sessionId, seq);
                return false;
            } else if (result == Sinks.EmitResult.FAIL_TERMINATED) {
                log.error("[{}] Sink已终止，无法发送事件: seq={}", sessionId, seq);
                return false;
            }

            retryCount++;

            if (System.currentTimeMillis() - startTime > SEND_TIMEOUT_MS) {
                log.error("[{}] 事件发送超时: seq={}, retry={}", sessionId, seq, retryCount);
                return false;
            }
        }

        log.error("[{}] 事件发送失败，重试耗尽: seq={}, lastEmitted={}", sessionId, seq, state.lastEmittedSequence.get());
        return false;
    }

    @Override
    public void release(String sessionId) {
        SessionState state = SESSION_STATE_MAP.remove(sessionId);
        if (state == null) {
            log.debug("[{}] 会话状态不存在，无需释放", sessionId);
            return;
        }

        if (!state.releasing.compareAndSet(false, true)) {
            log.debug("[{}] 已在释放中，跳过", sessionId);
            return;
        }

        try {
            if (state.manySink != null) {
                state.manySink.tryEmitComplete();
                log.info("[{}] 会话 Sink 已释放", sessionId);
            } else {
                log.info("[{}] Sink 为空，无需释放", sessionId);
            }
        } catch (Exception e) {
            log.warn("[{}] 释放 Sink 时出错: {}", sessionId, e.getMessage());
        }
    }

    public String statistics() {
        int activeCount = (int) SESSION_STATE_MAP.values().stream()
                .filter(s -> s.activeSubscribers.get() > 0)
                .count();
        return String.format(
                "ReActStreamManager[activeSessions=%d, totalSinks=%d]",
                activeCount,
                SESSION_STATE_MAP.size()
        );
    }

    @Override
    public int cleanupInactive() {
        int cleanedCount = 0;
        long currentTime = System.currentTimeMillis();

        try {
            var sessionIds = new ArrayList<>(SESSION_STATE_MAP.keySet());

            for (String sessionId : sessionIds) {
                SessionState state = SESSION_STATE_MAP.get(sessionId);
                if (state == null) {
                    continue;
                }

                long activeSubscribers = state.activeSubscribers.get();
                boolean flowCompleted = state.flowCompleted.get();

                boolean shouldCleanup = flowCompleted && activeSubscribers == 0;

                if (shouldCleanup) {
                    log.info("[快速清理] 清理不活跃会话: sessionId={}, activeSubscribers={}, flowCompleted={}",
                            sessionId, activeSubscribers, flowCompleted);
                    release(sessionId);

                    if (eventHistoryCleanupCallback != null) {
                        try {
                            eventHistoryCleanupCallback.accept(sessionId, currentTime);
                        } catch (Exception e) {
                            log.warn("[快速清理] 调用清理回调失败: sessionId={}, error={}", sessionId, e.getMessage());
                        }
                    }

                    cleanedCount++;
                }
            }

            log.info("快速清理完成: 清理数量={}, 当前会话数={}", cleanedCount, SESSION_STATE_MAP.size());

        } catch (Exception e) {
            log.error("快速清理失败", e);
        }

        return cleanedCount;
    }

    public List<String> getAllSessionIds() {
        return new ArrayList<>(SESSION_STATE_MAP.keySet());
    }
}
