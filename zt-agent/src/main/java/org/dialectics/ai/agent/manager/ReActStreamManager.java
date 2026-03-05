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
 */
@Slf4j
@Component
public class ReActStreamManager implements StreamManager<String, ReActEventVo> {
    /// 最大重试次数
    private static final int MAX_RETRY_COUNT = 3;
    /// 重试间隔（毫秒）
    private static final long RETRY_INTERVAL_MS = 10;
    /// 发送超时（毫秒）
    private static final long SEND_TIMEOUT_MS = 5000;
    /**
     * 会话状态映射（内存存储）
     * Key: sessionId, Value: SessionState
     */
    private static final Map<String, SessionState> SESSION_STATE_MAP = new ConcurrentHashMap<>();

    /**
     * 背压缓冲区大小
     */
    @Setter
    @Value("${zt-ai.react.backpressure-buffer-size:256}")
    private int backpressureBufferSize;
    /**
     * 事件历史清理回调
     */
    @Setter
    private BiConsumer<String, Long> eventHistoryCleanupCallback;

    /**
     * 会话状态封装
     */
    private record SessionState(
            Sinks.Many<ReActEventVo> manySink,
            AtomicLong activeSubscribers,
            AtomicBoolean flowCompleted,  // 流程是否已完成标志
            AtomicBoolean releasing,      // 释放中标志（防止重复释放）
            AtomicLong lastEventId,       // 最后发送的事件ID（用于重连时部分回放）
            AtomicLong sequenceNumber,     // 序列号生成器
            Object emitLock,              // 事件推送锁 - 保证串行化
            AtomicLong lastEmittedSequence // 最后成功推送的序列号
    ) {
    }

    /**
     * 获取或创建会话的 Flux（带活跃订阅者计数）
     * 返回的 Flux 会在订阅/取消订阅时自动更新活跃订阅者计数
     *
     * @param sessionId 会话 ID
     * @return Flux 实例，如果不存在返回 null
     */
    @Override
    public Flux<ReActEventVo> getStream(String sessionId) {
        SessionState state = SESSION_STATE_MAP.computeIfAbsent(sessionId, id -> {
            log.info("[{}] 创建新的 ReAct 流 Sink", sessionId);
            // 创建支持多订阅的 Sinks.Many，使用配置的背压缓冲区大小
            Sinks.Many<ReActEventVo> newManySink = Sinks.many().multicast().onBackpressureBuffer(this.backpressureBufferSize);
            // 活跃订阅者计数
            AtomicLong activeSubscribers = new AtomicLong(0);
            // 流程完成标志
            AtomicBoolean flowCompleted = new AtomicBoolean(false);

            return new SessionState(
                    newManySink,
                    activeSubscribers,
                    flowCompleted,
                    new AtomicBoolean(false),
                    new AtomicLong(0),
                    new AtomicLong(0),
                    new Object(),
                    new AtomicLong(-1)
            );
        });

        return state.manySink.asFlux()
                .doOnSubscribe(subscription -> {
                    long count = state.activeSubscribers.incrementAndGet();
                    log.debug("[{}] 订阅者增加: activeSubscribers={}", sessionId, count);
                })
                .doOnCancel(() -> {
                    long count = state.activeSubscribers.decrementAndGet();
                    log.debug("[{}] 订阅者减少: activeSubscribers={}, flowCompleted={}",
                            sessionId, count, state.flowCompleted.get());

                    // 只在无活跃订阅者且流程已完成且未在释放中时才释放
                    if (count == 0 && state.flowCompleted.get() && !state.releasing.get()) {
                        log.info("[{}] 快速清理：无活跃订阅者且流程已完成，立即释放资源", sessionId);
                        release(sessionId);
                    }
                });
    }

    /**
     * 获取活跃订阅者计数
     *
     * @param sessionId 会话 ID
     * @return 活跃订阅者数
     */
    @Override
    public long countActiveSubscriber(String sessionId) {
        SessionState state = SESSION_STATE_MAP.get(sessionId);
        return state != null ? state.activeSubscribers.get() : 0;
    }

    /**
     * 获取最后事件ID
     *
     * @param sessionId 会话 ID
     * @return 最后事件ID
     */
    public long getLastEventId(String sessionId) {
        SessionState state = SESSION_STATE_MAP.get(sessionId);
        return state != null ? state.lastEventId.get() : 0;
    }

    /**
     * 获取最后成功推送的序列号
     *
     * @param sessionId 会话 ID
     * @return 最后成功推送的序列号，不存在返回 -1
     */
    public long getLastEmittedSequence(String sessionId) {
        SessionState state = SESSION_STATE_MAP.get(sessionId);
        return state != null ? state.lastEmittedSequence.get() : -1;
    }

    /**
     * 检查会话是否活跃
     * <p>活跃 = 活跃标志存在 + sink订阅者数大于0 + 当前会话流程未完成 + 当前会话未在释放中
     *
     * @param sessionId 会话 ID
     * @return true-正在进行，false-已完成或不存在
     */
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

