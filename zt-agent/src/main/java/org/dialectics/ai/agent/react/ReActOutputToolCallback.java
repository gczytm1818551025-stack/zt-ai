package org.dialectics.ai.agent.react;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.*;
import org.dialectics.ai.agent.domain.pojo.ReActOutput;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.ai.tool.support.ToolDefinitions;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * <h4>ReAct标准输出工具回调对象
 * <p>装饰器模式
 */
public class ReActOutputToolCallback implements ToolCallback {

    private final ToolCallback toolCallback;

    public ReActOutputToolCallback(List<ToolCallback> outerTools) {
        Method toolMethod = ReflectionUtils.findMethod(ReActOutputToolCallback.class, "apply", ReActOutput.StepTrace.class, List.class);
        Assert.notNull(toolMethod, "No tool method found for ReActOutputTool");
        String inputSchema = JsonSchemaGenerator.generateForMethodInput(toolMethod);
        JSONObject rawJson = JSONUtil.parseObj(inputSchema);
        // 手动构造被泛型参数缺失抹去的JSON节点
        JSONObject itemsNode = JSONUtil.createObj()
                .set("type", "object")
                .set("properties", JSONUtil.createObj());
        rawJson.putByPath("properties.action.items", itemsNode);

        String actionItemsPath = "properties.action.items.properties";
        JSONObject actionItemsEntry = rawJson.getByPath(actionItemsPath, JSONObject.class);

        // toolName : tool的调用句柄【tool参数Map : tool返回值字符串】
        Map<String, Function<Map<String, Object>, String>> toolFunctionMap = new HashMap<>();
        for (ToolCallback tool : outerTools) {
            ToolDefinition def = tool.getToolDefinition();
            // 填充inputSchema
            actionItemsEntry.set(def.name(), JSONUtil.parseObj(def.inputSchema()));
            // 注册action的function句柄
            toolFunctionMap.put(def.name(), params -> tool.call(JSONUtil.toJsonStr(params)));
        }
        // 写回完整的toolSchema定义
        rawJson.putByPath(actionItemsPath, actionItemsEntry);

        // final+无toolCallback引用逃逸 保证this的多线程可见性
        this.toolCallback = MethodToolCallback.builder()
                .toolObject(this)
                .toolMethod(toolMethod)
                .toolDefinition(ToolDefinitions.builder(toolMethod)
                        // 显式指定增强后的inputSchema，不使用默认
                        .inputSchema(rawJson.toString())
                        .build())
                .toolMetadata(MetaData.builder().actionMap(toolFunctionMap).build())
                .build();
    }

    @Tool(name = "reActOutputTool", description = "ReAct任务工具")
    public Result apply(
            @ToolParam(description = "截止目前任务的进度跟踪信息，包括对上一子任务的结果评估、任务进度的记忆、导致当前决策的思考") ReActOutput.StepTrace stepTrace,
            @ToolParam(description = "当前决定调用的动作") List<Map<String, Map<String, Object>>> action
    ) {
        if (CollUtil.isEmpty(action)) {
            return new Result("no action was chose! thought: " + stepTrace.getThinking(), true);
        }
        Map<String, Function<Map<String, Object>, String>> toolFunctionMap = ((MetaData) this.toolCallback.getToolMetadata()).getActionMap();
        boolean success = true;
        StringBuilder trBuilder = new StringBuilder();
        for (Map<String, Map<String, Object>> a : action) {
            String actionName = a.keySet().stream().findFirst().orElseThrow();
            try {
                String result = toolFunctionMap.get(actionName).apply(a.get(actionName));
                trBuilder.append(actionName).append("action success! result: ").append(result).append("\n");
            } catch (Exception e) {
                trBuilder.append(actionName).append("action failed! result: ").append(e.getMessage()).append("\n");
                success = false;
            }
            break; // 一次仅调用一个工具
        }

        return new Result(trBuilder.toString(), success);
    }

    @NotNull
    @Override
    public ToolDefinition getToolDefinition() {
        return toolCallback.getToolDefinition();
    }

    @NotNull
    @Override
    public String call(@NotNull String toolInput) {
        return toolCallback.call(toolInput);
    }

    public record Result(String resultContent, Boolean success) {
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class MetaData implements ToolMetadata {
        private Map<String, Function<Map<String, Object>, String>> actionMap;
    }
}
