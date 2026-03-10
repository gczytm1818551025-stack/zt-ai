package org.dialectics.ai.agent.utils;

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * <h4>MCP-SSE短链接工具
 * <p>每次执行任务时，都快速创建并关闭一个SSE客户端连接
 * <p>由于amap的server在sse端链接2min后自动断链，springai不支持短sse方案，故每次做初始化，用完后关闭
 */
import cn.hutool.core.collection.CollStreamUtil;
import cn.hutool.core.util.BooleanUtil;
import lombok.AllArgsConstructor;

/**
 * 一些服务器在sse建立连接几分钟后自动断链，所以每次都做client初始化，使用完后销毁
 */
@Slf4j
public class McpShotSseUtil {
    /**
     * 使用一个ToolCallbackProvider进行一定行为并获得行为结果
     */
    public <T> T callSse(Supplier<McpAsyncClient> clientSupplier, Function<ToolCallbackProvider, T> func) {
        ToolCallbackProvider provider = new LazyAsyncMcpToolCallbackProvider(clientSupplier);
        return func.apply(provider);
    }

    /**
     * 使用一个即时创建-使用-销毁的MCP客户端进行一定行为
     * <p>核心方法——实现短连接SSE操作
     */
    static <T> T retrieveFromShortConnectionClient(Supplier<McpAsyncClient> clientSupplier, Function<McpAsyncClient, T> func) {
        McpAsyncClient mcpClient = clientSupplier.get();
        mcpClient.initialize()
                .doOnError(e -> log.error("error on sse client init", e))
                .doOnSuccess(v -> log.trace("sse client initialled."))
                .block();
        try {
            return func.apply(mcpClient);
        } finally {
            mcpClient.closeGracefully()
                    .doOnError(e -> log.error("error on sse client close", e))
                    .doOnSuccess(v -> log.trace("sse client closed.")).block();
        }
    }

    @AllArgsConstructor
    static class LazyAsyncMcpToolCallbackProvider implements ToolCallbackProvider {
        private final Supplier<McpAsyncClient> clientSupplier;

        @Override
        @NotNull
        public ToolCallback[] getToolCallbacks() {
            List<ToolCallback> toolCallBack = new LinkedList<>();
            retrieveFromShortConnectionClient(clientSupplier, mcpClient ->
                    toolCallBack.addAll(Objects.requireNonNull(mcpClient.listTools().map(toolsResult -> CollStreamUtil.toList(toolsResult.tools(), tool -> new LazyToolCallback(tool, clientSupplier))).block()))
            );
            return toolCallBack.toArray(ToolCallback[]::new);
        }

    }

    @AllArgsConstructor
    static class LazyToolCallback implements ToolCallback {
        private final Tool tool;
        private final Supplier<McpAsyncClient> clientSupplier;

        @Override
        public @NotNull ToolDefinition getToolDefinition() {
            String name = retrieveFromShortConnectionClient(clientSupplier, client -> client.getClientInfo().name());
            return ToolDefinition.builder()
                    .name(McpToolUtils.prefixedToolName(name, this.tool.name()))
                    .description(this.tool.description())
                    .inputSchema(ModelOptionsUtils.toJsonString(this.tool.inputSchema())).build();
        }

        @Override
        public @NotNull String call(@NotNull String functionInput) {
            log.info("tool call - [{}]input:{}", tool.name(), functionInput);
            var res = retrieveFromShortConnectionClient(clientSupplier, mcpClient -> {
                Map<String, Object> arguments = ModelOptionsUtils.jsonToMap(functionInput);
                // Note that we use the original tool name here, not the adapted one from
                // getToolDefinition
                return mcpClient.callTool(new CallToolRequest(this.tool.name(), arguments)).map(response -> {
                    if (BooleanUtil.isTrue(response.isError())) {
                        throw new IllegalStateException("calling tool exception: " + response.content());
                    }
                    return ModelOptionsUtils.toJsonString(response.content());
                }).block();
            });
            log.info("tool call - [{}]output:{}", tool.name(), res);
            return res;
        }

        @Override
        public @NotNull String call(@NotNull String toolArguments, @NotNull ToolContext toolContext) {
            return call(toolArguments);
        }

    }
}
