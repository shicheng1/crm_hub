package com.zmd.order.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletResponse;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 *
 * 统一异常返回格式为 R<T>，避免前端收到 Spring 默认 500 错误页
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常（参数校验、状态错误等） */
    @ExceptionHandler(BusinessException.class)
    public R<?> handleBusiness(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    /** 参数校验异常（@Valid 触发） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<?> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", msg);
        return R.fail(400, msg);
    }

    /** 绑定异常 */
    @ExceptionHandler(BindException.class)
    public R<?> handleBind(BindException e) {
        String msg = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数绑定失败: {}", msg);
        return R.fail(400, msg);
    }

    /** 非法参数 */
    @ExceptionHandler(IllegalArgumentException.class)
    public R<?> handleIllegalArg(IllegalArgumentException e) {
        log.warn("非法参数: {}", e.getMessage());
        return R.fail(400, e.getMessage());
    }

    /** 兜底：未预期的异常 */
    @ExceptionHandler(Exception.class)
    public R<?> handleException(Exception e, HttpServletResponse response) {
        log.error("系统异常: {}", e.getMessage(), e);
        return R.fail(500, "系统繁忙，请稍后再试");
    }
}
