package org.dialectics.ai.skills;

import org.dialectics.ai.skills.domain.Skill;

import java.util.Collection;

public interface Skills {
    Collection<Skill.SkillMetadata> metadata();

    Skill get(String name);
}
