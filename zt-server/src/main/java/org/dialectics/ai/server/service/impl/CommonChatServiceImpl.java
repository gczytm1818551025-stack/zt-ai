package org.dialectics.ai.server.service.impl;

import org.dialectics.ai.agent.agent.AgentFactory;
import org.dialectics.ai.agent.agent.impl.ChatAgent;
import org.dialectics.ai.agent.agent.AgentExecutionContext;
import org.dialectics.ai.agent.domain.vo.ChatEventVo;
import org.dialectics.ai.agent.service.ChatService;
import org.dialectics.ai.common.enums.AgentTypeEnum;
import org.dialectics.ai.common.enums.ChatSessionParamEnum;
import org.dialectics.ai.common.enums.GenerateTypeEnum;
import org.dialectics.ai.common.threadlocal.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Map;

@Service("commonChatService")
public class CommonChatServiceImpl implements ChatService {
    @Autowired
    private AgentFactory agentFactory;

    @Override
    public Flux<ChatEventVo> chat(String question, String sessionId, GenerateTypeEnum generateType) {
        // 获取对话智能体
        ChatAgent chatAgent = (ChatAgent) agentFactory.getAgent(AgentTypeEnum.ChatAgent);
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
        // 处理对话请求
        return chatAgent.process(question, context);
    }

    @Override
    public void stop(String sessionId) {
        ((ChatAgent) agentFactory.getAgent(AgentTypeEnum.ChatAgent)).stop(sessionId);
    }

}
