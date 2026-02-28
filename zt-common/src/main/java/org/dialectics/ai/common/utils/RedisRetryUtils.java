package org.dialectics.ai.common.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.util.*;
import java.util.function.Supplier;

import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;


/**
 * Redis操作重试工具类
 * <p>
 * 提供带重试机制的Redis操作方法，增强Redis操作的容错能力
 * </p>
 */
@Slf4j
public class RedisRetryUtils {

    /**
     * 默认重试次数
     */
    private static final int DEFAULT_MAX_RETRIES = 3;

    /**
     * 默认重试间隔（毫秒）
     */
    private static final long DEFAULT_RETRY_DELAY = 100;

    /**
     * 需要重试的Redis异常类型
     */
    private static final Set<String> RETRYABLE_EXCEPTIONS = new HashSet<>(Arrays.asList(
            "org.springframework.data.redis.connection.RedisConnectionFailureException",
            "org.springframework.data.redis.RedisConnectionFailureException",
            "org.springframework.data.redis.RedisSystemException",
            "io.lettuce.core.RedisConnectionException",
            "io.lettuce.core.RedisException",
            "java.net.ConnectException",
            "java.net.SocketTimeoutException"
    ));

    /**
     * 判断异常是否可重试
     *
     * @param e 异常对象
     * @return true-可重试，false-不可重试
     */
    private static boolean isRetryable(Throwable e) {
        Throwable cause = e;
        while (cause != null) {
            String className = cause.getClass().getName();
            if (RETRYABLE_EXCEPTIONS.contains(className)) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    /**
     * 执行带重试的Redis操作
     *
     * @param operation     Redis操作
     * @param operationDesc 操作描述
     * @param <T>           返回类型
     * @return 操作结果
     */
    public static <T> T executeWithRetry(Supplier<T> operation, String operationDesc) {
        return executeWithRetry(operation, operationDesc, DEFAULT_MAX_RETRIES, DEFAULT_RETRY_DELAY);
    }

    /**
     * 执行带重试的Redis操作（自定义重试次数和间隔）
     *
     * @param operation     Redis操作
     * @param operationDesc 操作描述
     * @param maxRetries    最大重试次数
     * @param retryDelay    重试间隔（毫秒）
     * @param <T>           返回类型
     * @return 操作结果
     */
    public static <T> T executeWithRetry(Supplier<T> operation, String operationDesc, int maxRetries, long retryDelay) {
        Throwable lastException = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                if (attempt > 0) {
                    log.debug("Redis操作重试: operation={}, attempt={}", operationDesc, attempt);
                    // 指数退避：每次重试间隔加倍
                    long delay = retryDelay * (1L << Math.min(attempt - 1, 4));
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Redis操作被中断", ie);
                    }
                }
                return operation.get();
            } catch (Throwable e) {
                lastException = e;
                if (attempt < maxRetries && isRetryable(e)) {
                    log.warn("Redis操作失败，准备重试: operation={}, attempt={}, error={}",
                            operationDesc, attempt, e.getMessage());
                    continue;
                }
                break;
            }
        }
        log.error("Redis操作最终失败: operation={}, error={}", operationDesc,
                lastException != null ? lastException.getMessage() : "unknown");
        throw new RuntimeException("Redis操作失败: " + operationDesc, lastException);
    }

    /**
     * 执行带重试的Redis操作（无返回值）
     *
     * @param operation     Redis操作
     * @param operationDesc 操作描述
     */
    public static void executeWithRetryVoid(Runnable operation, String operationDesc) {
        executeWithRetryVoid(operation, operationDesc, DEFAULT_MAX_RETRIES, DEFAULT_RETRY_DELAY);
    }

    /**
     * 执行带重试的Redis操作（无返回值，自定义重试参数）
     *
     * @param operation     Redis操作
     * @param operationDesc 操作描述
     * @param maxRetries    最大重试次数
     * @param retryDelay    重试间隔（毫秒）
     */
    public static void executeWithRetryVoid(Runnable operation, String operationDesc, int maxRetries, long retryDelay) {
        executeWithRetry(() -> {
            operation.run();
            return null;
        }, operationDesc, maxRetries, retryDelay);
    }


    /**
     * 异步执行Redis操作（带重试）
     *
     * @param operation     Redis操作
     * @param operationDesc 操作描述
     * @param maxRetries    最大重试次数
     * @param retryDelay    重试延迟（毫秒）
     * @return Mono 包装的结果
     */
    public static <T> Mono<T> executeWithRetryAsync(Supplier<Mono<T>> operation,
                                                    String operationDesc, int maxRetries, long retryDelay) {
        return operation.get()
                .retryWhen(Retry.backoff(maxRetries, Duration.ofMillis(retryDelay))
                        .filter(RedisRetryUtils::isRetryable)
                        .doBeforeRetry(signal ->
                                log.debug("Redis操作重试: operation={}, attempt={}",
                                        operationDesc, signal.totalRetries())))
                .timeout(Duration.ofSeconds(30))
                .doOnError(e ->
                        log.error("Redis操作最终失败: operation={}, error={}",
                                operationDesc, e.getMessage()));
    }

