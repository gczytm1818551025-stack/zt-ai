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
public class StepTrace {
    private Status currentState;
    private List<Map<String, Object>> action;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Status {
        private String previousEvaluation;
        private String thinking;
        private String memory;
    }

    public static StepTrace noAction(AssistantMessage thinking, List<Message> history) {
        return StepTrace.builder()
                .action(List.of())
                .currentState(Status.builder()
                        .memory(history.toString())
                        .thinking(thinking.getText())
                        .previousEvaluation("不确定")
                        .build())
                .build();
    }
}
