package com.aiwms.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 *
 * <p>@RestControllerAdvice 会拦截所有 Controller 抛出的异常，
 * 统一转换成 Result 结构返回，避免异常堆栈直接暴露给前端。
 *
 * <p>这样 Controller 里不用到处写 try-catch。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：返回友好提示 */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /** 参数校验失败（@Valid 校验 RequestBody） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .findFirst()
                .orElse("参数校验失败");
        log.warn("参数校验失败: {}", msg);
        return Result.fail(400, msg);
    }

    /** 参数绑定失败（表单/路径参数） */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .findFirst()
                .orElse("参数绑定失败");
        return Result.fail(400, msg);
    }

    /** 兜底：其他未预期的异常 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);   // 打印完整堆栈，便于排查
        return Result.fail(500, "系统繁忙，请稍后重试");
    }
}
