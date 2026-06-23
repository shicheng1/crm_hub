package com.zmd.order.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zmd.order.common.Constants;
import com.zmd.order.common.R;
import com.zmd.order.entity.User;
import com.zmd.order.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
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

    @PostMapping("/auth/login")
    public R<?> login(@RequestBody Map<String, String> params) {
        String username = params.get("username");
        String password = params.get("password");

        if (username == null || password == null) {
            return R.fail("用户名和密码不能为空");
        }

        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null || !user.getPassword().equals(password)) {
            return R.fail("用户名或密码错误");
        }

        // 生成 token
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());

        // token 存入 Redis（支持主动注销和验证）
        redisTemplate.opsForValue().set(
                Constants.TOKEN_PREFIX + token, user.getUsername(),
                Constants.JWT_EXPIRE_HOURS, TimeUnit.HOURS);

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
}
