package org.dialectics.ai.agent.domain.pojo;

import lombok.Getter;
import lombok.Setter;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.content.Media;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Setter
@Getter
public class ZAssistantMessage extends AssistantMessage {
    /// Tool调用相关参数
    private Map<String, Object> params;
    /// ReAct步骤列表
    private List<Map<String, Object>> steps;
    /// ReAct步骤计数
    private Integer stepCount;

    public ZAssistantMessage(String content, Map<String, Object> properties, List<ToolCall> toolCalls, List<Media> media,
                             Map<String, Object> params, List<Map<String, Object>> steps, Integer stepCount) {
        super(content, properties, toolCalls, media);
        this.params = params;
        this.steps = new ArrayList<>();
        this.stepCount = 0;
    }

    /**
     * 添加一个 ReAct 步骤
     */
    public void addStep(Map<String, Object> step) {
        this.steps.add(step);
    }

}
