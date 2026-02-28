package org.dialectics.ai.agent.manager;

import org.springframework.util.Assert;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ToolResultManager {
    /**
     * sessionId : ToolCallingResult(field:value)
     */
    private static final Map<String, Map<String, Object>> HANDLER_MAP = new ConcurrentHashMap<>();


    private ToolResultManager() {
    }

    public static void put(String key, String field, Object result) {
        Assert.notNull(key, "key can't be null!");
        Assert.notNull(field, "field can't be null!");

        HANDLER_MAP.computeIfAbsent(key, k -> new HashMap<>()).put(field, result);
    }

    public static Map<String, Object> get(String key) {
        return key == null ? null : HANDLER_MAP.get(key);
    }

    public static Object get(String key, String field) {
        Assert.notNull(key, "key can't be null!");
        Assert.notNull(field, "field can't be null!");

        return Optional.ofNullable(HANDLER_MAP.get(key))
                .map(map -> map.get(field))
                .orElse(null);
    }

    public static void remove(String key) {
        Assert.notNull(key, "key can't be null!");

        HANDLER_MAP.remove(key);
    }

}
