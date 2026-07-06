package com.zmd.order.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zmd.order.dto.ApprovalDTO;
import com.zmd.order.dto.OrderCreateDTO;
import com.zmd.order.entity.WorkOrder;

public interface OrderService {
    Long createOrder(OrderCreateDTO dto);
    IPage<WorkOrder> pageOrders(int page, int size, Integer status, String title);
    WorkOrder getOrderDetail(Long orderId);
    void approveOrder(ApprovalDTO dto);
    /** 退回后重新提交 */
    void resubmitOrder(Long orderId);
    IPage<WorkOrder> todoList(int page, int size);
    IPage<WorkOrder> doneList(int page, int size);
}
