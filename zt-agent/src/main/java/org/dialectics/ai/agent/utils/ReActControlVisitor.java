package org.dialectics.ai.agent.utils;

import org.dialectics.ai.agent.AgentContext;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

import static org.dialectics.ai.common.enums.ReActParamEnum.*;

/**
 * <h3>访问者模式-运行时动态获取reAct控制参数
 * <p>解耦AgentThreadLocal的参数结构和访问算法
 */
public class ReActControlVisitor {

    public static AtomicInteger stepCount(AgentContext context) {
        return context.get(STEP_COUNT);
    }

    public static <T> T targetTask(AgentContext context) {
        return context.get(TARGET_TASK);
    }

    public static List<Message> messageMemory(AgentContext context) {
        return context.get(MESSAGE_MEMORY);
    }

    public static <T> List<T> taskChain(AgentContext context) {
        return context.get(TASK_CHAIN);
    }

    public static List<String> subResultChain(AgentContext context) {
        return context.get(SUB_RESULT_CHAIN);
    }

    public static ToolCallback toolCallback(AgentContext context) {
        return context.get(TOOL_CALLBACK);
    }

    public static AtomicBoolean completed(AgentContext context) {
        return context.get(COMPLETED);
    }

    public static <T> T finalResult(AgentContext context) {
        return context.get(FINAL_RESULT);
    }

    public static LongAdder tokenCounter(AgentContext context) {
        return context.get(TOKEN_COUNTER);
    }

    public static AtomicBoolean terminatedFlag(AgentContext context) {
        return context.get(REACT_TERMINATED_FLAG);
    }

    public static String terminateText() {
        return "注意，你的执行已达到最大步数限制，下个子任务的内容必须是结束整个任务并总结截至目前的工作成果以及对遇到的困境加以说明。";
    }

}
