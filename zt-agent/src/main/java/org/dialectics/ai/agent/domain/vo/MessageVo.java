package org.dialectics.ai.agent.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dialectics.ai.common.domain.Vo;
import org.dialectics.ai.common.enums.MessageTypeEnum;

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
     * 附加参数
     */
    private Map<String, Object> params;

}
