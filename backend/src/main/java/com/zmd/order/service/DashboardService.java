package com.zmd.order.service;

import java.util.Map;

public interface DashboardService {
    /** 工单统计数据（带 Redis 缓存，5 分钟过期） */
    Map<String, Object> getStats();
    /** 近 7 天趋势数据 */
    Map<String, Object> getTrend();
    /** 清除看板缓存（工单状态变更时调用） */
    void evictStatsCache();
}
