package com.hify.model.config;

import com.hify.model.entity.ProviderTypeEnum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link HttpClientConfig} 的 RestClient 构建与 ChatClient 缓存行为，
 * 按 {@code docs/specs/llm-invoke-spec.md §3 超时控制} 规则运行。
 * <p>
 * 直接实例化 HttpClientConfig 进行测试，不依赖 Spring 容器。
 */
@DisplayName("HTTP 客户端配置验证（与 HttpClientConfig 同步）")
class HttpClientConfigTest {

    private HttpClientConfig httpClientConfig;

    @BeforeEach
    void setUp() {
        httpClientConfig = new HttpClientConfig();
    }

    @AfterEach
    void tearDown() {
        httpClientConfig.clearCache();
    }

    // ======================== ProviderTypeEnum ========================

    @Nested
    @DisplayName("ProviderTypeEnum 超时预设")
    class ProviderTypeEnumPresets {

        @Test
        @DisplayName("OPENAI: 连接 5s / 读取 60s / 流式读取 180s")
        void openAiPresets() {
            assertThat(ProviderTypeEnum.OPENAI.getConnectTimeout()).isEqualTo(Duration.ofSeconds(5));
            assertThat(ProviderTypeEnum.OPENAI.getReadTimeout()).isEqualTo(Duration.ofSeconds(60));
            assertThat(ProviderTypeEnum.OPENAI.getStreamReadTimeout()).isEqualTo(Duration.ofSeconds(180));
        }

        @Test
        @DisplayName("OLLAMA: 连接 3s / 读取 120s / 流式读取 300s")
        void ollamaPresets() {
            assertThat(ProviderTypeEnum.OLLAMA.getConnectTimeout()).isEqualTo(Duration.ofSeconds(3));
            assertThat(ProviderTypeEnum.OLLAMA.getReadTimeout()).isEqualTo(Duration.ofSeconds(120));
            assertThat(ProviderTypeEnum.OLLAMA.getStreamReadTimeout()).isEqualTo(Duration.ofSeconds(300));
        }

        @Test
        @DisplayName("VLLM: 连接 5s / 读取 60s / 流式读取 180s")
        void vllmPresets() {
            assertThat(ProviderTypeEnum.VLLM.getConnectTimeout()).isEqualTo(Duration.ofSeconds(5));
            assertThat(ProviderTypeEnum.VLLM.getReadTimeout()).isEqualTo(Duration.ofSeconds(60));
            assertThat(ProviderTypeEnum.VLLM.getStreamReadTimeout()).isEqualTo(Duration.ofSeconds(180));
        }

        @Test
        @DisplayName("DEEPSEEK: 连接 5s / 读取 60s / 流式读取 180s")
        void deepseekPresets() {
            assertThat(ProviderTypeEnum.DEEPSEEK.getConnectTimeout()).isEqualTo(Duration.ofSeconds(5));
            assertThat(ProviderTypeEnum.DEEPSEEK.getReadTimeout()).isEqualTo(Duration.ofSeconds(60));
            assertThat(ProviderTypeEnum.DEEPSEEK.getStreamReadTimeout()).isEqualTo(Duration.ofSeconds(180));
        }

        @Test
        @DisplayName("CUSTOM: 连接 5s / 读取 60s / 流式读取 180s（兜底值）")
        void customPresets() {
            assertThat(ProviderTypeEnum.CUSTOM.getConnectTimeout()).isEqualTo(Duration.ofSeconds(5));
            assertThat(ProviderTypeEnum.CUSTOM.getReadTimeout()).isEqualTo(Duration.ofSeconds(60));
            assertThat(ProviderTypeEnum.CUSTOM.getStreamReadTimeout()).isEqualTo(Duration.ofSeconds(180));
        }
    }

    @Nested
    @DisplayName("ProviderTypeEnum 反查")
    class ProviderTypeEnumFromDbValue {

        @Test
        @DisplayName("fromDbValue(\"openai\") → OPENAI")
        void fromOpenAi() {
            assertThat(ProviderTypeEnum.fromDbValue("openai")).isEqualTo(ProviderTypeEnum.OPENAI);
        }

        @Test
        @DisplayName("fromDbValue(\"OLLAMA\") → OLLAMA（大小写不敏感）")
        void fromOllamaCaseInsensitive() {
            assertThat(ProviderTypeEnum.fromDbValue("OLLAMA")).isEqualTo(ProviderTypeEnum.OLLAMA);
        }

