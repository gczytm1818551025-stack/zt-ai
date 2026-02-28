package org.dialectics.ai.common.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

public class JwtUtils {
    /**
     * 生成jwt-token, 私匙使用固定秘钥
     *
     * @param secretKey 私钥
     * @param ttlMillis jwt过期时间(ms)
     * @param claims    荷载参数
     * @return token
     */
    public static String createToken(String secretKey, Map<String, Object> claims, long ttlMillis) {
        return Jwts.builder()
                .signWith(SignatureAlgorithm.HS256, secretKey.getBytes(StandardCharsets.UTF_8)) // 设置签名算法和秘钥
                .setClaims(claims) // 有私有声明先设置自定义的私有声明，给builder的claim赋值，覆盖标准声明
                .setExpiration(new Date(System.currentTimeMillis() + ttlMillis))
                .compact();
    }

    /**
     * 解密jwt-token
     *
     * @param secretKey 私钥
     * @param token     加密后的token
     * @return 荷载参数
     */
    public static Claims parseToken(String secretKey, String token) {
        return Jwts.parser()
                .setSigningKey(secretKey.getBytes(StandardCharsets.UTF_8)) // 设置签名的秘钥
                .parseClaimsJws(token)
                .getBody(); // 获取荷载部分
    }

}
