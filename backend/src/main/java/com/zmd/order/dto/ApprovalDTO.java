package com.zmd.order.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class ApprovalDTO {
    @NotNull(message = "工单ID不能为空")
    private Long orderId;

    /** true=通过, false=驳回 */
    @NotNull(message = "审批结果不能为空")
    private Boolean approved;

    private String remark;
}
