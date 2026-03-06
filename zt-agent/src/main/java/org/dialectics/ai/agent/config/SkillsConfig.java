package org.dialectics.ai.agent.config;

import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.alibaba.cloud.ai.graph.skills.registry.classpath.ClasspathSkillRegistry;
import org.dialectics.ai.agent.skills.AgentSkillsHook;
import org.dialectics.ai.agent.skills.SkillsHook;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SkillsConfig {

    @Bean
    @ConditionalOnMissingBean(SkillsHook.class)
    public SkillsHook agentSkills(SkillRegistry skillRegistry) {
        return AgentSkillsHook.builder().skillRegistry(skillRegistry).build();
    }

    @Bean
    @ConditionalOnMissingBean(SkillRegistry.class)
    public SkillRegistry skillRegistry() {
        return ClasspathSkillRegistry.builder().classpathPath("skills").build();
    }

}
