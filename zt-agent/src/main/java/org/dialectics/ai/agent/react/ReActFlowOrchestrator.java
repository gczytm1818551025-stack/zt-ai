package org.dialectics.ai.agent.react;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.dialectics.ai.agent.AgentExecutionContext;
import org.dialectics.ai.agent.config.properties.ReActExecProperties;
import org.dialectics.ai.agent.domain.pojo.ZAssistantMessage;
import org.dialectics.ai.agent.domain.pojo.ReActOutput;
import org.dialectics.ai.agent.domain.vo.ReActEventVo;
import org.dialectics.ai.agent.manager.PromptManager;
import org.dialectics.ai.agent.manager.ReActStreamManager;
import org.dialectics.ai.agent.memory.ZChatMemory;
import org.dialectics.ai.agent.memory.ZChatMemoryRepository;
import org.dialectics.ai.common.base.StreamManager;
import org.dialectics.ai.common.constants.RedisConstant;
import org.dialectics.ai.common.exception.ReActFlowException;
import org.dialectics.ai.common.constants.PromptNameConstant;
import org.dialectics.ai.common.utils.RedisRetryUtils;
import org.dialectics.ai.common.enums.ReActStageEnum;
import org.dialectics.ai.common.utils.JsonFinder;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.alibaba.fastjson2.JSON;

import io.micrometer.core.instrument.Timer;

import static org.dialectics.ai.agent.utils.ReActControlVisitor.*;
import static org.dialectics.ai.agent.utils.ChatSessionVisitor.*;

import org.dialectics.ai.agent.service.SessionService;

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
 * <p>
 * 并发控制：
 * - 使用 Reactor 背压机制和调度器配置控制并发
 * - llmScheduler 和 toolScheduler 配置线程池大小
 * - StreamManager 的 Sinks.Many 配置背压缓冲区
 */
@Slf4j
@Component
public class ReActFlowOrchestrator {
    @Autowired
    private ReActAsyncExecutor asyncExecutor;
    @Autowired
    private ZChatMemory chatMemory;
    @Autowired
    private ZChatMemoryRepository chatMemoryRepository;
    @Autowired
    private SessionService sessionService;
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
        log.info("[{}] 启动ReAct流程: sessionId={}", requestId, sessionId);
        // 设置活跃状态标志
        String reactStatusKey = RedisConstant.REACT_STATUS_KEY_PREFIX + sessionId;
        RedisRetryUtils.safeSet(redisTemplate, reactStatusKey, "active", Duration.ofHours(1));

        // 添加系统提示词
        List<Message> messages = messageMemory(ctx);
        messages.add(SystemMessage.builder().text(PromptManager.renderFrom(PromptNameConstant.REACT_SYSTEM)).build());

        // 创建空的AssistantMessage用于流式追加所有步骤内容
        chatMemory.add(conversationId, new ZAssistantMessage(""));
        log.debug("[{}] 初始化 ReAct 消息: conversationId={}", requestId, conversationId);

        // 记录请求开始
        metrics.recordRequestStart();
        Timer.Sample totalTimer = metrics.startTotalTimer();

        // 获取配置
        int maxStep = properties.getMaxStep();
        Duration requestTimeout = Duration.ofSeconds(properties.getRequestTimeoutSeconds());
        AtomicBoolean timerStopped = new AtomicBoolean(false);

