package org.dialectics.ai.server.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.dypnsapi.model.v20170525.CheckSmsVerifyCodeRequest;
import com.aliyuncs.dypnsapi.model.v20170525.CheckSmsVerifyCodeResponse;
import com.aliyuncs.dypnsapi.model.v20170525.SendSmsVerifyCodeRequest;
import com.aliyuncs.dypnsapi.model.v20170525.SendSmsVerifyCodeResponse;
import com.aliyuncs.profile.DefaultProfile;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.dialectics.ai.common.constants.JwtClaimsConstant;
import org.dialectics.ai.common.utils.JwtUtils;
import org.dialectics.ai.common.utils.RedisRetryUtils;
import org.dialectics.ai.server.config.properties.AliyunProperties;
import org.dialectics.ai.server.config.properties.JwtProperties;
import org.dialectics.ai.server.domain.dto.UserLoginDto;
import org.dialectics.ai.server.domain.pojo.User;
import org.dialectics.ai.server.domain.vo.UserLoginVo;
import org.dialectics.ai.server.mapper.UserMapper;
import org.dialectics.ai.server.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private JwtProperties jwtProperties;
    @Autowired
    private AliyunProperties aliyunProperties;

    @Override
    public void sendCode(String phone) {
        // 1. 校验手机号
        if (StrUtil.isEmpty(phone)) {
            throw new RuntimeException("手机号不能为空");
        }

        // 2. 频率限制 (1分钟，带重试机制)
        String limitKey = "login:limit:" + phone;
        try {
            Boolean hasKey = RedisRetryUtils.safeHasKey(redisTemplate, limitKey);
            if (BooleanUtil.isTrue(hasKey)) {
                throw new RuntimeException("验证码发送过于频繁");
            }
        } catch (Exception e) {
            log.warn("检查Redis频率限制失败: phone={}, error={}", phone, e.getMessage());
            throw new RuntimeException("系统繁忙，请稍后重试");
        }

        // 3. 发送短信 (真实调用 Aliyun SendSmsVerifyCode)
        try {
            sendAliyunSms(phone);
        } catch (Exception e) {
            log.error("Send SMS failed", e);
            throw new RuntimeException("短信发送失败");
        }

        // 设置频率限制 Key (有效期1分钟，带重试机制)
        try {
            RedisRetryUtils.safeSet(redisTemplate, limitKey, "1", java.time.Duration.ofMinutes(1));
        } catch (Exception e) {
            log.error("设置Redis频率限制失败: phone={}, error={}", phone, e.getMessage());
        }
    }

    @Override
    public UserLoginVo login(UserLoginDto userLoginDto) {
        String phone = userLoginDto.getPhone();
        String code = userLoginDto.getCode();
        if (StrUtil.isEmpty(phone) || StrUtil.isEmpty(code)) {
            throw new RuntimeException("手机号或验证码不能为空");
        }

        // 1. 校验验证码
        // TODO 测试时不实际校验，因为短信认证服务需要运营资质
//        checkAliyunSms(phone, code);
        // 2. 获取或创建用户
        User user = this.lambdaQuery().eq(User::getPhone, phone).one();
        if (user == null) {
            // 默认昵称：LingHuUser_随机数(5位)
            int suffix = ThreadLocalRandom.current().nextInt(10000, 100000);
            user = User.builder()
                    .phone(phone)
                    .nickName("LingHuUser_" + suffix)
                    .build();
            this.save(user);
        }
        // 3. 生成Token
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, user.getId());
        long ttlMillis = jwtProperties.getTtl() * 60 * 60 * 1000L;
        String token = JwtUtils.createToken(jwtProperties.getSecretKey(), claims, ttlMillis);

        // 4. 返回VO
        return UserLoginVo.builder()
                .token(token)
                .nickName(user.getNickName())
                .build();
    }

    private void sendAliyunSms(String phone) throws Exception {
        DefaultProfile profile = DefaultProfile.getProfile("cn-hangzhou", aliyunProperties.getAccessKeyId(), aliyunProperties.getAccessKeySecret());
        IAcsClient client = new DefaultAcsClient(profile);

        SendSmsVerifyCodeRequest request = new SendSmsVerifyCodeRequest();
        request.setPhoneNumber(phone);
        request.setSignName(aliyunProperties.getSms().getSignName());
        request.setTemplateCode(aliyunProperties.getSms().getTemplateCode());
        request.setTemplateParam(aliyunProperties.getSms().getTemplateParam());

        SendSmsVerifyCodeResponse resp = client.getAcsResponse(request);
        if (!StrUtil.equals("OK", resp.getCode())) {
            log.error("Aliyun SendSmsVerifyCode Error: {} - {}", resp.getCode(), resp.getMessage());
            throw new RuntimeException("短信服务异常: " + resp.getMessage());
        }
    }

    private void checkAliyunSms(String phone, String code) {
        try {
            DefaultProfile profile = DefaultProfile.getProfile("cn-hangzhou",
                    aliyunProperties.getAccessKeyId(),
                    aliyunProperties.getAccessKeySecret());
            IAcsClient client = new DefaultAcsClient(profile);

            CheckSmsVerifyCodeRequest request = new CheckSmsVerifyCodeRequest();
            request.setPhoneNumber(phone);
            request.setVerifyCode(code);

            CheckSmsVerifyCodeResponse resp = client.getAcsResponse(request);
            if (!StrUtil.equals("OK", resp.getCode())) {
                log.error("Aliyun CheckSmsVerifyCode Error: {} - {}", resp.getCode(), resp.getMessage());
                throw new RuntimeException("验证码校验失败: " + resp.getMessage());
            }
        } catch (Exception e) {
            log.error("Check SMS Code failed", e);
            throw new RuntimeException("验证码校验异常");
        }
    }
}
