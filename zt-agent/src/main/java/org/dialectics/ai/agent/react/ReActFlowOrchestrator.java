package org.dialectics.ai.agent.react;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.dialectics.ai.agent.agent.AgentExecutionContext;
import org.dialectics.ai.agent.config.properties.ReActExecProperties;
import org.dialectics.ai.agent.domain.pojo.AssistantMessage2;
import org.dialectics.ai.agent.domain.pojo.StepTrace;
import org.dialectics.ai.agent.domain.vo.ReActEventVo;
import org.dialectics.ai.agent.manager.PromptManager;
import org.dialectics.ai.agent.manager.ReActStreamManager;
import org.dialectics.ai.agent.memory.ChatMemory2Repository;
import org.dialectics.ai.common.constants.RedisConstant;
import org.dialectics.ai.common.exception.ReActFlowException;
import org.dialectics.ai.common.constants.PromptNameConstant;
import org.dialectics.ai.common.utils.RedisRetryUtils;
import org.dialectics.ai.common.enums.ReActStageEnum;
import org.dialectics.ai.common.utils.JsonFinder;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import jakarta.annotation.Resource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.alibaba.fastjson2.JSON;

import io.micrometer.core.instrument.Timer;

import static org.dialectics.ai.agent.utils.ReActControlVisitor.*;
import static org.dialectics.ai.agent.utils.ChatSessionVisitor.*;

import org.dialectics.ai.agent.service.SessionService;
import org.dialectics.ai.agent.service.EventHistoryService;

/**
 * ReAct 流程编排器
 * <p>
 * 核心职责：
 * 1. 使用全局 StreamManager 的 Flux 直接返回，避免间接转发
 * 2. 异步编排observe → think → act循环流程和对话记忆
 * 3. 处理取消、超时、错误
 * 4. 收集性能指标
 * 5. 每个请求的推送互不干扰
 * 6. 支持会话重连和状态恢复
 * <p>
 * 隔离性保证：
 * - 使用全局 ReActStreamManager 管理会话 Sink
 * - 每个会话支持多订阅和重连
 * - Redis 存储会话状态，支持刷新后恢复
 * - 通过 connectionCount 判断是否启动过流程
 */
@Slf4j
@Component
public class ReActFlowOrchestrator {

    /**
     * 资源守卫——确保并发许可在任何情况下都能释放
     */
    static class ReActResourceGuard implements AutoCloseable {
        private final ReActConcurrencyLimiter limiter;
        private final AtomicBoolean released = new AtomicBoolean(false);

        public ReActResourceGuard(ReActConcurrencyLimiter limiter) {
            this.limiter = limiter;
        }

        public boolean acquired() {
            return !released.get();
        }

        @Override
        public void close() {
            if (released.compareAndSet(false, true)) {
                limiter.release();
            }
        }
    }

    @Autowired
    private ReActAsyncExecutor asyncExecutor;
    @Autowired
    private ChatMemory chatMemory;
    @Autowired
    private ChatMemory2Repository chatMemoryRepository;
    @Autowired
    private SessionService sessionService;
    @Resource(name = "reActConcurrencyLimiter")
    private ReActConcurrencyLimiter concurrencyLimiter;
    @Autowired
    private ReActPerformanceMetrics metrics;
    @Resource(name = "orchestrationScheduler")
    private Scheduler scheduler;
    @Resource(name = "reActExecutorProperties")
    private ReActExecProperties properties;
    @Autowired
    private ReActStreamManager streamManager;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private EventHistoryService eventHistoryService;

