package org.dialectics.ai.agent.memory;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;

import java.util.function.Function;

public interface ZChatMemory extends ChatMemory {
    void add(String conversationId, ZChatMemoryRepository repository, Function<ZChatMemoryRepository, Message> beforeHandle);
}
