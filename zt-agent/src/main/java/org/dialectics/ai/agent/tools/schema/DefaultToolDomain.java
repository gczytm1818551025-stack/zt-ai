package org.dialectics.ai.agent.tools.schema;

import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.util.ParsingUtils;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 默认的工具模型
 *
 * <p>资源类型：用户指定目录下的JSON文件
 * <p>规定action的json路径: inputSchema.properties.action.items.properties
 *
 */
public class DefaultToolDomain implements ToolDomain {
    private JSONObject raw;
    private final String name;
    private final String description;
    private final String inputSchema;
    private final Map<String, Function<Map<String, Object>, String>> actions;

    public static Builder builder() {
        return new Builder();
    }

    private DefaultToolDomain(String name, String description, String inputSchema, Map<String, Function<Map<String, Object>, String>> actions, JSONObject raw) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
        this.actions = actions;
        this.raw = raw;
    }

    @Override
    public @NotNull String name() {
        return name;
    }

    @Override
    public @NotNull String description() {
        return description;
    }

    @Override
    public @NotNull String inputSchema() {
        return inputSchema;
    }

    @Override
    public JSONObject jsonObject() {
        return raw;
    }

    @Override
    public Map<String, Function<Map<String, Object>, String>> actions() {
        return actions;
    }

    public static final class Builder {
        private String resource;
        private List<ToolCallbackProvider> toolProviders;

        private Builder() {
        }

        public Builder template(String location) {
            this.resource = location;
            return this;
        }

        public Builder toolProviders(List<ToolCallbackProvider> toolProviders) {
            this.toolProviders = toolProviders;
            return this;
        }

        public ToolDomain build() {
            String actionItemsPath = "inputSchema.properties.action.items.properties";
            String templateText;
            synchronized (this) {
                templateText = ResourceUtil.readUtf8Str(resource);
            }
            JSONObject rawJson = JSONUtil.parseObj(templateText);
            JSONObject actionItemsEntry = rawJson.getByPath(actionItemsPath, JSONObject.class);

            Map<String, Function<Map<String, Object>, String>> actionCalls = new HashMap<>();
            for (ToolCallbackProvider provider : toolProviders) {
                for (ToolCallback tool : provider.getToolCallbacks()) {
                    ToolDefinition def = tool.getToolDefinition();
                    // 填充工具的定义
                    actionItemsEntry.set(def.name(), JSONUtil.parseObj(def.inputSchema()));
                    // 注册action，之后用AssistantMessage的toolCalls获取arguments参数解析出Map参数表以调用工具
                    actionCalls.put(def.name(), params -> tool.call(JSONUtil.toJsonStr(params)));
                }
            }
            // 写回完整的toolSchema定义
            rawJson.putByPath(actionItemsPath, actionItemsEntry);

            // build
            String name, description;
            Assert.hasText(name = rawJson.getStr("name"), "toolName cannot be null or empty");
            if (!StringUtils.hasText(description = rawJson.getStr("description"))) {
                description = ParsingUtils.reConcatenateCamelCase(name, " ");
            }
            return new DefaultToolDomain(name, description, rawJson.getStr("inputSchema"), actionCalls, rawJson);
        }
    }

    @Override
    public String toString() {
        return raw.toString();
    }
}
