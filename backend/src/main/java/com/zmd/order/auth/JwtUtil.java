package com.zmd.order.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具类
 *
 * 密钥和过期时间从配置文件读取，避免硬编码
 */
@Slf4j
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expireHours;

    public JwtUtil(
            @Value("${jwt.secret:zmd-order-approval-secret-key-2024-default}") String secret,
            @Value("${jwt.expire-hours:24}") long expireHours) {
        // 密钥长度不足 32 字节时补齐（HS256 要求至少 256 bit）
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            keyBytes = new byte[32];
            System.arraycopy(secret.getBytes(StandardCharsets.UTF_8), 0, keyBytes, 0, Math.min(secret.length(), 32));
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.expireHours = expireHours;
    }

    public String generateToken(Long userId, String username, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("role", role);

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expireHours * 3600 * 1000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public boolean isTokenValid(String token) {
        try {
            Claims claims = parseToken(token);
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    /** token 过期时间（小时），供外部同步 Redis TTL 使用 */
    public long getExpireHours() {
        return expireHours;
    }
}
