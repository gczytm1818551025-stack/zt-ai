package org.dialectics.ai.common.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 消息类型枚举
 */
@Getter
public enum MessageTypeEnum {
    USER(1, "用户提问"),
    ASSISTANT(2, "AI的回答");

    @JsonValue
    private final int value;
    private final String desc;

    MessageTypeEnum(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    @Override
    public String toString() {
        return this.name();
    }
}
