package org.dialectics.ai.agent.service.impl;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.dialectics.ai.agent.domain.vo.ReActEventVo;
import org.dialectics.ai.agent.service.EventHistoryService;
import org.dialectics.ai.common.constants.RedisConstant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.Record;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 事件历史服务实现
 * <p>
 * 使用 Redis Stream 存储事件历史，支持：
 * 1. 事件记录到 Stream
 * 2. 历史事件回放
 * 3. 自动清理过期数据（XTRIM + TTL）
 */
@Slf4j
@Service
public class EventHistoryServiceImpl implements EventHistoryService {

    /**
     * Redis Stream TTL：30 分钟
     */
    private static final Duration STREAM_TTL = Duration.ofMinutes(30);

    /**
     * 默认历史事件数量限制
     */
    private static final int DEFAULT_LIMIT = 100;

    @Autowired
    private RedisTemplate redisTemplate;

    @Override
    public void recordEvent(String sessionId, ReActEventVo event) {
        String streamKey = getStreamKey(sessionId);
        try {
            // 将事件序列化为 JSON
            String json = JSON.toJSONString(event);

            // 创建 Stream 记录
            MapRecord<String, Object, Object> record = StreamRecords.newRecord()
                    .in(streamKey)
                    .ofMap(Map.of("event", json, "timestamp", System.currentTimeMillis()));

            // 添加到 Stream
            redisTemplate.opsForStream().add(record);

            // 使用 XTRIM 配置自动清理旧数据（保留最新N条）
            redisTemplate.opsForStream().trim(streamKey, RedisConstant.REACT_EVENT_STREAM_MAXLEN, true);

            // 设置 TTL 自动清理过期 Stream
            redisTemplate.expire(streamKey, STREAM_TTL);

            log.debug("[{}] 事件已记录到历史: type={}", sessionId, event.getType());
        } catch (Exception e) {
            log.error("[{}] 记录事件历史失败: type={}, error={}", sessionId, event.getType(), e.getMessage());
        }
    }

    @Override
    public List<ReActEventVo> getHistory(String sessionId, int limit) {
        String streamKey = getStreamKey(sessionId);
        try {
            // 检查 Stream 是否存在
            Boolean exists = redisTemplate.hasKey(streamKey);
            if (!Boolean.TRUE.equals(exists)) {
                log.debug("[{}] 事件历史 Stream 不存在", sessionId);
                return List.of();
            }

            // 获取所有记录
            List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().range(streamKey, Range.unbounded());

            if (records == null || records.isEmpty()) {
                log.debug("[{}] 无历史事件可回放", sessionId);
                return List.of();
            }

            // 解析事件
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

            // 应用数量限制
            if (limit > 0 && events.size() > limit) {
                // 从尾部截取最新的 limit 个事件
                int fromIndex = events.size() - limit;
                events = events.subList(fromIndex, events.size());
            }

            log.info("[{}] 回放历史事件: total={}, returned={}", sessionId, records.size(), events.size());
            return events;

        } catch (Exception e) {
            log.error("[{}] 获取历史事件失败: {}", sessionId, e.getMessage());
            return List.of();
        }
    }

    @Override
    public void clearHistory(String sessionId) {
        String streamKey = getStreamKey(sessionId);
        try {
            Boolean deleted = redisTemplate.delete(streamKey);
            if (Boolean.TRUE.equals(deleted)) {
                log.info("[{}] 事件历史已清理", sessionId);
            }
        } catch (Exception e) {
            log.error("[{}] 清理事件历史失败: {}", sessionId, e.getMessage());
        }
    }

    @Override
    public List<ReActEventVo> getHistorySince(String sessionId, long startEventId) {
        String streamKey = getStreamKey(sessionId);
        try {
            // 检查 Stream 是否存在
            Boolean exists = redisTemplate.hasKey(streamKey);
            if (!Boolean.TRUE.equals(exists)) {
                log.debug("[{}] 事件历史 Stream 不存在", sessionId);
                return List.of();
            }

            // 获取所有记录
            List<MapRecord<String, Object, Object>> records =
                    redisTemplate.opsForStream().range(streamKey, Range.unbounded());

            if (records == null || records.isEmpty()) {
                log.debug("[{}] 无历史事件可回放", sessionId);
                return List.of();
            }

            // 解析事件并过滤
            List<ReActEventVo> events = new ArrayList<>();
            int currentIndex = 0;

            for (MapRecord<String, Object, Object> record : records) {
                try {
                    String json = (String) record.getValue().get("event");
                    if (json != null) {
                        currentIndex++;
                        // 跳过startEventId之前的事件
                        if (currentIndex <= startEventId) {
                            continue;
                        }
                        ReActEventVo event = JSON.parseObject(json, ReActEventVo.class);
                        events.add(event);
                    }
                } catch (Exception e) {
                    log.warn("[{}] 解析历史事件失败: recordId={}, error={}", sessionId, record.getId(), e.getMessage());
                }
            }

            log.info("[{}] 从事件ID {} 开始回放: total={}, returned={}", sessionId, startEventId, records.size(), events.size());
            return events;

        } catch (Exception e) {
            log.error("[{}] 获取历史事件失败: {}", sessionId, e.getMessage());
            return List.of();
        }
    }

    /**
     * 获取 Stream 键名
     */
    private String getStreamKey(String sessionId) {
        return RedisConstant.REACT_EVENT_STREAM_PREFIX + sessionId;
    }
}
