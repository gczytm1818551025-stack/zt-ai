package org.dialectics.ai.server.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.dialectics.ai.agent.AgentFactory;
import org.dialectics.ai.agent.AgentExecutionContext;
import org.dialectics.ai.agent.react.ReActTaskAgent;
import org.dialectics.ai.agent.domain.vo.ReActEventVo;
import org.dialectics.ai.agent.manager.ReActStreamManager;
import org.dialectics.ai.agent.service.ChatService;
import org.dialectics.ai.common.constants.RedisConstant;
import org.dialectics.ai.common.utils.RedisRetryUtils;
import org.dialectics.ai.common.enums.AgentEnum;
import org.dialectics.ai.common.enums.ChatSessionParamEnum;
import org.dialectics.ai.common.enums.GenerateTypeEnum;
import org.dialectics.ai.common.threadlocal.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Map;

@Slf4j
@Service("reActChatService")
public class ReActChatServiceImpl implements ChatService {
    @Autowired
    private AgentFactory agentFactory;
    @Autowired
    private ReActStreamManager streamManager;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public Flux<ReActEventVo> chat(String question, String sessionId, GenerateTypeEnum generateType) {
        // 获取ReAct任务智能体
        ReActTaskAgent reActTaskAgent = (ReActTaskAgent) agentFactory.getAgent(AgentEnum.ReActTaskAgent);
        // 构建对话上下文
        AgentExecutionContext context = new AgentExecutionContext();
        String conversationId = ChatService.getConversationId(sessionId, String.valueOf(UserContext.get()));
        context.set(Map.of(
                ChatSessionParamEnum.SESSION_ID, sessionId,
                ChatSessionParamEnum.CONVERSATION_ID, conversationId,
                ChatSessionParamEnum.USER_ID, UserContext.get(),
                ChatSessionParamEnum.GENERATE_TYPE, generateType
                // question参数交给agent处理并装配，可能需做prompt封装加强
        ));
        // 处理任务请求，返回流式结果
        return reActTaskAgent.process(question, context);
    }

    @Override
    public void stop(String sessionId) {
        // 清除 Redis 状态（带重试机制）
        String reactStatusKey = RedisConstant.REACT_STATUS_KEY_PREFIX + sessionId;
        try {
            RedisRetryUtils.safeDelete(redisTemplate, reactStatusKey);
        } catch (Exception e) {
            log.warn("清除 Redis 状态失败: sessionId={}, error={}", sessionId, e.getMessage());
        }

        // 释放 Sink
        streamManager.release(sessionId);

        log.info("停止 ReAct 会话: sessionId={}", sessionId);
    }
}
