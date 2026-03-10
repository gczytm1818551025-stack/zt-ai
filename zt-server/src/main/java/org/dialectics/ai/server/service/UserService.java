package org.dialectics.ai.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.dialectics.ai.server.domain.dto.UserLoginDto;
import org.dialectics.ai.server.domain.pojo.User;
import org.dialectics.ai.server.domain.vo.UserLoginVo;

public interface UserService extends IService<User> {

    /**
     * 发送短信验证码
     * @param phone 手机号
     */
    void sendCode(String phone);

    /**
     * 用户登录
     * @param userLoginDto 登录参数
     * @return 登录结果
     */
    UserLoginVo login(UserLoginDto userLoginDto);
}
