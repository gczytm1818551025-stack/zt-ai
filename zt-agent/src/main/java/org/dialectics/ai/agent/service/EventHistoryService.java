package org.dialectics.ai.agent.service;

import org.dialectics.ai.agent.domain.vo.ReActEventVo;

import java.util.List;

/**
 * 事件历史服务接口
 * <p>
 * 功能：
 * 1. 记录事件到 Redis Stream
 * 2. 获取历史事件用于重连回放
 * 3. 清理历史事件
 */
public interface EventHistoryService {

    /**
     * 记录事件到历史
     *
     * @param sessionId 会话 ID
     * @param event     事件数据
     */
    void recordEvent(String sessionId, ReActEventVo event);

    /**
     * 获取历史事件
     *
     * @param sessionId 会话 ID
     * @param limit     限制返回数量，0表示不限制
     * @return 历史事件列表
     */
    List<ReActEventVo> getHistory(String sessionId, int limit);

    /**
     * 获取从指定事件ID之后的历史事件（用于重连时部分回放）
     *
     * @param sessionId   会话 ID
     * @param startEventId 起始事件ID（不包含此ID），从下一个事件开始获取
     * @return 历史事件列表
     */
    List<ReActEventVo> getHistorySince(String sessionId, long startEventId);

    /**
     * 清理历史事件
     *
     * @param sessionId 会话 ID
     */
    void clearHistory(String sessionId);
}
