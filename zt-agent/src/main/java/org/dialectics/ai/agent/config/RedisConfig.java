package org.dialectics.ai.agent.config;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.dialectics.ai.common.base.FastJson2JsonRedisSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.Duration;

/**
 * Redis配置类
 * <p>
 * 功能：
 * 1. 配置RedisTemplate序列化方式
 * 2. 配置Lettuce连接池，提升性能和稳定性
 * 3. 支持连接池参数自定义配置
 * <p>
 * 注意：
 * - 使用Spring Boot 3.x自动配置机制
 * - 通过配置属性控制连接池行为
 */
@Configuration
public class RedisConfig {

    /**
     * Redis连接池配置参数
     */
    @Value("${spring.data.redis.lettuce.pool.max-active:100}")
    private int maxActive;
    @Value("${spring.data.redis.lettuce.pool.max-idle:20}")
    private int maxIdle;
    @Value("${spring.data.redis.lettuce.pool.min-idle:5}")
    private int minIdle;
    @Value("${spring.data.redis.lettuce.pool.max-wait:10000}")
    private long maxWait;

    @Bean
    @SuppressWarnings(value = {"unchecked", "rawtypes"})
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        FastJson2JsonRedisSerializer serializer = new FastJson2JsonRedisSerializer(Object.class);

        template.setKeySerializer(RedisSerializer.string());
        template.setValueSerializer(serializer);

        template.setHashKeySerializer(RedisSerializer.string());
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * 配置Lettuce连接池
     * <p>
     * 作用：
     * 1. 复用连接，减少连接创建开销
     * 2. 控制最大连接数，防止资源耗尽
     * 3. 提供连接池统计和监控能力
     * <p>
     * 注意：
     * - Spring Boot 3.x通过RedisProperties自动配置连接池
     * - 此配置通过@ConditionalOnMissingBean只在没有自定义连接工厂时生效
     *
     * @return GenericObjectPoolConfig 连接池配置
     */
    @Bean
    @ConditionalOnMissingBean
    public GenericObjectPoolConfig<?> redisPoolConfig() {
        GenericObjectPoolConfig<?> poolConfig = new GenericObjectPoolConfig<>();

        // 最大连接数：连接池能分配的最大连接数
        poolConfig.setMaxTotal(maxActive);

        // 最大空闲连接数：连接池中最多保持的空闲连接数
        poolConfig.setMaxIdle(maxIdle);

        // 最小空闲连接数：连接池中至少保持的空闲连接数
        poolConfig.setMinIdle(minIdle);

        // 获取连接最大等待时间（毫秒）：当连接池耗尽时，客户端等待获取连接的最大时间
        // -1表示无限等待，建议设置一个合理值避免阻塞
        poolConfig.setMaxWait(Duration.ofMillis(maxWait));

        // 连接空闲超时时间（毫秒）：空闲连接超过此时间将被回收
        poolConfig.setTimeBetweenEvictionRuns(Duration.ofMillis(60000));

        // 每次回收空闲连接时检查的最小连接数
        poolConfig.setNumTestsPerEvictionRun(3);

        // 连接创建时是否验证连接有效性
        poolConfig.setTestOnCreate(false);

        // 从连接池获取连接时是否验证连接有效性
        poolConfig.setTestOnBorrow(true);

        // 归还连接到连接池时是否验证连接有效性
        poolConfig.setTestOnReturn(false);

        // 连接空闲时是否验证连接有效性
        poolConfig.setTestWhileIdle(true);

        // 是否启用JMX监控
        poolConfig.setJmxEnabled(true);

        return poolConfig;
    }
}
