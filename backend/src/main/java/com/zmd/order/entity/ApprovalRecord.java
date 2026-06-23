package com.zmd.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("approval_record")
public class ApprovalRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long stepId;
    private Integer stepOrder;
    private Long approverId;
    /** APPROVED / REJECTED */
    private String result;
    private String remark;
    private LocalDateTime operateTime;
}
