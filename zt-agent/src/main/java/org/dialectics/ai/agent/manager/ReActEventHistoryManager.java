package org.dialectics.ai.agent.manager;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.dialectics.ai.agent.domain.vo.ReActEventVo;
import org.dialectics.ai.common.constants.RedisConstant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.Record;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * ReAct 事件历史管理器
 * <p>
 * 功能：
 * 1. 使用 Redis Stream 存储事件历史
 * 2. 支持重连时回放历史事件
 * 3. 异步批量写入提升性能
 * 4. 自动清理过期事件
 */
@Slf4j
@Component
public class ReActEventHistoryManager {
    /**
     * Redis Stream 过期时间：30 分钟
     */
    private static final Duration STREAM_TTL = Duration.ofMinutes(30);

    /**
     * 批量写入阈值
     */
    private static final int BATCH_WRITE_THRESHOLD = 10;

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 异步批量写入线程池
     */
    private ExecutorService batchWriteExecutor;

    /**
     * 待写入的事件队列
     */
    private final LinkedBlockingQueue<EventWriteTask> writeQueue = new LinkedBlockingQueue<>(1000);

    /**
     * 批量写入任务记录
     */
    private record EventWriteTask(String sessionId, ReActEventVo event) {
    }

    @PostConstruct
    public void init() {
        // 初始化批量写入线程池
        batchWriteExecutor = new ThreadPoolExecutor(
                1,  // 核心线程数
                1,  // 最大线程数
                STREAM_TTL.toSeconds(),  // 非核心线程存活时间（与STREAM_TTL一致）
                TimeUnit.SECONDS,       // 使用 SECONDS 作为单位
                new LinkedBlockingQueue<>(1000),
                r -> {
                    Thread t = new Thread(r, "react-event-history-writer");
                    t.setDaemon(true);
                    return t;
                }
        );

        // 启动批量写入任务
        batchWriteExecutor.submit(this::batchWriteLoop);
        log.info("ReActEventHistoryManager 初始化完成");
    }

