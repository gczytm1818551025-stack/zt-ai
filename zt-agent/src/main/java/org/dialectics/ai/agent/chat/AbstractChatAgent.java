package org.dialectics.ai.agent.chat;

import org.dialectics.ai.agent.Agent;
import org.dialectics.ai.agent.AgentExecutionContext;
import org.dialectics.ai.agent.utils.ChatSessionVisitor;
import org.dialectics.ai.common.domain.EventVo;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

public abstract class AbstractChatAgent implements Agent {

    protected static final Object[] EMPTY_TOOLS = new Object[0];

    /**
     * 终止会话
     *
     * @param sessionId 需要终止的会话ID
     */
    public abstract void stop(String sessionId);

    /**
     * 聊天任务处理，流式回复
     *
     * @param question 任务描述
     * @param context  任务上下文
     * @return 处理结果
     */
    @Override
    public abstract Flux<? extends EventVo> process(String question, AgentExecutionContext context);

    /**
     * 构造聊天智能体通用参数
     */
    protected ChatClient.ChatClientRequestSpec buildChatRequestSpec(AgentExecutionContext context) {
        return chatClient().prompt()
                // 系统提示词模板及其所需参数
                .system(s -> s.text(systemMessage()).params(systemMessageParams(context)))
                // 用户提示词
                .user(ChatSessionVisitor.question(context))
                // 所需tool列表
                .tools(tools())
                // tool调用所需参数
                .toolContext(toolContext(context))
                // 在默认advisors的基础上添加特定advisor集及所需参数
                .advisors(advisor -> advisor.advisors(advisors()).params(advisorParams(context)));
    }

    public abstract ChatClient chatClient();

    @Override
    public final ChatModel chatModel() {
        throw new UnsupportedOperationException("For chatModel, you shouldn't directly get chatModel but to get chatClient");
    }

    /**
     * 获取系统提示词模板
     *
     * @return 默认返回空字符串
     */
    String systemMessage() {
        return "你是智瞳对话助手，知识渊博，思维敏捷，能够回答用户关于各个领域的疑难问题";
    }

    /**
     * 获取系统提示词模板的参数
     *
     * @return 默认返回空Map
     */
    Map<String, Object> systemMessageParams(AgentExecutionContext context) {
        return Map.of();
    }


    /**
     * 获取tool列表
     *
     * @return 默认返回空数组
     */
    Object[] tools() {
        return EMPTY_TOOLS;
    }

    /**
     * 获取ToolCalling所需上下文
     *
     * @param context 当前Agent执行上下文
     * @return 默认返回空Map
     */
    Map<String, Object> toolContext(AgentExecutionContext context) {
        return Map.of();
    }

    /**
     * 获取Advisor列表
     *
     * @return 默认返回空列表
     */
    List<Advisor> advisors() {
        return List.of();
    }

    /**
     * 获取Advisor参数
     *
     * @param context 当前Agent执行上下文
     * @return 默认返回空Map
     */
    Map<String, Object> advisorParams(AgentExecutionContext context) {
        return Map.of();
    }

}
