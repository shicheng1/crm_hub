package com.zmd.order.dto;

import lombok.Data;

/**
 * 看板统计汇总（单条 SQL 聚合产出）。
 * 状态枚举值对应 {@link com.zmd.order.common.Constants}：
 * 0 待审批 / 1 审批中 / 2 已通过 / 3 已驳回 / 4 已关闭 / 5 退回修改。
 */
@Data
public class StatsSummary {
    private Long total;
    private Long pending;
    private Long reviewing;
    private Long approved;
    private Long rejected;
    private Long closed;
    private Long returned;
    private Long todayNew;
    private Long todayApproved;
}
