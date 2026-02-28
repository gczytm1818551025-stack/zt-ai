package org.dialectics.ai.agent.service;

import org.dialectics.ai.common.domain.EventVo;
import org.dialectics.ai.common.enums.GenerateTypeEnum;
import reactor.core.publisher.Flux;

public interface ChatService {
    /**
     * 聊天
     *
     * @param question  问题
     * @param sessionId 会话id
     * @param chatType  聊天类型
     * @return 回复流
     */
    Flux<? extends EventVo> chat(String question, String sessionId, GenerateTypeEnum chatType);

    /**
     * 停止生成
     *
     * @param sessionId 会话id
     */
    void stop(String sessionId);

    /**
     * 按“userId_sessionId”的格式生成对话id
     *
     * @param sessionId 会话id
     * @param userId    用户id
     */
    static String getConversationId(String sessionId, String userId) {
        return userId + "_" + sessionId;
    }
}
