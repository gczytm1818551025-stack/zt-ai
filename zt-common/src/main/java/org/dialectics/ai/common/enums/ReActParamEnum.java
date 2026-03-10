package org.dialectics.ai.common.enums;

public enum ReActParamEnum {
    /// ReAct配置参数
    REACT_PROPERTIES,
    /// ReAct当前执行到的步数
    STEP_COUNT,
    TARGET_TASK,
    MESSAGE_MEMORY,
    TASK_CHAIN,
    SUB_RESULT_CHAIN,
    /// ReAct工具回调对象
    TOOL_CALLBACK,
    COMPLETED,
    FINAL_RESULT,
    TOKEN_COUNTER,

    /// ReAct终止标志：当达到最大步数限制时添加了终止提示
    REACT_TERMINATED_FLAG,
    /// ReAct Sink 键（用于事件发送）
    SINK,
    ;
}
