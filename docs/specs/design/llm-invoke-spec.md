# Hify LLM 调用技术方案

**范围：** hify-model 模块的 LLM API 调用层。覆盖 OpenAI-compatible、Claude、Gemini、Ollama。

**核心问题：** 外部 LLM API 慢（10-60s）、不稳定（限流/超时/5xx）、并发受限。必须将失败隔离在模型调用层，不影响上层 Agent 和 Chat。

---

## 一、总体架构

```
Agent / Workflow (调用方)
    │
    ▼
ModelService.getById() + invoke(modelId, prompt)
    │
    ▼
┌─────────────────────────────────────────────────────┐
│  LlmInvoker (模型调用门面)                           │
│                                                      │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐          │
│  │ 超时控制  │  │ 重试策略  │  │ 熔断隔离  │          │
│  │ (Timeout) │  │ (Retry)  │  │(Circuit  │          │
│  │          │  │          │  │ Breaker)  │          │
│  └──────────┘  └──────────┘  └──────────┘          │
│                         │                            │
│              ┌──────────┴──────────┐                │
│              │  LlmThreadPool      │                │
│              │  (隔离线程池)        │                │
│              └──────────┬──────────┘                │
│                         │                            │
│              Spring AI ChatClient                    │
│              (OpenAI-compatible HTTP)                │
└─────────────────────────────────────────────────────┘
```

**核心组件：**

| 组件 | 职责 | 实现 |
|---|---|---|
| `LlmInvoker` | 调用门面，组合超时/重试/熔断 | Spring Bean |
| `LlmThreadPool` | 隔离 LLM 调用的专用线程池 | `@Bean` ThreadPoolTaskExecutor |
| `Resilience4j` | 重试 + 熔断 + 限流 + 舱壁 | Resilience4j `@CircuitBreaker` + `@Retry` |
| `Spring AI ChatClient` | 底层 HTTP 调用 | Spring AI 自动配置 |

---

## 二、线程管理

### 2.1 问题

Tomcat 默认线程池 200 线程。假设 50 人并发对话、每个 LLM 调用阻塞 30 秒：
- 200 ÷ 30s ≈ QPS 6.6
- 如果有 10 个人同时聊天，Tomcat 线程就耗掉一半
- 线程池打满 → 所有 HTTP 请求排队 → 系统整体不可用

### 2.2 方案：独立线程池隔离

LLM 调用统一提交到专用线程池，与 Tomcat 请求线程解耦。

```java
@Configuration
public class LlmThreadPoolConfig {

    @Bean("llmExecutor")
    public ThreadPoolTaskExecutor llmExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);         // 常驻线程（日常并发量）
        executor.setMaxPoolSize(50);           // 峰值线程（上限）
        executor.setQueueCapacity(200);        // 超过 50 并发后排队，最多排 200 个
        executor.setKeepAliveSeconds(120);     // 超出 core 的线程空闲 120s 回收
        executor.setRejectedExecutionHandler(new CallerRunsPolicy()); // 队列满 → 调用线程自己执行（自然降级）
        executor.setThreadNamePrefix("llm-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
```

**参数推算逻辑：**

| 参数 | 值 | 推算依据 |
|---|---|---|
| corePoolSize | 10 | 日常 50 人并发度约 3-5%，10 线程处理稳态 |
| maxPoolSize | 50 | SSE 峰值连接数 ~10，乘以 5 倍余量 |
| queueCapacity | 200 | 缓冲 4 秒内的突发流量（50 线程 × 1 req/s） |
| RejectedExecutionPolicy | CallerRuns | 拒绝策略 = 自然降级，而非抛异常 |

### 2.3 调用方式

```java
@Service
public class LlmInvoker {

    private final Map<Long, ChatClient> chatClientCache;
    private final ThreadPoolTaskExecutor llmExecutor;

    /**
     * 非流式调用：提交到 LLM 线程池，用 CompletableFuture 等待结果。
     * 调用方在 Tomcat 线程上阻塞等待（SSE 除外），但 Tomcat 线程不参与 LLM IO。
     */
    public ChatResponse invokeSync(Long modelId, Prompt prompt, Duration timeout) {
        return CompletableFuture
                .supplyAsync(() -> doInvoke(modelId, prompt), llmExecutor)
                .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .join();
    }

    /**
     * 流式调用：返回 Flux，由 WebFlux 的事件循环驱动。
     * 不占用 LLM 线程池——Flux 的 subscribeOn 绑定到 llmExecutor，
     * 但每个 token 推送由 Reactor 调度，不阻塞线程。
     */
    public Flux<ChatResponse> invokeStream(Long modelId, Prompt prompt) {
        return Mono.fromCallable(() -> doStreamInvoke(modelId, prompt))
                .subscribeOn(Schedulers.fromExecutor(llmExecutor))
                .flatMapMany(Function.identity());
    }
}
```

