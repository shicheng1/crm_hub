package com.zmd.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("work_order")
public class WorkOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String content;
    /** 状态: 0待审批 1审批中 2已通过 3已驳回 4已关闭 5退回修改 */
    private Integer status;
    private Long creatorId;
    private Long approverId;
    private Long flowId;
    /** 当前审批步骤序号（0表示未开始审批） */
    private Integer currentStep;
    /** 发起人提交时的步骤（驳回ORIGIN模式时回到此步） */
    private Integer submitStep;
    private Long deptId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime approveTime;

    @TableField(exist = false)
    private List<ApprovalFlowStep> flowSteps;
    @TableField(exist = false)
    private List<ApprovalRecord> records;
    @TableField(exist = false)
    private String creatorName;
    @TableField(exist = false)
    private String flowName;
    @TableField(exist = false)
    private String rejectMode;
    /** 是否已填充展示信息（审批记录/快照/创建人名）。命中缓存时为真，避免 re-enrich（P0-2/P1-1） */
    @TableField(exist = false)
    private boolean enriched;
}
