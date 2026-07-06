package com.zmd.order.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zmd.order.common.Constants;
import com.zmd.order.entity.WorkOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 工单缓存管理
 *
 * 使用 Redis String + JSON 序列化缓存工单详情
 * 策略：先更新数据库，再删除缓存（Cache Aside Pattern）
 * 防穿透：缓存空对象（短 TTL）防止恶意查询不存在的工单
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final long CACHE_EXPIRE_HOURS = 2;
    /** 空值缓存时间（秒），防止缓存穿透 */
    private static final long NULL_CACHE_SECONDS = 60;

    /**
     * 缓存工单详情
     */
    public void cacheOrder(WorkOrder order) {
        String key = Constants.CACHE_ORDER_DETAIL + order.getId();
        try {
            String json = objectMapper.writeValueAsString(order);
            redisTemplate.opsForValue().set(key, json, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
            log.debug("缓存工单详情, orderId={}", order.getId());
        } catch (JsonProcessingException e) {
            log.error("缓存工单序列化失败, orderId={}", order.getId(), e);
        }
    }

    /** 空值缓存标记 */
    private static final String NULL_MARKER = "NULL";

    /**
     * 缓存空值（防止缓存穿透）
     */
    public void cacheNull(Long orderId) {
        String key = Constants.CACHE_ORDER_DETAIL + orderId;
        redisTemplate.opsForValue().set(key, NULL_MARKER, NULL_CACHE_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 判断是否命中空值缓存
     */
    public boolean isNullCached(Long orderId) {
        String key = Constants.CACHE_ORDER_DETAIL + orderId;
        String json = redisTemplate.opsForValue().get(key);
        return NULL_MARKER.equals(json);
    }

    /**
     * 从缓存获取工单（返回 null 表示缓存未命中或空值缓存）
     */
    public WorkOrder getCachedOrder(Long orderId) {
        String key = Constants.CACHE_ORDER_DETAIL + orderId;
        String json = redisTemplate.opsForValue().get(key);
        if (json == null || NULL_MARKER.equals(json)) {
            return null; // 缓存未命中或空值缓存
        }
        try {
            WorkOrder order = objectMapper.readValue(json, WorkOrder.class);
            log.debug("缓存命中, orderId={}", orderId);
            return order;
        } catch (JsonProcessingException e) {
            log.error("缓存反序列化失败, orderId={}", orderId, e);
            redisTemplate.delete(key);
            return null;
        }
    }

    /**
     * 删除缓存（工单更新后调用）
     */
    public void evictOrder(Long orderId) {
        String key = Constants.CACHE_ORDER_DETAIL + orderId;
        redisTemplate.delete(key);
        log.debug("删除工单缓存, orderId={}", orderId);
    }
}
