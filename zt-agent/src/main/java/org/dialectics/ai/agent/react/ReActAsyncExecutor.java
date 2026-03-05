package org.dialectics.ai.agent.react;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.dashscope.spec.DashScopeApiSpec;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.dialectics.ai.agent.AgentExecutionContext;
import org.dialectics.ai.agent.config.properties.ReActExecProperties;
import org.dialectics.ai.agent.domain.pojo.ReActOutput;
import org.dialectics.ai.agent.domain.pojo.TaskNode;
import org.dialectics.ai.agent.utils.ChatSessionVisitor;
import org.dialectics.ai.common.exception.ReActExecutionException;
import org.dialectics.ai.agent.manager.PromptManager;
import org.dialectics.ai.common.constants.PromptNameConstant;
import org.dialectics.ai.common.utils.JsonFinder;
import org.dialectics.ai.common.utils.RenderUtil;
import org.dialectics.ai.skills.Skills;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import jakarta.annotation.Resource;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import io.micrometer.core.instrument.Timer;

import static org.dialectics.ai.agent.utils.ReActControlVisitor.*;

/**
 * ReAct 异步执行器
 * <p>
 * 核心职责：
 * 1. 将同步的 LLM 调用封装为异步 Mono
 * 2. 提供 observe、think、act 的异步版本
 * 3. 支持取消和超时
 * 4. 集成性能指标收集
 * 5. 使用专门的调度器隔离不同类型的任务
 * <p>
 * 线程安全说明：
 * - Spring AI 的 OpenAiChatModel 和 DashScopeChatModel 是线程安全的
 * - 使用 llmScheduler 并发执行 LLM 调用
 * - 使用 toolScheduler 执行工具调用
 */
@Slf4j
@Component
public class ReActAsyncExecutor {
    @Resource(name = "dashScopeChatModel")
    private ChatModel mainModel; // 暂时仅使用同一个模型
    @Autowired
    private Skills skills;

    @Resource(name = "reActExecutorProperties")
    private ReActExecProperties properties;
    @Resource(name = "llmScheduler")
    private Scheduler llmScheduler;
    @Resource(name = "toolScheduler")
    private Scheduler toolScheduler;
    @Autowired
    private ReActPerformanceMetrics metrics;

    /**
     * 异步执行observe阶段
     * <p>
     * 观察前一步骤的行动结果，规划当前子任务
     *
     * @param ctx 执行上下文
     * @return ObserveResult 的 Mono
     */
    public Mono<ObserveResult> observe(AgentExecutionContext ctx) {
        String conversationId = ChatSessionVisitor.conversationId(ctx);
        List<Message> messages = messageMemory(ctx);
        return Mono.fromCallable(() -> {
                    Timer.Sample timer = metrics.startObserveTimer();
                    try {
                        ObserveResult result = doObserve(messages, ctx);
                        log.debug("[{}] Observe 完成: taskContent={}", conversationId, result.taskContent());
                        return result;
                    } finally {
                        metrics.recordObserveDuration(timer);
                    }
                })
                .subscribeOn(llmScheduler)
                .timeout(Duration.ofSeconds(properties.getLlmTimeoutSeconds()), Mono.error(
                        new ReActExecutionException("Observe超时", new java.util.concurrent.TimeoutException())
                ))
                .doOnError(e -> {
                    log.error("[{}] Observe 失败: error={}", conversationId, e.getMessage());
                    metrics.recordFailure("observe_error");
                });
    }

