package com.zmd.order.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zmd.order.common.PageQuery;
import com.zmd.order.common.R;
import com.zmd.order.dto.ApprovalDTO;
import com.zmd.order.dto.OrderCreateDTO;
import com.zmd.order.entity.OrderOperationLog;
import com.zmd.order.entity.WorkOrder;
import com.zmd.order.mapper.OperationLogMapper;
import com.zmd.order.rate.RateLimit;
import com.zmd.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OperationLogMapper logMapper;

    @PostMapping("/create")
    @RateLimit(key = "order:create", maxCount = 10, windowSeconds = 60)
    public R<Long> create(@Valid @RequestBody OrderCreateDTO dto) {
        return R.ok(orderService.createOrder(dto));
    }

    @GetMapping("/page")
    public R<IPage<WorkOrder>> page(PageQuery query,
                                    @RequestParam(required = false) Integer status,
                                    @RequestParam(required = false) String title) {
        return R.ok(orderService.pageOrders(query.getPage(), query.getSize(), status, title));
    }

    @GetMapping("/detail/{id}")
    public R<WorkOrder> detail(@PathVariable Long id) {
        return R.ok(orderService.getOrderDetail(id));
    }

    @PostMapping("/approve")
    public R<?> approve(@Valid @RequestBody ApprovalDTO dto) {
        orderService.approveOrder(dto);
        return R.ok();
    }

    /** 退回后重新提交 */
    @PostMapping("/resubmit/{id}")
    public R<?> resubmit(@PathVariable Long id) {
        orderService.resubmitOrder(id);
        return R.ok();
    }

    @GetMapping("/todo")
    public R<IPage<WorkOrder>> todo(PageQuery query) {
        return R.ok(orderService.todoList(query.getPage(), query.getSize()));
    }

    @GetMapping("/done")
    public R<IPage<WorkOrder>> done(PageQuery query) {
        return R.ok(orderService.doneList(query.getPage(), query.getSize()));
    }

    @GetMapping("/logs/{orderId}")
    public R<List<OrderOperationLog>> logs(@PathVariable Long orderId) {
        return R.ok(logMapper.selectList(
                new LambdaQueryWrapper<OrderOperationLog>()
                        .eq(OrderOperationLog::getOrderId, orderId)
                        .orderByAsc(OrderOperationLog::getOperateTime)));
    }
}
