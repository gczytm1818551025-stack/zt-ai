package org.dialectics.ai.agent.agent;

import org.springframework.ai.chat.model.ChatModel;

public interface Agent {

    /**
     * 处理任务
     *
     * @param task    任务描述
     * @param context 任务上下文
     * @return 处理结果
     */
    Object process(String task, AgentExecutionContext context);

    /**
     * 获取大模型实例
     *
     * @return ChatModel实例
     */
    ChatModel chatModel();

    /**
     * 获取agent名称
     *
     * @return agent名称
     */
    default String name() {
        return this.getClass().getSimpleName();
    }

}