        @Test
        @DisplayName("fromDbValue(null) → CUSTOM")
        void fromNull() {
            assertThat(ProviderTypeEnum.fromDbValue(null)).isEqualTo(ProviderTypeEnum.CUSTOM);
        }

        @Test
        @DisplayName("fromDbValue(未知值) → CUSTOM")
        void fromUnknown() {
            assertThat(ProviderTypeEnum.fromDbValue("some-future-provider")).isEqualTo(ProviderTypeEnum.CUSTOM);
        }
    }

    // ======================== RestClient 构建 ========================

    @Nested
    @DisplayName("RestClient.Builder 构建")
    class RestClientBuilderCreation {

        @Test
        @DisplayName("buildRestClientBuilder 返回非空 builder")
        void shouldReturnNonNullBuilder() {
            RestClient.Builder builder = httpClientConfig.buildRestClientBuilder(ProviderTypeEnum.OPENAI);
            assertThat(builder).isNotNull();
        }

        @Test
        @DisplayName("builder 可以 build() 出有效的 RestClient")
        void shouldBuildValidRestClient() {
            RestClient.Builder builder = httpClientConfig.buildRestClientBuilder(ProviderTypeEnum.OPENAI);
            RestClient restClient = builder.build();
            assertThat(restClient).isNotNull();
        }

        @Test
        @DisplayName("不同 Provider 类型均能正常构建")
        void shouldBuildForAllProviderTypes() {
            for (ProviderTypeEnum type : ProviderTypeEnum.values()) {
                RestClient restClient = httpClientConfig.buildRestClientBuilder(type).build();
                assertThat(restClient).isNotNull();
            }
        }

