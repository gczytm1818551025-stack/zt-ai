package org.dialectics.ai.agent.config.properties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ReAct 执行器配置属性
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReActExecProperties {
    private int llmTimeoutSeconds;
    private int toolTimeoutSeconds;
    private int maxConcurrent;
    private int requestTimeoutSeconds;
    private int backpressureBufferSize;
    private String backpressureStrategy;
    private int maxStep;
    private int maxActionPerCall;
    /**
     * 历史消息保留的最大数量，用于控制上下文窗口大小
     * 默认保留最近的20条消息
     */
    @Builder.Default
    private int maxHistoryMessages = 20;
}
