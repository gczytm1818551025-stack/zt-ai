package org.dialectics.ai.skills.config;

import org.dialectics.ai.skills.AgentSkills;
import org.dialectics.ai.skills.Skills;
import org.dialectics.ai.skills.provider.SkillsResourceProvider;
import org.dialectics.ai.skills.provider.impl.ClasspathSkillsResourceProvider;
import org.dialectics.ai.skills.repository.SkillsRepository;
import org.dialectics.ai.skills.repository.impl.DefaultSkillsRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SkillsConfig {

    @Bean
    @ConditionalOnMissingBean(Skills.class)
    public Skills agentSkills(SkillsRepository skillsRepository) {
        return new AgentSkills(skillsRepository);
    }

    /// Spring自动搜集容器中所有SkillsResourceProvider实例并注入
    @Bean
    @ConditionalOnMissingBean(SkillsRepository.class)
    public SkillsRepository skillsRepository(List<SkillsResourceProvider> skillsResourceProviders) {
        return new DefaultSkillsRepository(skillsResourceProviders);
    }

    @Bean
    public SkillsResourceProvider skillsResourceProvider() {
        return new ClasspathSkillsResourceProvider();
    }

}
