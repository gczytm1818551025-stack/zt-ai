package org.dialectics.ai.agent.react;

import io.micrometer.core.instrument.*;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ReAct 性能指标收集器
 * <p>
 * 收集指标：
 * 1. 请求计数：总请求数、成功数、失败数、取消数
 * 2. 耗时计时：总耗时、各阶段耗时、LLM调用耗时
 * 3. 并发指标：活跃请求数
 * 4. 资源指标：内存使用、Token消耗
 * <p>
 * 设计原则：
 * - 使用 Micrometer 统一指标格式
 * - 提供百分位数（P50、P95、P99）
 * - 支持不同监控后端（Prometheus、InfluxDB 等）
 */
@Slf4j
public class ReActPerformanceMetrics {

    private final MeterRegistry registry;

    // ==================== 请求计数 ====================

    private Counter requestCounter;
    private Counter successCounter;
    private Counter failureCounter;
    private Counter cancelCounter;

    // ==================== 耗时计时 ====================

    private Timer totalTimer;
    private Timer observeTimer;
    private Timer thinkTimer;
    private Timer actTimer;
    private Timer llmCallTimer;
    private Timer toolCallTimer;

    // ==================== Token 消耗 ====================

    private Counter totalTokenCounter;

    // ==================== 步数统计 ====================

    private Counter stepCounter;
    private DistributionSummary stepDistribution;

    // ==================== 并发指标 ====================

    private final AtomicLong activeRequests;

