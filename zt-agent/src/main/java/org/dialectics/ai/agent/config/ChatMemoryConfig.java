package org.dialectics.ai.agent.config;

import org.dialectics.ai.agent.memory.impl.ConcurrentChatMemory;
import org.dialectics.ai.agent.memory.repository.mongodb.MongoDBChatMemoryRepository;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

@Configuration
public class ChatMemoryConfig {
    @Value("${zt-ai.memory.maxMessages:100}")
    private Integer maxMessages;

    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository mongoDBChatMemoryRepository) {
        return ConcurrentChatMemory.builder()
                .chatMemoryRepository(mongoDBChatMemoryRepository)
                .maxMessages(maxMessages)
                .build();
    }

    @Bean
    @ConditionalOnClass(MongoTemplate.class)
    public ChatMemoryRepository mongoDBChatMemoryRepository() {
        return new MongoDBChatMemoryRepository();
    }

}
