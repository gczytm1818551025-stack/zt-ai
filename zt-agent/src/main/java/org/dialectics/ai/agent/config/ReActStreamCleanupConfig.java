package org.dialectics.ai.agent.config;

import lombok.extern.slf4j.Slf4j;
import org.dialectics.ai.agent.manager.ReActEventHistoryManager;
import org.dialectics.ai.agent.manager.ReActStreamManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * ReAct 流定期清理配置
 * <p>
 * 独立于 ReActExecutorConfig，避免循环依赖
 */
@Slf4j
@Configuration
@EnableScheduling
public class ReActStreamCleanupConfig {
    @Autowired
    private ReActStreamManager streamManager;
    @Autowired
    private ReActEventHistoryManager eventHistoryManager;

    /**
     * 快速清理不活跃的会话（使用SCAN批量清理）
     * <p>
     * 每 5 分钟清理一次，释放不活跃会话的内存资源
     * 使用快速清理路径，避免逐个检查的性能问题
     */
    @Scheduled(fixedRate = 300000) // 5 分钟
    public void cleanupInactiveSessions() {
        try {
            String stats = streamManager.statistics();
            log.info("定期清理前: {}", stats);

            // 使用快速清理路径
            int cleanedCount = streamManager.cleanupInactive();

            if (cleanedCount > 0) {
                String statsAfter = streamManager.statistics();
                log.info("定期清理后: {}, 清理数量={}", statsAfter, cleanedCount);
            }
        } catch (Exception e) {
            log.error("定期清理失败", e);
        }
    }

    /**
     * 清理过期的事件历史
     * <p>
     * 每 10 分钟清理一次，释放Redis Stream的过期资源
     */
    @Scheduled(fixedRate = 600000) // 10 分钟
    public void cleanupExpiredEventHistory() {
        try {
            // 获取所有会话ID
            java.util.List<String> sessionIds = streamManager.getAllSessionIds();

            if (!sessionIds.isEmpty()) {
                int cleanedCount = eventHistoryManager.fastCleanupInactiveSessions(sessionIds);
                if (cleanedCount > 0) {
                    log.info("事件历史清理完成: 清理数量={}", cleanedCount);
                }
            }
        } catch (Exception e) {
            log.error("事件历史清理失败", e);
        }
    }
}
