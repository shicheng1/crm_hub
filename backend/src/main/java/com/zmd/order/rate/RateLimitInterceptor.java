package com.zmd.order.rate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zmd.order.auth.LoginUser;
import com.zmd.order.common.Constants;
import com.zmd.order.common.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collections;

/**
 * 限流拦截器（Redis + Lua 滑动窗口）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private DefaultRedisScript<Long> rateLimitScript;

    @PostConstruct
    public void init() {
        rateLimitScript = new DefaultRedisScript<>();
        rateLimitScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/rate_limit.lua")));
        rateLimitScript.setResultType(Long.class);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;
        RateLimit rateLimit = handlerMethod.getMethodAnnotation(RateLimit.class);
        if (rateLimit == null) {
            return true;
        }

        // 限流 key = 前缀 + 用户ID（已登录）或 IP（未登录）
        LoginUser loginUser = LoginUser.get();
        String identity;
        if (loginUser != null) {
            identity = "u" + loginUser.getUserId();
        } else {
            identity = "ip" + getClientIp(request);
        }
        String key = Constants.RATE_LIMIT_PREFIX + rateLimit.key() + ":" + identity;

        long windowMs = rateLimit.windowSeconds() * 1000L;
        long now = System.currentTimeMillis();

        Long result = redisTemplate.execute(
                rateLimitScript,
                Collections.singletonList(key),
                String.valueOf(windowMs),
                String.valueOf(rateLimit.maxCount()),
                String.valueOf(now));

        if (result == null || result == 0) {
            log.warn("接口限流: key={}, identity={}, maxCount={}/{}s",
                    rateLimit.key(), identity, rateLimit.maxCount(), rateLimit.windowSeconds());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(R.fail(429, "请求过于频繁，请稍后再试")));
            return false;
        }

        return true;
    }

    /** 获取客户端真实 IP（穿透代理） */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多级代理取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "unknown";
    }
}
