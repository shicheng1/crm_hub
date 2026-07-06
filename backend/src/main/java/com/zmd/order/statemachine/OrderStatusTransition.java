package com.zmd.order.statemachine;

import com.zmd.order.common.BusinessException;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 工单状态机：集中维护合法状态流转，避免状态迁移散落在 service 中。
 */
public final class OrderStatusTransition {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED.put(OrderStatus.PENDING, EnumSet.of(OrderStatus.REVIEWING, OrderStatus.CLOSED));
        ALLOWED.put(OrderStatus.REVIEWING, EnumSet.of(OrderStatus.REVIEWING, OrderStatus.APPROVED, OrderStatus.REJECTED, OrderStatus.RETURNED, OrderStatus.CLOSED));
        ALLOWED.put(OrderStatus.RETURNED, EnumSet.of(OrderStatus.REVIEWING, OrderStatus.CLOSED));
        ALLOWED.put(OrderStatus.APPROVED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(OrderStatus.REJECTED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(OrderStatus.CLOSED, EnumSet.noneOf(OrderStatus.class));
    }

    private OrderStatusTransition() {
    }

    public static boolean canTransit(Integer fromCode, Integer toCode) {
        OrderStatus from = OrderStatus.fromCode(fromCode);
        OrderStatus to = OrderStatus.fromCode(toCode);
        if (from == null || to == null) {
            return false;
        }
        return ALLOWED.getOrDefault(from, EnumSet.noneOf(OrderStatus.class)).contains(to);
    }

    public static void assertCanTransit(Integer fromCode, Integer toCode) {
        if (!canTransit(fromCode, toCode)) {
            OrderStatus from = OrderStatus.fromCode(fromCode);
            OrderStatus to = OrderStatus.fromCode(toCode);
            String fromDesc = from != null ? from.getDesc() : String.valueOf(fromCode);
            String toDesc = to != null ? to.getDesc() : String.valueOf(toCode);
            throw new BusinessException(400, "工单状态不允许从[" + fromDesc + "]流转到[" + toDesc + "]");
        }
    }
}
