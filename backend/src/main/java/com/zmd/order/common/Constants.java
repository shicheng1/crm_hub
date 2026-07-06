package com.zmd.order.common;

/**
 * 常量定义：Redis key 前缀、MQ 常量等
 */
public interface Constants {

    // ==================== Redis Key 前缀 ====================
    /** 分布式锁 - 工单创建 */
    String LOCK_ORDER_CREATE = "order:lock:create:";
    /** 分布式锁 - 工单审批 */
    String LOCK_ORDER_APPROVE = "order:lock:approve:";
    /** 工单详情缓存 */
    String CACHE_ORDER_DETAIL = "order:cache:detail:";
    /** JWT token */
    String TOKEN_PREFIX = "order:token:";
    /** 限流计数器 */
    String RATE_LIMIT_PREFIX = "order:rate:";

    // ==================== MQ 常量 ====================
    String MQ_EXCHANGE = "order.exchange";
    String MQ_QUEUE_APPROVAL = "order.approval.queue";
    String MQ_ROUTING_KEY_APPROVAL = "order.approval.notify";

    // ==================== 业务常量 ====================
    /** 工单状态 */
    int STATUS_PENDING = 0;    // 待审批
    int STATUS_REVIEWING = 1;  // 审批中
    int STATUS_APPROVED = 2;   // 已通过
    int STATUS_REJECTED = 3;   // 已驳回（终止）
    int STATUS_CLOSED = 4;     // 已关闭
    int STATUS_RETURNED = 5;   // 退回修改（可重新提交）

    /** 超时自动关闭时间（小时） */
    int AUTO_CLOSE_HOURS = 24;
}
