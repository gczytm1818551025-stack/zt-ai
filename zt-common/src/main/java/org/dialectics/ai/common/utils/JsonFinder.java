package org.dialectics.ai.common.utils;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import lombok.SneakyThrows;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

public class JsonFinder {
    // 开启宽容模式，允许注释、未转义字符等（应对 LLM 的一些不规范输出）
    private static final JsonFactory JSON_FACTORY = new JsonFactory()
            .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS.mappedFeature())
            .enable(JsonReadFeature.ALLOW_YAML_COMMENTS.mappedFeature())
            .enable(JsonReadFeature.ALLOW_MISSING_VALUES.mappedFeature());

    private static final int MAX_JSON_SIZE = 10 * 1024 * 1024;
    private static final int INITIAL_BUFFER_SIZE = 64 * 1024;

    private JsonFinder() {
    }

    public static String findFirst(String text) {
        if (StrUtil.isEmpty(text)) {
            return null;
        }
        try (StringReader sr = new StringReader(text)) {
            return findFirst(sr);
        }
    }

    @SneakyThrows
    public static String findFirst(Reader reader) {
        StringBuilder buffer = new StringBuilder(INITIAL_BUFFER_SIZE);

        int braceDepth = 0;
        int bracketDepth = 0;
        boolean inString = false;
        boolean isEscaped = false;
        boolean recording = false;

        int data;
        while ((data = reader.read()) != -1) {
            char c = (char) data;

            // 1. 寻找起始点
            if (!recording) {
                if (c == '{' || c == '[') {
                    recording = true;
                    if (c == '{') braceDepth = 1;
                    else bracketDepth = 1;
                    buffer.append(c);
                }
                continue;
            }

            // 2. 录入模式
            buffer.append(c);

            // 3. 安全熔断
            if (buffer.length() > MAX_JSON_SIZE) {
                reset(buffer);
                recording = false;
                braceDepth = 0;
                bracketDepth = 0;
                continue;
            }

            // 4. 状态机逻辑
            if (isEscaped) {
                isEscaped = false;
                continue;
            }
            if (c == '\\') {
                isEscaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) continue;

            // 5. 括号深度计算
            if (c == '{') braceDepth++;
            else if (c == '}') braceDepth--;
            else if (c == '[') bracketDepth++;
            else if (c == ']') bracketDepth--;

            // 6. 归零判定（完整块提取）
            if (braceDepth == 0 && bracketDepth == 0) {
                String candidate = buffer.toString();
                // 核心修复：使用更智能的校验逻辑
                if (smartValidate(candidate)) {
                    return candidate;
                } else {
                    // 校验失败（例如提取到了 [最终目标]），重置继续寻找
                    reset(buffer);
                    recording = false;
                }
            }

            // 7. 容错逻辑
            if (braceDepth < 0 || bracketDepth < 0) {
                reset(buffer);
                recording = false;
                braceDepth = 0;
                bracketDepth = 0;
            }
        }
        return null;
    }

    private static void reset(StringBuilder sb) {
        sb.setLength(0);
        if (sb.capacity() > 1024 * 1024) {
            sb.trimToSize();
            sb.ensureCapacity(INITIAL_BUFFER_SIZE);
        }
    }

    /**
     * 智能校验
     * 1. 先尝试严格/标准校验
     * 2. 如果失败，判断是否为"类JSON"结构，防止因转义问题丢弃有效数据
     */
    private static boolean smartValidate(String json) {
        // 排除过短的噪音
        if (json.length() < 2) return false;

        // 步骤一：尝试 Jackson 校验 (利用配置的宽容特性)
        try (JsonParser parser = JSON_FACTORY.createParser(new StringReader(json))) {
            while (parser.nextToken() != null) {
            }
            return true;
        } catch (IOException e) {
            // 步骤二：兜底策略 (Heuristic Fallback)
            // 针对案例中 [最终目标] 这种 Markdown 标记，它没有双引号，应该被丢弃
            // 针对案例中 { ... \\" ... } 这种转义错误的 JSON，它包含双引号和键值对，应该被保留

            boolean hasQuotes = json.indexOf('"') != -1;
            boolean isObject = json.startsWith("{");
            boolean isArray = json.startsWith("[");

            // 如果是对象结构，且包含引号，即使 Jackson 报错也认为是目标 JSON (容忍 LLM 的转义幻觉)
            if (isObject && hasQuotes) {
                return true;
            }

            // 如果是数组，必须包含引号才算 JSON (过滤掉 [标题] 这种 Markdown 语法)
            if (isArray && hasQuotes) {
                return true;
            }

            return false;
        }
    }
}