    /**
     * observe阶段同步流程
     */
    private ObserveResult doObserve(List<Message> messages, AgentExecutionContext ctx) {
        String conversationId = ChatSessionVisitor.conversationId(ctx);
        // 刷新历史任务记忆，重装系统提示并装配新的任务
        flushMessagesForNewTaskNode(messages, ctx);

        // 总结历史任务链
        String taskResultHistory = subResultChain(ctx).stream()
                .reduce((x, y) -> x + " -> " + y)
                .orElse("尚无子任务，无需总结");

        // 提示规划下一子任务
        messages.add(UserMessage.builder().text(PromptManager.renderFrom(
                PromptNameConstant.TASK_PLAN,
                Map.of(
                        "targetTask", targetTask(ctx),
                        "latestTaskHistory", taskResultHistory,
                        "skillsMetadata", skills.metadata(),
                        "currentStep", stepCount(ctx).get(),
                        "maxStep", properties.getMaxStep()
                )
        )).build());
        // 检查终止标志并添加终止提示
        if (terminatedFlag(ctx).get()) {
            messages.add(UserMessage.builder().text(terminateText()).build());
            log.debug("[{}] 终止提示已恢复到 messages: conversationId={}", ChatSessionVisitor.requestId(ctx), conversationId);
        }

        ToolCallback toolCallback = toolCallback(ctx);
        ChatResponse chatResponse = doCallLLM(mainModel, messages, toolCallback);

        // 统计 token
        if (chatResponse.getMetadata().getUsage() != null) {
            long tokens = chatResponse.getMetadata().getUsage().getTotalTokens();
            tokenCounter(ctx).add(tokens);
            metrics.recordTokenConsumption(tokens);
            log.info("[{}] 任务规划后消耗总token数：{}", conversationId, tokenCounter(ctx).sum());
        }

        ReActOutput reActOutput = parseStepTrace(chatResponse.getResult().getOutput(), messages, toolCallback);
        String resultJson = toolCallback.call(JSON.toJSONString(reActOutput));

        // 解析大模型的子任务规划意图
        ReActOutputToolCallback.Result planResult = JSON.parseObject(resultJson, ReActOutputToolCallback.Result.class);
        if (!planResult.success()) {
            throw new ReActExecutionException("子任务规划失败");
        }
        TaskNode nextNode = JSON.parseObject(JsonFinder.findFirst(planResult.resultContent()), TaskNode.class);
        nextNode.setThinking(reActOutput.getStepTrace().getThinking());
        taskChain(ctx).add(nextNode);
        log.info("[{}] 成功规划子任务: {}", conversationId, nextNode);

        // 清晰描述当前规划好的子任务
        List<String> subResultChain = subResultChain(ctx);
        String latestResult = subResultChain.isEmpty() ? "第一个子任务尚未执行"
                : subResultChain.get(subResultChain.size() - 1);

        messages.remove(messages.size() - 1);
        messages.add(UserMessage.builder().text(PromptManager.renderFrom(
                PromptNameConstant.TASK_DESC,
                Map.of(
                        "index", taskChain(ctx).size(),
                        "taskNode", JSON.toJSONString(nextNode),
                        "latestResult", latestResult,
                        "skill", skills.get(nextNode.getSkillName())
                )
        )).build());

        return new ObserveResult(nextNode.getTaskContent(), reActOutput.getStepTrace());
    }

    /**
     * 异步执行think阶段
     * <p>
     * 思考当前子任务的完成策略
     *
     * @param context 执行上下文
     * @return ThinkResult 的 Mono
     */
    public Mono<ThinkResult> think(AgentExecutionContext context) {
        String conversationId = ChatSessionVisitor.conversationId(context);
        ToolCallback toolCallback = toolCallback(context);
        List<Message> messages = messageMemory(context);

        return Mono.fromCallable(() -> {
                    Timer.Sample timer = metrics.startThinkTimer();
                    try {
                        ChatResponse chatResponse = doCallLLM(mainModel, messages, toolCallback);

                        // 统计 token
                        if (chatResponse.getMetadata().getUsage() != null) {
                            long tokens = chatResponse.getMetadata().getUsage().getTotalTokens();
                            tokenCounter(context).add(tokens);
                            metrics.recordTokenConsumption(tokens);
                            log.info("[{}] 思考后消耗总token数：{}", conversationId, tokenCounter(context).sum());
                        }

                        return new ThinkResult(chatResponse);
                    } finally {
                        metrics.recordThinkDuration(timer);
                    }
                })
                .subscribeOn(llmScheduler)
                .timeout(Duration.ofSeconds(properties.getLlmTimeoutSeconds()), Mono.error(
                        new ReActExecutionException("Think超时", new java.util.concurrent.TimeoutException())
                ))
                .doOnError(e -> {
                    log.error("[{}] Think 失败: error={}", conversationId, e.getMessage());
                    metrics.recordFailure("think_error");
                });
    }

