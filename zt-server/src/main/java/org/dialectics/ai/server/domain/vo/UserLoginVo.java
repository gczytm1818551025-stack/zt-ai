package org.dialectics.ai.server.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.dialectics.ai.common.domain.Vo;

import java.io.Serial;

@Data
@AllArgsConstructor
@Builder
public class UserLoginVo implements Vo {
    @Serial
    private static final long serialVersionUID = 1L;

    /// 鉴权token
    private String token;
    /// 昵称
    private String nickName;
}
