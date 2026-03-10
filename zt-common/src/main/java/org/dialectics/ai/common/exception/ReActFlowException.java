package org.dialectics.ai.common.exception;

import lombok.Getter;

/**
 * ReAct 流程异常
 * <p>
 * 用于表示 ReAct 流程编排和执行过程中的错误
 * <p>
 * 典型场景：
 * - 并发限制（繁忙）
 * - 请求超时
 * - 流程编排错误
 */
public class ReActFlowException extends RuntimeException {
    @Getter
    private final String errorCode;

    public ReActFlowException(String message) {
        super(message);
        this.errorCode = "REACT_FLOW_ERROR";
    }

    public ReActFlowException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "REACT_FLOW_ERROR";
    }

    public ReActFlowException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ReActFlowException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * 繁忙异常
     * <p>
     * 当并发请求数达到上限时抛出
     */
    public static class BusyException extends ReActFlowException {
        public BusyException(String message) {
            super("REACT_BUSY", message);
        }
    }

    /**
     * 超时异常
     * <p>
     * 当整个请求超时时抛出
     */
    public static class TimeoutException extends ReActFlowException {
        public TimeoutException(String message) {
            super("REACT_TIMEOUT", message);
        }

        public TimeoutException(String message, Throwable cause) {
            super("REACT_TIMEOUT", message, cause);
        }
    }

    /**
     * 取消异常
     * <p>
     * 当请求被取消时抛出
     */
    public static class CanceledException extends ReActFlowException {
        public CanceledException(String message) {
            super("REACT_CANCELLED", message);
        }
    }
}
