package org.dialectics.ai.agent.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
public class ChatClientConfig {
    /**
     * 阿里百炼 对话客户端
     */
    @Bean
    public ChatClient dashScopeChatClient(
            @Qualifier("dashScopeChatModel") ChatModel chatModel,
            Advisor loggerAdvisor,
            List<ToolCallbackProvider> providers
    ) {
        List<ToolCallback> toolCallbacks = new ArrayList<>();
        for (ToolCallbackProvider provider : providers) {
            toolCallbacks.addAll(Arrays.asList(provider.getToolCallbacks()));
        }
        return ChatClient.builder(chatModel)
                .defaultAdvisors(loggerAdvisor)
                .defaultToolCallbacks(toolCallbacks)
                .build();
    }

    // --------------------- Advisor ---------------------

    /**
     * 日志Advisor
     */
    @Bean
    public Advisor loggerAdvisor() {
        return new SimpleLoggerAdvisor();
    }

    /**
     * 基于Redis的会话记忆，聊天记忆整合到message列表中实现多轮对话
     */
    @Bean
    public Advisor messageChatMemoryAdvisor(ChatMemory chatMemory) {
        return MessageChatMemoryAdvisor.builder(chatMemory).build();
    }
}
