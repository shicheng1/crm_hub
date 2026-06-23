package com.zmd.order.mq;

import com.rabbitmq.client.Channel;
import com.zmd.order.common.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 审批通知消息消费者（mq.enabled=true 时生效）
 *
 * 和 CRM 项目中 BaseMessageListener 消费模式类似：
 * 1. 手动 ACK —— 业务处理成功后才确认消息
 * 2. 消费失败 nack —— 不重新入队（防止无限重试）
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "mq.enabled", havingValue = "true")
public class ApprovalConsumer {

    @RabbitListener(queues = Constants.MQ_QUEUE_APPROVAL)
    public void handleApprovalNotify(ApprovalMessage message, Channel channel, Message msg) {
        long deliveryTag = msg.getMessageProperties().getDeliveryTag();
        try {
            log.info("收到审批通知: orderId={}, result={}, creator={}, approver={}",
                    message.getOrderId(), message.getResult(),
                    message.getCreatorName(), message.getApproverName());

            processApprovalNotify(message);

            channel.basicAck(deliveryTag, false);
            log.info("审批通知消费成功, orderId={}", message.getOrderId());

        } catch (Exception e) {
            log.error("审批通知消费失败, orderId={}, error={}", message.getOrderId(), e.getMessage(), e);
            try {
                channel.basicNack(deliveryTag, false, false);
            } catch (IOException ex) {
                log.error("nack 失败", ex);
            }
        }
    }

    private void processApprovalNotify(ApprovalMessage message) {
        if ("APPROVED".equals(message.getResult())) {
            log.info(">>> 通知创建人 [{}]: 您的工单 [{}] 已通过审批",
                    message.getCreatorName(), message.getOrderTitle());
        } else {
            log.info(">>> 通知创建人 [{}]: 您的工单 [{}] 已被驳回，原因: {}",
                    message.getCreatorName(), message.getOrderTitle(), message.getRemark());
        }
    }
}
