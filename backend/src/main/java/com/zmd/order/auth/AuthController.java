package com.zmd.order.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zmd.order.common.Constants;
import com.zmd.order.common.R;
import com.zmd.order.entity.User;
import com.zmd.order.mapper.UserMapper;
import com.zmd.order.rate.RateLimit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @PostMapping("/auth/login")
    @RateLimit(key = "auth:login", maxCount = 5, windowSeconds = 60)
    public R<?> login(@RequestBody Map<String, String> params) {
        String username = params.get("username");
        String password = params.get("password");

        if (username == null || password == null) {
            return R.fail("用户名和密码不能为空");
        }

        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        // 密码校验：兼容 BCrypt 加密密码和明文密码（初始化数据为明文，登录后建议升级）
        if (user == null || !matchPassword(password, user.getPassword())) {
            return R.fail("用户名或密码错误");
        }

        // 生成 token
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());

        // token 存入 Redis（支持主动注销和验证），TTL 与 token 过期时间一致
        redisTemplate.opsForValue().set(
                Constants.TOKEN_PREFIX + token, user.getUsername(),
                jwtUtil.getExpireHours(), TimeUnit.HOURS);

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("userId", user.getId());
        result.put("username", user.getUsername());
        result.put("role", user.getRole());
        return R.ok(result);
    }

    @PostMapping("/auth/logout")
    public R<?> logout(@RequestHeader("Authorization") String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            redisTemplate.delete(Constants.TOKEN_PREFIX + token);
        }
        return R.ok();
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
