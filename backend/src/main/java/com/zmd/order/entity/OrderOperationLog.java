package com.zmd.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("order_operation_log")
public class OrderOperationLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long operatorId;
    private String operatorName;
    /** CREATE / SUBMIT / APPROVE / REJECT / CLOSE */
    private String operation;
    private String detail;
    private LocalDateTime operateTime;
}
