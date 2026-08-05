package com.hify.model.llm;

import com.hify.model.config.HttpClientConfig;
import com.hify.model.config.ModelConfig;
import com.hify.model.entity.ProviderTypeEnum;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link LlmInvoker} 的方法签名、注解声明和降级行为。
 * <p>
 * Resilience4j AOP 注解在单元测试中不生效（无 Spring 容器代理），
 * 熔断/重试/舱壁的实际行为需通过 Spring 集成测试验证。
 */
@DisplayName("LlmInvoker — 调用门面验证")
class LlmInvokerTest {

    private LlmInvoker llmInvoker;
    private HttpClientConfig httpClientConfig;
    private ThreadPoolTaskExecutor llmExecutor;

    @BeforeEach
    void setUp() {
        httpClientConfig = new HttpClientConfig();
        llmExecutor = llmExecutor();
        llmInvoker = new LlmInvoker(httpClientConfig, llmExecutor, new RateLimitHandler());
    }

    // ======================== 注解声明验证 ========================

    @Nested
    @DisplayName("注解声明")
    class AnnotationPresence {

        @Test
        @DisplayName("invokeSync 方法声明了 @CircuitBreaker")
        void invokeSyncHasCircuitBreaker() throws NoSuchMethodException {
            Method method = LlmInvoker.class.getMethod("invokeSync", ModelConfig.class, Prompt.class);
            assertThat(method.isAnnotationPresent(CircuitBreaker.class)).isTrue();
        }

        @Test
        @DisplayName("invokeSync 方法声明了 @Retry")
        void invokeSyncHasRetry() throws NoSuchMethodException {
            Method method = LlmInvoker.class.getMethod("invokeSync", ModelConfig.class, Prompt.class);
            assertThat(method.isAnnotationPresent(Retry.class)).isTrue();
        }

        @Test
        @DisplayName("invokeSync 方法声明了 @Bulkhead")
        void invokeSyncHasBulkhead() throws NoSuchMethodException {
            Method method = LlmInvoker.class.getMethod("invokeSync", ModelConfig.class, Prompt.class);
            assertThat(method.isAnnotationPresent(Bulkhead.class)).isTrue();
        }

        @Test
        @DisplayName("invokeSync 方法声明了 @TimeLimiter")
        void invokeSyncHasTimeLimiter() throws NoSuchMethodException {
            Method method = LlmInvoker.class.getMethod("invokeSync", ModelConfig.class, Prompt.class);
            assertThat(method.isAnnotationPresent(TimeLimiter.class)).isTrue();
        }

        @Test
        @DisplayName("invokeStream 方法声明了 @CircuitBreaker")
        void invokeStreamHasCircuitBreaker() throws NoSuchMethodException {
            Method method = LlmInvoker.class.getMethod("invokeStream", ModelConfig.class, Prompt.class);
            assertThat(method.isAnnotationPresent(CircuitBreaker.class)).isTrue();
        }

        @Test
        @DisplayName("invokeStream 方法没有 @Retry（流式不重试）")
        void invokeStreamHasNoRetry() throws NoSuchMethodException {
            Method method = LlmInvoker.class.getMethod("invokeStream", ModelConfig.class, Prompt.class);
            assertThat(method.isAnnotationPresent(Retry.class)).isFalse();
        }
    }

    // ======================== 降级方法验证 ========================

    @Nested
    @DisplayName("降级方法")
    class FallbackBehavior {

        private final ModelConfig config = new ModelConfig(
                1L, "https://api.openai.com/v1", "sk-key",
                "gpt-4o", ProviderTypeEnum.OPENAI);

        private final Prompt prompt = new Prompt(new UserMessage("Hello"));

        @Test
        @DisplayName("syncFallback 返回一个完成的 CompletableFuture（非 null，不抛异常）")
        void syncFallbackReturnsCompletedFuture() throws Exception {
            CompletableFuture<ChatResponse> future = llmInvoker.syncFallback(
                    config, prompt, new RuntimeException("测试异常"));

            assertThat(future).isNotNull();
            assertThat(future).isDone();
            ChatResponse response = future.get();
            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("streamFallback 返回包含错误的 Flux（非 null）")
        void streamFallbackReturnsErrorFlux() {
            var flux = llmInvoker.streamFallback(config, prompt, new RuntimeException("测试异常"));

            assertThat(flux).isNotNull();
            // Flux.error 在被订阅时会发出错误
            flux.onErrorResume(e -> {
                assertThat(e).isInstanceOf(RuntimeException.class);
                return reactor.core.publisher.Flux.empty();
            }).blockLast();
        }
    }

    // ======================== helper ========================

    private static ThreadPoolTaskExecutor llmExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(10);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setThreadNamePrefix("llm-");
        executor.initialize();
        return executor;
    }
}
