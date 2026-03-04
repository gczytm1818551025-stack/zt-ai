package org.dialectics.ai.agent.utils;

import org.dialectics.ai.agent.AgentExecutionContext;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;

import static org.dialectics.ai.common.enums.ReActParamEnum.*;

/**
 * <h3>访问者模式-运行时动态获取reAct控制参数
 * <p>解耦AgentThreadLocal的参数结构和访问算法
 */
public class ReActControlVisitor {

    public static AtomicInteger stepCount(AgentExecutionContext context) {
        return context.get(STEP_COUNT);
    }

    public static <T> T targetTask(AgentExecutionContext context) {
        return context.get(TARGET_TASK);
    }

    public static List<Message> messageMemory(AgentExecutionContext context) {
        return context.get(MESSAGE_MEMORY);
    }

    public static <T> List<T> taskChain(AgentExecutionContext context) {
        return context.get(TASK_CHAIN);
    }

    public static List<String> subResultChain(AgentExecutionContext context) {
        return context.get(SUB_RESULT_CHAIN);
    }

    public static ToolCallback toolCallback(AgentExecutionContext context) {
        return context.get(TOOL_CALLBACK);
    }

    public static Map<String, Function<Map<String, Object>, String>> actions(AgentExecutionContext context) {
        return context.get(ACTIONS);
    }

    public static AtomicBoolean completed(AgentExecutionContext context) {
        return context.get(COMPLETED);
    }

    public static <T> T finalResult(AgentExecutionContext context) {
        return context.get(FINAL_RESULT);
    }

    public static LongAdder tokenCounter(AgentExecutionContext context) {
        return context.get(TOKEN_COUNTER);
    }

    public static AtomicBoolean terminatedFlag(AgentExecutionContext context) {
        return context.get(REACT_TERMINATED_FLAG);
    }

    public static String terminateText() {
        return "注意，你的执行已达到最大步数限制，下个子任务的内容必须是结束整个任务并总结截至目前的工作成果以及对遇到的困境加以说明。";
    }

}
