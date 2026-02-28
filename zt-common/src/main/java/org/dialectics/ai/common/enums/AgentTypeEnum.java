package org.dialectics.ai.common.enums;

import cn.hutool.core.util.EnumUtil;
import lombok.Getter;

/**
 * 智能体类型
 */
@Getter
public enum AgentTypeEnum {
    ChatAgent("chatAgent"),
    ReActTaskAgent("reActTaskAgent"),
    ;

    private final String agentName;

    AgentTypeEnum(String agentName) {
        this.agentName = agentName;
    }

    @Override
    public String toString() {
        return this.name();
    }

    /**
     * 通过智能体的名称查找枚举
     */
    public static AgentTypeEnum nameOf(String agentName) {
        return EnumUtil.getBy(AgentTypeEnum::getAgentName, agentName);
    }
}
