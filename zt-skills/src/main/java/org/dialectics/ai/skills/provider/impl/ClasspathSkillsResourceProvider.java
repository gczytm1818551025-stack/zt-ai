package org.dialectics.ai.skills.provider.impl;

import org.dialectics.ai.skills.provider.SkillsResourceProvider;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClasspathSkillsResourceProvider implements SkillsResourceProvider {
    /*
     * ------ Agent Skills 目录规范 ------
     * [skill-name]/
     *  ├── SKILL.md         # 必需：指令和元数据
     *  ├── scripts/         # 可选：可执行脚本
     *  ├── references/      # 可选：参考文档
     *  └── assets/          # 可选：模板和资源
     * */
    private static final String PATH_PATTERN = "classpath*:**/*/SKILL.md";
    private final ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    /// skill名称提取正则式
    private static final Pattern NAME_EXTRACTOR = Pattern.compile("([^/]+)/([^/]+)/SKILL\\.md$");
    private final Map<String, Resource> namedResourceMap = new ConcurrentHashMap<>();

    public ClasspathSkillsResourceProvider() {
        try {
            Resource[] resources = resolver.getResources(PATH_PATTERN);
            for (Resource resource : resources) {
                String path = resource.getURL().getPath();
                Matcher matcher = NAME_EXTRACTOR.matcher(path);

                if (matcher.find()) {
                    String name = matcher.group(2);
                    namedResourceMap.put(name, resource);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("提取skill资源失败：", e);
        }

    }

    @Override
    public Map<String, Resource> getNamedResources() {
        return namedResourceMap;
    }
}
