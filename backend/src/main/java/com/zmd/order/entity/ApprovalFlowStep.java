package com.zmd.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("approval_flow_step")
public class ApprovalFlowStep {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long flowId;
    private Integer stepOrder;
    private String stepName;
    /** @deprecated 已迁移到 approval_step_approver 表，保留兼容 */
    private Long approverId;
    private LocalDateTime createTime;

    /** 本步骤的审批人列表（非数据库字段） */
    @TableField(exist = false)
    private List<ApprovalStepApprover> approvers;
}
