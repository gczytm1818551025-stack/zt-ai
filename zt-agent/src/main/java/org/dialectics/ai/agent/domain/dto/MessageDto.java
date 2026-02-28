package org.dialectics.ai.agent.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dialectics.ai.common.constants.MessageTypeConstant;
import org.dialectics.ai.common.domain.Dto;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageDto implements Dto {
    // 消息类型
    private String type = MessageTypeConstant.TYPE_SERVER;
    // 消息内容
    private String text;
    // 图片地址
    private String imageUrl;
    // 文件地址
    private String fileUrl;
    // 链接地址
    private String openUrl;

}
