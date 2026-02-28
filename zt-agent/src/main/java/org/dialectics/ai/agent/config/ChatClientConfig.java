package org.dialectics.ai.agent.config;

import org.dialectics.ai.agent.memory.impl.ConcurrentChatMemory;
import org.dialectics.ai.agent.memory.repository.mongodb.MongoDBChatMemoryRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

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
            @Qualifier("dashscopeChatModel") ChatModel chatModel,
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

    /**
     * 标题总结客户端
     */
    @Bean
    public ChatClient summaryClient(
            @Qualifier("openAiChatModel") ChatModel chatModel,
            Advisor loggerAdvisor
    ) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(loggerAdvisor)
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
