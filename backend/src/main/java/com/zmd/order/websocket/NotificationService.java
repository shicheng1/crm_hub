package com.zmd.order.websocket;

import com.zmd.order.mq.ApprovalMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket 通知服务
 *
 * 通过 STOMP 向指定用户的频道推送实时通知
 * 目标地址：/topic/notifications/{userId}
 * 前端按 userId 订阅自己的频道
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 向工单创建人推送审批结果通知
     */
    public void notifyCreator(ApprovalMessage message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "APPROVAL_RESULT");
        payload.put("orderId", message.getOrderId());
        payload.put("orderTitle", message.getOrderTitle());
        payload.put("result", message.getResult());
        payload.put("approverName", message.getApproverName());
        payload.put("remark", message.getRemark());
        payload.put("approveTime", message.getApproveTime() != null ? message.getApproveTime().toString() : "");

        // 发送到 /topic/notifications/{userId}，前端按 userId 订阅
        String destination = "/topic/notifications/" + message.getCreatorId();
        messagingTemplate.convertAndSend(destination, payload);

        log.info("WebSocket 通知已推送到 {}, userId={}, orderId={}, result={}",
                destination, message.getCreatorId(), message.getOrderId(), message.getResult());
    }
}
