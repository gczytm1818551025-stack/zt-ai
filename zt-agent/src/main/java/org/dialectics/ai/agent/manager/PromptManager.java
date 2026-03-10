package org.dialectics.ai.agent.manager;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.dialectics.ai.common.utils.RenderUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class PromptManager {
    /// Map<提示词文件名, 提示词模板内容>
    private static final Map<String, String> PROMPT_MAP = new ConcurrentHashMap<>();

    @PostConstruct
    public void initializingLoadPromptResources() throws IOException {
        PathMatchingResourcePatternResolver loader = new PathMatchingResourcePatternResolver();
        Resource[] promptResources = loader.getResources("classpath:prompt/**/*.st");
        for (Resource resource : promptResources) {
            PROMPT_MAP.put(resource.getFilename(), IoUtil.readUtf8(resource.getInputStream()));
            log.info("基础提示词【{}】加载成功！", resource.getFilename());
        }
    }

    /**
     * 渲染prompt模板
     *
     * @param name   prompt文件名
     * @param params 模板参数
     * @return 渲染后的完整utf8文本内容
     */
    public static String renderFrom(String name, Map<String, Object> params) {
        String promptKey = StrUtil.endWith(name, ".st") ? name : name + ".st";
        return RenderUtil.render(PROMPT_MAP.get(promptKey), params);
    }

    /**
     * 渲染prompt模板（无参数）
     *
     * @param name prompt文件名称（无后缀）
     * @return 渲染后的完整utf8文本内容
     */
    public static String renderFrom(String name) {
        return renderFrom(name, null);
    }
}
