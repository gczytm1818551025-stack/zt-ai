package org.dialectics.ai.agent.memory;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;

import java.util.Map;
import java.util.function.Function;

public interface ChatMemory2Repository extends ChatMemoryRepository {
    Message findLastByConversationId(String conversationId);

    void deleteLastNByConversationId(String conversationId, int count);

    /**
     * 追加更新最后一条消息的 content 和 params
     * 用于 ReAct 模式下流式追加步骤内容
     *
     * @param conversationId 会话ID
     * @param contentSuffix 要追加到 content 的文本后缀
     * @param paramsUpdater params 更新函数，接收当前 params，返回更新后的 params
     */
    void appendToLastMessage(String conversationId, String contentSuffix,
                             Function<Map<String, Object>, Map<String, Object>> paramsUpdater);
}
