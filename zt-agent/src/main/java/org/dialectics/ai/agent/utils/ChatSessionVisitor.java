package org.dialectics.ai.agent.utils;

import org.dialectics.ai.agent.agent.AgentExecutionContext;
import org.dialectics.ai.common.enums.GenerateTypeEnum;

import static org.dialectics.ai.common.enums.ChatSessionParamEnum.*;

public class ChatSessionVisitor {
    public static String sessionId(AgentExecutionContext context) {
        return context.get(SESSION_ID);
    }

    public static String conversationId(AgentExecutionContext context) {
        return context.get(CONVERSATION_ID);
    }

    public static <T> T userId(AgentExecutionContext context) {
        return context.get(USER_ID);
    }

    public static String requestId(AgentExecutionContext context) {
        return context.get(REQUEST_ID);
    }

    public static String question(AgentExecutionContext context) {
        return context.get(QUESTION);
    }

    public static GenerateTypeEnum generateType(AgentExecutionContext context) {
        return context.get(GENERATE_TYPE);
    }
}
