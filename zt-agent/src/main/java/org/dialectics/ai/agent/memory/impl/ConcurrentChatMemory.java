package org.dialectics.ai.agent.memory.impl;

import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.util.Assert;

import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * <h4>线程安全ChatMemory实现
 * <p>对MessageWindowChatMemory进行装饰器增强
 */
public class ConcurrentChatMemory implements ChatMemory {
    private final MessageWindowChatMemory messageWindowChatMemory;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public ConcurrentChatMemory(MessageWindowChatMemory messageWindowChatMemory) {
        this.messageWindowChatMemory = messageWindowChatMemory;
    }

    /**
     * 添加单条消息（写）
     */
    public void add(@NotNull String conversationId, @NotNull Message message) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        Assert.notNull(message, "message cannot be null");
        this.add(conversationId, List.of(message));
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
