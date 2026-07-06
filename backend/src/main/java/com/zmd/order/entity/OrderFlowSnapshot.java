package com.zmd.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("order_flow_snapshot")
public class OrderFlowSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long flowId;
    private String snapshotJson;
    private LocalDateTime createTime;
}
