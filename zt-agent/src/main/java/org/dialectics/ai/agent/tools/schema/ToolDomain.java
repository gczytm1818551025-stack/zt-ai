package org.dialectics.ai.agent.tools.schema;

import cn.hutool.json.JSONObject;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.Map;
import java.util.function.Function;

/**
 * 工具的概念模型
 *
 */
public interface ToolDomain extends ToolDefinition {
    /**
     * 工具模型名称
     *
     */
    @NotNull String name();

    /**
     * 工具模型描述
     *
     */
    @NotNull String description();


    /**
     * 工具模型的json模板字符串
     *
     */
    @NotNull String inputSchema();

    /**
     * 获取JSON对象
     *
     */
    JSONObject jsonObject();

    /**
     * 工具模型中的行为集合
     * key-actionName
     * value-actionFunc
     */
    Map<String, Function<Map<String, Object>, String>> actions();

    static DefaultToolDomain.Builder builder() {
        return DefaultToolDomain.builder();
    }
}
