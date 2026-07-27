package com.hify.common.exception;

/**
 * 业务异常，系统中所有业务错误的唯一抛出类型。
 * <p>
 * 不同于 {@link RuntimeException}：它强制携带 {@link ErrorCode}，
 * 让全局异常处理器（GlobalExceptionHandler）能精确映射为 {@code Result.fail()} 响应。
 * <p>
 * <b>使用约束：</b>
 * <ul>
 *   <li>Service 层遇到业务规则冲突 → {@code throw new BizException(ErrorCode.xxx)}</li>
 *   <li>需要补充上下文信息 → {@code throw new BizException(ErrorCode.NOT_FOUND, "Agent 不存在: id=" + id)}</li>
 *   <li>包装底层异常 → {@code throw new BizException(ErrorCode.DB_ERROR, "保存 Agent 失败", e)}</li>
 *   <li>Controller 层禁止 try-catch 手动组装错误响应，交给全局异常处理器</li>
 * </ul>
 * <p>
 * 正确示例：
 * <pre>{@code
 * // Service 中校验引用存在性
 * public AgentResponse getById(Long id) {
 *     Agent agent = agentMapper.findById(id);
 *     if (agent == null) {
 *         throw new BizException(ErrorCode.NOT_FOUND, "Agent 不存在: id=" + id);
 *     }
 *     return AgentResponse.from(agent);
 * }
 * }</pre>
 */
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * 使用 ErrorCode 默认消息。
     *
     * @param errorCode 错误码
     */
    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /**
     * 自定义消息覆盖 ErrorCode 默认消息。
     *
     * @param errorCode     错误码
     * @param customMessage 覆盖消息（会代替 errorCode.getMessage()）
     */
    public BizException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
    }

    /**
     * 自定义消息 + 原始异常（用于包装底层异常，保留堆栈）。
     *
     * @param errorCode     错误码
     * @param customMessage 覆盖消息
     * @param cause         原始异常
     */
    public BizException(ErrorCode errorCode, String customMessage, Throwable cause) {
        super(customMessage, cause);
        this.errorCode = errorCode;
    }

    /**
     * 使用 ErrorCode 默认消息 + 原始异常。
     *
     * @param errorCode 错误码
     * @param cause     原始异常
     */
    public BizException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
