package org.dialectics.ai.common.constants;

public interface RedisConstant {
    String GENERATE_STATUS_KEY = "GENERATE_STATUS";

    String CONVERSATION_KEY_PREFIX = "CHAT:";

    // ReAct 状态键（用于取消标志等）
    String REACT_STATUS_KEY_PREFIX = "react:status:";

    // ReAct 事件历史 Stream 键（用于重连回放）
    String REACT_EVENT_STREAM_PREFIX = "react:events:";

    // ReAct 事件历史 Stream 最大长度
    int REACT_EVENT_STREAM_MAXLEN = 1000;
}
