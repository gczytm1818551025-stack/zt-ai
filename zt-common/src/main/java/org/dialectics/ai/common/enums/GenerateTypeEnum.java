package org.dialectics.ai.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum GenerateTypeEnum {
    /// 普通对话
    NORMAL("0"),
    /// 重新生成
    REGENERATE("1");

    @JsonValue
    private final String code;

    GenerateTypeEnum(String code) {
        this.code = code;
    }

    @JsonCreator
    public static GenerateTypeEnum fromCode(String code) {
        for (GenerateTypeEnum type : GenerateTypeEnum.values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown ChatType code: " + code);
    }
}
