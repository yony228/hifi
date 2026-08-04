package com.hify.model.config;

import com.hify.model.entity.ProviderTypeEnum;

import java.util.Objects;

/**
 * 模型连通性参数——用于创建 {@link org.springframework.ai.openai.api.OpenAiApi}。
 *
 * <p>与数据库实体解耦，由 Service 层从 {@code model_provider} + {@code model} 两表
 * 拼装后传入 {@link HttpClientConfig#getOrCreateChatClient(ModelConfig)}。</p>
 *
 * <p>字段映射：</p>
 * <table>
 *   <tr><th>字段</th><th>来源表.列</th></tr>
 *   <tr><td>{@code id}</td><td>model.id</td></tr>
 *   <tr><td>{@code endpoint}</td><td>model_provider.base_url</td></tr>
 *   <tr><td>{@code apiKey}</td><td>model_provider.api_key</td></tr>
 *   <tr><td>{@code modelName}</td><td>model.model_name</td></tr>
 *   <tr><td>{@code providerType}</td><td>model_provider.provider_type</td></tr>
 * </table>
 */
public final class ModelConfig {

    private final Long id;
    private final String endpoint;
    private final String apiKey;
    private final String modelName;
    private final ProviderTypeEnum providerType;

    public ModelConfig(Long id,
                       String endpoint,
                       String apiKey,
                       String modelName,
                       ProviderTypeEnum providerType) {
        this.id = Objects.requireNonNull(id, "model.id 不能为空");
        this.endpoint = Objects.requireNonNull(endpoint, "provider.base_url 不能为空");
        this.apiKey = apiKey; // Ollama 本地部署可能不需要 key
        this.modelName = Objects.requireNonNull(modelName, "model.model_name 不能为空");
        this.providerType = Objects.requireNonNull(providerType, "provider.provider_type 不能为空");
    }

    // ── getters ──

    public Long getId() { return id; }
    public String getEndpoint() { return endpoint; }
    public String getApiKey() { return apiKey; }
    public String getModelName() { return modelName; }
    public ProviderTypeEnum getProviderType() { return providerType; }

    /**
     * 生成缓存 key：{@code modelId + "@" + endpoint}，确保
     * 同一 model 换 endpoint 后缓存失效（如 API Key 更换）。
     */
    public String cacheKey() {
        return id + "@" + endpoint;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ModelConfig that)) return false;
        return id.equals(that.id)
                && endpoint.equals(that.endpoint)
                && Objects.equals(apiKey, that.apiKey)
                && modelName.equals(that.modelName)
                && providerType == that.providerType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, endpoint, apiKey, modelName, providerType);
    }

    @Override
    public String toString() {
        return "ModelConfig{id=" + id + ", endpoint='" + endpoint + "', modelName='" + modelName + "'}";
    }
}
