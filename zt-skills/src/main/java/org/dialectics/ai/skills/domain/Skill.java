package org.dialectics.ai.skills.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
public class Skill implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /*
     * -------- Frontmatter 字段规范 --------
     * 字段              是否必须  描述
     * name             是       最多64字符，仅小写字母、数字、连字符，不能以连字符开头或结尾
     * description      是       最多1024字符，描述技能功能和使用场景
     */
    public record SkillMetadata(String name, String description) {
    }

    private SkillMetadata metadata;
    private String title;
    private String instructions;
    private List<String> examples;
    private List<String> guidelines;

}
