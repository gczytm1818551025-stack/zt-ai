package org.dialectics.ai.skills.repository.impl;

import cn.hutool.core.util.StrUtil;
import org.dialectics.ai.skills.domain.Skill;
import org.dialectics.ai.skills.provider.SkillsResourceProvider;
import org.dialectics.ai.skills.repository.SkillsRepository;
import org.dialectics.ai.skills.utils.MarkdownSkillParser;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultSkillsRepository implements SkillsRepository {
    private final Map<String, Skill.SkillMetadata> namedMetadataMap;
    private final Map<String, Resource> namedResources;

    public DefaultSkillsRepository(List<SkillsResourceProvider> skillsResourceProviders) {
        this.namedResources = checkDuplicatesAndFlat(skillsResourceProviders);
        this.namedMetadataMap = new ConcurrentHashMap<>();
        this.namedResources.forEach((name, resource) -> {
            String content = null;
            try {
                content = resource.getContentAsString(StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("加载skill " + name + " 失败", e);
            }
            Skill.SkillMetadata metadata = MarkdownSkillParser.parseMetadata(content);
            namedMetadataMap.put(name, metadata);
        });
    }

    @Override
    public Collection<Skill.SkillMetadata> findAllMetadata() {
        return namedMetadataMap.values();
    }

    @Override
    public Skill.SkillMetadata findMetadataByName(String name) {
        Assert.notNull(name, "skill's name can't be null");
        return namedMetadataMap.get(name);
    }

    @Override
    public Skill findByName(String name) {
        Assert.notNull(name, "skill's name can't be null");

        Resource resource = namedResources.get(name);
        String content = null;
        try {
            content = resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("加载skill " + name + " 失败", e);
        }
        return MarkdownSkillParser.parseContent(content);
    }

    private Map<String, Resource> checkDuplicatesAndFlat(List<SkillsResourceProvider> skillsResourceProviders) {
        Map<String, Resource> namedResources = new ConcurrentHashMap<>();
        HashSet<String> duplicates = new HashSet<>();
        HashSet<String> checkSet = new HashSet<>();
        for (SkillsResourceProvider provider : skillsResourceProviders) {
            for (Map.Entry<String, Resource> namedResource : provider.getNamedResources().entrySet()) {
                String name = namedResource.getKey();
                if (!checkSet.add(name)) {
                    duplicates.add(name);
                } else { // 无重复则加入
                    namedResources.put(name, namedResource.getValue());
                }
            }
        }
        Assert.isTrue(duplicates.isEmpty(), StrUtil.format("skill冲突，存在同名skill - dulplicates: {}", duplicates));
        return namedResources;
    }

}