    /**
     * 编排执行ReAct流程(入口)
     *
     * @param task 任务描述
     * @param ctx  执行上下文
     * @return ReAct 事件流
     */
    public Flux<ReActEventVo> orchestrate(String task, AgentExecutionContext ctx) {
        String sessionId = sessionId(ctx);
        String conversationId = conversationId(ctx);
        String requestId = requestId(ctx);

        log.info("[{}] 开始 ReAct 流程: taskId={}, sessionId={}", requestId, task, sessionId);

        // 获取数据源Flux
        Flux<ReActEventVo> dataSource = streamManager.getStream(sessionId);
        if (dataSource == null) {
            log.error("[{}] Flux 为空: sessionId={}", requestId, sessionId);
            return Flux.error(new IllegalStateException("会话 Flux 创建失败"));
        }

        // TODO 重连模式：发送恢复提示和历史回放
        if (streamManager.isActive(sessionId)) {
            log.info("[{}] 会话流程已进行中，返回重连 Flux: sessionId={}", requestId, sessionId);
            return dataSource.doOnSubscribe(s -> {
                log.debug("[{}] 重连Flux已订阅", requestId);
                // 从 Redis Stream 获取历史事件以计算进度信息
                String reconnectMessage = "正在继续之前的对话...";
                try {
                    List<ReActEventVo> historyEvents = eventHistoryService.getHistory(sessionId, 0);
                    int historySize = historyEvents.size();

                    // 计算已完成步数（统计ACTION_RESULT类型的事件）
                    long completedSteps = historyEvents.stream()
                            .filter(e -> e.getStage() == ReActStageEnum.ACTION_RESULT)
                            .count();

                    // 检查是否有最终结果
                    boolean hasFinalResult = historyEvents.stream()
                            .anyMatch(e -> e.getStage() == ReActStageEnum.FINAL_SUMMARY);

                    // 构建详细的恢复提示消息
                    if (hasFinalResult) {
                        reconnectMessage = String.format("任务已完成。已完成 %d 步，共回放 %d 条历史消息。",
                                completedSteps, historySize);
                    } else {
                        reconnectMessage = String.format("正在继续之前的对话... 已完成 %d 步，共回放 %d 条历史消息。",
                                completedSteps, historySize);
                    }

                    log.debug("[{}] 重连信息: {}", requestId, reconnectMessage);
                } catch (Exception e) {
                    log.warn("[{}] 获取历史事件失败，使用默认重连消息: {}", requestId, e.getMessage());
                }

                // 发送重连提示消息
                streamManager.tryEmit(sessionId, ReActEventVo.newDataEvent(reconnectMessage, null));
            });
        }

        // ---------- 首次请求 ----------
        log.info("[{}] 首次请求，启动ReAct流程: sessionId={}", requestId, sessionId);
        // 设置活跃状态标志
        String reactStatusKey = RedisConstant.REACT_STATUS_KEY_PREFIX + sessionId;
        RedisRetryUtils.safeSet(redisTemplate, reactStatusKey, "active", Duration.ofHours(1));

        // 添加系统提示词
        List<Message> messages = messageMemory(ctx);
        messages.add(SystemMessage.builder().text(PromptManager.renderFrom(PromptNameConstant.REACT_SYSTEM)).build());

        // 创建空的AssistantMessage用于流式追加所有步骤内容
        AssistantMessage emptyAssistantMessage = new AssistantMessage2("", Map.of(), List.of(), List.of(), Map.of("steps", new ArrayList<>(), "stepCount", 0));
        chatMemory.add(conversationId, emptyAssistantMessage);
        log.debug("[{}] 初始化 ReAct 消息: conversationId={}", requestId, conversationId);

        // 记录请求开始
        metrics.recordRequestStart();
        Timer.Sample totalTimer = metrics.startTotalTimer();

        // 获取配置
        int maxStep = properties.getMaxStep();
        Duration requestTimeout = Duration.ofSeconds(properties.getRequestTimeoutSeconds());
        AtomicBoolean timerStopped = new AtomicBoolean(false);

        // 启动异步编排——使用Mono.using确保并发许可安全释放
        Mono.using(() -> {
                            // 资源获取：尝试获取并发许可
                            if (!concurrencyLimiter.tryAcquire(properties.getRequestTimeoutSeconds())) {
                                log.warn("[{}] 并发限制：已拒绝请求", requestId);
                                // 清除会话活跃状态
                                RedisRetryUtils.safeDelete(redisTemplate, reactStatusKey);
                                throw new ReActFlowException.BusyException("系统繁忙，请稍后重试");
                            }
                            return new ReActResourceGuard(concurrencyLimiter);
                        },
                        guard -> executeReActLoop(ctx, maxStep, requestTimeout)
                                .then() // 将 Flux<Void> 转换为 Mono<Void>
                                .doOnSuccess(v -> {
                                    handleSuccess(ctx, totalTimer);
                                    timerStopped.set(true);
                                })
                                .doOnError(error -> {
                                    handleError(error, ctx, totalTimer);
                                    timerStopped.set(true);
                                }),
                        ReActResourceGuard::close
                ).doFinally(signal -> {
                    // 发送停止事件
                    streamManager.tryEmit(sessionId, ReActEventVo.stopEvent());
                    log.debug("[{}] 停止事件已发送: signal={}", requestId, signal);

                    // 停止计时器并记录指标（如果还未停止）
                    if (timerStopped.compareAndSet(false, true)) {
                        try {
                            metrics.recordTotalDuration(totalTimer);
                        } catch (Exception e) {
                            log.trace("[{}] 计时器停止异常: {}", requestId, e.getMessage());
                        }
                    }

                    // 标记流程完成，管理Sink释放
                    streamManager.markFlowCompleted(sessionId);
                    // 清除会话活跃状态
                    RedisRetryUtils.safeDelete(redisTemplate, reactStatusKey);

                    metrics.recordComplete();
                    log.debug("[{}] 流程完成，释放资源: signal={}", requestId, signal);
                })
                .subscribeOn(scheduler)
                .subscribe();

        // 返回Flux
        return dataSource.doOnCancel(() -> {
            log.info("[{}] 客户端取消订阅", requestId(ctx));
        });
    }

