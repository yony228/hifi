package com.hify.common.exception;

/**
 * 业务错误码枚举。
 * <p>
 * 五位数编码：{@code 1xxxx} 客户端错误，{@code 2xxxx} 服务端错误，{@code 0} 成功。
 * 所有业务异常通过 {@link BizException} 抛出，全局异常处理器根据 ErrorCode 组装统一响应。
 * <p>
 * 使用示例：
 * <pre>{@code
 * throw new BizException(ErrorCode.NOT_FOUND, "Agent 不存在: id=" + id);
 * }</pre>
 */
public enum ErrorCode {

    // ======================== 成功 ========================
    SUCCESS(0, "ok"),

    // ======================== 通用客户端错误 (10xxx) ========================
    PARAM_INVALID(10001, "参数校验失败"),
    REQUEST_BODY_UNREADABLE(10002, "请求体不可读"),
    PAGE_SIZE_EXCEEDED(10003, "分页 size 超限"),
    UNAUTHORIZED(10004, "未授权或登录已过期"),

    // ======================== 资源错误 (11xxx) ========================
    NOT_FOUND(11001, "资源不存在"),
    NAME_DUPLICATE(11002, "资源名称重复"),
    REFERENCED(11003, "资源被引用，无法删除"),

    // ======================== 模型错误 (12xxx) ========================
    MODEL_CONNECT_FAILED(12001, "模型连接失败"),
    MODEL_TIMEOUT(12002, "模型响应超时"),

    // ======================== 知识库错误 (13xxx) ========================
    FILE_FORMAT_UNSUPPORTED(13001, "文件格式不支持，仅支持 PDF / Markdown / TXT"),
    FILE_EMPTY(13002, "文件无有效文本内容"),

    // ======================== Agent 错误 (14xxx) ========================
    AGENT_MAX_ITERATIONS(14001, "Agent 推理轮次已达上限"),
    AGENT_TOOL_FAILED(14002, "Agent 工具调用失败"),

    // ======================== 服务端错误 (20xxx) ========================
    INTERNAL_ERROR(20001, "系统内部错误"),
    DB_ERROR(20002, "数据库操作失败"),
    EXTERNAL_SERVICE_FAILED(20003, "外部服务调用失败"),
    MODEL_CALL_FAILED(20004, "模型调用失败"),
    EMBEDDING_FAILED(20005, "向量化服务异常"),
    FILE_IO_ERROR(20006, "文件读写失败");

    // ======================== 字段 ========================

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
