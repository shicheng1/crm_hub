package com.zmd.order.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zmd.order.common.Constants;
import com.zmd.order.entity.WorkOrder;
import com.zmd.order.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时任务：自动关闭超时工单
 *
 * 每 10 分钟扫描一次，将超过 24 小时未审批的工单自动关闭
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderScheduleTask {

    private final OrderMapper orderMapper;

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
}
