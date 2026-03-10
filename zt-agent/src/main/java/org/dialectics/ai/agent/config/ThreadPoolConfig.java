package org.dialectics.ai.agent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class ThreadPoolConfig {
    @Value("${zt-ai.react.core-pool-size:10}")
    private int corePoolSize;
    @Value("${zt-ai.react.max-pool-size:50}")
    private int maxPoolSize;
    @Value("${zt-ai.react.queue-capacity:100}")
    private int queueCapacity;
    @Value("${zt-ai.react.keep-alive-seconds:60}")
    private int keepAliveSeconds;

    /**
     * ReAct 任务专用线程池
     * <p>
     * 配置说明：
     * - corePoolSize: 核心线程数，即使空闲也会保留的线程数量
     * - maxPoolSize: 最大线程数，当队列满时会创建新线程，直到达到此值
     * - queueCapacity: 队列容量，超出此值后创建新线程，线程池满后拒绝新任务
     * - keepAliveSeconds: 线程空闲存活时间，超过此时间会被回收
     * - rejectedExecutionHandler: 拒绝策略，AbortPolicy 直接抛出异常，快速失败
     * <p>
     * 内存控制：
     * - 每个请求维护上下文状态，包括消息列表、任务链等，约占用 1-5MB
     * - 假设每个请求占用 2MB，50 个并发请求约占用 100MB
     * - 建议根据服务器内存配置调整 maxPoolSize
     *
     * @return ReAct 任务专用线程池
     */
    @Bean("reActTaskScheduler")
    public ThreadPoolTaskExecutor reActTaskScheduler() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数：建议设置为 CPU 核心数或略高
        executor.setCorePoolSize(corePoolSize);
        // 最大线程数：建议设置为 CPU 核心数的 2-4 倍
        executor.setMaxPoolSize(maxPoolSize);
        // 队列容量：有界队列，防止任务堆积
        executor.setQueueCapacity(queueCapacity);
        // 线程名称前缀，便于监控和调试
        executor.setThreadNamePrefix("react-task-");
        // 线程存活时间：空闲线程超过此时间会被回收
        executor.setKeepAliveSeconds(keepAliveSeconds);
        // 是否允许核心线程超时回收
        executor.setAllowCoreThreadTimeOut(true);
        // 拒绝策略：AbortPolicy 直接抛出异常，快速失败，避免雪崩
        // CallerRunsPolicy可能导致Tomcat线程被占用
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // 等待任务完成后关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);
        // 最多等待 60 秒让任务完成
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();
        return executor;
    }

    @Bean("chatTaskScheduler")
    public ThreadPoolTaskExecutor chatTaskScheduler() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int processors = Runtime.getRuntime().availableProcessors();
        executor.setCorePoolSize(processors * 2);
        executor.setMaxPoolSize(processors * 4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("chat-exec-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        executor.initialize();
        return executor;
    }
}
