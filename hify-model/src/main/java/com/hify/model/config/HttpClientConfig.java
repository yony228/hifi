package com.hify.model.config;

import com.hify.model.entity.ProviderTypeEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LLM 模型 HTTP 客户端配置。
 *
 * <p>对应规范 {@code docs/specs/design/llm-invoke-spec.md §3 超时控制}：</p>
 * <ul>
 *   <li>为每个 Provider 类型预设差异化的连接/读取超时</li>
 *   <li>通过 {@link #buildRestClientBuilder(ProviderTypeEnum)} 创建带超时的 {@link RestClient.Builder}</li>
 *   <li>{@link #getOrCreateChatClient(ModelConfig)} 缓存 {@link OpenAiChatModel} 实例，避免频繁重建</li>
 *   <li>适配 OpenAI / Ollama / vLLM / DeepSeek 等所有 OpenAI-compatible 接口</li>
 * </ul>
 *
 * <h3>超时配置速查</h3>
 * <table>
 *   <tr><th>Provider</th><th>连接超时</th><th>读取（非流式）</th><th>读取（流式）</th></tr>
 *   <tr><td>OpenAI / vLLM / DeepSeek</td><td>5s</td><td>60s</td><td>180s</td></tr>
 *   <tr><td>Ollama（本地）</td><td>3s</td><td>120s</td><td>300s</td></tr>
 * </table>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * &#64;Autowired
 * private HttpClientConfig httpClientConfig;
 *
 * ModelConfig config = new ModelConfig(modelId, baseUrl, apiKey, modelName, providerType);
 * ChatModel chatModel = httpClientConfig.getOrCreateChatClient(config);
 * ChatResponse response = chatModel.call(prompt);
 * }</pre>
 *
 * @see com.hify.model.config.LlmThreadPoolConfig
 */
@Configuration
public class HttpClientConfig {

    private static final Logger log = LoggerFactory.getLogger(HttpClientConfig.class);

    /** ChatModel 缓存，key 为 {@link ModelConfig#cacheKey()}。 */
    private final Map<String, OpenAiChatModel> chatClientCache = new ConcurrentHashMap<>();

    // ======================== RestClient 构建 ========================

    /**
     * 为指定 Provider 类型创建带超时配置的 {@link RestClient.Builder}。
     * <p>
     * 使用 JDK {@link HttpClient} 作为底层传输（支持 HTTP/2 和更好的超时控制），
     * 连接超时和读取超时从 {@link ProviderTypeEnum} 预设中读取。
     * 流式场景建议使用此方法创建的 builder，其读取超时按流式值（180s/300s）设置。
     *
     * @param providerType 模型提供商类型（决定超时参数）
     * @return 配置了连接超时和流式读取超时的 RestClient.Builder
     */
    public RestClient.Builder buildRestClientBuilder(ProviderTypeEnum providerType) {
        Objects.requireNonNull(providerType, "providerType 不能为空");

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(providerType.getConnectTimeout())
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(providerType.getStreamReadTimeout());

        if (log.isDebugEnabled()) {
            log.debug("创建 RestClient.Builder: provider={}, connectTimeout={}, readTimeout(stream)={}",
                    providerType.getDbValue(),
                    providerType.getConnectTimeout(),
                    providerType.getStreamReadTimeout());
        }

        return RestClient.builder().requestFactory(requestFactory);
    }

    // ======================== ChatClient 缓存 ========================

    /**
     * 获取或创建缓存的 {@link OpenAiChatModel} 实例。
     * <p>
     * 缓存 key 由 {@link ModelConfig#cacheKey()} 生成（modelId + endpoint），
     * 同一模型的重复调用命中缓存，换 endpoint 后自动失效。
     * <p>
     * 内部使用 {@link OpenAiApi.Builder} 注入自定义 {@link RestClient.Builder}，
     * 模型名通过 {@link OpenAiChatOptions#getModel()} 设置。
     *
     * @param config 模型连通性参数（endpoint / apiKey / modelName / providerType）
     * @return 缓存的 OpenAiChatModel 实例（非空）
     */
    public OpenAiChatModel getOrCreateChatClient(ModelConfig config) {
        Objects.requireNonNull(config, "ModelConfig 不能为空");

        return chatClientCache.computeIfAbsent(config.cacheKey(), key -> {
            RestClient.Builder restClientBuilder = buildRestClientBuilder(config.getProviderType());

            // Ollama 等本地模型无需 API Key，传空字符串（Builder 不接受 null）
            String apiKey = config.getApiKey() != null ? config.getApiKey() : "";

            OpenAiApi openAiApi = OpenAiApi.builder()
                    .baseUrl(config.getEndpoint())
                    .apiKey(apiKey)
                    .restClientBuilder(restClientBuilder)
                    .build();

            OpenAiChatOptions defaultOptions = OpenAiChatOptions.builder()
                    .model(config.getModelName())
                    .build();

            OpenAiChatModel chatModel = OpenAiChatModel.builder()
                    .openAiApi(openAiApi)
                    .defaultOptions(defaultOptions)
                    .build();

            log.info("创建 ChatClient 缓存: key={}, endpoint={}, model={}",
                    key, config.getEndpoint(), config.getModelName());
            return chatModel;
        });
    }

    // ======================== 缓存管理 ========================

    /**
     * 按缓存 key 驱逐单个 ChatClient 实例（通常在模型配置变更时调用）。
     *
     * @param cacheKey 由 {@link ModelConfig#cacheKey()} 生成
     */
    public void evictCache(String cacheKey) {
        OpenAiChatModel removed = chatClientCache.remove(cacheKey);
        if (removed != null) {
            log.info("驱逐 ChatClient 缓存: key={}", cacheKey);
        }
    }

    /**
     * 清空全部缓存。
     */
    public void clearCache() {
        int size = chatClientCache.size();
        chatClientCache.clear();
        log.info("清空全部 ChatClient 缓存: count={}", size);
    }

    /**
     * 当前缓存的 ChatClient 数量（用于监控和测试）。
     */
    public int cacheSize() {
        return chatClientCache.size();
    }
}
