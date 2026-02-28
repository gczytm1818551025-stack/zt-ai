package org.dialectics.ai.common.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Data;
import lombok.Getter;

@Getter
public enum ReActStageEnum {
    /// 子任务规划内容
    TASK_PLAN(0, "子任务规划内容"),
    /// 策略思考内容
    STRATEGY_THINK(1, "策略思考内容"),
    /// 行动结果
    ACTION_RESULT(2, "行动结果"),
    /// 反思内容
    REFLECT(3, "反思内容"),
    /// 最终总结
    FINAL_SUMMARY(4, "最终总结");

    @JsonValue
    private final int value;
    private final String desc;

    ReActStageEnum(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }
}
