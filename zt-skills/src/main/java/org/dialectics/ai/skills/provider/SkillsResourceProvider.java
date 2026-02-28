package org.dialectics.ai.skills.provider;

import org.springframework.core.io.Resource;

import java.util.Collection;
import java.util.Map;

public interface SkillsResourceProvider {
    Map<String, Resource> getNamedResources();
}