    /**
     * 核心：执行ReAct流程循环
     */
    private Flux<Void> executeReActLoop(AgentExecutionContext ctx, int maxStep, Duration requestTimeout) {
        String reactStatusKey = RedisConstant.REACT_STATUS_KEY_PREFIX + sessionId(ctx);

        return Mono.defer(() -> {
                    // 检查管道是否活跃
                    if (!RedisRetryUtils.safeHasKey(redisTemplate, reactStatusKey)) {
                        log.info("[{}] Redis 状态不存在，任务被停止: sessionId={}", requestId(ctx), sessionId(ctx));
                        return Mono.error(new ReActFlowException("会话状态不存在"));
                    }
                    // 检查是否已完成（handleSuccess之后）
                    if (completed(ctx).get()) {
                        return Mono.empty();
                    }

                    // 检查是否需要超过步数限制还未结束
                    if (stepCount(ctx).get() >= maxStep && !completed(ctx).get()) {
                        log.info("[{}] 达到最大步数限制，添加终止提示", requestId(ctx));
                        // 设置终止标志
                        terminatedFlag(ctx).set(true);
                    }
                    // 执行单步ReAct流程
                    return executeOneStep(ctx);
                })
                .repeat(() -> !completed(ctx).get() && stepCount(ctx).get() <= maxStep)
                .timeout(requestTimeout, Mono.error(new ReActFlowException.TimeoutException("请求超时")))
                .onErrorResume(ReActFlowException.TimeoutException.class, e -> {
                    log.warn("[{}] 请求超时", requestId(ctx));
                    return Mono.error(e);
                });
    }

