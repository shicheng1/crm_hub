package com.zmd.order.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zmd.order.common.Constants;
import com.zmd.order.entity.ApprovalFlowStep;
import com.zmd.order.entity.ApprovalStepApprover;
import com.zmd.order.entity.WorkOrder;
import com.zmd.order.mapper.ApprovalFlowStepMapper;
import com.zmd.order.mapper.ApprovalStepApproverMapper;
import com.zmd.order.mapper.OrderMapper;
import com.zmd.order.websocket.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 定时任务：自动关闭超时工单、催办长时间未处理工单
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderScheduleTask {

    private static final int REMINDER_HOURS = 24;

    private final OrderMapper orderMapper;
    private final ApprovalFlowStepMapper stepMapper;
    private final ApprovalStepApproverMapper stepApproverMapper;
    private final NotificationService notificationService;

    /**
     * 每 10 分钟扫描一次，将超过 24 小时未审批的待审批工单自动关闭。
     */
    @Scheduled(cron = "0 */10 * * * ?")
    public void autoCloseTimeoutOrders() {
        LocalDateTime deadline = LocalDateTime.now().minusHours(Constants.AUTO_CLOSE_HOURS);

        List<WorkOrder> timeoutOrders = orderMapper.selectList(
                new LambdaQueryWrapper<WorkOrder>()
                        .in(WorkOrder::getStatus, Constants.STATUS_PENDING, Constants.STATUS_REVIEWING)
                        .lt(WorkOrder::getCreateTime, deadline)
        );

        if (timeoutOrders.isEmpty()) {
            return;
        }

        for (WorkOrder order : timeoutOrders) {
            order.setStatus(Constants.STATUS_CLOSED);
            order.setUpdateTime(LocalDateTime.now());
            orderMapper.updateById(order);
            log.info("自动关闭超时工单, orderId={}, title={}, createTime={}",
                    order.getId(), order.getTitle(), order.getCreateTime());
        }

        log.info("定时任务：自动关闭 {} 个超时工单", timeoutOrders.size());
    }

    /**
     * 每小时扫描一次，向超过 24 小时仍在审批中的工单当前审批人发送催办通知。
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void remindTimeoutReviewingOrders() {
        LocalDateTime deadline = LocalDateTime.now().minusHours(REMINDER_HOURS);
        List<WorkOrder> reviewingOrders = orderMapper.selectList(
                new LambdaQueryWrapper<WorkOrder>()
                        .eq(WorkOrder::getStatus, Constants.STATUS_REVIEWING)
                        .lt(WorkOrder::getUpdateTime, deadline));

        if (reviewingOrders.isEmpty()) {
            return;
        }

        int notifyCount = 0;
        for (WorkOrder order : reviewingOrders) {
            for (Long approverId : getCurrentApproverIds(order)) {
                notificationService.notifyApproverReminder(approverId, order);
                notifyCount++;
            }
        }
        log.info("定时任务：催办 {} 个审批中工单，推送 {} 条通知", reviewingOrders.size(), notifyCount);
    }

    private List<Long> getCurrentApproverIds(WorkOrder order) {
        if (order.getFlowId() == null || order.getCurrentStep() == null || order.getCurrentStep() == 0) {
            return Collections.emptyList();
        }
        ApprovalFlowStep step = stepMapper.selectOne(
                new LambdaQueryWrapper<ApprovalFlowStep>()
                        .eq(ApprovalFlowStep::getFlowId, order.getFlowId())
                        .eq(ApprovalFlowStep::getStepOrder, order.getCurrentStep()));
        if (step == null) {
            return Collections.emptyList();
        }
        return stepApproverMapper.selectList(
                        new LambdaQueryWrapper<ApprovalStepApprover>()
                                .eq(ApprovalStepApprover::getStepId, step.getId()))
                .stream()
                .map(ApprovalStepApprover::getUserId)
                .collect(Collectors.toList());
    }
}