        // 启动异步编排——直接订阅 Flux，使用 Reactor 背压和调度器控制并发
        reActLoop(ctx, maxStep, requestTimeout)
                .doOnComplete(() -> {
                    handleSuccess(ctx, totalTimer);
                    timerStopped.set(true);
                })
                .doOnError(error -> {
                    handleError(error, ctx, totalTimer);
                    timerStopped.set(true);
                })
                .doFinally(signal -> {
                    // 确保在失败情况下也发送 STOP 事件
                    if (!"onComplete".equals(signal.name())) {
                        streamManager.emit(sessionId, ReActEventVo.stopEvent());
                        log.debug("[{}] 停止事件已发送: signal={}", requestId, signal);
                    }

                    // 停止计时器并记录指标
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
            // 刷新对话标题
            flushChatTitle(ctx);

            // 客户端取消时，如果 ReAct 流程仍在进行，主动清理资源
            if (RedisRetryUtils.safeHasKey(redisTemplate, reactStatusKey)) {
                log.info("[{}] 客户端取消，主动清理 ReAct 会话资源: sessionId={}", requestId(ctx), sessionId(ctx));
                // 清除 Redis 状态
                RedisRetryUtils.safeDelete(redisTemplate, reactStatusKey);
                // 释放 Sink 并发送 STOP 事件
                streamManager.release(sessionId(ctx));
                streamManager.tryEmit(sessionId(ctx), ReActEventVo.stopEvent());
                log.info("[{}] ReAct 会话资源已清理: sessionId={}", requestId(ctx), sessionId(ctx));
            }
        });
    }

    /**
     * 编排ReAct循环
     */
    private Flux<Void> reActLoop(AgentExecutionContext ctx, int maxStep, Duration requestTimeout) {
        String reactStatusKey = RedisConstant.REACT_STATUS_KEY_PREFIX + sessionId(ctx);

        return Mono.defer(() -> {
                    // 检查管道是否活跃
                    if (!RedisRetryUtils.safeHasKey(redisTemplate, reactStatusKey)) {
                        log.info("[{}] Redis 状态不存在，任务被停止: sessionId={}", requestId(ctx), sessionId(ctx));
                        return Mono.empty();
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
     * 单步执行ReAct (observe → think → act)
     */
    private Mono<Void> executeOneStep(AgentExecutionContext ctx) {
        String conversationId = conversationId(ctx);
        // observe
        return asyncExecutor.observe(ctx)
                .flatMap(observeResult -> {
                    // 发送规划事件
                    ReActOutput.StepTrace stepTrace = observeResult.stepTrace();
                    ReActEventVo.PlanData planData = new ReActEventVo.PlanData(
                            taskChain(ctx).size(),
                            stepTrace.getPreviousEvaluation(),
                            stepTrace.getMemory(),
                            stepTrace.getThinking(),
                            observeResult.taskContent()
                    );
                    emitEvent(ReActEventVo.newDataEvent(planData, ReActStageEnum.TASK_PLAN), ctx);

                    // 追加规划步骤到对话记忆
                    Map<String, Object> planDataMap = Map.of(
                            "type", ReActStageEnum.TASK_PLAN.getCode(),
                            "index", taskChain(ctx).size(),
                            "taskContent", planData.taskContent(),
                            "previousEvaluation", planData.previousEvaluation(),
                            "memory", planData.memory(),
                            "thinking", planData.thinking()
                    );

                    chatMemory.add(conversationId, chatMemoryRepository, repository -> {
                        ZAssistantMessage lastMsg = (ZAssistantMessage) repository.findLastByConversationId(conversationId);
                        lastMsg.addStep(planDataMap);
                        repository.deleteLastNByConversationId(conversationId, 1);
                        return lastMsg;
                    });
                    log.debug("[{}] 追加规划步骤: stepIndex={}", requestId(ctx), taskChain(ctx).size());

                    // think
                    return asyncExecutor.think(ctx)
                            .flatMap(thinkResult -> {
                                // 发送思考内容事件
                                String thinkingText = thinkResult.thinkResponse().getResult().getOutput().getText();
                                ReActEventVo.ThinkData thinkData;
                                String stepTraceJson = JsonFinder.findFirst(thinkingText);
                                if (StrUtil.isEmpty(stepTraceJson)) {
                                    thinkData = new ReActEventVo.ThinkData(StrUtil.isEmpty(thinkingText) ? planData.thinking() : thinkingText);
                                } else {
                                    ReActOutput reActOutput = JSON.parseObject(stepTraceJson, ReActOutput.class);
                                    thinkData = new ReActEventVo.ThinkData(reActOutput.getStepTrace().getThinking());
                                }
                                ReActEventVo thinkEvent = ReActEventVo.newDataEvent(thinkData, ReActStageEnum.STRATEGY_THINK);
                                emitEvent(thinkEvent, ctx);

                                // 追加思考步骤到对话记忆
                                Map<String, Object> thinkDataMap = Map.of(
                                        "type", ReActStageEnum.STRATEGY_THINK.getCode(),
                                        "thinkContent", thinkData.thinkContent()
                                );

                                chatMemory.add(conversationId, chatMemoryRepository, repository -> {
                                    ZAssistantMessage lastMsg = (ZAssistantMessage) repository.findLastByConversationId(conversationId);
                                    lastMsg.addStep(thinkDataMap);
                                    repository.deleteLastNByConversationId(conversationId, 1);
                                    return lastMsg;
                                });
                                log.debug("[{}] 追加思考步骤", requestId(ctx));

                                // act并发送行动结果事件
                                return asyncExecutor.act(thinkResult.thinkResponse(), ctx)
                                        .flatMap(actResult -> {
                                            // 发送行动结果事件
                                            ReActEventVo.ActionData actionData = new ReActEventVo.ActionData(
                                                    actResult.success(),
                                                    actResult.result()
                                            );
                                            ReActEventVo actEvent = ReActEventVo.newDataEvent(actionData, ReActStageEnum.ACTION_RESULT);
                                            emitEvent(actEvent, ctx);

                                            // 追加行动步骤到对话记忆
                                            Map<String, Object> actionDataMap = Map.of(
                                                    "type", ReActStageEnum.ACTION_RESULT.getCode(),
                                                    "success", actionData.success(),
                                                    "result", actionData.result(),
                                                    "resultType", 1 // 默认为文本类型
                                            );

                                            chatMemory.add(conversationId, chatMemoryRepository, repository -> {
                                                ZAssistantMessage lastMsg = (ZAssistantMessage) repository.findLastByConversationId(conversationId);
                                                lastMsg.addStep(actionDataMap);
                                                lastMsg.incrementStepCount(); // act步骤结束代表完整一步执行完毕，步数+1
                                                repository.deleteLastNByConversationId(conversationId, 1);
                                                return lastMsg;
                                            });
                                            log.debug("[{}] 追加行动步骤: success={}", requestId(ctx), actionData.success());

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
     * 处理成功
     */
    private void handleSuccess(AgentExecutionContext ctx, Timer.Sample totalTimer) {
        // 发送最终结果
        Object finalResult = finalResult(ctx);

        if (null != finalResult) {
            log.info("[{}] 任务完成，最终结果: {}", requestId(ctx), finalResult);
        } else {
            log.warn("[{}] 任务最终结果为空", requestId(ctx));
            finalResult = "任务已中断";
        }

        // 发送最终结果事件
        ReActEventVo finalResultEvent = ReActEventVo.newDataEvent(new ReActEventVo.FinalData((String) finalResult), ReActStageEnum.FINAL_SUMMARY);
        emitEvent(finalResultEvent, ctx);
        log.info("[{}] FINAL_SUMMARY 事件已发送: finalResult={}", requestId(ctx), finalResult);

        // 发送停止事件
        streamManager.emit(sessionId(ctx), ReActEventVo.stopEvent());
        log.info("[{}] STOP 事件已发送", requestId(ctx));

        // 追加最终结果记忆
        final Object ffResult = finalResult;
        chatMemory.add(conversationId(ctx), chatMemoryRepository, repository -> {
            ZAssistantMessage lastMsg = (ZAssistantMessage) chatMemoryRepository.findLastByConversationId(conversationId(ctx));
            AssistantMessage updatedMsg = new ZAssistantMessage(
                    (String) ffResult, // 更新content（AssistantMessage的content不可变）
                    lastMsg.getMetadata(),
                    lastMsg.getToolCalls(),
                    lastMsg.getMedia(),
                    lastMsg.getParams(),
                    lastMsg.getSteps(),
                    lastMsg.getStepCount()
            );
            chatMemoryRepository.deleteLastNByConversationId(conversationId(ctx), 1);
            return updatedMsg;
        });
        log.debug("[{}] 追加最终结果", requestId(ctx));

        // 记录资源信息
        metrics.recordSuccess();
        long durationNanos = metrics.recordTotalDuration(totalTimer);
        long durationMs = durationNanos / 1_000_000;

        log.info("[{}] 任务完成: conversationId={}, 耗时={}ms, 步数={}, tokens={}",
                requestId(ctx), conversationId(ctx), durationMs, stepCount(ctx).get(),
                tokenCounter(ctx).sum());

        metrics.recordSteps(stepCount(ctx).get());

        // 刷新对话标题
        flushChatTitle(ctx);
    }

    /**
     * 处理错误
     */
    private void handleError(Throwable error, AgentExecutionContext ctx, Timer.Sample totalTimer) {
        metrics.recordFailure(error.getClass().getSimpleName());
        log.error("[{}] 任务失败: conversationId={}, error={}", requestId(ctx), conversationId(ctx), error.getMessage(), error);

        // 在错误情况下追加提示信息
        String errorMsg = "任务已终止"/* + error.getMessage()*/;

        // 发送最终结果事件
        ReActEventVo finalResultEvent = ReActEventVo.newDataEvent(new ReActEventVo.FinalData(errorMsg), ReActStageEnum.FINAL_SUMMARY);
        emitEvent(finalResultEvent, ctx);
        log.info("[{}] FINAL_SUMMARY 事件已发送: finalResult={}", requestId(ctx), errorMsg);

        // 追加最终结果记忆
        chatMemory.add(conversationId(ctx), chatMemoryRepository, repository -> {
            ZAssistantMessage lastMsg = (ZAssistantMessage) chatMemoryRepository.findLastByConversationId(conversationId(ctx));
            // 追加错误信息到 content
            String currentContent = lastMsg.getText() != null ? lastMsg.getText() + errorMsg : "";
            AssistantMessage updatedMsg = new ZAssistantMessage(
                    currentContent,
                    lastMsg.getMetadata(),
                    lastMsg.getToolCalls(),
                    lastMsg.getMedia(),
                    lastMsg.getParams(),
                    lastMsg.getSteps(),
                    lastMsg.getStepCount()
            );
            chatMemoryRepository.deleteLastNByConversationId(conversationId(ctx), 1);
            return updatedMsg;
        });
        log.debug("[{}] 追加错误信息", requestId(ctx));

        // 刷新对话标题
        flushChatTitle(ctx);
    }

    // ==================== 辅助方法 ====================

    /**
     * 刷新对话标题
     */
    private void flushChatTitle(AgentExecutionContext ctx) {
        sessionService.flushChat(sessionId(ctx), userId(ctx), question(ctx), targetTask(ctx));
    }

    /**
     * 发送事件
     *
     * @param event 事件对象
     * @param ctx   执行上下文
     */
    private void emitEvent(ReActEventVo event, AgentExecutionContext ctx) {
        try {
            boolean success = streamManager.tryEmit(sessionId(ctx), event);
            if (!success) {
                log.warn("[{}] 事件发送失败: type={}, stage={}", requestId(ctx), event.getType(), event.getStage());
            } else {
                log.debug("[{}] 事件发送成功: type={}, stage={}, seq={}",
                        requestId(ctx), event.getType(), event.getStage(), event.getSequenceNumber());
            }
        } catch (Exception e) {
            log.error("[{}] 事件发送异常: type={}, stage={}, error={}",
                    requestId(ctx), event.getType(), event.getStage(), e.getMessage(), e);
        }
    }

}



