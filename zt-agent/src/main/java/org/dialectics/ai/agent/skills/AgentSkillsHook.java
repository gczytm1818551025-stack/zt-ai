package org.dialectics.ai.agent.skills;

import com.alibaba.cloud.ai.graph.agent.hook.skills.ReadSkillTool;
import com.alibaba.cloud.ai.graph.skills.SkillMetadata;
import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import org.springframework.ai.tool.ToolCallback;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class AgentSkillsHook implements SkillsHook {
    private final SkillRegistry skillRegistry;
    private final Map<String, List<ToolCallback>> groupedTools;
    private final ToolCallback readSkillTool;

    private AgentSkillsHook(Builder builder) {
        if (builder.skillRegistry == null) {
            throw new IllegalArgumentException("SkillRegistry must be provided!");
        }
        this.skillRegistry = builder.skillRegistry;
        this.groupedTools = builder.groupedTools != null ? builder.groupedTools : Collections.emptyMap();
        this.readSkillTool = ReadSkillTool.createReadSkillToolCallback(this.skillRegistry, "Reads the full content of a skill from the SkillRegistry.\nYou can use this tool to read the complete content of any skill by providing its name.\n\nUsage:\n- The skill_name parameter must match the name of the skill as registered in the registry\n- The tool returns the full content of the skill file (e.g., SKILL.md) without frontmatter\n- If the skill is not found, an error will be returned\n\nExample:\n- read_skill(\"pdf-extractor\")\n");
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public List<SkillMetadata> listSkills() {
        return skillRegistry.listAll();
    }

    @Override
    public boolean hasSkill(String skillName) {
        return skillRegistry.contains(skillName);
    }

    @Override
    public String getSkillContent(String skillName) {
        try {
            return skillRegistry.readSkillContent(skillName);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read skill content: " + skillName);
        }
    }

    public List<ToolCallback> listTools() {
        return List.of(this.readSkillTool);
    }

    public static class Builder {
        private SkillRegistry skillRegistry;
        private Map<String, List<ToolCallback>> groupedTools;

        public Builder skillRegistry(SkillRegistry skillRegistry) {
            this.skillRegistry = skillRegistry;
            return this;
        }

        public Builder groupedTools(Map<String, List<ToolCallback>> groupedTools) {
            this.groupedTools = groupedTools;
            return this;
        }

        public AgentSkillsHook build() {
            return new AgentSkillsHook(this);
        }
    }
}
