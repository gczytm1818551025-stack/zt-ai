package org.dialectics.ai.common.enums;

import lombok.Getter;

/**
 * 工具动作类型枚举
 * <p>
 * 用于标识 ReAct 流程中不同类型的工具动作
 */
@Getter
public enum ToolActionTypeEnum {
    /**
     * 生成下一个子任务
     */
    GENERATE_NEXT("generateNext"),

    /**
     * 完成任务并返回结果
     */
    DONE("done"),

    /**
     * 思考/规划动作
     */
    THINK("think"),

    /**
     * 其他未知动作类型
     */
    UNKNOWN("unknown");

    private final String actionName;

    ToolActionTypeEnum(String actionName) {
        this.actionName = actionName;
    }

    @Override
    public String toString() {
        return this.name();
    }

    /**
     * 根据动作名称查找对应的枚举
     *
     * @param actionName 动作名称字符串
     * @return 对应的动作类型枚举，如果未找到则返回 UNKNOWN
     */
    public static ToolActionTypeEnum fromActionName(String actionName) {
        if (actionName == null || actionName.trim().isEmpty()) {
            return UNKNOWN;
        }

        for (ToolActionTypeEnum type : values()) {
            if (type.actionName.equalsIgnoreCase(actionName)) {
                return type;
            }
        }
        return UNKNOWN;
    }

    /**
     * 判断是否为有效的动作名称
     *
     * @param actionName 动作名称字符串
     * @return 如果是已知的动作类型则返回 true
     */
    public static boolean isValidActionName(String actionName) {
        return fromActionName(actionName) != UNKNOWN;
    }

    /**
     * 判断是否为生成下一个子任务的动作
     */
    public boolean isGenerateNext() {
        return this == GENERATE_NEXT;
    }

    /**
     * 判断是否为完成任务的动作
     */
    public boolean isDone() {
        return this == DONE;
    }
}
