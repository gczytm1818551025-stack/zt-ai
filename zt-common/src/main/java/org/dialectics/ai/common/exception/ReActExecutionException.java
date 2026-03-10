package org.dialectics.ai.common.exception;

import lombok.Getter;

/**
 * ReAct 执行异常
 * <p>
 * 用于表示 ReAct 流程执行过程中的错误
 * <p>
 * 典型场景：
 * - LLM 调用失败
 * - 工具执行失败
 * - 解析错误
 * - 超时错误
 */
public class ReActExecutionException extends RuntimeException {
    @Getter
    private final String errorCode;

    public ReActExecutionException(String message) {
        super(message);
        this.errorCode = "REACT_EXEC_ERROR";
    }

    public ReActExecutionException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "REACT_EXEC_ERROR";
    }

    public ReActExecutionException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ReActExecutionException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * LLM 调用异常
     */
    public static class LLMCallException extends ReActExecutionException {
        public LLMCallException(String message) {
            super("LLM_CALL_ERROR", message);
        }

        public LLMCallException(String message, Throwable cause) {
            super("LLM_CALL_ERROR", message, cause);
        }
    }

    /**
     * 工具执行异常
     */
    public static class ToolExecutionException extends ReActExecutionException {
        public ToolExecutionException(String message) {
            super("TOOL_EXEC_ERROR", message);
        }

        public ToolExecutionException(String message, Throwable cause) {
            super("TOOL_EXEC_ERROR", message, cause);
        }
    }

    /**
     * 解析异常
     */
    public static class ParseException extends ReActExecutionException {
        public ParseException(String message) {
            super("PARSE_ERROR", message);
        }

        public ParseException(String message, Throwable cause) {
            super("PARSE_ERROR", message, cause);
        }
    }

    /**
     * 超时异常
     */
    public static class TimeoutException extends ReActExecutionException {
        public TimeoutException(String message) {
            super("TIMEOUT_ERROR", message);
        }

        public TimeoutException(String message, Throwable cause) {
            super("TIMEOUT_ERROR", message, cause);
        }
    }

    /**
     * 取消异常
     */
    public static class CanceledException extends ReActExecutionException {
        public CanceledException(String message) {
            super("CANCELED", message);
        }
    }
}
