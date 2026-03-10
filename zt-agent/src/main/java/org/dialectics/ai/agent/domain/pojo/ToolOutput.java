package org.dialectics.ai.agent.domain.pojo;

public record ToolOutput(String toolName, String resultContent, Boolean success) {
}
