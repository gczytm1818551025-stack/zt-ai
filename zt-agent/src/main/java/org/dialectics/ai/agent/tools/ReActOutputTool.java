package org.dialectics.ai.agent.tools;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.dialectics.ai.agent.domain.pojo.ReActOutput;
import org.dialectics.ai.agent.domain.pojo.ToolOutput;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.definition.ToolDefinition;
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
 * ReAct标准输出工具
 */
public class ReActOutputTool {
    private final String inputSchema;
    private final Map<String, Function<Map<String, Object>, String>> toolFunctionMap;

    private ReActOutputTool(List<ToolCallback> outerTools) {
        Method toolMethod = ReflectionUtils.findMethod(ReActOutputTool.class, "apply", ReActOutput.StepTrace.class, List.class);
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
        this.inputSchema = rawJson.toString();
        this.toolFunctionMap = toolFunctionMap;
    }

    public static ToolCallback createReActOutputToolCallback(List<ToolCallback> outerTools) {
        Method toolMethod = ReflectionUtils.findMethod(ReActOutputTool.class, "apply", ReActOutput.StepTrace.class, List.class);
        ReActOutputTool instance = new ReActOutputTool(outerTools);
        return MethodToolCallback.builder()
                .toolObject(instance)
                .toolMethod(toolMethod)
                .toolDefinition(ToolDefinitions.builder(toolMethod)
                        // 显式指定增强后的inputSchema，不使用默认
                        .inputSchema(instance.inputSchema)
                        .build())
                .build();
    }

    @Tool(name = "reActOutputTool", description = "ReAct任务工具")
    public ToolOutput apply(
            @ToolParam(description = "截止目前任务的进度跟踪信息，包括对上一子任务的结果评估、任务进度的记忆、导致当前决策的思考") ReActOutput.StepTrace stepTrace,
            @ToolParam(description = "当前决定调用的动作") List<Map<String, Map<String, Object>>> action
    ) {
        if (CollUtil.isEmpty(action)) {
            return new ToolOutput(NON_TOOL, "no action was chose! thought: " + stepTrace.getThinking(), true);
        }
        Map<String, Function<Map<String, Object>, String>> toolFunctionMap = this.toolFunctionMap;
        boolean success = true;
        StringBuilder trBuilder = new StringBuilder();
        String toolName = NON_TOOL;
        for (Map<String, Map<String, Object>> a : action) {
            toolName = a.keySet().stream().findFirst().orElseThrow();
            try {
                String result = toolFunctionMap.get(toolName).apply(a.get(toolName));
                trBuilder.append(toolName).append("action success! result: ").append(result).append("\n");
            } catch (Exception e) {
                trBuilder.append(toolName).append("action failed! result: ").append(e.getMessage()).append("\n");
                success = false;
            }
            break; // 暂且一次仅调用一个工具
        }

        return new ToolOutput(toolName, trBuilder.toString(), success);
    }

    public static final String NON_TOOL = "unknown";
}