    @PreDestroy
    public void destroy() {
        // 刷新剩余事件
        flushRemainingEvents();

        // 关闭线程池
        if (batchWriteExecutor != null) {
            batchWriteExecutor.shutdown();
            try {
                if (!batchWriteExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    batchWriteExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                batchWriteExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("ReActEventHistoryManager 已关闭");
    }

    /**
     * 添加事件到历史（异步批量写入）
     *
     * @param sessionId 会话 ID
     * @param event     事件数据
     */
    public void addEvent(String sessionId, ReActEventVo event) {
        try {
            EventWriteTask task = new EventWriteTask(sessionId, event);
            if (!writeQueue.offer(task)) {
                // 队列已满，直接同步写入
                log.warn("[{}] 事件写入队列已满，同步写入: type={}", sessionId, event.getType());
                writeEventDirectly(sessionId, event);
            }
        } catch (Exception e) {
            log.error("[{}] 添加事件到队列失败: {}", sessionId, e.getMessage());
        }
    }

    /**
     * 获取事件历史用于回放
     *
     * @param sessionId 会话 ID
     * @param sinceId   从哪个消息ID开始回放（null表示从头开始）
     * @return 事件列表
     */
    public List<ReActEventVo> getEventHistory(String sessionId, String sinceId) {
        String streamKey = RedisConstant.REACT_EVENT_STREAM_PREFIX + sessionId;
        try {
            List<MapRecord<String, Object, Object>> records;

            if (sinceId == null || sinceId.isEmpty()) {
                // 从头开始获取
                records = redisTemplate.opsForStream().range(streamKey, Range.unbounded());
            } else {
                // 从指定ID开始获取
                RecordId sinceRecordId = RecordId.of(sinceId);
                // 使用 String 类型的 range，使用 RecordId 的字符串表示
                records = redisTemplate.opsForStream().range(streamKey,
                        Range.closed(sinceRecordId.getValue(),
                                String.format("%d-%d", Long.MAX_VALUE, Long.MAX_VALUE)));
            }

            if (records == null || records.isEmpty()) {
                log.debug("[{}] 无历史事件可回放", sessionId);
                return List.of();
            }

            // 转换为事件列表
            List<ReActEventVo> events = new ArrayList<>(records.size());
            for (MapRecord<String, Object, Object> record : records) {
                try {
                    String json = (String) record.getValue().get("event");
                    if (json != null) {
                        ReActEventVo event = JSON.parseObject(json, ReActEventVo.class);
                        events.add(event);
                    }
                } catch (Exception e) {
                    log.warn("[{}] 解析历史事件失败: recordId={}, error={}", sessionId, record.getId(), e.getMessage());
                }
            }

            log.info("[{}] 回放历史事件: count={}", sessionId, events.size());
            return events;

        } catch (Exception e) {
            log.error("[{}] 获取事件历史失败: {}", sessionId, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 获取最新事件ID
     *
     * @param sessionId 会话 ID
     * @return 最新事件ID，不存在返回null
     */
    public String getLatestEventId(String sessionId) {
        String streamKey = RedisConstant.REACT_EVENT_STREAM_PREFIX + sessionId;
        try {
            // 获取第一条记录（使用 reverseRange 从最后往前读，取第一条）
            // Spring Data Redis 的 Limit 接口在较新版本中没有 limit() 静态方法
            // 使用 xrevrange 命令并限制结果数量为 1
            List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                    .reverseRange(streamKey, Range.unbounded());

            // 如果有记录，取第一条即可
            String latestId = null;
            if (records != null && !records.isEmpty()) {
                latestId = records.get(0).getId().getValue();
            }

            return latestId;
        } catch (Exception e) {
            log.warn("[{}] 获取最新事件ID失败: {}", sessionId, e.getMessage());
            return null;
        }
    }

    /**
     * 清理指定会话的事件历史
     *
     * @param sessionId 会话 ID
     */
    public void cleanupEventHistory(String sessionId) {
        String streamKey = RedisConstant.REACT_EVENT_STREAM_PREFIX + sessionId;
        try {
            Boolean deleted = redisTemplate.delete(streamKey);
            if (Boolean.TRUE.equals(deleted)) {
                log.info("[{}] 事件历史已清理", sessionId);
            }
        } catch (Exception e) {
            log.warn("[{}] 清理事件历史失败: {}", sessionId, e.getMessage());
        }
    }

    /**
     * 快速清理路径：清理不活跃会话的事件历史
     *
     * @param sessionIds 需要检查的会话ID列表
     * @return 清理的数量
     */
    public int fastCleanupInactiveSessions(List<String> sessionIds) {
        int cleanedCount = 0;
        long startTime = System.currentTimeMillis();

        for (String sessionId : sessionIds) {
            try {
                String streamKey = RedisConstant.REACT_EVENT_STREAM_PREFIX + sessionId;
                // 检查 Stream 是否存在
                Boolean exists = redisTemplate.hasKey(streamKey);

                if (Boolean.TRUE.equals(exists)) {
                    // 检查最后一条记录的时间戳（使用 reverseRange 获取最新的一条记录）
                    List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                            .reverseRange(streamKey, Range.unbounded());

                    if (records != null && !records.isEmpty()) {
                        MapRecord<String, Object, Object> lastRecord = records.get(0);
                        long lastTimestamp = lastRecord.getId().getTimestamp();
                        long ageSeconds = (System.currentTimeMillis() - lastTimestamp) / 1000;

                        // 超过30分钟未更新，清理
                        if (ageSeconds > STREAM_TTL.toSeconds()) {
                            redisTemplate.delete(streamKey);
                            cleanedCount++;
                            log.info("[快速清理] 清理过期事件历史: sessionId={}, age={}s", sessionId, ageSeconds);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("[快速清理] 检查会话失败: sessionId={}, error={}", sessionId, e.getMessage());
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("[快速清理] 完成: 清理数量={}, 耗时={}ms", cleanedCount, duration);

        return cleanedCount;
    }

    /**
     * 批量写入循环（后台任务）
     */
    private void batchWriteLoop() {
        List<EventWriteTask> batch = new ArrayList<>(BATCH_WRITE_THRESHOLD);

        while (!Thread.currentThread().isInterrupted()) {
            try {
                // 从队列中获取事件
                EventWriteTask task = writeQueue.poll(100, TimeUnit.MILLISECONDS);

                if (task != null) {
                    batch.add(task);

                    // 达到批量阈值或超时，写入
                    if (batch.size() >= BATCH_WRITE_THRESHOLD || !writeQueue.isEmpty()) {
                        if (batch.size() >= BATCH_WRITE_THRESHOLD || (batch.size() > 0 && writeQueue.isEmpty())) {
                            writeBatch(batch);
                            batch.clear();
                        }
                    }
                } else if (!batch.isEmpty()) {
                    // 队列为空但有未写入的事件，立即写入
                    writeBatch(batch);
                    batch.clear();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("批量写入循环异常: {}", e.getMessage(), e);
                // 清空批次，避免重复写入
                batch.clear();
            }
        }

        // 退出前刷新剩余事件
        if (!batch.isEmpty()) {
            writeBatch(batch);
        }
    }

    /**
     * 批量写入事件
     */
    private void writeBatch(List<EventWriteTask> batch) {
        if (batch.isEmpty()) {
            return;
        }

        try {
            // 按sessionId分组
            java.util.Map<String, List<ReActEventVo>> grouped = batch.stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            EventWriteTask::sessionId,
                            java.util.stream.Collectors.mapping(EventWriteTask::event, java.util.stream.Collectors.toList())
                    ));

            // 逐个会话写入
            for (java.util.Map.Entry<String, List<ReActEventVo>> entry : grouped.entrySet()) {
                String sessionId = entry.getKey();
                List<ReActEventVo> events = entry.getValue();
                String streamKey = RedisConstant.REACT_EVENT_STREAM_PREFIX + sessionId;

                // 批量添加到 Stream
                for (ReActEventVo event : events) {
                    String json = JSON.toJSONString(event);
                    MapRecord<String, Object, Object> record = StreamRecords.newRecord()
                            .in(streamKey)
                            .ofMap(Map.of("event", json));
                    redisTemplate.opsForStream().add(record);
                }

                // 设置Stream长度限制（保留最新N条）
                redisTemplate.opsForStream().trim(streamKey, RedisConstant.REACT_EVENT_STREAM_MAXLEN, true);

                // 设置TTL
                redisTemplate.expire(streamKey, STREAM_TTL);

                log.debug("[{}] 批量写入事件: count={}", sessionId, events.size());
            }
        } catch (Exception e) {
            log.error("批量写入失败: count={}, error={}", batch.size(), e.getMessage());
        }
    }

    /**
     * 直接写入事件（同步方式）
     */
    private void writeEventDirectly(String sessionId, ReActEventVo event) {
        String streamKey = RedisConstant.REACT_EVENT_STREAM_PREFIX + sessionId;
        try {
            String json = JSON.toJSONString(event);
            MapRecord<String, Object, Object> record = StreamRecords.newRecord()
                    .in(streamKey)
                    .ofMap(Map.of("event", json));
            redisTemplate.opsForStream().add(record);

            // 设置Stream长度限制
            redisTemplate.opsForStream().trim(streamKey, RedisConstant.REACT_EVENT_STREAM_MAXLEN, true);

            // 设置TTL
            redisTemplate.expire(streamKey, STREAM_TTL);

            log.debug("[{}] 直接写入事件: type={}", sessionId, event.getType());
        } catch (Exception e) {
            log.error("[{}] 直接写入事件失败: {}", sessionId, e.getMessage());
        }
    }

    /**
     * 刷新剩余事件（关闭时调用）
     */
    private void flushRemainingEvents() {
        List<EventWriteTask> remaining = new ArrayList<>();
        writeQueue.drainTo(remaining);

        if (!remaining.isEmpty()) {
            log.info("刷新剩余事件: count={}", remaining.size());
            writeBatch(remaining);
        }
    }
}