        @Test
        @DisplayName("providerType 为 null 时抛出 NPE")
        void shouldThrowOnNullProviderType() {
            assertThatThrownBy(() -> httpClientConfig.buildRestClientBuilder(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ======================== ChatClient 缓存 ========================

    @Nested
    @DisplayName("ChatClient 缓存行为")
    class ChatClientCaching {

        private final ModelConfig config1 = new ModelConfig(
                1L, "https://api.openai.com/v1", "sk-test-key",
                "gpt-4o", ProviderTypeEnum.OPENAI);

        private final ModelConfig config2 = new ModelConfig(
                2L, "http://localhost:11434/v1", null,
                "llama3", ProviderTypeEnum.OLLAMA);

        @Test
        @DisplayName("getOrCreateChatClient 返回非空实例")
        void shouldReturnNonNullChatClient() {
            OpenAiChatModel client = httpClientConfig.getOrCreateChatClient(config1);
            assertThat(client).isNotNull();
        }

        @Test
        @DisplayName("相同 config → 缓存命中，返回同一实例")
        void shouldCacheAndReturnSameInstance() {
            OpenAiChatModel client1 = httpClientConfig.getOrCreateChatClient(config1);
            OpenAiChatModel client2 = httpClientConfig.getOrCreateChatClient(config1);

            assertThat(client1).isSameAs(client2);
            assertThat(httpClientConfig.cacheSize()).isEqualTo(1);
        }

        @Test
        @DisplayName("不同 config → 缓存未命中，返回不同实例")
        void shouldReturnDifferentInstancesForDifferentConfigs() {
            OpenAiChatModel client1 = httpClientConfig.getOrCreateChatClient(config1);
            OpenAiChatModel client2 = httpClientConfig.getOrCreateChatClient(config2);

            assertThat(client1).isNotSameAs(client2);
            assertThat(httpClientConfig.cacheSize()).isEqualTo(2);
        }

        @Test
        @DisplayName("相同 modelId 不同 endpoint → 视为不同缓存 key")
        void shouldCacheByModelIdAndEndpoint() {
            ModelConfig config1b = new ModelConfig(
                    1L, "https://api.openai.com/v2", "sk-test-key",
                    "gpt-4o", ProviderTypeEnum.OPENAI);

            OpenAiChatModel client1 = httpClientConfig.getOrCreateChatClient(config1);
            OpenAiChatModel client1b = httpClientConfig.getOrCreateChatClient(config1b);

            assertThat(client1).isNotSameAs(client1b);
            assertThat(httpClientConfig.cacheSize()).isEqualTo(2);
        }

        @Test
        @DisplayName("config 为 null 时抛出 NPE")
        void shouldThrowOnNullConfig() {
            assertThatThrownBy(() -> httpClientConfig.getOrCreateChatClient(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ======================== 缓存驱逐 ========================

    @Nested
    @DisplayName("缓存驱逐")
    class CacheEviction {

        private final ModelConfig config = new ModelConfig(
                1L, "https://api.openai.com/v1", "sk-key",
                "gpt-4o", ProviderTypeEnum.OPENAI);

        @Test
        @DisplayName("evictCache 后再次调用 → 返回新实例")
        void shouldReturnNewInstanceAfterEviction() {
            OpenAiChatModel client1 = httpClientConfig.getOrCreateChatClient(config);
            assertThat(httpClientConfig.cacheSize()).isEqualTo(1);

            httpClientConfig.evictCache(config.cacheKey());
            assertThat(httpClientConfig.cacheSize()).isEqualTo(0);

            OpenAiChatModel client2 = httpClientConfig.getOrCreateChatClient(config);
            assertThat(client1).isNotSameAs(client2);
            assertThat(httpClientConfig.cacheSize()).isEqualTo(1);
        }

        @Test
        @DisplayName("evictCache 不存在的 key 不抛异常")
        void shouldNotThrowOnMissingKey() {
            httpClientConfig.evictCache("nonexistent-key");
            assertThat(httpClientConfig.cacheSize()).isEqualTo(0);
        }

        @Test
        @DisplayName("clearCache 清空所有缓存")
        void shouldClearAllCache() {
            ModelConfig config2 = new ModelConfig(
                    2L, "http://localhost:11434/v1", null,
                    "llama3", ProviderTypeEnum.OLLAMA);

            httpClientConfig.getOrCreateChatClient(config);
            httpClientConfig.getOrCreateChatClient(config2);
            assertThat(httpClientConfig.cacheSize()).isEqualTo(2);

            httpClientConfig.clearCache();
            assertThat(httpClientConfig.cacheSize()).isEqualTo(0);
        }
    }

    // ======================== ModelConfig ========================

    @Nested
    @DisplayName("ModelConfig 值对象")
    class ModelConfigValidation {

        @Test
        @DisplayName("cacheKey = id@endpoint")
        void cacheKeyFormat() {
            ModelConfig mc = new ModelConfig(
                    42L, "https://api.example.com/v1", "sk-key",
                    "gpt-4", ProviderTypeEnum.OPENAI);

            assertThat(mc.cacheKey()).isEqualTo("42@https://api.example.com/v1");
        }

        @Test
        @DisplayName("id 为 null 抛出 NPE")
        void shouldNotAcceptNullId() {
            assertThatThrownBy(() -> new ModelConfig(
                    null, "https://api.example.com", "key", "model", ProviderTypeEnum.OPENAI))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("endpoint 为 null 抛出 NPE")
        void shouldNotAcceptNullEndpoint() {
            assertThatThrownBy(() -> new ModelConfig(
                    1L, null, "key", "model", ProviderTypeEnum.OPENAI))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("modelName 为 null 抛出 NPE")
        void shouldNotAcceptNullModelName() {
            assertThatThrownBy(() -> new ModelConfig(
                    1L, "https://api.example.com", "key", null, ProviderTypeEnum.OPENAI))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("apiKey 可以为 null（Ollama 本地部署无需 key）")
        void shouldAllowNullApiKey() {
            ModelConfig mc = new ModelConfig(
                    1L, "http://localhost:11434/v1", null,
                    "llama3", ProviderTypeEnum.OLLAMA);

            assertThat(mc.getApiKey()).isNull();
        }

        @Test
        @DisplayName("相同参数 → equals 返回 true")
        void equalsForSameValues() {
            ModelConfig a = new ModelConfig(1L, "https://api.x.com", "key", "gpt", ProviderTypeEnum.OPENAI);
            ModelConfig b = new ModelConfig(1L, "https://api.x.com", "key", "gpt", ProviderTypeEnum.OPENAI);
            assertThat(a).isEqualTo(b);
        }

        @Test
        @DisplayName("不同 id → equals 返回 false")
        void notEqualsForDifferentId() {
            ModelConfig a = new ModelConfig(1L, "https://api.x.com", "key", "gpt", ProviderTypeEnum.OPENAI);
            ModelConfig b = new ModelConfig(2L, "https://api.x.com", "key", "gpt", ProviderTypeEnum.OPENAI);
            assertThat(a).isNotEqualTo(b);
        }
    }
}
