package com.zmd.order.cache;

import com.zmd.order.common.Constants;
import com.zmd.order.entity.WorkOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 工单缓存管理
 *
 * 使用 Redis Hash 结构缓存工单详情（和 CRM 项目中缓存客户类型映射一样的模式）
 * 更新时采用「先更新数据库，再删缓存」策略
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCacheService {

    private final StringRedisTemplate redisTemplate;

    private static final long CACHE_EXPIRE_HOURS = 2;

    /**
     * 缓存工单详情（Hash 结构）
     */
    public void cacheOrder(WorkOrder order) {
        String key = Constants.CACHE_ORDER_DETAIL + order.getId();
        HashOperations<String, String, String> hash = redisTemplate.opsForHash();

        hash.put(key, "id", String.valueOf(order.getId()));
        hash.put(key, "title", order.getTitle());
        hash.put(key, "content", order.getContent() != null ? order.getContent() : "");
        hash.put(key, "status", String.valueOf(order.getStatus()));
        hash.put(key, "creatorId", String.valueOf(order.getCreatorId()));
        hash.put(key, "approverId", order.getApproverId() != null ? String.valueOf(order.getApproverId()) : "");
        hash.put(key, "createTime", order.getCreateTime() != null ? order.getCreateTime().toString() : "");

        redisTemplate.expire(key, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
        log.debug("缓存工单详情, orderId={}", order.getId());
    }

    /**
     * 从缓存获取工单（返回 null 表示缓存未命中）
     */
    public WorkOrder getCachedOrder(Long orderId) {
        String key = Constants.CACHE_ORDER_DETAIL + orderId;
        HashOperations<String, String, String> hash = redisTemplate.opsForHash();

        Map<String, String> entries = hash.entries(key);
        if (entries == null || entries.isEmpty()) {
            return null;
        }

        WorkOrder order = new WorkOrder();
        order.setId(Long.parseLong(entries.get("id")));
        order.setTitle(entries.get("title"));
        order.setContent(entries.get("content"));
        order.setStatus(Integer.parseInt(entries.get("status")));
        order.setCreatorId(Long.parseLong(entries.get("creatorId")));
        String approverId = entries.get("approverId");
        if (approverId != null && !approverId.isEmpty()) {
            order.setApproverId(Long.parseLong(approverId));
        }
        log.debug("缓存命中, orderId={}", orderId);
        return order;
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
