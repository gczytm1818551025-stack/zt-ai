package org.dialectics.ai.common.base;

import reactor.core.publisher.Flux;

/**
 * 流管理接口
 * <p>
 * 定义一种机制，用于管理和发布事件流。
 * 实现此接口的类应确保线程安全且防止内存泄漏，以支持并发访问。
 *
 * @param <K> 流对象的唯一标识
 * @param <V> 流中的数据类型
 */
public interface StreamManager<K, V> {
    /**
     * 获取或创建一个新的事件流
     * <p>如果指定的键不存在，则创建一个新的事件流，否则返回已存在的流。
     *
     * @param key 流的唯一标识符
     * @return 对应的事件流
     */
    Flux<V> getStream(K key);

    /**
     * 发布新事件到指定流
     * <p>将给定事件发布到与指定键关联的流中。
     *
     * @param key    流的唯一标识符
     * @param latest 要发布的最新事件
     */
    void emit(K key, V latest);

     /**
      * 释放流
      * <p>移除与指定键关联的流，释放相关资源。
      *
      * @param key 流的唯一标识符
      */
    void release(K key);

    /**
     * 检查流是否正在进行
     * 使用 Redis 状态键来判断流是否真正活跃
     *
     * @param key 流的唯一标识符
     * @return true-正在进行，false-已完成或不存在
     */
    boolean isActive(String key);

    /**
     * 获取活跃订阅者计数
     *
     * @param key 会话 ID
     * @return 活跃订阅者数
     */
    long countActiveSubscriber(String key);

    /**
     * 清理不活跃的流
     * <p>
     * 清理规则：
     * 1. Redis 状态为空或 false 的流
     * 2. 活跃订阅者为 0 的流
     * 3. 或者流程已完成且无活跃订阅者的流
     *
     * @return 清理的流数量
     */
    int cleanupInactive();
}
