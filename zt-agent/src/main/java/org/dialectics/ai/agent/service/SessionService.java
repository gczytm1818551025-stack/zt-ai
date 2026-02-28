package org.dialectics.ai.agent.service;

import org.dialectics.ai.agent.domain.dto.SessionCreateDto;
import org.dialectics.ai.agent.domain.vo.ChatRecordVo;
import org.dialectics.ai.agent.domain.vo.MessageVo;
import org.dialectics.ai.agent.domain.vo.SessionVo;

import java.util.List;

public interface SessionService {

    /**
     * 创建会话记录
     *
     * @return 会话信息
     */
    SessionVo createSession(SessionCreateDto dto);

    /**
     * 根据会话id查询当前会话信息
     *
     * @param sessionId 会话id
     * @return 会话信息
     */
    SessionVo currentSession(String sessionId);

    /**
     * 根据会话id查询消息列表
     *
     * @param sessionId 会话id
     * @return 消息列表
     */
    List<MessageVo> queryMemoryBySessionId(String sessionId);

    /**
     * 对话标题总结与更新
     *
     * @param sessionId 会话id
     * @param userId    用户id
     * @param question  第一条问题内容
     * @param response  第一条回复内容
     */
    void flushChat(String sessionId, Long userId, String question, String response);

    /**
     * 查询历史对话列表
     * <p>对话分4组：当天，30天内，1年内，1年以上
     *
     * @return Map(分组名称, 对应分组下的chatSessionVO列表)
     */
    List<ChatRecordVo> queryHistorySessions();

    /**
     * 更新历史会话标题
     */
    void updateTitle(String sessionId, String title);

    /**
     * 删除历史会话
     */
    void delete(String sessionId);

    /**
     * 删除对话记忆（按会话类型）
     * 注意：此方法提供给前端使用，用于清空指定类型的会话记忆
     * 例如：清空 Agent 模式的 ReAct 步骤记忆，但保留普通聊天历史
     *
     * @param sessionId 会话 ID
     * @param modeType 会话类型（CHAT/AGENT），可选
     * @param clearAll 是否清空所有记忆，默认 true
     */
    void clearConversationMemory(String sessionId, String modeType, Boolean clearAll);
}
