package org.dialectics.ai.agent;

import org.dialectics.ai.agent.domain.pojo.ReActOutput;
import org.dialectics.ai.agent.domain.pojo.TaskNode;
import org.dialectics.ai.agent.tools.ReActOutputTool;
import org.dialectics.ai.common.enums.ReActParamEnum;
import org.junit.jupiter.api.Test;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.ai.tool.support.ToolDefinitions;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.dialectics.ai.agent.utils.ReActControlVisitor.completed;

public class TestReActOutputTool {
    String t = "{\"currentState\": {\"previousEvaluation\": \"未知\", \"memory\": \"尚未执行任何子任务，总任务为查询昆明市西山区明天的天气情况。\", \"thinking\": \"我需要查询昆明市西山区明天的天气，而高德地图技能（amap-skill）支持天气查询功能，因此我应该使用amap-skill来获取该地区的天气信息。\"}, \"action\": \n" +
            "[{\"generateNext\": {\"skillName\": \"amap-skill\", \"taskContent\": \"查询昆明市西山区明天的天气情况\"}}]\n" +
            "\n" +
            "}";
    String t2 = "{\"stepTrace\": {\"currentState\": {\"previousEvaluation\": \"未知\", \"memory\": \"尚未执行任何子任务，总任务为查询昆明市西山区明天的天气情况。\", \"thinking\": \"我需要查询昆明市西山区明天的天气，而高德地图技能（amap-skill）支持天气查询功能，因此我应该使用amap-skill来获取该地区的天气信息。\"}, \"action\": \n" +
            "[{\"generateNext\": {\"skillName\": \"amap-skill\", \"taskContent\": \"查询昆明市西山区明天的天气情况\"}}]\n" +
            "\n" +
            "}}";
    @Test
    public void testOutput() {
//        StepTrace stepTrace = JSON.parseObject(t2, StepTrace.class);
//        System.out.println(stepTrace);
        ToolCallback[] tools = ToolCallbacks.from(new PlanTools(), new DoneTools(new AgentExecutionContext()));
        ReActOutputTool reActOutputTool = new ReActOutputTool(Arrays.asList(tools));
        System.out.println(reActOutputTool.getInputSchema());

        Method apply = ReflectionUtils.findMethod(ReActOutputTool.class, "apply", ReActOutput.StepTrace.class, List.class);
        MethodToolCallback tool = MethodToolCallback.builder()
                .toolObject(reActOutputTool)
                .toolMethod(apply)
                .toolDefinition(ToolDefinitions.builder(apply)
                        .inputSchema(reActOutputTool.getInputSchema())
                        .build())
                .build();
        System.out.println(tool);
    }

    /**
     * Plan工具
     */
    record PlanTools() {
        @Tool(name = "generateNext", description = "规划下一个子任务节点")
        public TaskNode generateNext(
                @ToolParam(description = "skill名称") String skillName,
                @ToolParam(description = "子任务内容") String taskContent
        ) {
            return TaskNode.builder().skillName(skillName).taskContent(taskContent).build();
        }
    }

    /**
     * Done工具
     */
    record DoneTools(AgentExecutionContext context) {
        @Tool(name = "done", description = "任务完成")
        public String done(
                @ToolParam(description = "任务完成情况的最终总结") String text,
                @ToolParam(description = "任务是否成功") Boolean success
        ) {
            completed(context).set(true);
            context.set(ReActParamEnum.FINAL_RESULT, text);
            return text;
        }
    }
}
