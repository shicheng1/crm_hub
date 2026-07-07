package com.zmd.order.config;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 缓存命中率统计。
 *
 * 由 {@code OrderCacheService} 在每次缓存读取时累加 hit / miss，
 * 通过 {@code DashboardController} 的 {@code /api/dashboard/cache-stats} 暴露。
 * 计数器为进程内 AtomicLong，重启后归零（命中率本身是短期观测指标，无需持久化）。
 */
@Component
public class CacheMetrics {

    private final AtomicLong hit = new AtomicLong(0);
    private final AtomicLong miss = new AtomicLong(0);

    /** 缓存命中（含空值缓存，均避免回源数据库） */
    public void incrementHit() {
        hit.incrementAndGet();
    }

    /** 缓存未命中（无缓存条目，或缓存反序列化失败需回源） */
    public void incrementMiss() {
        miss.incrementAndGet();
    }

    public Map<String, Object> getStats() {
        long h = hit.get();
        long m = miss.get();
        long total = h + m;
        double rate = total == 0 ? 0.0 : (double) h / total;
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("hit", h);
        stats.put("miss", m);
        stats.put("total", total);
        stats.put("hitRate", Math.round(rate * 10000.0) / 10000.0);
        stats.put("hitRatePercent", Math.round(rate * 10000.0) / 100.0);
        return stats;
    }
}
