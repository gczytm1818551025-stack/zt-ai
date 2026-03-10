package org.dialectics.ai.server.domain.dto;

import lombok.Data;
import org.dialectics.ai.common.domain.Dto;

import java.io.Serial;

@Data
public class UserLoginDto implements Dto {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 手机号
     */
    private String phone;
    /**
     * 验证码
     */
    private String code;

}
