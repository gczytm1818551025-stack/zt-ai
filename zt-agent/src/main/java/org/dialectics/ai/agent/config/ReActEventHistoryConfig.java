package org.dialectics.ai.agent.config;

import lombok.extern.slf4j.Slf4j;
import org.dialectics.ai.agent.manager.ReActEventHistoryManager;
import org.dialectics.ai.agent.manager.ReActStreamManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * ReAct 事件历史配置
 * <p>
 * 配置事件历史清理回调，确保会话释放时同时清理事件历史
 */
@Slf4j
@Configuration
public class ReActEventHistoryConfig {
    @Autowired
    private ReActStreamManager streamManager;
    @Autowired
    private ReActEventHistoryManager eventHistoryManager;

    @PostConstruct
    public void init() {
        // 设置事件历史清理回调
        streamManager.setEventHistoryCleanupCallback((sessionId, timestamp) -> {
            eventHistoryManager.cleanupEventHistory(sessionId);
            log.debug("[{}] 事件历史已清理（通过回调）", sessionId);
        });

        log.info("ReActEventHistoryConfig 初始化完成 - 事件历史清理回调已设置");
    }
}