    /**
     * 标记会话流程已完成
     *
     * @param sessionId 会话 ID
     */
    public void markFlowCompleted(String sessionId) {
        SessionState state = SESSION_STATE_MAP.get(sessionId);
        if (state != null) {
            state.flowCompleted.set(true);
            log.info("[{}] 会话流程已标记为完成", sessionId);

            // 标记完成后，检查是否可以立即清理（无活跃订阅者）
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
     * 安全发送事件（用于内部使用）
     * <p>
     * 核心修复：
     * 1. 使用锁保证串行化推送，避免并发导致乱序
     * 2. 失败时自动重试，确保事件一定发送
     * 3. 分配序列号用于前端去重和排序
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

        // 分配序列号
        long seq = state.sequenceNumber.incrementAndGet();
        event.setSequenceNumber(seq);
        event.setSessionId(sessionId);
        event.setTimestamp(System.currentTimeMillis());

        // 使用锁保证串行化推送
        synchronized (state.emitLock) {
            int retryCount = 0;
            long startTime = System.currentTimeMillis();
            // 自旋重试
            while (retryCount < MAX_RETRY_COUNT) {
                try {
                    Sinks.EmitResult result = state.manySink.tryEmitNext(event);

                    if (result.isSuccess()) {
                        // 推送成功
                        state.lastEventId.incrementAndGet();
                        state.lastEmittedSequence.set(seq);

                        log.debug("[{}] 事件已发送: seq={}, type={}, stage={}, cost={}ms",
                                sessionId, seq, event.getType(), event.getStage(),
                                System.currentTimeMillis() - startTime);
                        return true;
                    }

                    // 推送失败，分析原因并重试
                    if (result == Sinks.EmitResult.FAIL_NON_SERIALIZED) {
                        // 背压缓冲区满，等待后重试
                        log.warn("[{}] 背压缓冲区满，等待重试: seq={}, retry={}",
                                sessionId, seq, retryCount + 1);
                        Thread.sleep(RETRY_INTERVAL_MS);
                    } else if (result == Sinks.EmitResult.FAIL_CANCELLED) {
                        // Sink 已取消，无法重试
                        log.error("[{}] Sink已取消，无法发送事件: seq={}", sessionId, seq);
                        state.sequenceNumber.decrementAndGet();  // 回退序列号
                        return false;
                    } else if (result == Sinks.EmitResult.FAIL_TERMINATED) {
                        // Sink 已终止，无法重试
                        log.error("[{}] Sink已终止，无法发送事件: seq={}", sessionId, seq);
                        state.sequenceNumber.decrementAndGet();
                        return false;
                    }

                    retryCount++;

                    // 检查超时
                    if (System.currentTimeMillis() - startTime > SEND_TIMEOUT_MS) {
                        log.error("[{}] 事件发送超时: seq={}, retry={}", sessionId, seq, retryCount);
                        state.sequenceNumber.decrementAndGet();
                        return false;
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("[{}] 事件发送被中断: seq={}", sessionId, seq);
                    state.sequenceNumber.decrementAndGet();
                    return false;
                } catch (Exception e) {
                    log.error("[{}] 事件发送异常: seq={}, retry={}", sessionId, seq, retryCount, e);
                    retryCount++;
                }
            }

            // 重试次数耗尽
            log.error("[{}] 事件发送失败，重试耗尽: seq={}, lastEmitted={}", sessionId, seq, state.lastEmittedSequence.get());
            state.sequenceNumber.decrementAndGet();
            return false;
        }
    }

    /**
     * 释放会话 Sink
     *
     * @param sessionId 会话 ID
     */
    @Override
    public void release(String sessionId) {
        // 先获取并移除会话状态
        SessionState state = SESSION_STATE_MAP.remove(sessionId);
        if (state == null) {
            log.debug("[{}] 会话状态不存在，无需释放", sessionId);
            return;
        }

        // 设置释放中标志
        if (!state.releasing.compareAndSet(false, true)) {
            log.debug("[{}] 已在释放中，跳过", sessionId);
            return;
        }

        // 释放Sink
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

    /**
     * 获取统计信息
     *
     * @return 统计信息字符串
     */
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

    /**
     * 清理不活跃的会话
     * <p>
     * 清理规则：
     * 1. Redis 状态为空或 false 的会话
     * 2. 活跃订阅者为 0 的会话
     * 3. 或者流程已完成且无活跃订阅者的会话
     *
     * @return 清理的会话数量
     */
    @Override
    public int cleanupInactive() {
        int cleanedCount = 0;
        long currentTime = System.currentTimeMillis();

        try {
            // 复制 key 集合避免 ConcurrentModificationException
            var sessionIds = new ArrayList<>(SESSION_STATE_MAP.keySet());

            for (String sessionId : sessionIds) {
                SessionState state = SESSION_STATE_MAP.get(sessionId);
                if (state == null) {
                    continue;
                }

                long activeSubscribers = state.activeSubscribers.get();
                boolean flowCompleted = state.flowCompleted.get();

                // 快速清理条件：流程已完成且没有活跃订阅者
                boolean shouldCleanup = flowCompleted && activeSubscribers == 0;

                if (shouldCleanup) {
                    log.info("[快速清理] 清理不活跃会话: sessionId={}, activeSubscribers={}, flowCompleted={}",
                            sessionId, activeSubscribers, flowCompleted);
                    release(sessionId);

                    // 调用清理回调（如果存在）
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

    /**
     * 获取所有会话 ID
     *
     * @return 会话 ID 列表
     */
    public List<String> getAllSessionIds() {
        return new ArrayList<>(SESSION_STATE_MAP.keySet());
    }
}
