package org.dialectics.ai.agent.react;

import lombok.extern.slf4j.Slf4j;
import org.dialectics.ai.agent.Agent;
import org.dialectics.ai.agent.AgentExecutionContext;
import org.dialectics.ai.agent.domain.pojo.TaskNode;
import org.dialectics.ai.agent.domain.vo.ReActEventVo;
import org.dialectics.ai.agent.memory.ZChatMemory;
import org.dialectics.ai.agent.memory.ZChatMemoryRepository;
import org.dialectics.ai.agent.utils.ChatSessionVisitor;
import org.dialectics.ai.common.enums.ChatSessionParamEnum;
import org.dialectics.ai.common.enums.GenerateTypeEnum;
import org.dialectics.ai.common.enums.ReActParamEnum;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

import static org.dialectics.ai.agent.utils.ReActControlVisitor.completed;
import static org.dialectics.ai.common.enums.ReActParamEnum.*;

/**
 * ReAct任务智能体
 * <p>
 * 优化点：
 * 1. 全异步执行，不阻塞线程
 * 2. 使用 Flux.create，支持背压和取消
 * 3. 委托给 ReActFlowOrchestrator 进行流程编排
 * 4. 委托给 ReActAsyncExecutor 进行异步执行
 * 5. 集成并发控制和性能监控
 * 6. 每个请求的 SSE 推送互不干扰
 * <p>
 * 架构说明：
 * - Service层总创建新的AgentExecutionContext对象，保证多线程请求上下文隔离性
 * - 委托给 FlowOrchestrator 创建独立的 Flux
 * - FlowOrchestrator 使用 Flux.create 创建独立的 Sink
 * - AsyncExecutor 使用 Reactor 实现异步调用
 */
@Slf4j
@Component
public class ReActTaskAgent implements Agent {
    @Autowired
    private ReActFlowOrchestrator orchestrator;
    @Autowired
    private ZChatMemory chatMemory;
    @Autowired
    private ZChatMemoryRepository chatMemoryRepository;
    @Autowired(required = false)
    protected List<ToolCallbackProvider> toolCallbackProviders;

    @Override
    public ChatModel chatModel() {
        throw new UnsupportedOperationException("chatModel is not support to get directly");
    }

    /**
     * 处理 ReAct 任务
     * <p>
     * 流程：
     * 1. 初始化 ReAct 基础参数
     * 2. 加载工具链容器
     * 3. 委托给编排器执行
     *
     * @param task    任务描述
     * @param context 执行上下文
     * @return ReAct 事件流
     */
    @Override
    public Flux<ReActEventVo> process(String task, AgentExecutionContext context) {
        String conversationId = ChatSessionVisitor.conversationId(context);
        GenerateTypeEnum generateType = ChatSessionVisitor.generateType(context);

        // 如果是"重新生成"类型的对话，删除最后一对问答记忆
        if (GenerateTypeEnum.REGENERATE.equals(generateType)) {
            chatMemoryRepository.deleteLastNByConversationId(conversationId, 2);
        }

        chatMemory.add(conversationId, UserMessage.builder().text(task).build());

        log.debug("[{}] 开始处理 ReAct 任务: task={}", conversationId, task);
        // 1. 初始化 ReAct 基础参数
        initReActAttributes(task, context);
        // 2. 加载工具链容器
        loadReActTools(context);
        // 3. 委托给编排器执行
        return orchestrator.orchestrate(task, context);
    }

    /**
     * 初始化ReAct流程控制参数
     * <p>
     * 统一状态初始化入口，所有ReAct相关状态在此处初始化
     * <p>
     * 包含：
     * 1. ReAct流程基础参数（任务、记忆、步数等）
     * 2. 会话相关参数（由Service层设置）
     * 3. 流程控制标志（取消、完成等）
     */
    private void initReActAttributes(Object task, AgentExecutionContext context) {
        String requestId = UUID.randomUUID().toString();
        context.set(Map.of(
                ChatSessionParamEnum.REQUEST_ID, requestId,
                ChatSessionParamEnum.QUESTION, task,
                // 流程控制标志
                REACT_TERMINATED_FLAG, new AtomicBoolean(false)
        ));
        context.set(Map.of(
                TARGET_TASK, task,
                MESSAGE_MEMORY, new ArrayList<Message>(),
                TASK_CHAIN, new ArrayList<TaskNode>(),
                SUB_RESULT_CHAIN, new ArrayList<String>(),
                STEP_COUNT, new AtomicInteger(0),
                COMPLETED, new AtomicBoolean(false),
                TOKEN_COUNTER, new LongAdder()
        ));
    }

    /**
     * 初始化工具链容器
     */
    private void loadReActTools(AgentExecutionContext ctx) {
        ToolCallback reActOutputTool = new ReActOutputToolCallback(toolCallbacks(ctx));
        ctx.set(TOOL_CALLBACK, reActOutputTool);
        log.debug("工具域加载完成: toolDomainName={}", reActOutputTool.getToolDefinition().name());
    }

    /**
     * 获取主要工具提供者
     * <p>
     * 包含 Done 和 Plan 工具，以及所有注册的工具提供者
     *
     * @param ctx 执行上下文
     * @return 工具提供者列表
     */
    private List<ToolCallback> toolCallbacks(AgentExecutionContext ctx) {
        List<ToolCallback> toolCallbacks = new ArrayList<>(Arrays.asList(ToolCallbacks.from(
                new DoneTools(ctx),
                new PlanTools()
        )));
        for (ToolCallbackProvider provider : toolCallbackProviders) {
            toolCallbacks.addAll(Arrays.asList(provider.getToolCallbacks()));
        }
        return toolCallbacks;
    }

    /**
     * Plan工具
     */
    protected record PlanTools() {
        @Tool(name = "generateNext", description = "规划下一个子任务节点")
        public TaskNode generateNext(
                @ToolParam(description = "skill名称") String skillName,
                @ToolParam(description = "子任务内容") String taskContent
        ) {
            return TaskNode.builder().skillName(skillName).taskContent(taskContent).build();
        }
    }

    /**
     * Done工具
     */
    protected record DoneTools(AgentExecutionContext context) {
        @Tool(name = "done", description = "任务完成")
        public String done(
                @ToolParam(description = "任务完成情况的最终总结") String text,
                @ToolParam(description = "任务是否成功") Boolean success
        ) {
            completed(context).set(true);
            context.set(ReActParamEnum.FINAL_RESULT, text);
            return text;
        }
    }
}
