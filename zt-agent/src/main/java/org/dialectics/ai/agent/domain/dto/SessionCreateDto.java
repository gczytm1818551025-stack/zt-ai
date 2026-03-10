package org.dialectics.ai.agent.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dialectics.ai.common.domain.Dto;
import org.dialectics.ai.common.enums.SessionTypeEnum;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SessionCreateDto implements Dto {
    SessionTypeEnum type;
}
