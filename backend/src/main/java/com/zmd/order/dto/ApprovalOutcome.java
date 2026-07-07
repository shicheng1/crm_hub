package com.zmd.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审批引擎产出结果：只描述「目标状态 / 步骤 / 是否终态」，
 * 不含任何数据库写入、缓存失效或通知发送。由 OrderServiceImpl 负责落库与发事件。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalOutcome {

    /** 目标工单状态，对应 Constants.STATUS_* */
    private int targetStatus;

    /** 目标步骤；null 表示保持当前步骤不变 */
    private Integer targetStep;

    /** 是否审批完成（进入终态 STATUS_APPROVED） */
    private boolean completed;

    /** 操作日志类型：APPROVE / REJECT */
    private String logOp;

    /** 操作日志详情 */
    private String logDetail;

    /** 是否回填 approverId / approveTime（完成态与驳回态为 true） */
    private boolean markApprover;
}
