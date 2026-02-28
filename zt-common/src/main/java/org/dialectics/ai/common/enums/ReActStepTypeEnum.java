package org.dialectics.ai.common.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * ReAct 步骤类型枚举
 * 使用整数 code 传输，与前端枚举对应
 */
@Getter
public enum ReActStepTypeEnum {
    PLAN(1),
    THINKING(2),
    ACTION(3),
    FINAL(4)
    ;

    @JsonValue
    private final Integer code;

    ReActStepTypeEnum(Integer code) {
        this.code = code;
    }

    public static ReActStepTypeEnum fromCode(Integer code) {
        if (null != code) {
            for (ReActStepTypeEnum type : values()) {
                if (type.code.equals(code)) {
                    return type;
                }
            }
        }
        throw new IllegalArgumentException("No enum constant for reAct step type code " + code);
    }
}
