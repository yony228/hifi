package com.hify.model.entity;

import java.time.Duration;

/**
 * 模型提供商类型枚举，映射 {@code model_provider.provider_type} 字段。
 *
 * <p>每种类型预设了连接超时、读取超时（非流式）、读取超时（流式）三个参数，
 * 对应 {@code docs/specs/design/llm-invoke-spec.md §3.3 超时配置速查表}。</p>
 *
 * <table>
 *   <tr><th>类型</th><th>连接超时</th><th>读取（非流式）</th><th>读取（流式）</th><th>原因</th></tr>
 *   <tr><td>OPENAI</td><td>5s</td><td>60s</td><td>180s</td><td>云 API，网络稳定</td></tr>
 *   <tr><td>OLLAMA</td><td>3s</td><td>120s</td><td>300s</td><td>本地 GPU，推理更慢</td></tr>
 *   <tr><td>VLLM</td><td>5s</td><td>60s</td><td>180s</td><td>同 OpenAI 兼容</td></tr>
 *   <tr><td>DEEPSEEK</td><td>5s</td><td>60s</td><td>180s</td><td>同 OpenAI 兼容</td></tr>
 *   <tr><td>CUSTOM</td><td>5s</td><td>60s</td><td>180s</td><td>兜底值</td></tr>
 * </table>
 */
public enum ProviderTypeEnum {

    OPENAI("openai",
            Duration.ofSeconds(5), Duration.ofSeconds(60), Duration.ofSeconds(180)),
    OLLAMA("ollama",
            Duration.ofSeconds(3), Duration.ofSeconds(120), Duration.ofSeconds(300)),
    VLLM("vllm",
            Duration.ofSeconds(5), Duration.ofSeconds(60), Duration.ofSeconds(180)),
    DEEPSEEK("deepseek",
            Duration.ofSeconds(5), Duration.ofSeconds(60), Duration.ofSeconds(180)),
    CUSTOM("custom",
            Duration.ofSeconds(5), Duration.ofSeconds(60), Duration.ofSeconds(180));

    /** 数据库存储值（VARCHAR(64)）。 */
    private final String dbValue;

    /** TCP + TLS 握手超时。 */
    private final Duration connectTimeout;

    /** 非流式读取超时：两次数据包之间的最大间隔。 */
    private final Duration readTimeout;

    /** 流式读取超时：SSE 场景允许更长的 token 间隔。 */
    private final Duration streamReadTimeout;

    ProviderTypeEnum(String dbValue,
                     Duration connectTimeout,
                     Duration readTimeout,
                     Duration streamReadTimeout) {
        this.dbValue = dbValue;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.streamReadTimeout = streamReadTimeout;
    }

    /**
     * 从数据库值反查枚举。
     *
     * @param dbValue 数据库中的 {@code provider_type} 字符串
     * @return 对应的枚举常量，未匹配时返回 {@link #CUSTOM}
     */
    public static ProviderTypeEnum fromDbValue(String dbValue) {
        if (dbValue == null) {
            return CUSTOM;
        }
        for (ProviderTypeEnum type : values()) {
            if (type.dbValue.equalsIgnoreCase(dbValue)) {
                return type;
            }
        }
        return CUSTOM;
    }

    // ── getters ──

    public String getDbValue() { return dbValue; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
    public Duration getStreamReadTimeout() { return streamReadTimeout; }
}
