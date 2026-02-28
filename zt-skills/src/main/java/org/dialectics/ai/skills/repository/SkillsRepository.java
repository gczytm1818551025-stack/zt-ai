package org.dialectics.ai.skills.repository;

import org.dialectics.ai.skills.domain.Skill;

import java.util.Collection;

/**
 * skills仓库抽象，面向skills文件系统
 */
public interface SkillsRepository {
    /**
     * 获取所有skill的元数据列表
     *
     * @return 上下文中所有skill的元数据列表
     *
     */
    Collection<Skill.SkillMetadata> findAllMetadata();

    /**
     * 根据名称获取特定skill的原数据
     *
     * @param name skill名称
     * @return skill原数据对象
     *
     */
    Skill.SkillMetadata findMetadataByName(String name);

    /**
     * 根据名称获取特定skill的完整信息
     *
     * @param name skill名称
     * @return skill对象
     */
    Skill findByName(String name);

}
