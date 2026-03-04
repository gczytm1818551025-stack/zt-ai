package org.dialectics.ai.agent.domain.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;

import java.util.List;
import java.util.Map;

/**
 * 步骤跟踪
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReActOutput {
    private StepTrace stepTrace;
    /// toolName : tool的参数键值对Map
    private List<Map<String, Map<String, Object>>> action;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StepTrace {
        private String previousEvaluation;
        private String thinking;
        private String memory;
    }

    public static ReActOutput noAction(AssistantMessage thinking, List<Message> history) {
        return ReActOutput.builder()
                .action(List.of())
                .stepTrace(StepTrace.builder()
                        .memory(history.toString())
                        .thinking(thinking.getText())
                        .previousEvaluation("不确定")
                        .build())
                .build();
    }
}
