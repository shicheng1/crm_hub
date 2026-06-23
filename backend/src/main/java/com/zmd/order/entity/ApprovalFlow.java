package com.zmd.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("approval_flow")
public class ApprovalFlow {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    /** 1启用 0禁用 */
    private Integer status;
    /** 驳回策略: RESTART=回到第一步, PREVIOUS=回到上一步, ORIGIN=退回发起人重新提交 */
    private String rejectMode;
    private LocalDateTime createTime;

    @TableField(exist = false)
    private List<ApprovalFlowStep> steps;
}
