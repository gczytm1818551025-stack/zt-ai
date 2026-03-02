package org.dialectics.ai.agent.domain.pojo;

import lombok.Data;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.content.Media;

import java.util.List;
import java.util.Map;

@Data
public class ZMessage {
    // AbstractMessage
    private String messageType;
    private Map<String, Object> metadata = Map.of();
    private String textContent;
    // ReAct logic
    private List<Map<String, Object>> steps = List.of();
    private Integer stepCount = 0;

    // AssistantMessage, UserMessage
    private List<Media> media = List.of();
    // AssistantMessage
    private List<AssistantMessage.ToolCall> toolCalls = List.of();
    // ToolResponseMessage
    private List<ToolResponseMessage.ToolResponse> toolResponses = List.of();

    // 额外信息
    private Map<String, Object> params = Map.of();

}