    /**
     * 异步执行act阶段
     * <p>
     * 根据思考结果执行动作
     *
     * @param thinkResponse LLM 响应
     * @param context       执行上下文
     * @return ActResult 的 Mono
     */
    public Mono<ActResult> act(ChatResponse thinkResponse, AgentExecutionContext context) {
        String conversationId = ChatSessionVisitor.conversationId(context);
        List<Message> messages = messageMemory(context);
        return Mono.fromCallable(() -> {
                    Timer.Sample timer = metrics.startActTimer();
                    try {
                        ActResult result = doAct(thinkResponse, messages, context);
                        log.debug("[{}] Act 完成: success={}", conversationId, result.success());
                        return result;
                    } finally {
                        metrics.recordActDuration(timer);
                    }
                })
                .subscribeOn(toolScheduler)
                .timeout(Duration.ofSeconds(properties.getToolTimeoutSeconds()), Mono.error(
                        new ReActExecutionException("Act超时", new TimeoutException())
                ))
                .doOnError(e -> {
                    log.error("[{}] Act 失败: error={}", conversationId, e.getMessage());
                    metrics.recordFailure("act_error");
                });
    }

    /**
     * act同步流程
     */
    private ActResult doAct(ChatResponse thinkResponse, List<Message> messages, AgentExecutionContext ctx) {
        ToolCallback toolCallback = toolCallback(ctx);
        AssistantMessage assistantMessage = thinkResponse.getResult().getOutput();
        List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls();
        StringBuilder trBuilder = new StringBuilder();

        boolean success;
        if (CollUtil.isNotEmpty(toolCalls)) {
            AssistantMessage.ToolCall tool = toolCalls.get(0);
            String toolName = tool.name();

            if (hasReActToolCall(toolName, toolCallback)) {
                ReActOutput reActOutput = parseStepTrace(thinkResponse.getResult().getOutput(), messages, toolCallback);
                ReActOutputToolCallback.Result toolResult = callReActTool(reActOutput, toolCallback);
                success = toolResult.success();
                trBuilder.append(toolResult.resultContent());
            } else {
                var actions = ((ReActOutputToolCallback.MetaData) toolCallback(ctx).getToolMetadata()).getActionMap();
                success = callSkillTool(actions, trBuilder, toolCalls);
            }
        } else {
            String stepTraceJson = JsonFinder.findFirst(assistantMessage.getText());
            if (StrUtil.isNotEmpty(stepTraceJson)) {
                ReActOutput reActOutput = JSON.parseObject(stepTraceJson, ReActOutput.class);
                ReActOutputToolCallback.Result toolResult = callReActTool(reActOutput, toolCallback);
                success = toolResult.success();
                trBuilder.append(toolResult.resultContent());
            } else {
                // 根据LLM返回的文本内容判断是否真的失败
                String assistantText = assistantMessage.getText();
                if (StrUtil.isNotBlank(assistantText)) {
                    // 有文本内容，可能是正常的思考或回复，不标记为失败
                    trBuilder.append("无动作执行，返回内容为: ").append(assistantText).append("\n");
                    success = true;
                } else {
                    // 文本内容为空，才标记为失败
                    trBuilder.append("无动作执行，且无有效回复内容\n");
                    success = false;
                }
            }
        }

        // 总结动作结果
        List<TaskNode> taskChain = taskChain(ctx);
        TaskNode node = taskChain.get(taskChain.size() - 1);
        String callResult = trBuilder.toString();

        node.setSuccess(success);
        node.setResult(callResult);
        String actionSummary = RenderUtil.render("根据思路【{{thinking}}】的行动结果如下: {{result}}",
                Map.of("thinking", node.getThinking(), "result", callResult));

        subResultChain(ctx).add(actionSummary);

        return new ActResult(success, callResult);
    }

    // ==================== 大模型和工具调用方法 ====================

    /**
     * 执行 LLM 调用（同步方法）
     *
     * <p>
     * 私有方法，由 callLLMAsync 异步调用
     */
    private ChatResponse doCallLLM(ChatModel model, List<Message> messages, org.springframework.ai.tool.ToolCallback toolCallback) {
        return model.call(Prompt.builder()
                .messages(messages)
                .chatOptions(DashScopeChatOptions.builder()
                        .toolCallbacks(List.of(toolCallback))
                        .toolChoice(DashScopeApiSpec.ChatCompletionRequestParameter.ToolChoiceBuilder.function(toolCallback.getToolDefinition().name()))
                        .internalToolExecutionEnabled(false)
                        .build())
                .build());
    }

