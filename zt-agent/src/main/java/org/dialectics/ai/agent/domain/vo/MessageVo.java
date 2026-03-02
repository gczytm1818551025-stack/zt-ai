package org.dialectics.ai.agent.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dialectics.ai.common.domain.Vo;
import org.dialectics.ai.common.enums.MessageTypeEnum;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageVo implements Vo {
    /**
     * 消息类型，USER-用户提问，ASSISTANT--AI的回答
     */
    private MessageTypeEnum type;
    /**
     * 消息内容
     */
    private String content;
    /**
     * 附加参数（用于 Tool 调用等，与 ReAct steps 解耦）
     */
    private Map<String, Object> params;

    // ========== ReAct 步骤专用字段（与 params 解耦） ==========
    /**
     * ReAct 步骤列表
     */
    private List<Map<String, Object>> steps;

    /**
     * ReAct 步骤计数
     */
    private Integer stepCount;
    // ==========================================================

}