// ==================== 封装常用Redis操作 ====================

    /**
     * 安全设置键值对
     */
    public static void safeSet(RedisTemplate<String, Object> redisTemplate, String key, Object value, Duration timeout) {
        executeWithRetryVoid(() -> redisTemplate.opsForValue().set(key, value, timeout),
                "set(key=" + key + ")");
    }

    /**
     * 安全获取键值
     */
    public static Object safeGet(RedisTemplate<String, Object> redisTemplate, String key) {
        return executeWithRetry(() -> redisTemplate.opsForValue().get(key),
                "get(key=" + key + ")");
    }

    /**
     * 安全删除键
     */
    public static void safeDelete(RedisTemplate<String, Object> redisTemplate, String key) {
        executeWithRetryVoid(() -> redisTemplate.delete(key),
                "delete(key=" + key + ")");
    }

    /**
     * 安全删除多个键
     */
    public static void safeDelete(RedisTemplate<String, Object> redisTemplate, Collection<String> keys) {
        if (CollectionUtils.isEmpty(keys)) {
            return;
        }
        executeWithRetryVoid(() -> redisTemplate.delete(keys),
                "delete(keys=" + keys + ")");
    }

    /**
     * 安全检查键是否存在
     */
    public static Boolean safeHasKey(RedisTemplate<String, Object> redisTemplate, String key) {
        return executeWithRetry(() -> redisTemplate.hasKey(key),
                "hasKey(key=" + key + ")");
    }

    /**
     * 安全设置Hash值
     */
    public static void safeHashPut(RedisTemplate<String, Object> redisTemplate, String key, Object hashKey, Object value) {
        executeWithRetryVoid(() -> redisTemplate.opsForHash().put(key, hashKey, value),
                "hashPut(key=" + key + ", hashKey=" + hashKey + ")");
    }

    /**
     * 安全获取Hash值
     */
    public static Object safeHashGet(RedisTemplate<String, Object> redisTemplate, String key, Object hashKey) {
        return executeWithRetry(() -> redisTemplate.opsForHash().get(key, hashKey),
                "hashGet(hashKey=" + hashKey + ", key=" + key + ")");
    }

    /**
     * 安全删除Hash中的key
     */
    public static void safeHashDelete(RedisTemplate<String, Object> redisTemplate, String key, Object... hashKeys) {
        if (hashKeys == null || hashKeys.length == 0) {
            return;
        }
        executeWithRetryVoid(() -> redisTemplate.opsForHash().delete(key, hashKeys),
                "hashDelete(key=" + key + ", hashKeys=" + Arrays.toString(hashKeys) + ")");
    }

}