    /**
     * 核心：执行ReAct单流程 (observe → think → act)
     */
    private Mono<Void> executeOneStep(AgentExecutionContext ctx) {
        // observe
        return asyncExecutor.observe(ctx)
                .flatMap(observeResult -> {
                    // 发送规划事件
                    StepTrace.Status currentState = observeResult.currentState();
                    ReActEventVo planEvent = ReActEventVo.newDataEvent(new ReActEventVo.PlanData(
                            taskChain(ctx).size(),
                            currentState.getPreviousEvaluation(),
                            currentState.getMemory(),
                            currentState.getThinking(),
                            observeResult.taskContent()
                    ), ReActStageEnum.TASK_PLAN);
                    emitEvent(planEvent, ctx);

                    // 追加规划步骤到对话记忆
                    ReActEventVo.PlanData planEventData = (ReActEventVo.PlanData) planEvent.getData();
                    Map<String, Object> planData = Map.of(
                            "type", 1, // PLAN
                            "index", taskChain(ctx).size(),
                            "taskContent", planEventData.taskContent(),
                            "previousEvaluation", planEventData.previousEvaluation(),
                            "memory", planEventData.memory(),
                            "thinking", planEventData.thinking()
                    );
                    chatMemoryRepository.appendToLastMessage(
                            conversationId(ctx),
                            "",  // 不追加文本
                            params -> {
                                @SuppressWarnings("unchecked")
                                List<Map<String, Object>> steps = (List<Map<String, Object>>) params.get("steps");
                                steps.add(planData);
                                return params;
                            }
                    );
                    log.debug("[{}] 追加规划步骤: stepIndex={}", requestId(ctx), taskChain(ctx).size());

                    // think
                    return asyncExecutor.think(ctx)
                            .flatMap(thinkResult -> {
                                // 发送思考内容事件
                                String thinkingText = thinkResult.thinkResponse().getResult().getOutput().getText();
                                ReActEventVo.ThinkData thinkData;
                                String stepTraceJson = JsonFinder.findFirst(thinkingText);
                                if (StrUtil.isEmpty(stepTraceJson)) {
                                    thinkData = new ReActEventVo.ThinkData(StrUtil.isEmpty(thinkingText) ? "暂无详细思考" : thinkingText);
                                } else {
                                    StepTrace stepTrace = JSON.parseObject(stepTraceJson, StepTrace.class);
                                    thinkData = new ReActEventVo.ThinkData(stepTrace.getCurrentState().getThinking());
                                }
                                ReActEventVo thinkEvent = ReActEventVo.newDataEvent(thinkData, ReActStageEnum.STRATEGY_THINK);
                                emitEvent(thinkEvent, ctx);

                                // 追加思考步骤到对话记忆
                                Map<String, Object> thinkDataMap = Map.of(
                                        "type", 2, // THINKING
                                        "thinkContent", thinkData.thinkContent()
                                );
                                chatMemoryRepository.appendToLastMessage(
                                        conversationId(ctx),
                                        "",  // 不追加任何文本到 content
                                        params -> {
                                            @SuppressWarnings("unchecked")
                                            List<Map<String, Object>> steps = (List<Map<String, Object>>) params.get("steps");
                                            steps.add(thinkDataMap);
                                            return params;
                                        }
                                );
                                log.debug("[{}] 追加思考步骤", requestId(ctx));

                                // act并发送行动结果事件
                                return asyncExecutor.act(thinkResult.thinkResponse(), ctx)
                                        .flatMap(actResult -> {
                                            // 发送行动结果事件
                                            ReActEventVo.ActionData actionEventData = new ReActEventVo.ActionData(
                                                    actResult.success(),
                                                    actResult.result()
                                            );
                                            ReActEventVo actEvent = ReActEventVo.newDataEvent(actionEventData, ReActStageEnum.ACTION_RESULT);
                                            emitEvent(actEvent, ctx);

                                            // 追加行动步骤到对话记忆
                                            Map<String, Object> actionData = Map.of(
                                                    "type", 3, // ACTION
                                                    "success", actionEventData.success(),
                                                    "result", actionEventData.result(),
                                                    "resultType", 1 // 默认为文本类型
                                            );
                                            chatMemoryRepository.appendToLastMessage(
                                                    conversationId(ctx),
                                                    "",  // 不追加任何文本到 content
                                                    params -> {
                                                        @SuppressWarnings("unchecked")
                                                        List<Map<String, Object>> steps = (List<Map<String, Object>>) params.get("steps");
                                                        steps.add(actionData);
                                                        // 更新 stepCount
                                                        int actionCount = (int) params.getOrDefault("stepCount", 0) + 1;
                                                        params.put("stepCount", actionCount);
                                                        return params;
                                                    }
                                            );
                                            log.debug("[{}] 追加行动步骤: success={}", requestId(ctx), actionEventData.success());

                                            // observe-think-act单步完成，增加步数
                                            stepCount(ctx).incrementAndGet();
                                            log.debug("[{}] 步数增加: currentStep={}", requestId(ctx), stepCount(ctx).get());

                                            return Mono.<Void>empty();
                                        });
                            });
                })
                .onErrorResume(error -> {
                    log.error("[{}] 单步执行失败: step={}, error={}", requestId(ctx), stepCount(ctx).get(), error.getMessage());
                    metrics.recordFailure("step_error");
                    return Mono.empty();
                });
    }

