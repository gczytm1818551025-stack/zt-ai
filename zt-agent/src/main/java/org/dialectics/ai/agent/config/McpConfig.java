package org.dialectics.ai.agent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import org.springframework.ai.mcp.client.autoconfigure.NamedClientMcpTransport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
public class McpConfig {
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    /**
     * MCP SSE服务注册
     */
    @Bean
    public List<NamedClientMcpTransport> mcpClientTransports(Environment environment) {
        return List.of(
                new NamedClientMcpTransport("amap_sse", amapMcpTransport(environment))
        );
    }

    private McpClientTransport amapMcpTransport(Environment environment) {
        return HttpClientSseClientTransport.builder("https://mcp.amap.com")
                .sseEndpoint("/sse?key=" + environment.getProperty("AMAP_KEY"))
                .objectMapper(new ObjectMapper())
                .build();
    }

}
