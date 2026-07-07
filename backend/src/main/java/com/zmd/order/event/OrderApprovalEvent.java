package com.zmd.order.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

/**
 * 审批完成领域事件。
 *
 * <p>审批事务提交后由 {@code OrderServiceImpl} 发布，监听器（缓存失效 / 通知）在
 * 事务提交后异步消费，从而把「缓存失效」与「通知」从工单服务与分布式锁中解耦。
 */
@Getter
public class OrderApprovalEvent extends ApplicationEvent {

    private final Long orderId;
    private final String title;
    private final Long creatorId;
    private final String creatorName;
    private final Long operatorId;
    private final String operatorName;
    private final boolean approved;
    private final String remark;
    private final LocalDateTime approveTime;

    public OrderApprovalEvent(Object source, Long orderId, String title, Long creatorId,
                              String creatorName, Long operatorId, String operatorName,
                              boolean approved, String remark, LocalDateTime approveTime) {
        super(source);
        this.orderId = orderId;
        this.title = title;
        this.creatorId = creatorId;
        this.creatorName = creatorName;
        this.operatorId = operatorId;
        this.operatorName = operatorName;
        this.approved = approved;
        this.remark = remark;
        this.approveTime = approveTime;
    }
}
