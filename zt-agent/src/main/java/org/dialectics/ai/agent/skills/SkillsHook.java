package org.dialectics.ai.agent.skills;

import com.alibaba.cloud.ai.graph.skills.SkillMetadata;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

public interface SkillsHook {
    List<SkillMetadata> listSkills();

    boolean hasSkill(String skillName);

    String getSkillContent(String skillName);

    default String getName() {
        return this.getClass().getSimpleName();
    }
}
