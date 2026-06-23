package com.zmd.order.mq;

import com.zmd.order.common.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 审批通知消息生产者（mq.enabled=true 时生效）
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "mq.enabled", havingValue = "true")
public class ApprovalProducer {

    private final RabbitTemplate rabbitTemplate;

    public void sendApprovalNotify(ApprovalMessage message) {
        String msgId = UUID.randomUUID().toString();
        CorrelationData correlationData = new CorrelationData(msgId);

        rabbitTemplate.convertAndSend(
                Constants.MQ_EXCHANGE,
                Constants.MQ_ROUTING_KEY_APPROVAL,
                message,
                correlationData);

        log.info("发送审批通知消息, msgId={}, orderId={}, result={}",
                msgId, message.getOrderId(), message.getResult());
    }
}
