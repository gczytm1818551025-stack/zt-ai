package org.dialectics.ai.agent.memory.impl;

import org.dialectics.ai.agent.memory.ZChatMemory;
import org.dialectics.ai.agent.memory.ZChatMemoryRepository;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.Message;

import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

/**
 * <h4>线程安全ChatMemory实现
 * <p>对MessageWindowChatMemory进行装饰器增强
 */
public class ConcurrentChatMemory implements ZChatMemory {
    private final MessageWindowChatMemory messageWindowChatMemory;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public ConcurrentChatMemory(MessageWindowChatMemory messageWindowChatMemory) {
        this.messageWindowChatMemory = messageWindowChatMemory;
    }

    @Override
    public void add(String conversationId, ZChatMemoryRepository repository, Function<ZChatMemoryRepository, Message> beforeHandle) {
        lock.writeLock().lock();
        try {
            Message message = beforeHandle.apply(repository);
            messageWindowChatMemory.add(conversationId, List.of(message));
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 批量添加消息（写）
     */
    @Override
    public void add(@NotNull String conversationId, @NotNull List<Message> messages) {
        lock.writeLock().lock();
        try {
            messageWindowChatMemory.add(conversationId, messages);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 获取消息记录（读）
     */
    @NotNull
    @Override
    public List<Message> get(@NotNull String conversationId) {
        lock.readLock().lock();
        try {
            return messageWindowChatMemory.get(conversationId);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 清空记录（写）
     */
    @Override
    public void clear(@NotNull String conversationId) {
        lock.writeLock().lock();
        try {
            messageWindowChatMemory.clear(conversationId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ChatMemoryRepository chatMemoryRepository;
        private int maxMessages = 20;

        private Builder() {
        }

        public Builder chatMemoryRepository(ChatMemoryRepository chatMemoryRepository) {
            this.chatMemoryRepository = chatMemoryRepository;
            return this;
        }

        public Builder maxMessages(int maxMessages) {
            this.maxMessages = maxMessages;
            return this;
        }

        public ConcurrentChatMemory build() {
            if (this.chatMemoryRepository == null) {
                this.chatMemoryRepository = new InMemoryChatMemoryRepository();
            }

            return new ConcurrentChatMemory(MessageWindowChatMemory.builder().chatMemoryRepository(this.chatMemoryRepository).maxMessages(this.maxMessages).build());
        }
    }
}
