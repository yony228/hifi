package com.hify.common.exception;

import com.hify.common.dto.Result;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 * <p>
 * 拦截所有 Controller 抛出的异常，统一转换为 {@link Result#fail} 响应。
 * 所有业务错误返回 HTTP 200，仅 Spring Security 认证拦截和 404 路由不匹配返回 4xx。
 * <p>
 * <b>异常处理优先级（Spring 按匹配精度选择 handler，不是声明顺序）：</b>
 * <ol>
 *   <li>{@link BizException} → 取出 ErrorCode，保留自定义消息</li>
 *   <li>{@link MethodArgumentNotValidException} → 拼接字段校验失败详情</li>
 *   <li>{@link HttpMessageNotReadableException} → 请求体格式错误</li>
 *   <li>{@link ConstraintViolationException} → 方法参数校验失败</li>
 *   <li>{@link Exception} → 兜底，记录完整堆栈，返回通用错误</li>
 * </ol>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ======================== 业务异常 ========================

    /**
     * 业务异常：取出 ErrorCode 和消息，直接返回。
     * <p>
     * 消息使用 {@link BizException#getMessage()}，它已根据构造方式返回：
     * <ul>
     *   <li>传了 customMessage → 返回 customMessage</li>
     *   <li>没传 → 返回 ErrorCode 的默认消息</li>
     * </ul>
     */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException e) {
        log.warn("业务异常: code={}, message={}", e.getErrorCode().getCode(), e.getMessage());
        return Result.fail(e.getErrorCode(), e.getMessage());
    }

    // ======================== 参数校验 ========================

    /**
     * JSON 请求体校验失败（{@code @Valid @RequestBody}）。
     * 拼接所有字段错误：{@code "name: 不能为空; age: 必须大于0"}。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", detail);
        return Result.fail(ErrorCode.PARAM_INVALID, detail);
    }

    /**
     * 请求体不可读（JSON 格式错误、字段类型不匹配等）。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体不可读: {}", e.getMessage());
        return Result.fail(ErrorCode.REQUEST_BODY_UNREADABLE);
    }

    /**
     * 方法参数校验失败（{@code @Validated} on class-level, or query/path param validation）。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        String detail = e.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", detail);
        return Result.fail(ErrorCode.PARAM_INVALID, detail);
    }

    // ======================== 兜底 ========================

    /**
     * 未预期的异常：记录完整堆栈，返回通用错误，不暴露内部细节。
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统内部错误", e);
        return Result.fail(ErrorCode.INTERNAL_ERROR);
    }
}
