package org.dialectics.ai.agent.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dialectics.ai.common.domain.Vo;
import org.dialectics.ai.common.enums.SessionTypeEnum;

import java.io.Serial;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRecordVo implements Vo {
    private String sessionId;
    private SessionTypeEnum sessionType;
    private String title;
    private String describe;
    private LocalDateTime updateTime;
}
