package com.zmd.order.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zmd.order.common.Constants;
import com.zmd.order.common.R;
import com.zmd.order.entity.User;
import com.zmd.order.mapper.UserMapper;
import com.zmd.order.rate.RateLimit;
import com.zmd.order.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;
    private final LoginAttemptService loginAttemptService;
    private final AuditLogService auditLogService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @PostMapping("/auth/login")
    @RateLimit(key = "auth:login", maxCount = 5, windowSeconds = 60)
    public R<?> login(@RequestBody Map<String, String> params, HttpServletRequest request) {
        String username = params.get("username");
        String password = params.get("password");

        if (username == null || password == null) {
            return R.fail("用户名和密码不能为空");
        }

        // 检查账号是否被锁定
        if (loginAttemptService.isLocked(username)) {
            return R.fail(423, "账号已被锁定，请" + Constants.LOGIN_LOCK_MINUTES + "分钟后再试");
        }

        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        // 密码校验：兼容 BCrypt 加密密码和明文密码（初始化数据为明文，登录后建议升级）
        if (user == null || !matchPassword(password, user.getPassword())) {
            loginAttemptService.recordFailedAttempt(username);
            int remaining = loginAttemptService.getRemainingAttempts(username);
            if (remaining > 0) {
                return R.fail("用户名或密码错误，剩余尝试次数：" + remaining);
            } else {
                return R.fail(423, "密码错误次数过多，账号已被锁定" + Constants.LOGIN_LOCK_MINUTES + "分钟");
            }
        }

        // 检查用户状态
        if (user.getStatus() != null && user.getStatus() == 0) {
            return R.fail("账号已被禁用，请联系管理员");
        }

        // 登录成功，清除失败计数
        loginAttemptService.clearAttempts(username);
        auditLogService.log(user.getId(), user.getUsername(), "LOGIN", "USER", user.getId(), "用户登录", getClientIp(request));

        // 生成 token
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getUsername(), user.getRole());

        // token 存入 Redis（支持主动注销和验证），TTL 与 token 过期时间一致
        redisTemplate.opsForValue().set(
                Constants.TOKEN_PREFIX + token, user.getUsername(),
                jwtUtil.getExpireHours(), TimeUnit.HOURS);
        redisTemplate.opsForValue().set(
                Constants.REFRESH_TOKEN_PREFIX + refreshToken, user.getUsername(),
                jwtUtil.getRefreshExpireDays(), TimeUnit.DAYS);

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("refreshToken", refreshToken);
        result.put("userId", user.getId());
        result.put("username", user.getUsername());
        result.put("role", user.getRole());
        return R.ok(result);
    }

    @PostMapping("/auth/refresh")
    public R<?> refresh(@RequestBody Map<String, String> params) {
        String refreshToken = params.get("refreshToken");
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            return R.fail(401, "refreshToken 不能为空");
        }
        if (!jwtUtil.isTokenValid(refreshToken)) {
            return R.fail(401, "refreshToken 已过期");
        }
        String username = redisTemplate.opsForValue().get(Constants.REFRESH_TOKEN_PREFIX + refreshToken);
        if (username == null) {
            return R.fail(401, "refreshToken 已失效");
        }
        io.jsonwebtoken.Claims claims = jwtUtil.parseToken(refreshToken);
        Long userId = claims.get("userId", Long.class);
        String role = claims.get("role", String.class);
        String newToken = jwtUtil.generateToken(userId, username, role);
        redisTemplate.opsForValue().set(
                Constants.TOKEN_PREFIX + newToken, username,
                jwtUtil.getExpireHours(), TimeUnit.HOURS);

        Map<String, Object> result = new HashMap<>();
        result.put("token", newToken);
        return R.ok(result);
    }

    @PostMapping("/auth/logout")
    public R<?> logout(@RequestHeader("Authorization") String authHeader, HttpServletRequest request) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                io.jsonwebtoken.Claims claims = jwtUtil.parseToken(token);
                auditLogService.log(
                        claims.get("userId", Long.class),
                        claims.get("username", String.class),
                        "LOGOUT", "USER", claims.get("userId", Long.class),
                        "用户登出", getClientIp(request));
            } catch (Exception ignored) {
                // token 无效时仍允许清理 Redis 中的 token
            }
            redisTemplate.delete(Constants.TOKEN_PREFIX + token);
        }
        return R.ok();
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "unknown";
    }

    /**
     * 密码校验：兼容 BCrypt 加密密码和明文密码
     * （初始化数据为明文，新增用户建议用 BCrypt 加密）
     */
    private boolean matchPassword(String rawPassword, String storedPassword) {
        if (storedPassword == null) return false;
        if (storedPassword.startsWith("$2a$") || storedPassword.startsWith("$2b$")) {
            return passwordEncoder.matches(rawPassword, storedPassword);
        }
        return storedPassword.equals(rawPassword);
    }
}
