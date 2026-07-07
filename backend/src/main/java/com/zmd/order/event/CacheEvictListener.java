package com.zmd.order.event;

import com.zmd.order.cache.OrderCacheService;
import com.zmd.order.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 审批完成后的缓存失效监听器。
 *
 * <p>通过监听 {@link OrderApprovalEvent} 解耦「工单服务」与「看板缓存」：
 * OrderServiceImpl 不再直接依赖 DashboardService（消除 A3 反向依赖），
 * 缓存失效统一在此处、且于事务提交后执行，保证读到已提交数据。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheEvictListener {

    private final OrderCacheService orderCacheService;
    private final DashboardService dashboardService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderApproved(OrderApprovalEvent event) {
        orderCacheService.evictOrder(event.getOrderId());
        dashboardService.evictStatsCache();
        log.debug("审批后缓存失效, orderId={}", event.getOrderId());
    }
}