    /**
     * 发送最终结果
     */
    private void sendFinalResult(AgentExecutionContext ctx) {
        Object finalResult = finalResult(ctx);

        if (null != finalResult) {
            log.info("[{}] 任务完成，最终结果: {}", requestId(ctx), finalResult);
        } else {
            log.warn("[{}] 任务最终结果为空", requestId(ctx));
            finalResult = "任务已中断";
        }
        ReActEventVo finalResultEvent = ReActEventVo.newDataEvent(new ReActEventVo.FinalData((String) finalResult), ReActStageEnum.FINAL_SUMMARY);
        emitEvent(finalResultEvent, ctx);

        // 最终结果存储到对话记忆
        chatMemoryRepository.appendToLastMessage(
                conversationId(ctx),
                (String) finalResult,
                params -> params
        );
        log.debug("[{}] 追加最终结果", requestId(ctx));
    }

    /**
     * 处理成功
     */
    private void handleSuccess(AgentExecutionContext ctx, Timer.Sample totalTimer) {
        // 发送最终结果
        sendFinalResult(ctx);

        metrics.recordSuccess();
        long durationNanos = metrics.recordTotalDuration(totalTimer);
        long durationMs = durationNanos / 1_000_000;

        log.info("[{}] 任务完成: conversationId={}, 耗时={}ms, 步数={}, tokens={}",
                requestId(ctx), conversationId(ctx), durationMs, stepCount(ctx).get(),
                tokenCounter(ctx).sum());

        metrics.recordSteps(stepCount(ctx).get());

        // 刷新对话标题
        String sessionId = sessionId(ctx);
        Long userId = userId(ctx);
        String question = targetTask(ctx);
        String response = finalResult(ctx);
        sessionService.flushChat(sessionId, userId, question, response);
    }

    /**
     * 处理错误
     */
    private void handleError(Throwable error, AgentExecutionContext ctx, Timer.Sample totalTimer) {
        metrics.recordFailure(error.getClass().getSimpleName());

        log.error("[{}] 任务失败: conversationId={}, error={}",
                requestId(ctx), conversationId(ctx), error.getMessage(), error);

        // 在错误情况下追加提示信息
        String errorMsg = "任务已终止"/* + error.getMessage()*/;
        chatMemoryRepository.appendToLastMessage(
                conversationId(ctx),
                errorMsg,
                params -> params
        );
        log.debug("[{}] 追加错误信息", requestId(ctx));
    }

    // ==================== 辅助方法 ====================

    private void emitEvent(ReActEventVo event, AgentExecutionContext ctx) {
        try {
            boolean success = streamManager.tryEmit(sessionId(ctx), event);
            if (!success) {
                log.warn("[{}] 事件发送失败: type={}", requestId(ctx), event.getType());
            }
        } catch (Exception e) {
            log.error("[{}] {}事件发送异常: stage={}, error={}", requestId(ctx), event.getType(), event.getStage(), e.getMessage(), e);
        }
    }

}



