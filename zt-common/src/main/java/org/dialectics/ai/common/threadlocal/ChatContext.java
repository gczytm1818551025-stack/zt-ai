package org.dialectics.ai.common.threadlocal;

import lombok.extern.slf4j.Slf4j;

/**
 * 聊天会话 ThreadLocal
 * 用于存储当前执行线程的 ConversationId
 */
@Slf4j
public class ChatContext {

    private static final ThreadLocal<String> LOCAL = new ThreadLocal<>();

    private ChatContext() {
    }

    /**
     * 将 conversationId 放到 ThreadLocal 中
     *
     * @param conversationId 会话ID
     */
    public static void set(String conversationId) {
        LOCAL.set(conversationId);
    }

    /**
     * 从 ThreadLocal 中获取 conversationId
     */
    public static String get() {
        return LOCAL.get();
    }

    /**
     * 从当前线程中删除 conversationId
     */
    public static void remove() {
        LOCAL.remove();
    }
}