**两个关键决策：**

| 场景 | 线程模型 | 原因 |
|---|---|---|
| 非流式 | `CompletableFuture` + `llmExecutor` | 调用方（Agent ReAct loop）本身就是同步逻辑，阻塞等待结果 |
| 流式 SSE | `Flux` + `subscribeOn(llmExecutor)` | LLM IO 在 llmExecutor 上启动，token 推送由 Reactor Netty 事件循环驱动，不耗业务线程 |

### 2.4 舱壁隔离（Bulkhead）

按模型供应商隔离并发数，防止一个供应商的慢响应拖垮全部调用：

```yaml
# application.yml
resilience4j:
  bulkhead:
    instances:
      llm-openai:
        max-concurrent-calls: 20
        max-wait-duration: 5s       # 超过 20 并发时，新请求最多等 5 秒
      llm-claude:
        max-concurrent-calls: 10    # Claude 配额通常更少
        max-wait-duration: 5s
      llm-ollama:
        max-concurrent-calls: 5     # 本地模型，GPU 显存限制
        max-wait-duration: 10s
```

```java
@Bulkhead(name = "llm-openai", type = Bulkhead.Type.SEMAPHORE)
public ChatResponse invokeOpenAi(Prompt prompt) { ... }
```

---

## 三、超时控制

### 3.1 分层超时

LLM 调用涉及三层网络交互，每层独立超时：

```
DNS 解析 → TCP 握手 → TLS 握手 → 发送请求 → 首 token → 逐 token → 完成
│                              │  ├─────── 连接超时 ──────┤
│                              │  ├────────────── 读取超时 ──────────────┤
│                              │  ├────────────── 总超时 ─────────────────────────┤
```

| 超时类型 | 非流式 | 流式 (SSE) | 含义 |
|---|---|---|---|
| 连接超时 | 5s | 5s | TCP + TLS 握手完成 |
| 读取超时 | 60s | 180s | 两次数据包之间的最大间隔 |
| 总超时 | 120s | 300s | 整个调用允许的最长时间 |

### 3.2 Spring AI 超时配置

```java
@Configuration
public class HttpClientConfig {

    /**
     * 为每个 LLM 模型创建独立超时配置的 RestClient。
     * Spring AI 的 ChatClient 底层用 RestClient.Builder 构建。
     */
    public RestClient.Builder llmRestClientBuilder(ModelConfig modelConfig) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));      // 连接超时 5s
        factory.setReadTimeout(Duration.ofSeconds(180));        // 默认 180s（覆盖流式场景）

        // JDK HttpClient（推荐，支持 HTTP/2 和更好的超时控制）
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        JdkClientHttpRequestFactory jdkFactory = new JdkClientHttpRequestFactory(httpClient);
        jdkFactory.setReadTimeout(Duration.ofSeconds(180));

        return RestClient.builder()
                .requestFactory(jdkFactory);
    }

    /**
     * 每个模型配置有自己的 ChatClient 实例 + 超时。
     * 缓存 key = modelId，避免每次请求重建。
     */
    public ChatClient getOrCreateChatClient(ModelConfig config) {
        return chatClientCache.computeIfAbsent(config.getId(), id -> {
            RestClient restClient = llmRestClientBuilder(config).build();

            // OpenAI-compatible ChatClient
            return OpenAiChatModel.builder()
                    .openAiApi(new OpenAiApi(config.getEndpoint(), config.getApiKey(), restClient))
                    .defaultOptions(OpenAiChatOptions.builder()
                            .model(config.getModelName())
                            .build())
                    .build();
        });
    }
}
```

### 3.3 超时配置速查表

| 模型类型 | 连接超时 | 读取超时(非流式) | 读取超时(流式) | 原因 |
|---|---|---|---|---|
| OpenAI / 兼容接口 | 5s | 60s | 180s | 云 API，网络稳定 |
| Claude (Anthropic) | 5s | 60s | 180s | 同上 |
| Gemini | 5s | 60s | 180s | 同上 |
| Ollama (本地) | 3s | 120s | 300s | 本地 GPU，无网络延迟但推理更慢 |
| 通用默认 | 5s | 60s | 180s | 兜底值 |

---

## 四、容错——熔断器（Circuit Breaker）

### 4.1 问题

如果 OpenAI 挂了，每个请求仍然等 60 秒超时 → 10 个并发就耗尽线程池 → 系统阻塞。熔断器的本质是**快速失败**：检测到下游异常后，直接拒绝请求，等下游恢复后再尝试。

