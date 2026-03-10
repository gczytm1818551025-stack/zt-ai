package org.dialectics.ai.server.controller;

import org.dialectics.ai.common.domain.R;
import org.dialectics.ai.server.domain.dto.UserLoginDto;
import org.dialectics.ai.server.domain.vo.UserLoginVo;
import org.dialectics.ai.server.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/public/user")
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/code")
    public R<String> sendCode(@RequestBody UserLoginDto userLoginDto) {
        // TODO 测试时不实际发送，因为短信认证服务需要运营资质，输入任意码均视为验证成功
//        userService.sendCode(userLoginDto.getPhone());
        return R.ok("验证码发送成功");
    }

    @PostMapping("/login")
    public R<UserLoginVo> login(@RequestBody UserLoginDto userLoginDto) {
        UserLoginVo userLoginVo = userService.login(userLoginDto);
        return R.ok(userLoginVo);
    }
}
