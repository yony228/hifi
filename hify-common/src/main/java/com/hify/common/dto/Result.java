package com.hify.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hify.common.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应体。
 * <p>
 * 所有 Controller 方法必须返回 {@code Result<T>}，由 Spring MVC 序列化为 JSON：
 * <pre>{@code
 * { "code": 0, "message": "ok", "data": { ... } }
 * }</pre>
 * <p>
 * 使用静态工厂方法创建，禁止直接 new：
 * <pre>{@code
 * Result.ok(data);                      // 成功 + 数据
 * Result.ok();                          // 成功（无数据，创建/更新/删除）
 * Result.fail(ErrorCode.NOT_FOUND);                  // 使用错误码默认消息
 * Result.fail(ErrorCode.NOT_FOUND, "Agent 不存在");   // 覆盖错误码消息
 * }</pre>
 *
 * @param <T> data 字段的类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Result<T> {

    /**
     * 业务状态码：0 = 成功，非 0 = 失败。
     * <p>通过 {@code ALWAYS} 覆盖全局 NON_NULL，确保即使为零值也出现在 JSON 中。</p>
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private int code;

    /**
     * 人类可读的简短描述。
     * <p>通过 {@code ALWAYS} 覆盖全局 NON_NULL，确保始终出现在 JSON 中。</p>
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String message;

    /**
     * 响应数据，成功时有值，失败时为 null。
     * <p>通过 {@code ALWAYS} 覆盖全局 NON_NULL，确保失败时 {@code "data": null} 仍出现在 JSON 中。</p>
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private T data;

    // ======================== 成功工厂 ========================

    /** 成功 + 数据 */
    public static <T> Result<T> ok(T data) {
        return new Result<>(0, "ok", data);
    }

    /** 成功（无数据，用于创建/更新/删除等无返回体的操作） */
    public static <T> Result<T> ok() {
        return new Result<>(0, "ok", null);
    }

    // ======================== 失败工厂 ========================

    /** 业务失败（使用 ErrorCode 默认消息） */
    public static <T> Result<T> fail(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    /** 业务失败（ErrorCode + 自定义消息覆盖） */
    public static <T> Result<T> fail(ErrorCode errorCode, String customMessage) {
        return new Result<>(errorCode.getCode(), customMessage, null);
    }

    /** 业务失败（原始 code + message） */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    /** 业务失败（仅消息，默认 code=1） */
    public static <T> Result<T> fail(String message) {
        return new Result<>(1, message, null);
    }
}
