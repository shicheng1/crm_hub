package com.zmd.order.common;

import lombok.Getter;

/**
 * 业务异常
 *
 * 用于业务逻辑中的可预期错误（如状态不允许、权限不足等）
 * 被 GlobalExceptionHandler 捕获后返回给前端，不记录 ERROR 日志
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
