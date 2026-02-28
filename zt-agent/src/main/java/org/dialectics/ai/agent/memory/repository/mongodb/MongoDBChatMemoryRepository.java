package org.dialectics.ai.agent.memory.repository.mongodb;

import cn.hutool.core.collection.CollStreamUtil;
import org.dialectics.ai.agent.domain.pojo.AssistantMessage2;
import org.dialectics.ai.agent.memory.ChatMemory2Repository;
import org.dialectics.ai.agent.utils.ChatMessageConverter;
import org.dialectics.ai.agent.domain.pojo.SessionMessageDocument;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * mongoDB会话记忆存储仓库实现
 */
public class MongoDBChatMemoryRepository implements ChatMemory2Repository {
    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    public List<String> findConversationIds() {
        Query query = new Query();
        query.fields().include("conversationId");
        List<SessionMessageDocument> records = mongoTemplate.find(query, SessionMessageDocument.class);

        return CollStreamUtil.toList(records, SessionMessageDocument::getConversationId);
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        SessionMessageDocument record = mongoTemplate.findOne(new Query(Criteria.where("conversationId").is(conversationId)), SessionMessageDocument.class);

        if (record == null) {
            return List.of();
        }
        return CollStreamUtil.toList(record.getMessages(), ChatMessageConverter::toMessage);
    }

    @Override
    public Message findLastByConversationId(String conversationId) {
        List<Message> messages = findByConversationId(conversationId);
        Assert.notEmpty(messages, "No messages found for conversationId " + conversationId);
        return messages.get(messages.size() - 1);
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        Assert.notEmpty(messages, "messages can't be empty");

        // 1.清除
        deleteByConversationId(conversationId);
        // 2.存储 (所有会话记忆存储为一个文档)
        SessionMessageDocument record = SessionMessageDocument.builder()
                .conversationId(conversationId)
                .messages(CollStreamUtil.toList(messages, ChatMessageConverter::toJson)) // 设置当前对话下的所有对话数据
                .build();
        mongoTemplate.save(record);
    }


    @Override
    public void deleteByConversationId(String conversationId) {
        mongoTemplate.remove(new Query(Criteria.where("conversationId").is(conversationId)), SessionMessageDocument.class);
    }

    @Override
    public void deleteLastNByConversationId(String conversationId, int count) {
        // 1. 获取会话记忆
        List<Message> messages = findByConversationId(conversationId);

        // 2. 排除倒数两条，重新存储
        List<Message> restMessages = messages.subList(0, messages.size() - 2);
        deleteByConversationId(conversationId);
        SessionMessageDocument record = SessionMessageDocument.builder()
                .conversationId(conversationId)
                .messages(CollStreamUtil.toList(restMessages, ChatMessageConverter::toJson)) // 设置当前对话下的所有对话数据
                .build();
        mongoTemplate.save(record);
    }

    @Override
    public void appendToLastMessage(String conversationId, String contentSuffix, Function<Map<String, Object>, Map<String, Object>> paramsUpdater) {
        // 1. 获取当前消息列表
        List<Message> messages = findByConversationId(conversationId);
        if (messages.isEmpty()) {
            throw new IllegalStateException("No messages found for conversationId: " + conversationId);
        }

        // 2. 获取最后一条消息
        Message lastMessage = messages.get(messages.size() - 1);
        if (!(lastMessage instanceof AssistantMessage2)) {
            throw new IllegalStateException("Last message is not an AssistantMessage2");
        }

        AssistantMessage2 lastMsg = (AssistantMessage2) lastMessage;

        // 3. 追加 content
        String currentContent = lastMsg.getText() != null ? lastMsg.getText() : "";
        String newContent = currentContent + contentSuffix;

        // 4. 更新 params
        Map<String, Object> currentParams = lastMsg.getParams();
        Map<String, Object> newParams = paramsUpdater.apply(currentParams != null ? currentParams : Map.of());

        // 5. 创建新消息对象
        AssistantMessage2 updatedMessage = new AssistantMessage2(
                newContent,
                lastMsg.getMetadata(),
                lastMsg.getToolCalls(),
                lastMsg.getMedia(),
                newParams
        );

        // 6. 替换最后一条消息
        List<Message> updatedMessages = new ArrayList<>(messages.subList(0, messages.size() - 1));
        updatedMessages.add(updatedMessage);

        // 7. 保存
        saveAll(conversationId, updatedMessages);
    }
}
