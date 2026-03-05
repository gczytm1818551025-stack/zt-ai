package org.dialectics.ai.agent.config;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.dialectics.ai.agent.config.properties.ReActExecProperties;
import org.dialectics.ai.agent.react.ReActPerformanceMetrics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * ReAct 响应式执行器配置
 * <p>
 * 设计原则：
 * 1. 线程池分离：LLM调用、I/O操作、流程编排使用不同线程池
 * 2. 资源隔离：避免不同类型任务互相影响
 * 3. 可观测性：集成 Micrometer 指标
 * 4. 线程安全：Spring AI 官方 ChatModel 已线程安全，使用并发调度器
 * <p>
 * 并发控制说明：
 * - 使用 Reactor 背压机制和调度器配置控制并发，无需手动 Semaphore 控制
 * - llmScheduler 的 maxPoolSize 控制 LLM 调用并发数
 * - toolScheduler 的 maxPoolSize 控制工具执行并发数
 * - StreamManager 的 Sinks.Many 背压缓冲区控制事件积压
 * - Reactor 的背压机制天然处理流量控制
 */
@Slf4j
@Configuration
public class ReActExecutorConfig {

    // ==================== LLM 调用线程池配置 ====================
    // LLM 调用特点：I/O 密集、耗时较长(1-10秒)、需要较多并发

    @Value("${zt-ai.react.llm.core-pool-size:20}")
    private int llmCorePoolSize;
    @Value("${zt-ai.react.llm.max-pool-size:100}")
    private int llmMaxPoolSize;
    @Value("${zt-ai.react.llm.queue-capacity:500}")
    private int llmQueueCapacity;
    @Value("${zt-ai.react.llm.keep-alive-seconds:120}")
    private int llmKeepAliveSeconds;
    @Value("${zt-ai.react.llm.timeout-seconds:60}")
    private int llmTimeoutSeconds;

    // ==================== 工具执行线程池配置 ====================
    // 工具执行特点：I/O 密集、耗时中等(0.1-2秒)

    @Value("${zt-ai.react.tool.core-pool-size:10}")
    private int toolCorePoolSize;
    @Value("${zt-ai.react.tool.max-pool-size:50}")
    private int toolMaxPoolSize;
    @Value("${zt-ai.react.tool.queue-capacity:200}")
    private int toolQueueCapacity;
    @Value("${zt-ai.react.tool.keep-alive-seconds:60}")
    private int toolKeepAliveSeconds;
    @Value("${zt-ai.react.tool.timeout-seconds:30}")
    private int toolTimeoutSeconds;

    // ==================== 超时配置 ====================

    @Value("${zt-ai.react.request-timeout-seconds:300}")
    private int requestTimeoutSeconds;

    // ==================== 背压配置 ====================

    @Value("${zt-ai.react.backpressure-buffer-size:256}")
    private int backpressureBufferSize;
    @Value("${zt-ai.react.backpressure-strategy:BUFFER}")
    private String backpressureStrategy;

    // ==================== ReAct 流程配置 ====================

    @Value("${zt-ai.react.max-step:20}")
    private int maxStep;
    @Value("${zt-ai.react.max-action-per-call:10}")
    private int maxActionPerCall;

    /**
     * LLM 调用专用调度器
     * <p>
     * Spring AI 的 OpenAiChatModel 和 DashScopeChatModel 是线程安全的，
     * 因此可以使用并发调度器，充分发挥并发优势。
     * <p>
     * 特点：
     * - 使用 boundedElastic 调度器，自动扩容和收缩
     * - 适合 I/O 密集型任务
     * - 任务多时自动扩展线程
     * - 空闲时自动回收，节省资源
     *
     * @return LLM 调用调度器
     */
    @Bean("llmScheduler")
    public Scheduler llmScheduler() {
        log.info("初始化 LLM 调度器: maxThreads={}", llmMaxPoolSize);
        return Schedulers.newBoundedElastic(
                llmMaxPoolSize,
                llmQueueCapacity,
                "react-llm-"
        );
    }

    /**
     * 工具执行专用调度器
     * <p>
     * 特点：
     * - 适合 I/O 密集型任务（工具调用）
     * - 弹性线程池，自动扩容和收缩
     */
    @Bean("toolScheduler")
    public Scheduler toolScheduler() {
        log.info("初始化工具调度器: maxThreads={}", toolMaxPoolSize);
        return Schedulers.newBoundedElastic(
                toolMaxPoolSize,
                toolQueueCapacity,
                "react-tool-"
        );
    }

    /**
     * 流程编排调度器
     * <p>用于 ReAct 循环的异步编排，不执行耗时操作
     * <p>特点：
     * - 线程数较少，主要用于任务编排
     * - CPU 密集型调度器
     */
    @Bean("orchestrationScheduler")
    public Scheduler orchestrationScheduler() {
        int corePoolSize = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        log.info("初始化编排调度器: poolSize={}", corePoolSize);
        return Schedulers.newParallel("reAct-orchestration-", corePoolSize);
    }

    /**
     * ReAct 性能指标收集器
     *
     * @return 性能指标收集器
     */
    @Bean("reActMetrics")
    public ReActPerformanceMetrics performanceMetrics(MeterRegistry meterRegistry) {
        log.info("初始化性能指标收集器");
        return new ReActPerformanceMetrics(meterRegistry);
    }

    /**
     * ReAct 配置属性Bean
     * <p>
     * 提供配置参数的集中管理
     * <p>
     * 并发控制说明：
     * - 使用 Reactor 背压机制和调度器配置控制并发
     * - llmScheduler 和 toolScheduler 已配置线程池大小
     * - StreamManager 的 Sinks.Many 已配置背压缓冲区
     *
     * @return ReAct 配置属性
     */
    @Bean("reActExecutorProperties")
    public ReActExecProperties reActExecutorProperties() {
        return ReActExecProperties.builder()
                .llmTimeoutSeconds(llmTimeoutSeconds)
                .toolTimeoutSeconds(toolTimeoutSeconds)
                .requestTimeoutSeconds(requestTimeoutSeconds)
                .backpressureBufferSize(backpressureBufferSize)
                .backpressureStrategy(backpressureStrategy)
                .maxStep(maxStep)
                .maxActionPerCall(maxActionPerCall)
                .build();
    }


}
