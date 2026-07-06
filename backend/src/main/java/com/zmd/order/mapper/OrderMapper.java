package com.zmd.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zmd.order.entity.WorkOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
