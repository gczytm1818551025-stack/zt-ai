package org.dialectics.ai.agent.domain.pojo;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;

public record ModelOutput(AssistantMessage assistantMessage, ChatResponseMetadata metadata, boolean hasToolCalls) {
}
