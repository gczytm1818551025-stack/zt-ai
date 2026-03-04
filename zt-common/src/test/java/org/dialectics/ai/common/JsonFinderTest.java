package org.dialectics.ai.common;

import com.alibaba.fastjson2.JSON;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dialectics.ai.common.utils.JsonFinder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;

import java.io.StringReader;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonFinderTest {
    @Test
    @DisplayName("场景业务实战测试：解析stepTrace")
    void testParseStepTrace() {
        String input = """
                ## 输入格式
                [最终目标] 这是你最终要完成的目标任务
                [子任务链] ...

                ## 返回值规则
                返回值结构只能如下：
                {
                    "stepTrace": {
                       "previousEvaluation": "Success|Failed",
                       "memory": "已执行事项",
                       "thinking": "模拟人类思考，包含引号内容 \\"心情愉悦\\""
                    },
                    "action": [{"print": { "content": "hello" }}]
                }

                ## 其他说明
                请严格遵守格式。
                """;
        String json = JsonFinder.findFirst(input);
        // 注意：解析器会保留原始文本中的换行和缩进
        String expected = "{\n    \"stepTrace\": {\n       \"previousEvaluation\": \"Success|Failed\",\n       \"memory\": \"已执行事项\",\n       \"thinking\": \"模拟人类思考，包含引号内容 \\\"心情愉悦\\\"\"\n    },\n    \"action\": [{\"print\": { \"content\": \"hello\" }}]\n}";// 4. 断言验证
        assertNotNull(json, "解析结果不应为空");
        assertEquals(expected, json, "提取的 JSON 内容必须与预期完全匹配");
        StepTrace stepTrace = JSON.parseObject(json, StepTrace.class);
        System.out.println(stepTrace);
        System.out.println(JSON.toJSONString(stepTrace));
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    static class StepTrace {
        private Status stepTrace;
        private List<Map<String, Object>> action;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        private static class Status {
            private String previousEvaluation;
            private String thinking;
            private String memory;
        }

        public static StepTrace noAction(AssistantMessage thinking, List<Message> history) {
            return StepTrace.builder().action(List.of()).stepTrace(Status.builder().memory(history.toString()).thinking(thinking.getText()).previousEvaluation("不确定").build()).build();
        }
    }

    @Test
    @DisplayName("场景1：标准 JSON 提取 - 混合在普通文本中")
    void testStandardExtraction() throws Exception {
        String input = "LogPrefix: 2026-02-12 [INFO] content: {\"id\":100, \"name\":\"Gemini\"} trailing garbage";
        String result = JsonFinder.findFirst(new StringReader(input));
        assertEquals("{\"id\":100, \"name\":\"Gemini\"}", result);
    }

    @Test
    @DisplayName("场景2：处理字符串内部的转义和干扰括号")
    void testComplexString() throws Exception {
        // 字符串内容里含有 } 和 \"，这是最容易让垃圾正则崩溃的地方
        String json = "{\"message\": \"contains a } bracket and \\\" escaped quote\"}";
        String input = "prefix text " + json + " suffix";

        String result = JsonFinder.findFirst(new StringReader(input));
        assertEquals(json, result);
    }

    @Test
    @DisplayName("场景3：处理嵌套结构 - 提取最外层完整对象")
    void testNestedStructure() throws Exception {
        String json = "{\"outer\": {\"inner\": [1, 2, 3]}, \"status\": \"ok\"}";
        String input = "random stuff " + json + " more random stuff";

        String result = JsonFinder.findFirst(new StringReader(input));
        assertEquals(json, result);
    }

    @Test
    @DisplayName("场景4：提取 JSON 数组")
    void testArrayExtraction() throws Exception {
        String json = "[{\"id\":1}, {\"id\":2}]";
        String input = "ArrayData: " + json;

        String result = JsonFinder.findFirst(new StringReader(input));
        assertEquals(json, result);
    }

    @Test
    @DisplayName("场景5：极限性能/熔断测试 - 模拟超过 10MB 的无效片段")
    void testOverSizeLimit() throws Exception {
        // 构造一个 { 开头，但后面跟着超过 10MB 垃圾字符且不闭合的情况
        StringBuilder bigGarbage = new StringBuilder("{");
        for (int i = 0; i < 11 * 1024 * 1024; i++) {
            bigGarbage.append('a');
        }
        // 后面紧跟一个真实的合法 JSON
        String validJson = "{\"status\":\"fixed\"}";
        String input = bigGarbage.toString() + " some split text " + validJson;

        String result = JsonFinder.findFirst(new StringReader(input));
        // 状态机应该在 10MB 处熔断并重置，随后成功捕捉到后面的 validJson
        assertEquals(validJson, result);
    }

    @Test
    @DisplayName("场景6：容错测试 - 遇到干扰的闭合符")
    void testIncorrectBrackets() throws Exception {
        // 模拟 } { 这种破坏结构的文本
        String input = "text with } extra brace { \"valid\": true }";
        String result = JsonFinder.findFirst(new StringReader(input));
        assertEquals("{ \"valid\": true }", result);
    }

    @Test
    @DisplayName("场景7：空输入和无 JSON 输入")
    void testNoJson() throws Exception {
        assertNull(JsonFinder.findFirst(new StringReader("just some plain text without any braces")));
        assertNull(JsonFinder.findFirst(new StringReader("")));
        assertNull(JsonFinder.findFirst(new StringReader("{ incomplete json")));
    }
}
