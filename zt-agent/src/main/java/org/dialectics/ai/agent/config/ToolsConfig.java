package org.dialectics.ai.agent.config;

import org.dialectics.ai.agent.tools.FileStorageTools;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ToolsConfig {

    @Bean
    public ToolCallbackProvider fileStorageToolCallbacks(FileStorageTools fileStorageTools) {
        return () -> ToolCallbacks.from(fileStorageTools);
    }
}
