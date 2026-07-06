package com.zmd.order.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zmd.order.common.Constants;
import com.zmd.order.common.R;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;

/**
 * JWT 认证拦截器
 *
 * 流程：
 * 1. 从 Header 取 token
 * 2. 验证 token 有效性
 * 3. 检查 Redis 中 token 是否存在（支持主动注销）
 * 4. 将用户信息放入 ThreadLocal
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // OPTIONS 请求放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = request.getHeader("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            writeError(response, 401, "未登录");
            return false;
        }
        token = token.substring(7);

        // 验证 token
        if (!jwtUtil.isTokenValid(token)) {
            writeError(response, 401, "token 已过期");
            return false;
        }

        // 检查 Redis 中是否存在（防止已注销的 token 被使用）
        String redisKey = Constants.TOKEN_PREFIX + token;
        String exists = redisTemplate.opsForValue().get(redisKey);
        if (exists == null) {
            writeError(response, 401, "token 已失效");
            return false;
        }

        // 解析用户信息放入 ThreadLocal
        Claims claims = jwtUtil.parseToken(token);
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(claims.get("userId", Long.class));
        loginUser.setUsername(claims.get("username", String.class));
        loginUser.setRole(claims.get("role", String.class));
        LoginUser.set(loginUser);

        // 接口级权限检查
        if (handler instanceof HandlerMethod) {
            HandlerMethod handlerMethod = (HandlerMethod) handler;
            RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);
            if (requireRole == null) {
                requireRole = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
            }
            if (requireRole != null) {
                String userRole = loginUser.getRole();
                boolean hasRole = Arrays.asList(requireRole.value()).contains(userRole);
                if (!hasRole) {
                    writeError(response, 403, "权限不足");
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        LoginUser.clear();
    }

    private void writeError(HttpServletResponse response, int code, String msg) throws Exception {
        response.setContentType("application/json;charset=UTF-8");
        // 认证失败 401，权限不足 403，其他按实际 code 设置
        if (code == 401) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        } else if (code == 403) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        } else {
            response.setStatus(HttpServletResponse.SC_OK);
        }
        response.getWriter().write(objectMapper.writeValueAsString(R.fail(code, msg)));
    }
}
