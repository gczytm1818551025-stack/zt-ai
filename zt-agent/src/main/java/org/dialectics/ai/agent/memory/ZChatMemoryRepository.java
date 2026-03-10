package org.dialectics.ai.agent.memory;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;

public interface ZChatMemoryRepository extends ChatMemoryRepository {
    Message findLastByConversationId(String conversationId);

    void deleteLastNByConversationId(String conversationId, int count);

}