### 4.2 Resilience4j 熔断配置

```yaml
resilience4j:
  circuitbreaker:
    instances:
      llm-default:              # 通用配置
        sliding-window-type: COUNT_BASED
        sliding-window-size: 10        # 最近 10 次调用的统计窗口
        failure-rate-threshold: 50     # 失败率 ≥ 50% 触发熔断
        wait-duration-in-open-state: 30s   # 熔断后 30s 进入半开状态
        permitted-number-of-calls-in-half-open-state: 3  # 半开时最多放 3 个请求试探
        automatic-transition-from-open-to-half-open-enabled: true
        record-exceptions:
          - java.net.SocketTimeoutException
          - java.net.ConnectException
          - java.io.IOException
          - org.springframework.web.client.HttpServerErrorException  # 5xx
        ignore-exceptions:
          - com.hify.common.exception.BizException  # 业务异常不触发熔断
```

### 4.3 熔断状态机

```
  ┌──────────┐  失败率 > 50%   ┌──────────┐
  │  CLOSED  │ ───────────────→ │   OPEN   │
  │ (正常)    │                  │ (快速失败)│
  └──────────┘                  └────┬─────┘
       ▲                             │
       │         30 秒后             │
       │    ┌──────────┐             │
       └────│HALF_OPEN │←────────────┘
            │ (试探)    │
            └────┬──────┘
                 │
         ┌───────┴───────┐
         │               │
    试调用成功        试调用失败
    → CLOSED         → OPEN
```

### 4.4 熔断后的降级响应

```java
@Service
public class LlmInvoker {

    @CircuitBreaker(name = "llm-default", fallbackMethod = "llmFallback")
    @Retry(name = "llm-retry")
    public ChatResponse invoke(Long modelId, Prompt prompt) {
        return doInvoke(modelId, prompt);
    }

    /**
     * 降级方法：熔断打开或重试耗尽时调用。
     * 返回一个结构化的错误消息，让上层 Agent 能识别并友好提示用户。
     */
    public ChatResponse llmFallback(Long modelId, Prompt prompt, Exception e) {
        log.warn("LLM 调用降级: modelId={}, reason={}", modelId, e.getMessage());
        return ChatResponse.builder()
                .error("模型服务暂时不可用，请稍后重试。[" + e.getClass().getSimpleName() + "]")
                .fallback(true)
                .build();
    }
}
```

### 4.5 按模型供应商差异化熔断

不同 API 的故障特征不同，需要独立熔断器：

```yaml
resilience4j:
  circuitbreaker:
    instances:
      llm-openai:
        sliding-window-size: 20          # OpenAI QPS 高，窗口大一些
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
      llm-claude:
        sliding-window-size: 10          # Claude 调用频率低
        failure-rate-threshold: 40       # 更敏感（配额贵）
        wait-duration-in-open-state: 60s
      llm-ollama:
        sliding-window-size: 5           # 本地部署，失败窗口小
        failure-rate-threshold: 60       # 容忍度更高（本地可重启）
        wait-duration-in-open-state: 15s
      llm-gemini:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
```

熔断器实例按 `modelId` 后缀动态选择：`"llm-" + modelConfig.getProvider().name().toLowerCase()`。

---

## 五、重试策略

### 5.1 哪些错误值得重试

| 错误类型 | 是否重试 | 原因 |
|---|---|---|
| `ConnectException` / `SocketTimeoutException` | ✅ 重试 | 网络抖动，瞬时故障 |
| `HttpServerErrorException` (5xx) | ✅ 重试 | 服务端临时过载 |
| HTTP 429 (Rate Limit) | ✅ 重试（等 Retry-After） | 限流是临时的 |
| HTTP 401 / 403 (认证错误) | ❌ 不重试 | API Key 配错了，重试没用 |
| HTTP 400 (Bad Request) | ❌ 不重试 | 请求参数错误，重试没用 |
| `OutOfMemoryError` / 本地资源耗尽 | ❌ 不重试 | 本地问题，重试只会更糟 |

### 5.2 重试配置

```yaml
resilience4j:
  retry:
    instances:
      llm-retry:
        max-attempts: 3                  # 最多 3 次（1 次原始 + 2 次重试）
        wait-duration: 1s                # 基础等待 1s
        exponential-backoff-multiplier: 2 # 指数退避：1s → 2s → 4s
        max-wait-duration: 10s           # 单次重试等待上限
        enable-randomized-wait: true     # 开启 jitter
        randomized-wait-factor: 0.25     # jitter ±25%，避免惊群
        retry-exceptions:
          - java.net.SocketTimeoutException
          - java.net.ConnectException
          - java.io.IOException
          - org.springframework.web.client.HttpServerErrorException
        ignore-exceptions:
          - org.springframework.web.client.HttpClientErrorException  # 4xx（429 特殊处理）
```

