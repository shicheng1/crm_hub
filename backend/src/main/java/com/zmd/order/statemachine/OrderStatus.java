package com.zmd.order.statemachine;

import com.zmd.order.common.Constants;

public enum OrderStatus {
    PENDING(Constants.STATUS_PENDING, "待审批"),
    REVIEWING(Constants.STATUS_REVIEWING, "审批中"),
    APPROVED(Constants.STATUS_APPROVED, "已通过"),
    REJECTED(Constants.STATUS_REJECTED, "已驳回"),
    CLOSED(Constants.STATUS_CLOSED, "已关闭"),
    RETURNED(Constants.STATUS_RETURNED, "退回修改");

    private final int code;
    private final String desc;

    OrderStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static OrderStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (OrderStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }
}
