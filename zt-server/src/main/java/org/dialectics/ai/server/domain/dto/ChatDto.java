package org.dialectics.ai.server.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dialectics.ai.common.domain.Dto;
import org.dialectics.ai.common.enums.GenerateTypeEnum;

import java.io.Serial;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatDto implements Dto {
    @Serial
    private static final long serialVersionUID = 1L;

    /// 会话id
    private String sessionId;
    /// 用户的问题
    private String question;
    /// 对话类型：0-普通对话，1-重新生成
    private GenerateTypeEnum type;
}
