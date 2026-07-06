package com.zmd.order.statemachine;

import com.zmd.order.common.BusinessException;
import com.zmd.order.common.Constants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("工单状态机测试")
class OrderStatusTransitionTest {

    @Test
    @DisplayName("待审批可以进入审批中")
    void pendingCanTransitToReviewing() {
        assertTrue(OrderStatusTransition.canTransit(Constants.STATUS_PENDING, Constants.STATUS_REVIEWING));
    }

    @Test
    @DisplayName("审批中可以通过、退回或关闭")
    void reviewingCanTransitToExpectedStatuses() {
        assertTrue(OrderStatusTransition.canTransit(Constants.STATUS_REVIEWING, Constants.STATUS_APPROVED));
        assertTrue(OrderStatusTransition.canTransit(Constants.STATUS_REVIEWING, Constants.STATUS_RETURNED));
        assertTrue(OrderStatusTransition.canTransit(Constants.STATUS_REVIEWING, Constants.STATUS_CLOSED));
    }

    @Test
    @DisplayName("终态不能再次流转")
    void terminalStatusCannotTransit() {
        assertFalse(OrderStatusTransition.canTransit(Constants.STATUS_APPROVED, Constants.STATUS_REVIEWING));
        assertFalse(OrderStatusTransition.canTransit(Constants.STATUS_REJECTED, Constants.STATUS_REVIEWING));
        assertFalse(OrderStatusTransition.canTransit(Constants.STATUS_CLOSED, Constants.STATUS_REVIEWING));
    }

    @Test
    @DisplayName("非法流转抛业务异常")
    void invalidTransitionThrowsBusinessException() {
        assertThrows(BusinessException.class,
                () -> OrderStatusTransition.assertCanTransit(Constants.STATUS_APPROVED, Constants.STATUS_RETURNED));
    }
}
