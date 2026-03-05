package org.dialectics.ai.server.interceptor;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.dialectics.ai.common.constants.JwtClaimsConstant;
import org.dialectics.ai.common.domain.R;
import org.dialectics.ai.common.threadlocal.UserContext;
import org.dialectics.ai.common.utils.JwtUtils;
import org.dialectics.ai.server.config.properties.JwtProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@Slf4j
public class UserAuthInterceptor implements HandlerInterceptor {
    @Autowired
    private JwtProperties jwtProperties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return false;
        }
        String token = request.getHeader(jwtProperties.getTokenHeader());
        Long userId = verifyTokenAndGet(token);
        if (userId == null) {
            writeUnauthorizedResponse(response, "登录已过期，请重新登录");
            return false;
        }

        UserContext.set(userId);
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
                           Object handler, ModelAndView modelAndView) throws Exception {
        UserContext.remove();
    }

    private void writeUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        R<Void> result = R.fail(401, message);
        response.getWriter().write(JSON.toJSONString(result));
    }

    private Long verifyTokenAndGet(String token) {
        try {
            if (StrUtil.isNotEmpty(token)) {
                if (token.startsWith("Bearer ")) {
                    token = token.replace("Bearer ", "");
                }
            }
            Claims claims = JwtUtils.parseToken(jwtProperties.getSecretKey(), token);
            Long userId = Long.valueOf(String.valueOf(claims.get(JwtClaimsConstant.USER_ID)));
            return userId;
        } catch (Exception e) {
            log.warn("Token verification failed: {}", e.getMessage());
            return null;
        }
    }
}