### 5.3 429 Rate Limit 特殊处理

429 不是瞬时故障——是 API 主动限流。不能用固定退避，必须尊重 `Retry-After` 头：

```java
/**
 * 检测 HTTP 429 响应，解析 Retry-After 头，返回应等待的秒数。
 * 调用方用此值覆盖 Resilience4j 的默认退避时间。
 */
@Component
public class RateLimitHandler {

    private static final Pattern RETRY_AFTER_DELTA = Pattern.compile("^\\d+$");

    /**
     * 从 HTTP 429 响应中提取等待时间。
     * Retry-After 有两种格式：
     *   - "120"      → 等待 120 秒
     *   - "Wed, 21 Oct 2015 07:28:00 GMT" → 计算到该时间的差值
     */
    public Duration extractRetryAfter(HttpClientErrorException.TooManyRequests ex) {
        List<String> headers = ex.getResponseHeaders().get("Retry-After");
        if (headers == null || headers.isEmpty()) {
            return Duration.ofSeconds(15);  // 拿不到头，默认 15s
        }

        String value = headers.get(0).trim();
        if (RETRY_AFTER_DELTA.matcher(value).matches()) {
            long seconds = Long.parseLong(value);
            return Duration.ofSeconds(Math.min(seconds, 120)); // 上限 120s
        }

        try {
            Instant retryTime = Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(value));
            Duration wait = Duration.between(Instant.now(), retryTime);
            return wait.isNegative() ? Duration.ZERO : wait;
        } catch (DateTimeParseException e) {
            return Duration.ofSeconds(15);
        }
    }
}
```

### 5.4 重试消费器（监控 + 告警）

```java
@Component
@Slf4j
public class LlmRetryListener implements RetryEventConsumer<Object> {

    @Override
    public void onEvent(RetryEvent retryEvent) {
        int attempt = retryEvent.getNumberOfRetryAttempts();
        Throwable cause = retryEvent.getLastThrowable();

        log.warn("LLM 调用重试: attempt={}/{}, error={}",
                attempt,
                retryEvent.getRetryContext().getMaxAttempts(),
                cause != null ? cause.getClass().getSimpleName() : "unknown");

        // 最后一次重试仍然失败 → 告警
        if (attempt == retryEvent.getRetryContext().getMaxAttempts()) {
            log.error("LLM 调用最终失败: error={}", cause != null ? cause.getMessage() : "");
        }
    }
}
```

---

## 六、完整组合示例

### 6.1 注解组合顺序

Resilience4j 注解从外到内的执行顺序：

```
@Bulkhead     ← 1. 限制并发（舱壁）
@CircuitBreaker ← 2. 熔断判断（快速失败）
@Retry        ← 3. 重试（熔断关闭时）
@TimeLimiter  ← 4. 超时控制（单次调用）
  方法体      ← 5. 实际 HTTP 调用
```

### 6.2 实际调用代码

