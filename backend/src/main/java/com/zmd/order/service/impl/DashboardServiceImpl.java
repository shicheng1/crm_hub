package com.zmd.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zmd.order.common.Constants;
import com.zmd.order.config.CacheMetrics;
import com.zmd.order.dto.StatsSummary;
import com.zmd.order.entity.WorkOrder;
import com.zmd.order.mapper.OrderMapper;
import com.zmd.order.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 数据看板服务
 *
 * 技术亮点：Redis 缓存统计结果，5 分钟过期
 * 性能：统计聚合已从「22 次独立 COUNT」合并为「状态分布 1 条 + 创建趋势 1 条 + 通过趋势 1 条」（P0-1）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final OrderMapper orderMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final CacheMetrics cacheMetrics;

    private static final String STATS_CACHE_KEY = "dashboard:stats";
    private static final String TREND_CACHE_KEY = "dashboard:trend";
    private static final long CACHE_EXPIRE_MINUTES = 5;
    private static final int TREND_DAYS = 7;
    private static final DateTimeFormatter MM_DD = DateTimeFormatter.ofPattern("MM-dd");

    @Override
    public Map<String, Object> getStats() {
        String cached = redisTemplate.opsForValue().get(STATS_CACHE_KEY);
        if (cached != null) {
            log.debug("看板统计命中缓存");
            return parseMap(cached);
        }

        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        StatsSummary s = orderMapper.selectStatsSummary(todayStart); // 单条 SQL 聚合

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", s.getTotal());
        stats.put("pending", s.getPending());
        stats.put("reviewing", s.getReviewing());
        stats.put("approved", s.getApproved());
        stats.put("rejected", s.getRejected());
        stats.put("closed", s.getClosed());
        stats.put("returned", s.getReturned());
        stats.put("todayNew", s.getTodayNew());
        stats.put("todayApproved", s.getTodayApproved());

        redisTemplate.opsForValue().set(STATS_CACHE_KEY, toJson(stats),
                CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
        return stats;
    }

    @Override
    public Map<String, Object> getTrend() {
        return getTrend(TREND_DAYS);
    }

    public Map<String, Object> getTrend(int days) {
        String cached = redisTemplate.opsForValue().get(TREND_CACHE_KEY);
        if (cached != null) {
            return parseMap(cached);
        }

        LocalDate today = LocalDate.now();
        LocalDateTime start = LocalDateTime.of(today.minusDays(days - 1), LocalTime.MIN);

        List<String> dates = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            dates.add(today.minusDays(i).format(MM_DD));
        }

        Map<String, Long> createMap = toCountMap(orderMapper.selectCreateTrend(start));
        Map<String, Long> approveMap = toCountMap(orderMapper.selectApproveTrend(start));

        List<Long> newCounts = dates.stream()
                .map(d -> createMap.getOrDefault(d, 0L)).collect(Collectors.toList());
        List<Long> approveCounts = dates.stream()
                .map(d -> approveMap.getOrDefault(d, 0L)).collect(Collectors.toList());

        Map<String, Object> trend = new LinkedHashMap<>();
        trend.put("dates", dates);
        trend.put("newCounts", newCounts);
        trend.put("approveCounts", approveCounts);

        redisTemplate.opsForValue().set(TREND_CACHE_KEY, toJson(trend),
                CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
        return trend;
    }

    @Override
    public void evictStatsCache() {
        redisTemplate.delete(STATS_CACHE_KEY);
        redisTemplate.delete(TREND_CACHE_KEY);
        log.debug("看板缓存已清除");
    }

    @Override
    public Map<String, Object> getCacheStats() {
        return cacheMetrics.getStats();
    }

    /** 把趋势行 [{d:'MM-dd', c:count}] 转成日期→数量映射（缺日期由调用方补零） */
    private Map<String, Long> toCountMap(List<Map<String, Object>> rows) {
        Map<String, Long> map = new LinkedHashMap<>();
        if (rows == null) return map;
        for (Map<String, Object> r : rows) {
            String d = String.valueOf(r.get("d"));
            Object c = r.get("c");
            long cnt = c instanceof Number ? ((Number) c).longValue() : 0L;
            map.put(d, cnt);
        }
        return map;
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (Exception e) { return "{}"; }
    }

    private Map<String, Object> parseMap(String json) {
        try { return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {}); }
        catch (Exception e) { return new LinkedHashMap<>(); }
    }
}