    /**
     * 调用特定技能工具链，拼接调用结果
     */
    private boolean callSkillTool(Map<String, Function<Map<String, Object>, String>> actions, StringBuilder trBuilder, List<AssistantMessage.ToolCall> tools) {
        boolean success = true;
        Timer.Sample timer = metrics.startToolCallTimer();
        try {
            for (AssistantMessage.ToolCall tool : tools) {
                String toolName = tool.name();
                String arguments = tool.arguments();
                try {
                    String result = actions.get(toolName).apply(JSON.parseObject(arguments));
                    trBuilder.append(toolName).append("动作执行成功，结果为：").append(result).append("\n");
                } catch (Exception e) {
                    trBuilder.append(toolName).append("动作执行出错，报错为：").append(e.getMessage()).append("\n");
                    log.error("调用的技能工具 {} 执行出错：{}", toolName, e.getStackTrace());
                    success = false;
                }
            }
        } finally {
            metrics.recordToolCallDuration(timer);
        }
        return success;
    }

    /**
     * 调用虚拟主工具，拼接调用结果
     */
    private ReActOutputToolCallback.Result callReActTool(ReActOutput reActOutput, ToolCallback toolCallback) {
        Timer.Sample timer = metrics.startToolCallTimer();
        try {
            String resultJson = toolCallback.call(JSON.toJSONString(reActOutput));
            ReActOutputToolCallback.Result toolResult = JSON.parseObject(resultJson, ReActOutputToolCallback.Result.class);
            return toolResult;
        } finally {
            metrics.recordToolCallDuration(timer);
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 从大模型回复中解析步骤跟踪实体
     */
    private ReActOutput parseStepTrace(AssistantMessage assistantMessage, List<Message> messages, ToolCallback toolCallback) {
        List<AssistantMessage.ToolCall> toolCalls;
        if (CollUtil.isNotEmpty(toolCalls = assistantMessage.getToolCalls())) {
            AssistantMessage.ToolCall mainTool = toolCalls.get(0);
            if (!hasReActToolCall(mainTool.name(), toolCallback)) {
                throw new RuntimeException("ReAct工具域找不到，步调跟踪信息解析失败。期望工具: " + toolCallback.getToolDefinition().name() + ", 实际工具: " + mainTool.name());
            }
            return JSON.parseObject(mainTool.arguments(), ReActOutput.class);
        } else {
            String stepTraceJson = JsonFinder.findFirst(assistantMessage.getText());
            if (StrUtil.isEmpty(stepTraceJson)) {
                return ReActOutput.noAction(assistantMessage, messages);
            }
            return JSON.parseObject(stepTraceJson, ReActOutput.class);
        }
    }

    /**
     * 判断是否调用了ReAct工具域
     *
     * @param toolName     工具名称
     * @param toolCallback 域工具
     * @return 如果是域工具则返回 true
     */
    private boolean hasReActToolCall(String toolName, ToolCallback toolCallback) {
        if (toolName == null || toolCallback == null) {
            return false;
        }
        return toolCallback.getToolDefinition().name().equalsIgnoreCase(toolName);
    }

    /**
     * 刷新记忆消息列表，为新子任务节点准备
     * <p>
     * 优化方案：保留历史消息，根据需要限制长度以控制上下文窗口大小
     * <p>
     * 消息保留策略：
     * 1. 系统消息：始终保留并更新
     * 2. 原始用户问题：始终保留（在系统消息之后）
     * 3. 历史对话消息：根据配置保留最近 N 条
     */
    private void flushMessagesForNewTaskNode(List<Message> messages, AgentExecutionContext context) {
        messages.clear();
        // 重新添加系统消息（包含最新的任务信息和技能元数据）
        messages.add(SystemMessage.builder().text(PromptManager.renderFrom(PromptNameConstant.REACT_SYSTEM)).build());
    }

    // ==================== 结果类 ====================

    /**
     * Observe 阶段结果
     */
    public record ObserveResult(
            String taskContent,
            ReActOutput.StepTrace stepTrace
    ) {
    }

    /**
     * Think 阶段结果
     */
    public record ThinkResult(
            ChatResponse thinkResponse
    ) {
    }

    /**
     * Act 阶段结果
     */
    public record ActResult(
            /// 动作是否成功
            Boolean success,
            /// 行动结果内容
            String result
    ) {
    }

}
