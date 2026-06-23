package com.zmd.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 步骤-审批人关联（一个步骤可以配多个审批人，任何一人审批即可推进）
 */
@Data
@TableName("approval_step_approver")
public class ApprovalStepApprover {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long stepId;
    private Long userId;
    private LocalDateTime createTime;
}
