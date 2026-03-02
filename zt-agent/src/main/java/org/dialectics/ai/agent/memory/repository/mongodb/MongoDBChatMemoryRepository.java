package org.dialectics.ai.agent.memory.repository.mongodb;

import cn.hutool.core.collection.CollStreamUtil;
import org.dialectics.ai.agent.memory.ZChatMemoryRepository;
import org.dialectics.ai.agent.utils.ChatMessageConverter;
import org.dialectics.ai.agent.domain.pojo.SessionMessageDocument;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.util.Assert;

import java.util.List;

/**
 * mongoDB会话记忆存储仓库实现
 */
public class MongoDBChatMemoryRepository implements ZChatMemoryRepository {
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
        List<Message> restMessages = messages.subList(0, messages.size() - count);
        deleteByConversationId(conversationId);
        SessionMessageDocument record = SessionMessageDocument.builder()
                .conversationId(conversationId)
                .messages(CollStreamUtil.toList(restMessages, ChatMessageConverter::toJson)) // 设置当前对话下的所有对话数据
                .build();
        mongoTemplate.save(record);
    }
}
