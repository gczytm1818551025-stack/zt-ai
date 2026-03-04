package org.dialectics.ai.agent.tools;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.*;
import org.dialectics.ai.agent.domain.pojo.ReActOutput;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class ReActOutputTool {
    /// toolName : tool的调用句柄【tool参数Map : tool返回值字符串】
    private final Map<String, Function<Map<String, Object>, String>> toolFunctionMap = new HashMap<>();
    @Getter
    private final String inputSchema;

    public ReActOutputTool(List<ToolCallback> outerTools) {
        String inputSchema;
        try {
            inputSchema = JsonSchemaGenerator.generateForMethodInput(this.getClass().getMethod("apply", ReActOutput.StepTrace.class, List.class));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("method apply is not exist, inputSchema of " + this.getClass().getName() + " is failed generated");
        }
        JSONObject rawJson = JSONUtil.parseObj(inputSchema);
        // 手动构造被泛型参数缺失抹去的JSON节点
        JSONObject itemsNode = JSONUtil.createObj()
                .set("type", "object")
                .set("properties", JSONUtil.createObj());
        rawJson.putByPath("properties.action.items", itemsNode);

        String actionItemsPath = "properties.action.items.properties";
        JSONObject actionItemsEntry = rawJson.getByPath(actionItemsPath, JSONObject.class);

        for (ToolCallback tool : outerTools) {
            ToolDefinition def = tool.getToolDefinition();
            // 填充inputSchema
            actionItemsEntry.set(def.name(), JSONUtil.parseObj(def.inputSchema()));
            // 注册action的function句柄
            toolFunctionMap.put(def.name(), params -> tool.call(JSONUtil.toJsonStr(params)));
        }
        // 写回完整的toolSchema定义
        rawJson.putByPath(actionItemsPath, actionItemsEntry);
        this.inputSchema = rawJson.toString();
    }

    @Tool(name = "reActOutputTool", description = "ReAct任务工具")
    public Result apply(
            @ToolParam(description = "截止目前任务的进度跟踪信息") ReActOutput.StepTrace stepTrace,
            @ToolParam(description = "当前想要调用的工具列表") List<Map<String, Map<String, Object>>> action
    ) {
        if (CollUtil.isEmpty(action)) {
            return new Result("no action was chose! thought: " + stepTrace.getThinking(), false);
        }
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
        }

        return new Result(trBuilder.toString(), success);
    }

    public Map<String, Function<Map<String, Object>, String>> getActions() {
        return toolFunctionMap;
    }

    public record Result(String resultContent, Boolean success) {
    }
}
