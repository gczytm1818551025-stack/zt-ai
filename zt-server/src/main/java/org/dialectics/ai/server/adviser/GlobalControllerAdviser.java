package org.dialectics.ai.server.adviser;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.dialectics.ai.common.domain.R;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.io.IOException;

@ControllerAdvice
@Slf4j
public class GlobalControllerAdviser {

    /**
     * 拦截未知的运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public R<Object> handleRuntimeException(RuntimeException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}',发生未知异常.", requestURI, e);
        return R.fail(e.getMessage());
    }

    /**
     * 系统异常
     */
    @ExceptionHandler(Exception.class)
    public R<Object> handleException(Exception e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}',发生系统异常.", requestURI, e);
        return R.fail(e.getMessage());
    }

    /**
     * 处理客户端断开连接导致的“管道破裂”异常
     * 策略：降级日志级别为 DEBUG 或 INFO，不打印堆栈，不报警。
     */
    @ExceptionHandler({AsyncRequestNotUsableException.class, ClientAbortException.class})
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE) // 503 或者 200 均可，此时客户端已断开，状态码实际无法送达
    public void handleClientAbortException(Exception ex) {
        // 核心逻辑：判断是否为 Broken pipe
        if (isBrokenPipe(ex)) {
            // 这是一个预期内的行为，用户关闭了连接，无需 Error 日志
            log.debug("SSE客户端已断开连接: {}", ex.getMessage());
        } else {
            // 如果不是 Broken pipe，可能是其他异步处理错误，视情况记录 warn
            log.warn("异步请求错误: {}", ex.getMessage());
        }
    }

    @ExceptionHandler(IOException.class)
    public void handleIOException(IOException ex) {
        log.error("服务器IO异常：{}", ex.getMessage());
    }

    /**
     * 递归检查异常根因是否为 Broken pipe
     */
    private boolean isBrokenPipe(Throwable ex) {
        if (ex == null) {
            return false;
        }
        // 检查异常消息
        if (ex instanceof IOException && "Broken pipe".equalsIgnoreCase(ex.getMessage())) {
            return true;
        }
        // 检查 Tomcat 的 ClientAbortException
        if (ex instanceof ClientAbortException) {
            return true;
        }
        // 递归检查 Cause
        return isBrokenPipe(ex.getCause());
    }
}
