package org.dialectics.ai.skills;

import com.alibaba.fastjson2.JSON;
import org.dialectics.ai.skills.provider.impl.ClasspathSkillsResourceProvider;
import org.dialectics.ai.skills.repository.impl.DefaultSkillsRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

public class AgentSkillsTest {
    @Test
    public void skillsTest() {
        AgentSkills skills = new AgentSkills(new DefaultSkillsRepository(List.of(new ClasspathSkillsResourceProvider())));
        skills.metadata().forEach(System.out::println);
        System.out.println(skills.get("skill-amap"));
        System.out.println(skills.get("skill-browser"));
    }

    @Test
    public void skillsRepositoryTest() {
        ClasspathSkillsResourceProvider provider = new ClasspathSkillsResourceProvider();
        DefaultSkillsRepository repository = new DefaultSkillsRepository(List.of(provider));
        System.out.println(repository.findAllMetadata());
//        System.out.println(repository.findMetadataByName("skill-amap"));
        System.out.println(repository.findMetadataByName("skill-browser"));
//        System.out.println(repository.findByName("skill-amap"));
//        System.out.println(repository.findByName("skill-browser"));
        // JSON序列化只能用fastjson，用hutool时metadata会序列化失败
        System.out.println(JSON.toJSONString(repository.findByName("skill-amap")));
    }
}