```java
@Service
@Slf4j
public class LlmInvoker {

    private final Map<Long, ChatClient> clientCache = new ConcurrentHashMap<>();
    private final ModelMapper modelMapper;
    private final ThreadPoolTaskExecutor llmExecutor;
    private final RateLimitHandler rateLimitHandler;

    /**
     * 非流式调用——全链路保护。
     */
    @Bulkhead(name = "#{@providerNameResolver.resolve(modelId)}", type = Bulkhead.Type.SEMAPHORE)
    @CircuitBreaker(name = "#{@providerNameResolver.resolve(modelId)}")
    @Retry(name = "llm-retry")
    @TimeLimiter(name = "llm-timelimiter")
    public ChatResponse invoke(Long modelId, Prompt prompt) {
        ChatClient client = getClient(modelId);
        try {
            return client.call(prompt);
        } catch (HttpClientErrorException.TooManyRequests e) {
            // 429 特殊处理：记录但交给 Resilience4j 重试
            Duration wait = rateLimitHandler.extractRetryAfter(e);
            log.warn("LLM 限流: modelId={}, retry-after={}s", modelId, wait.toSeconds());
            throw e;  // 重新抛出，让 @Retry 处理
        }
    }

    /**
     * 流式调用——只做熔断，不做重试和舱壁。
     * SSE 已经发了部分 token 给用户，重试会导致输出重复。
     * 超时由 Flux#timeout 控制。
     */
    @CircuitBreaker(name = "#{@providerNameResolver.resolve(modelId)}")
    public Flux<ChatResponse> invokeStream(Long modelId, Prompt prompt) {
        return Mono.fromCallable(() -> getClient(modelId).stream(prompt))
                .subscribeOn(Schedulers.fromExecutor(llmExecutor))
                .flatMapMany(Function.identity())
                .timeout(Duration.ofSeconds(300))  // 流式总超时 5 分钟
                .doOnError(e -> log.warn("LLM 流式调用异常: modelId={}, error={}", modelId, e.getMessage()));
    }

    /**
     * 获取或创建缓存的 ChatClient。
     * 每 5 分钟检查一次模型配置是否变更（endpoint / apiKey / modelName）。
     */
    private ChatClient getClient(Long modelId) {
        return clientCache.computeIfAbsent(modelId, id -> {
            ModelConfig config = modelMapper.findById(id);
            if (config == null) {
                throw new BizException(ErrorCode.MODEL_NOT_FOUND);
            }
            return buildChatClient(config);
        });
    }

    /**
     * 解析 provider 名称，用于动态选择熔断器/舱壁实例。
     * 示例：modelId=1 provider=openai → 返回 "llm-openai"
     */
    @Component("providerNameResolver")
    public static class ProviderNameResolver {
        private final ModelMapper modelMapper;

        public String resolve(Long modelId) {
            ModelConfig config = modelMapper.findById(modelId);
            return "llm-" + config.getProvider().name().toLowerCase();
        }
    }
}
```

---

## 七、配置速查

### 7.1 全部 YAML 配置汇总

```yaml
# ======================== LLM 专用线程池 ========================
llm:
  thread-pool:
    core-size: 10
    max-size: 50
    queue-capacity: 200
    keep-alive-seconds: 120

# ======================== Resilience4j ========================
resilience4j:
  retry:
    instances:
      llm-retry:
        max-attempts: 3
        wait-duration: 1s
        exponential-backoff-multiplier: 2
        enable-randomized-wait: true
        randomized-wait-factor: 0.25
        retry-exceptions:
          - java.net.SocketTimeoutException
          - java.net.ConnectException
          - java.io.IOException
          - org.springframework.web.client.HttpServerErrorException
        ignore-exceptions:
          - org.springframework.web.client.HttpClientErrorException

  circuitbreaker:
    instances:
      llm-openai:
        sliding-window-size: 20
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 3
      llm-claude:
        sliding-window-size: 10
        failure-rate-threshold: 40
        wait-duration-in-open-state: 60s
        permitted-number-of-calls-in-half-open-state: 2
      llm-gemini:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 3
      llm-ollama:
        sliding-window-size: 5
        failure-rate-threshold: 60
        wait-duration-in-open-state: 15s
        permitted-number-of-calls-in-half-open-state: 2

  bulkhead:
    instances:
      llm-openai:
        max-concurrent-calls: 20
        max-wait-duration: 5s
      llm-claude:
        max-concurrent-calls: 10
        max-wait-duration: 5s
      llm-gemini:
        max-concurrent-calls: 15
        max-wait-duration: 5s
      llm-ollama:
        max-concurrent-calls: 5
        max-wait-duration: 10s

  timelimiter:
    instances:
      llm-timelimiter:
        timeout-duration: 120s          # 非流式 120s 上限

# ======================== HTTP 超时 ========================
spring:
  ai:
    openai:
      connect-timeout: 5s
      read-timeout: 180s                # 按流式取值，非流式由 TimeLimiter 保障
```

### 7.2 故障场景 → 行为映射

| 故障场景 | 重试 | 熔断 | 用户看到 |
|---|---|---|---|
| 网络抖动 1-2 次 | 退避重试，成功 | 不触发 | 正常响应（多等 2-4s） |
| OpenAI 间歇 5xx | 3 次全失败 | 触发熔断 | "模型服务暂时不可用，请稍后重试" |
| API Key 过期 (401) | 不重试 | 不触发（ignore-exception） | "模型配置错误"（BizException） |
| 触发限流 (429) | 等 Retry-After 后重试 | 不触发 | 等几秒后正常响应 |
| Ollama 本地 OOM | 不重试 | 触发熔断 | "模型服务暂时不可用" |
| 并发超 20 (OpenAI 舱壁) | - | - | 排队等 5s 或 "服务繁忙，请稍后" |
| 单次调用超 120s | - | 不触发（TimeLimiter 直接抛超时） | "请求超时，请简化问题" |
| SSE 流中断 | ❌ 不重试 | 不触发 | 前端显示已输出的 token + "连接中断" |
