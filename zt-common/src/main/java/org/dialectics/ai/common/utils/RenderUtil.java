package org.dialectics.ai.common.utils;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RenderUtil
 * 特性：高性能、全字符兼容、支持转义、无GC负担设计
 */
public final class RenderUtil {
    /**
     * 正则：
     * 1. (?<!\\\\) : 负向后行断言，确保 {{ 不被 \ 转义。即 \{{key}} 会被忽略。
     * 2. \\{\\{\\s* : 匹配 {{ 及其后的任意空格
     * 3. ([\\w\\.-]+) : 捕获组，支持单词、数字、下划线、点（用于嵌套对象）、中划线
     * 4. \\s*\\}\\} : 匹配尾部空格及 }}
     */
    private static final Pattern STABLE_PATTERN = Pattern.compile("(?<!\\\\)\\{\\{\\s*([\\w\\.-]+)\\s*\\}\\}");

    private RenderUtil() {
    }

    public static String render(final String template, final Map<String, Object> params) {
        if (template == null || template.isEmpty()) return "";
        if (params == null || params.isEmpty()) return template;

        final int len = template.length();
        // 预估容量：原长度 + 预计参数平均长度增量
        final StringBuilder sb = new StringBuilder(len + 256);
        final Matcher matcher = STABLE_PATTERN.matcher(template);

        int lastCursor = 0;
        while (matcher.find()) {
            // 1. 将上一个匹配点到当前匹配点之间的文本直接塞入（不经正则处理，极致性能）
            sb.append(template, lastCursor, matcher.start());

            String key = matcher.group(1);
            Object value = params.get(key);

            if (value != null) {
                // 2. 直接 append Object，Java 17 底层会根据类型优化 toString
                sb.append(value);
            } else {
                // 3. 容错：若 key 不存在，保留原样 {{key}}，确保 Prompt 结构不因渲染失败而坍塌
                sb.append(matcher.group(0));
            }

            lastCursor = matcher.end();
        }

        // 4. 剩余文本收尾
        if (lastCursor < len) {
            sb.append(template, lastCursor, len);
        }

        // 5. 后处理：将转义符 \{{ 还原为 {{
        return sb.toString().replace("\\{{", "{{");
    }
}
