package com.zmd.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zmd.order.dto.StatsSummary;
import com.zmd.order.entity.WorkOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface OrderMapper extends BaseMapper<WorkOrder> {

    /**
     * 查询待办工单：当前用户是当前步骤审批人且尚未审批的工单
     *
     * 通过 JOIN 一次性查出，避免 N+1 查询：
     * - work_order w: 工单
     * - approval_flow_step s: 当前步骤
     * - approval_step_approver a: 步骤审批人
     * - LEFT JOIN approval_record r: 排除已审批的
     */
    @Select("SELECT w.* FROM work_order w " +
            "INNER JOIN approval_flow_step s ON s.flow_id = w.flow_id AND s.step_order = w.current_step " +
            "INNER JOIN approval_step_approver a ON a.step_id = s.id AND a.user_id = #{userId} " +
            "LEFT JOIN approval_record r ON r.order_id = w.id AND r.step_id = s.id AND r.approver_id = #{userId} " +
            "WHERE w.status IN (0, 1) AND r.id IS NULL " +
            "ORDER BY w.create_time DESC")
    IPage<WorkOrder> selectTodoPage(Page<WorkOrder> page, @Param("userId") Long userId);

    /**
     * 查询已办工单：当前用户审批过的工单（去重）
     */
    @Select("SELECT DISTINCT w.* FROM work_order w " +
            "INNER JOIN approval_record r ON r.order_id = w.id " +
            "WHERE r.approver_id = #{userId} " +
            "ORDER BY w.update_time DESC")
    IPage<WorkOrder> selectDonePage(Page<WorkOrder> page, @Param("userId") Long userId);

    /**
     * 看板状态分布 + 今日新增/通过：单条条件聚合 SQL，替代原 8 次独立 COUNT（P0-1）。
     * 状态枚举值见 {@link com.zmd.order.common.Constants}：0 待审批 / 1 审批中 / 2 已通过 / 3 已驳回 / 4 已关闭 / 5 退回修改。
     */
    @Select("SELECT CAST(COUNT(*) AS UNSIGNED) total, " +
            "CAST(SUM(CASE WHEN status = 0 THEN 1 ELSE 0 END) AS UNSIGNED) pending, " +
            "CAST(SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END) AS UNSIGNED) reviewing, " +
            "CAST(SUM(CASE WHEN status = 2 THEN 1 ELSE 0 END) AS UNSIGNED) approved, " +
            "CAST(SUM(CASE WHEN status = 3 THEN 1 ELSE 0 END) AS UNSIGNED) rejected, " +
            "CAST(SUM(CASE WHEN status = 4 THEN 1 ELSE 0 END) AS UNSIGNED) closed, " +
            "CAST(SUM(CASE WHEN status = 5 THEN 1 ELSE 0 END) AS UNSIGNED) returned, " +
            "CAST(SUM(CASE WHEN create_time >= #{todayStart} THEN 1 ELSE 0 END) AS UNSIGNED) todayNew, " +
            "CAST(SUM(CASE WHEN status = 2 AND approve_time >= #{todayStart} THEN 1 ELSE 0 END) AS UNSIGNED) todayApproved " +
            "FROM work_order")
    StatsSummary selectStatsSummary(@Param("todayStart") LocalDateTime todayStart);

    /**
     * 近 N 天创建趋势（按日期分组）。配合 buildTrend 在 Java 侧补零，替代原 7 次 COUNT（P0-1）。
     */
    @Select("SELECT DATE_FORMAT(create_time, '%m-%d') d, CAST(COUNT(*) AS UNSIGNED) c FROM work_order " +
            "WHERE create_time >= #{start} GROUP BY DATE(create_time), DATE_FORMAT(create_time, '%m-%d') ORDER BY d")
    List<Map<String, Object>> selectCreateTrend(@Param("start") LocalDateTime start);

    /**
     * 近 N 天通过趋势（按审批时间分组）。
     */
    @Select("SELECT DATE_FORMAT(approve_time, '%m-%d') d, CAST(COUNT(*) AS UNSIGNED) c FROM work_order " +
            "WHERE approve_time >= #{start} GROUP BY DATE(approve_time), DATE_FORMAT(approve_time, '%m-%d') ORDER BY d")
    List<Map<String, Object>> selectApproveTrend(@Param("start") LocalDateTime start);
}
