package com.zmd.order.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审批通知消息体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalMessage implements Serializable {
    private Long orderId;
    private String orderTitle;
    private Long creatorId;
    private String creatorName;
    private Long approverId;
    private String approverName;
    /** APPROVED / REJECTED */
    private String result;
    private String remark;
    private LocalDateTime approveTime;
}
