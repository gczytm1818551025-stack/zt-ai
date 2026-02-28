package org.dialectics.ai.skills;

import org.dialectics.ai.skills.domain.Skill;
import org.dialectics.ai.skills.repository.SkillsRepository;

import java.util.Collection;


public class AgentSkills implements Skills {
    private final SkillsRepository skillsRepository;

    public AgentSkills(SkillsRepository skillsRepository) {
        this.skillsRepository = skillsRepository;
    }
    @Override
    public Collection<Skill.SkillMetadata> metadata() {
        return skillsRepository.findAllMetadata();
    }

    @Override
    public Skill get(String name) {
        return skillsRepository.findByName(name);
    }
}
