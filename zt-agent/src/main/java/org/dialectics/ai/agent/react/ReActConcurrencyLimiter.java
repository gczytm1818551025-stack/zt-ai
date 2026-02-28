package org.dialectics.ai.agent.react;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ReAct 并发限制器
 * <p>
 * 功能：
 * 1. 使用信号量限制同时处理的请求数量
 * 2. 记录并发指标（活跃请求数、拒绝请求数、超时数）
 * 3. 提供超时拒绝机制
 * 4. 线程安全的资源管理
 * <p>
 * 设计原理：
 * - 使用非公平信号量提高吞吐量，减少线程切换开销
 * - 记录拒绝和超时指标用于监控和告警
 * - 提供 tryAcquire 带超时的版本，避免无限等待
 */
@Slf4j
public class ReActConcurrencyLimiter {
    private final Semaphore semaphore;
    private final int maxPermits;
    private final AtomicInteger activeRequests = new AtomicInteger(0);
    private final AtomicInteger rejectedRequests = new AtomicInteger(0);
    private final AtomicInteger timeoutRequests = new AtomicInteger(0);

    // Micrometer 指标
    private final Counter rejectedCounter;
    private final Counter timeoutCounter;

    /**
     * 构造并发限制器（不带指标注册）
     * <p>
     * 用于测试或不需要指标的场景
     *
     * @param maxPermits 最大并发许可数
     */
    public ReActConcurrencyLimiter(int maxPermits) {
        this.maxPermits = maxPermits;
        this.semaphore = new Semaphore(maxPermits, false); // 非公平信号量（提高吞吐量）
        this.rejectedCounter = null;
        this.timeoutCounter = null;
        log.info("并发限制器初始化完成: maxPermits={}", maxPermits);
    }

    /**
     * 构造并发限制器（带指标注册）
     * <p>
     * 注册 Micrometer 指标用于监控
     *
     * @param maxPermits   最大并发许可数
     * @param meterRegistry 指标注册表
     */
    public ReActConcurrencyLimiter(int maxPermits, MeterRegistry meterRegistry) {
        this.maxPermits = maxPermits;
        this.semaphore = new Semaphore(maxPermits, false); // 非公平信号量（提高吞吐量）

        // 注册拒绝请求计数器
        this.rejectedCounter = Counter.builder("react.concurrency.request.rejected")
                .description("Rejected request count due to concurrency limit")
                .register(meterRegistry);

        // 注册超时请求计数器
        this.timeoutCounter = Counter.builder("react.concurrency.request.timeout")
                .description("Request timeout count while waiting for permit")
                .register(meterRegistry);

        // 注册活跃请求数指标
        Gauge.builder("react.concurrency.requests.active", activeRequests, AtomicInteger::get)
                .description("Currently active ReAct requests (concurrency limited)")
                .register(meterRegistry);

        // 注册拒绝请求数指标
        Gauge.builder("react.concurrency.requests.rejected.total", rejectedRequests, AtomicInteger::get)
                .description("Total rejected requests due to concurrency limit")
                .register(meterRegistry);

        // 注册超时请求数指标
        Gauge.builder("react.concurrency.requests.timeout.total", timeoutRequests, AtomicInteger::get)
                .description("Total timeout requests while waiting for permit")
                .register(meterRegistry);

        // 注册可用许可数指标
        Gauge.builder("react.concurrency.permits.available", semaphore, Semaphore::availablePermits)
                .description("Available permits for ReAct execution")
                .register(meterRegistry);

        log.info("并发限制器初始化完成: maxPermits={}", maxPermits);
    }

    /**
     * 尝试获取许可（带超时）
     * <p>
     * 如果在指定时间内无法获取许可，则返回 false
     * <p>
     * 使用场景：
     * - 请求进入时调用
     * - 超时时拒绝请求并返回错误给客户端
     *
     * @param timeoutSeconds 超时时间（秒）
     * @return 是否成功获取许可
     */
    public boolean tryAcquire(int timeoutSeconds) {
        try {
            boolean acquired = semaphore.tryAcquire(timeoutSeconds, TimeUnit.SECONDS);
            if (acquired) {
                int active = activeRequests.incrementAndGet();
                log.debug("获取并发许可成功: active={}/{}, available={}",
                        active, maxPermits, semaphore.availablePermits());
            } else {
                int rejected = rejectedRequests.incrementAndGet();
                int active = activeRequests.get();
                log.warn("并发限制：已拒绝请求, active={}, rejected={}/{}, timeoutSeconds={}",
                        active, rejected, maxPermits, timeoutSeconds);

                if (rejectedCounter != null) {
                    rejectedCounter.increment();
                }
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("获取并发许可被中断", e);
            return false;
        }
    }

    /**
     * 获取许可（阻塞直到成功）
     * <p>
     * 警告：此方法会无限期阻塞，建议使用 tryAcquire 带超时的版本
     *
     * @return 是否成功获取许可（总是返回 true）
     */
    public boolean acquire() {
        try {
            semaphore.acquire();
            int active = activeRequests.incrementAndGet();
            log.debug("获取并发许可成功: active={}/{}", active, maxPermits);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("获取并发许可被中断", e);
            return false;
        }
    }

    /**
     * 释放许可
     * <p>
     * 使用场景：
     * - 请求完成时调用
     * - 请求取消时调用
     * - 请求失败时调用
     */
    public void release() {
        int active = activeRequests.decrementAndGet();
        semaphore.release();
        log.debug("释放并发许可: active={}/{}, available={}",
                active, maxPermits, semaphore.availablePermits());
    }

    /**
     * 记录超时
     * <p>
     * 当请求在等待许可时超时调用
     */
    public void recordTimeout() {
        int timeout = timeoutRequests.incrementAndGet();
        if (timeoutCounter != null) {
            timeoutCounter.increment();
        }
        log.warn("记录请求超时: totalTimeout={}", timeout);
    }

    /**
     * 获取可用许可数
     *
     * @return 可用许可数
     */
    public int getAvailablePermits() {
        return semaphore.availablePermits();
    }

    /**
     * 获取当前活跃请求数
     *
     * @return 活跃请求数
     */
    public int getActiveRequests() {
        return activeRequests.get();
    }

    /**
     * 获取最大许可数
     *
     * @return 最大许可数
     */
    public int getMaxPermits() {
        return maxPermits;
    }

    /**
     * 获取拒绝请求数
     *
     * @return 拒绝请求数
     */
    public int getRejectedRequests() {
        return rejectedRequests.get();
    }

    /**
     * 获取超时请求数
     *
     * @return 超时请求数
     */
    public int getTimeoutRequests() {
        return timeoutRequests.get();
    }

    /**
     * 获取利用率（活跃请求数 / 最大许可数）
     *
     * @return 利用率（0.0 - 1.0）
     */
    public double getUtilization() {
        return (double) activeRequests.get() / maxPermits;
    }

    /**
     * 重置统计
     * <p>
     * 用于测试或定期重置统计
     */
    public void resetStats() {
        rejectedRequests.set(0);
        timeoutRequests.set(0);
        log.info("并发限制器统计已重置");
    }

    /**
     * 获取统计信息
     *
     * @return 统计信息字符串
     */
    public String getStats() {
        return String.format(
                "ReactConcurrencyLimiter[maxPermits=%d, active=%d, available=%d, rejected=%d, timeout=%d, utilization=%.2f%%]",
                maxPermits,
                activeRequests.get(),
                semaphore.availablePermits(),
                rejectedRequests.get(),
                timeoutRequests.get(),
                getUtilization() * 100
        );
    }
}
