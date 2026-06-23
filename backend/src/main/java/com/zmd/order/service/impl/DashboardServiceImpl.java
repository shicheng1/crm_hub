package com.zmd.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zmd.order.common.Constants;
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

/**
 * 数据看板服务
 *
 * 技术亮点：Redis 缓存统计结果，5 分钟过期
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final OrderMapper orderMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String STATS_CACHE_KEY = "dashboard:stats";
    private static final String TREND_CACHE_KEY = "dashboard:trend";
    private static final long CACHE_EXPIRE_MINUTES = 5;

    @Override
    public Map<String, Object> getStats() {
        String cached = redisTemplate.opsForValue().get(STATS_CACHE_KEY);
        if (cached != null) {
            log.debug("看板统计命中缓存");
            return parseMap(cached);
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", orderMapper.selectCount(null));
        stats.put("pending", countByStatus(Constants.STATUS_PENDING));
        stats.put("reviewing", countByStatus(Constants.STATUS_REVIEWING));
        stats.put("approved", countByStatus(Constants.STATUS_APPROVED));
        stats.put("rejected", countByStatus(Constants.STATUS_REJECTED));
        stats.put("closed", countByStatus(Constants.STATUS_CLOSED));
        stats.put("returned", countByStatus(Constants.STATUS_RETURNED));
        stats.put("todayNew", countTodayNew());
        stats.put("todayApproved", countTodayApproved());

        redisTemplate.opsForValue().set(STATS_CACHE_KEY, toJson(stats),
                CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
        return stats;
    }

    @Override
    public Map<String, Object> getTrend() {
        String cached = redisTemplate.opsForValue().get(TREND_CACHE_KEY);
        if (cached != null) {
            return parseMap(cached);
        }

        List<String> dates = new ArrayList<>();
        List<Long> newCounts = new ArrayList<>();
        List<Long> approveCounts = new ArrayList<>();

        for (int i = 6; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            dates.add(date.format(DateTimeFormatter.ofPattern("MM-dd")));
            LocalDateTime dayStart = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime dayEnd = LocalDateTime.of(date, LocalTime.MAX);

            newCounts.add(orderMapper.selectCount(
                    new LambdaQueryWrapper<WorkOrder>()
                            .between(WorkOrder::getCreateTime, dayStart, dayEnd)));
            approveCounts.add(orderMapper.selectCount(
                    new LambdaQueryWrapper<WorkOrder>()
                            .eq(WorkOrder::getStatus, Constants.STATUS_APPROVED)
                            .between(WorkOrder::getApproveTime, dayStart, dayEnd)));
        }

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

    private long countByStatus(int status) {
        return orderMapper.selectCount(
                new LambdaQueryWrapper<WorkOrder>().eq(WorkOrder::getStatus, status));
    }

    private long countTodayNew() {
        return orderMapper.selectCount(
                new LambdaQueryWrapper<WorkOrder>()
                        .ge(WorkOrder::getCreateTime, LocalDateTime.of(LocalDate.now(), LocalTime.MIN)));
    }

    private long countTodayApproved() {
        return orderMapper.selectCount(
                new LambdaQueryWrapper<WorkOrder>()
                        .eq(WorkOrder::getStatus, Constants.STATUS_APPROVED)
                        .ge(WorkOrder::getApproveTime, LocalDateTime.of(LocalDate.now(), LocalTime.MIN)));
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
