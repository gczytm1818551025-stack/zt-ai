package org.dialectics.ai.common.enums;

import cn.hutool.core.util.EnumUtil;
import lombok.Getter;

/**
 * 智能体类型
 */
@Getter
public enum AgentEnum {
    ChatAgent("chatAgent"),
    ReActTaskAgent("reActTaskAgent"),
    ;

    private final String name;

    AgentEnum(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return this.name();
    }

    /**
     * 通过智能体的名称查找枚举
     */
    public static AgentEnum nameOf(String name) {
        return EnumUtil.getBy(AgentEnum::getName, name);
    }
}
