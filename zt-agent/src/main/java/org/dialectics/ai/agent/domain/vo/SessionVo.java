package org.dialectics.ai.agent.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dialectics.ai.common.domain.Vo;
import org.dialectics.ai.common.enums.SessionTypeEnum;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionVo implements Vo {
    private String sessionId;
    private String title;
    private SessionTypeEnum sessionType;
}