    /**
     * 构造性能指标收集器
     *
     * @param registry Micrometer 指标注册表
     */
    public ReActPerformanceMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.activeRequests = new AtomicLong(0);
        initCounters();
        initTimers();
        initTokenMetrics();
        initStepMetrics();
        initActiveGauge();
        log.info("ReAct 性能指标收集器初始化完成");
    }

    /**
     * 初始化计数器
     */
    private void initCounters() {
        this.requestCounter = Counter.builder("react.request.total")
                .description("Total ReAct requests")
                .tag("type", "react")
                .register(registry);

        this.successCounter = Counter.builder("react.request.success")
                .description("Successful ReAct requests")
                .tag("type", "react")
                .register(registry);

        this.failureCounter = Counter.builder("react.request.failure")
                .description("Failed ReAct requests")
                .tag("type", "react")
                .register(registry);

        this.cancelCounter = Counter.builder("react.request.cancelled")
                .description("Cancelled ReAct requests")
                .tag("type", "react")
                .register(registry);
    }

    /**
     * 初始化计时器
     */
    private void initTimers() {
        this.totalTimer = Timer.builder("react.duration.total")
                .description("Total ReAct request duration")
                .tag("phase", "total")
                .publishPercentiles(0.5, 0.75, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(registry);

        this.observeTimer = Timer.builder("react.duration.observe")
                .description("Observe phase duration")
                .tag("phase", "observe")
                .publishPercentiles(0.5, 0.75, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(registry);

        this.thinkTimer = Timer.builder("react.duration.think")
                .description("Think phase duration")
                .tag("phase", "think")
                .publishPercentiles(0.5, 0.75, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(registry);

        this.actTimer = Timer.builder("react.duration.act")
                .description("Act phase duration")
                .tag("phase", "act")
                .publishPercentiles(0.5, 0.75, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(registry);

        this.llmCallTimer = Timer.builder("react.duration.llm.call")
                .description("LLM call duration")
                .tag("type", "llm")
                .publishPercentiles(0.5, 0.75, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(registry);

        this.toolCallTimer = Timer.builder("react.duration.tool.call")
                .description("Tool call duration")
                .tag("type", "tool")
                .publishPercentiles(0.5, 0.75, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(registry);
    }

    /**
     * 初始化 Token 指标
     */
    private void initTokenMetrics() {
        this.totalTokenCounter = Counter.builder("react.token.total")
                .description("Total tokens consumed")
                .tag("type", "token")
                .register(registry);
    }

    /**
     * 初始化步数指标
     */
    private void initStepMetrics() {
        this.stepCounter = Counter.builder("react.step.total")
                .description("Total ReAct steps executed")
                .tag("type", "step")
                .register(registry);

        this.stepDistribution = DistributionSummary.builder("react.steps.per.request")
                .description("Number of steps per ReAct request")
                .publishPercentiles(0.5, 0.75, 0.95, 0.99)
                .register(registry);
    }

    /**
     * 初始化活跃请求指标
     */
    private void initActiveGauge() {
        Gauge.builder("react.request.active", activeRequests, AtomicLong::get)
                .description("Currently active ReAct requests")
                .register(registry);
    }

    // ==================== 请求计数方法 ====================

    public void recordRequestStart() {
        requestCounter.increment();
        activeRequests.incrementAndGet();
    }

    public void recordSuccess() {
        successCounter.increment();
    }

    public void recordFailure(String reason) {
        failureCounter.increment();
    }

    public void recordCancel() {
        cancelCounter.increment();
    }

    public void recordComplete() {
        activeRequests.decrementAndGet();
    }

    // ==================== 耗时计时方法 ====================

    /**
     * 开始总耗时计时
     *
     * @return 计时器采样
     */
    public Timer.Sample startTotalTimer() {
        return Timer.start(registry);
    }

    public long recordTotalDuration(Timer.Sample sample) {
        return sample.stop(totalTimer);
    }

    public Timer.Sample startObserveTimer() {
        return Timer.start(registry);
    }

    public void recordObserveDuration(Timer.Sample sample) {
        sample.stop(observeTimer);
    }

    public Timer.Sample startThinkTimer() {
        return Timer.start(registry);
    }

    public void recordThinkDuration(Timer.Sample sample) {
        sample.stop(thinkTimer);
    }

    public Timer.Sample startActTimer() {
        return Timer.start(registry);
    }

    public void recordActDuration(Timer.Sample sample) {
        sample.stop(actTimer);
    }

    public Timer.Sample startLLMCallTimer() {
        return Timer.start(registry);
    }

    public void recordLLMCallDuration(Timer.Sample sample) {
        sample.stop(llmCallTimer);
    }

    public Timer.Sample startToolCallTimer() {
        return Timer.start(registry);
    }

    public void recordToolCallDuration(Timer.Sample sample) {
        sample.stop(toolCallTimer);
    }

    // ==================== Token 统计方法 ====================

    /**
     * 记录 Token 消耗
     *
     * @param tokenCount 消耗的 Token 数量
     */
    public void recordTokenConsumption(long tokenCount) {
        totalTokenCounter.increment(tokenCount);
    }

    // ==================== 步数统计方法 ====================

    /**
     * 记录执行步数
     *
     * @param stepCount 步数
     */
    public void recordSteps(int stepCount) {
        stepCounter.increment(stepCount);
        stepDistribution.record(stepCount);
    }

    // ==================== 查询方法 ====================

    /**
     * 获取当前活跃请求数
     *
     * @return 活跃请求数
     */
    public long getActiveRequests() {
        return activeRequests.get();
    }

    /**
     * 获取总请求数
     *
     * @return 总请求数
     */
    public double getTotalRequests() {
        return requestCounter.count();
    }

    /**
     * 获取成功率
     *
     * @return 成功率（0.0 - 1.0）
     */
    public double getSuccessRate() {
        double total = requestCounter.count();
        if (total == 0) {
            return 0.0;
        }
        return successCounter.count() / total;
    }

    /**
     * 获取平均请求耗时（毫秒）
     *
     * @return 平均耗时（毫秒）
     */
    public double getAverageDurationMs() {
        return totalTimer.mean(TimeUnit.MILLISECONDS);
    }

    /**
     * 获取 P95 耗时（毫秒）
     *
     * @return P95 耗时（毫秒）
     */
    public double getP95DurationMs() {
        return totalTimer.percentile(0.95, TimeUnit.MILLISECONDS);
    }

    /**
     * 获取 P99 耗时（毫秒）
     *
     * @return P99 耗时（毫秒）
     */
    public double getP99DurationMs() {
        return totalTimer.percentile(0.99, TimeUnit.MILLISECONDS);
    }
}
