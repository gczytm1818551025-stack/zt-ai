package org.dialectics.ai.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum SessionTypeEnum {
    CHAT(0),
    AGENT(1);

    @JsonValue
    @EnumValue
    private final int code;

    SessionTypeEnum(int code) {
        this.code = code;
    }

    @JsonCreator
    public static SessionTypeEnum fromCode(int code) {
        for (SessionTypeEnum e : SessionTypeEnum.values()) {
            if (e.code == code) {
                return e;
            }
        }
        throw new IllegalArgumentException("No enum constant for session type code " + code);
    }
}
