package org.dialectics.ai.common.threadlocal;

import lombok.extern.slf4j.Slf4j;

/**
 * 用户ThreadLocal
 */
@Slf4j
public class UserContext {

    private static final ThreadLocal<Long> LOCAL = new ThreadLocal<>();

    private UserContext() {
    }

    /**
     * 将authUserInfo放到ThreadLocal中
     *
     * @param id {@link Long}
     */
    public static void set(Long id) {
        LOCAL.set(id);
    }

    /**
     * 从ThreadLocal中获取authUserInfo
     */
    public static Long get() {
        return LOCAL.get();
    }

    /**
     * 从当前线程中删除authUserInfo
     */
    public static void remove() {
        LOCAL.remove();
    }


}
