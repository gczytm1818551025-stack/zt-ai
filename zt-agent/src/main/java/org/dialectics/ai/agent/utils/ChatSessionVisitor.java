package org.dialectics.ai.agent.utils;

import org.dialectics.ai.agent.AgentContext;
import org.dialectics.ai.common.enums.GenerateTypeEnum;

import static org.dialectics.ai.common.enums.ChatSessionParamEnum.*;

public class ChatSessionVisitor {
    public static String sessionId(AgentContext context) {
        return context.get(SESSION_ID);
    }

    public static String conversationId(AgentContext context) {
        return context.get(CONVERSATION_ID);
    }

    public static <T> T userId(AgentContext context) {
        return context.get(USER_ID);
    }

    public static String requestId(AgentContext context) {
        return context.get(REQUEST_ID);
    }

    public static String question(AgentContext context) {
        return context.get(QUESTION);
    }

    public static GenerateTypeEnum generateType(AgentContext context) {
        return context.get(GENERATE_TYPE);
    }
}
