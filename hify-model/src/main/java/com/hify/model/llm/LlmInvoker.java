package com.hify.model.llm;

import com.hify.model.config.HttpClientConfig;
import com.hify.model.config.ModelConfig;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * LLM 调用统一门面——组合线程隔离 + Resilience4j 容错保护。
 *
 * <p>对应规范 {@code docs/specs/design/llm-invoke-spec.md §4-6}：</p>
 *
 * <h3>注解顺序（从外到内）</h3>
 * <pre>
 * &#64;Bulkhead          ← 1. 限制并发（舱壁隔离，按 Provider 维度）
 * &#64;CircuitBreaker     ← 2. 熔断判断（快速失败）
 * &#64;Retry             ← 3. 重试（3 次指数退避，仅网络/超时/5xx）
 * &#64;TimeLimiter       ← 4. 超时控制（120s 上限）
 *   方法体            ← 5. 实际 LLM 调用（在 llmExecutor 线程池中）
 * </pre>
 *
 * <h3>流式调用</h3>
 * 不做重试和舱壁——已发出的 token 无法撤回，超时由 {@code Flux#timeout} 控制。
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * &#64;Autowired
 * private LlmInvoker llmInvoker;
 *
 * ModelConfig config = new ModelConfig(1L, "https://api.openai.com/v1", "sk-xxx", "gpt-4o", ProviderTypeEnum.OPENAI);
 * Prompt prompt = new Prompt(new UserMessage("Hello"));
 *
 * // 非流式
 * ChatResponse response = llmInvoker.invokeSync(config, prompt).join();
 *
 * // 流式
 * Flux<ChatResponse> flux = llmInvoker.invokeStream(config, prompt);
 * }</pre>
 *
 * @see com.hify.model.config.HttpClientConfig
 * @see com.hify.model.config.LlmThreadPoolConfig
 */
@Service
@Slf4j
public class LlmInvoker {

    private final HttpClientConfig httpClientConfig;
    private final ThreadPoolTaskExecutor llmExecutor;
    private final RateLimitHandler rateLimitHandler;

    public LlmInvoker(HttpClientConfig httpClientConfig,
                      ThreadPoolTaskExecutor llmExecutor,
                      RateLimitHandler rateLimitHandler) {
        this.httpClientConfig = httpClientConfig;
        this.llmExecutor = llmExecutor;
        this.rateLimitHandler = rateLimitHandler;
    }

    // ======================== 非流式调用（全链路保护） ========================

    /**
     * 非流式 LLM 调用——全链路保护。
     *
     * <p>保护链：舱壁（按 Provider）→ 熔断 → 重试（3 次）→ 超时（120s）→ 实际调用。</p>
     * <p>实际 LLM 调用提交到 {@code llmExecutor} 线程池，不占用 Tomcat worker 线程。</p>
     * <p>调用方通过返回的 {@link CompletableFuture} 等待结果或继续编排。</p>
     *
     * @param modelConfig 模型连通性参数
     * @param prompt 提示词（含消息历史和系统提示）
     * @return 封装了 LLM 响应的 CompletableFuture
     */
    @Bulkhead(name = "#{@providerNameResolver.resolve(#args[0].providerType)}",
              type = Bulkhead.Type.SEMAPHORE)
    @CircuitBreaker(name = "#{@providerNameResolver.resolve(#args[0].providerType)}",
                    fallbackMethod = "syncFallback")
    @Retry(name = "llm-retry")
    @TimeLimiter(name = "llm-timelimiter")
    public CompletableFuture<ChatResponse> invokeSync(ModelConfig modelConfig, Prompt prompt) {
        return CompletableFuture.supplyAsync(() -> {
            ChatModel chatModel = httpClientConfig.getOrCreateChatClient(modelConfig);
            try {
                return chatModel.call(prompt);
            } catch (HttpClientErrorException.TooManyRequests e) {
                // 429 特殊处理：记录 Retry-After 时间，交给 @Retry 重试
                Duration wait = rateLimitHandler.extractRetryAfter(e);
                log.warn("LLM 限流 (429): config={}, retry-after={}",
                        modelConfig.cacheKey(), wait);
                throw e; // 重新抛出，让 Resilience4j @Retry 处理
            }
        }, llmExecutor);
    }

    /**
     * 非流式调用降级方法：熔断打开或重试耗尽时调用。
     *
     * @return 包含降级错误信息的 CompletableFuture
     */
    public CompletableFuture<ChatResponse> syncFallback(ModelConfig modelConfig,
                                                         Prompt prompt,
                                                         Exception e) {
        log.warn("LLM 调用降级: config={}, reason={}",
                modelConfig.cacheKey(), e != null ? e.getClass().getSimpleName() : "unknown");
        return CompletableFuture.completedFuture(
                new ChatResponse(java.util.Collections.emptyList()));
    }

    // ======================== 流式调用（简化保护） ========================

    /**
     * 流式 LLM 调用——仅做熔断，不做重试/舱壁/超时。
     *
     * <p>SSE 已发出的 token 无法撤回，重试会导致输出重复，因此流式调用不重试。</p>
     * <p>LLM IO 在 {@code llmExecutor} 线程池中启动，
     * 每个 token 推送由 Reactor Netty 事件循环驱动，不阻塞业务线程。</p>
     * <p>超时由 {@link Flux#timeout(Duration)} 控制，默认 300 秒。</p>
     *
     * @param modelConfig 模型连通性参数
     * @param prompt 提示词
     * @return LLM 流式响应的 Flux
     */
    @CircuitBreaker(name = "#{@providerNameResolver.resolve(#args[0].providerType)}",
                    fallbackMethod = "streamFallback")
    public Flux<ChatResponse> invokeStream(ModelConfig modelConfig, Prompt prompt) {
        return Mono.fromCallable(() -> {
                    ChatModel chatModel = httpClientConfig.getOrCreateChatClient(modelConfig);
                    return chatModel.stream(prompt);
                })
                .subscribeOn(Schedulers.fromExecutor(llmExecutor))
                .flatMapMany(Function.identity())
                .timeout(Duration.ofSeconds(300))
                .doOnError(e -> log.warn("LLM 流式调用异常: config={}, error={}",
                        modelConfig.cacheKey(), e.getMessage()));
    }

    /**
     * 流式调用降级方法：熔断打开时返回包含错误信号的 Flux。
     */
    public Flux<ChatResponse> streamFallback(ModelConfig modelConfig,
                                              Prompt prompt,
                                              Exception e) {
        log.warn("LLM 流式调用降级: config={}, reason={}",
                modelConfig.cacheKey(), e != null ? e.getClass().getSimpleName() : "unknown");
        return Flux.error(new RuntimeException("模型服务暂时不可用，请稍后重试"));
    }
}
