package org.dialectics.ai.agent.domain.vo;

import lombok.*;
import org.dialectics.ai.agent.domain.pojo.StepTrace;
import org.dialectics.ai.common.domain.EventVo;
import org.dialectics.ai.common.enums.EventTypeEnum;
import org.dialectics.ai.common.enums.ReActStageEnum;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReActEventVo implements EventVo {
    private Object data;
    /// 事件类型 - 1001数据事件，1002停止事件，1003参数事件
    private EventTypeEnum type;
    // 事件发生阶段 - 0子任务规划内容，1策略思考内容，2行动结果，3反思内容，4最终总结
    private ReActStageEnum stage;

    /// 事件序列号 - 用于保证顺序和去重
    private Long sequenceNumber;
    /// 事件时间戳（ms）
    private Long timestamp;
    /// 会话ID - 用于前端追踪
    private String sessionId;

    public record PlanData(
            /// 子任务序号
            int index,
            /// 任务截止当前的局部总结
            String previousEvaluation,
            String memory,
            String thinking,
            /// 子任务内容
            String taskContent
    ) {
    }

    public record ThinkData(
            /// 策略思考内容
            String thinkContent
    ) {
    }

    public record ActionData(
            /// 动作是否成功
            Boolean success,
            /// 行动结果内容
            String result
    ) {
    }

    public record FinalData(
            String finalResult
    ) {
    }

    /**
     * 创建数据事件（默认成功状态）
     */
    public static ReActEventVo newDataEvent(Object data, ReActStageEnum stage) {
        return ReActEventVo.builder().data(data).type(EventTypeEnum.DATA).stage(stage).build();
    }

    /**
     * 创建停止事件
     */
    public static ReActEventVo stopEvent() {
        return STOP_EVENT;
    }

    private static final ReActEventVo STOP_EVENT = ReActEventVo.builder().type(EventTypeEnum.STOP).build();
}
