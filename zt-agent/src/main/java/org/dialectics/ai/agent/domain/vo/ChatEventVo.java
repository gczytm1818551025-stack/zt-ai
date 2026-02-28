package org.dialectics.ai.agent.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dialectics.ai.common.domain.EventVo;
import org.dialectics.ai.common.enums.EventTypeEnum;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatEventVo implements EventVo {
    private Object data;
    /// 事件类型，1001-数据事件，1002-停止事件，1003-参数事件
    private EventTypeEnum type;


    public static ChatEventVo newDataEvent(Object data) {
        return new ChatEventVo(data, EventTypeEnum.DATA);
    }

    public static ChatEventVo newParamEvent(Object param) {
        return new ChatEventVo(param, EventTypeEnum.PARAM);
    }

    public static ChatEventVo stopEvent() {
        return STOP_EVENT;
    }

    public static ChatEventVo newStopEvent(Object data) {
        return new ChatEventVo(data, EventTypeEnum.STOP);
    }

    /**
     * 全局终止事件单例
     */
    private static final ChatEventVo STOP_EVENT = new ChatEventVo(null, EventTypeEnum.STOP);
}
