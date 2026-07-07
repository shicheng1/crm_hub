package com.zmd.order.event;

import com.zmd.order.mq.ApprovalMessage;
import com.zmd.order.mq.ApprovalProducer;
import com.zmd.order.websocket.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 审批完成后的通知监听器。
 *
 * <p>监听 {@link OrderApprovalEvent} 发送 WebSocket 实时通知与 RabbitMQ 异步消息。
 * 原逻辑在分布式锁内同步调用（P0-3 风险），现改为事务提交后 {@code @Async} 异步执行，
 * 锁内不再做任何慢操作，彻底消除并发双审与锁过期风险。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationListener {

    private final NotificationService notificationService;
    private final ObjectProvider<ApprovalProducer> approvalProducerProvider;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderApproved(OrderApprovalEvent event) {
        ApprovalMessage message = new ApprovalMessage(
                event.getOrderId(), event.getTitle(),
                event.getCreatorId(), event.getCreatorName(),
                event.getOperatorId(), event.getOperatorName(),
                event.isApproved() ? "APPROVED" : "REJECTED", event.getRemark(), event.getApproveTime());

        ApprovalProducer producer = approvalProducerProvider.getIfAvailable();
        if (producer != null) {
            producer.sendApprovalNotify(message);
        }
        notificationService.notifyCreator(message);
    }
}
