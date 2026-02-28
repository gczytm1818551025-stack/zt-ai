package org.dialectics.ai.common.listener;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 会话生命周期事件
 * <p>
 * 观察者模式：封装会话生命周期变化的事件数据
 * <p>
 * 设计原则：
 * 1. 封装事件数据：携带足够的信息供监听器使用
 * 2. 不可变性：事件创建后不应修改
 * 3. 时间戳：记录事件发生时间
 */
@Data
@Builder
public class SessionLifecycleEvent {

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 事件类型
     */
    private EventType eventType;

    /**
     * 事件发生时间
     */
    @Builder.Default
    private LocalDateTime eventTime = LocalDateTime.now();

    /**
     * 请求ID
     */
    private String requestId;

    /**
     * 事件详情
     */
    private String details;

    /**
     * 额外属性（扩展用）
     */
    private Map<String, Object> properties;

    /**
     * 事件类型枚举
     */
    public enum EventType {
        /**
         * 会话创建
         */
        CREATED,
        /**
         * 会话开始
         */
        STARTED,
        /**
         * 会话活跃
         */
        ACTIVE,
        /**
         * 会话暂停
         */
        PAUSED,
        /**
         * 会话恢复
         */
        RESUMED,
        /**
         * 会话完成
         */
        COMPLETED,
        /**
         * 会话取消
         */
        CANCELLED,
        /**
         * 会话超时
         */
        TIMEOUT,
        /**
         * 会话销毁
         */
        DESTROYED,
        /**
         * 会话错误
         */
        ERROR
    }

    /**
     * 添加属性
     */
    public void addProperty(String key, Object value) {
        if (this.properties == null) {
            this.properties = new java.util.HashMap<>();
        }
        this.properties.put(key, value);
    }

    /**
     * 获取属性
     */
    public Object getProperty(String key) {
        if (this.properties == null) {
            return null;
        }
        return this.properties.get(key);
    }

    /**
     * 判断是否为成功结束
     */
    public boolean isSuccessEnding() {
        return eventType == EventType.COMPLETED;
    }

    /**
     * 判断是否为异常结束
     */
    public boolean isFailureEnding() {
        return eventType == EventType.ERROR
                || eventType == EventType.TIMEOUT
                || eventType == EventType.CANCELLED;
    }

    /**
     * 获取耗时（毫秒，如果包含 startTimestamp 属性）
     */
    public Long getDurationMs() {
        if (this.properties == null) {
            return null;
        }
        Object startTimestamp = this.properties.get("startTimestamp");
        if (startTimestamp instanceof Long) {
            return System.currentTimeMillis() - (Long) startTimestamp;
        }
        return null;
    }
}
