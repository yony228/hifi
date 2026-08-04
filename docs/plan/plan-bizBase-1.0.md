# Hify 业务基础组件规划 v1.0

> 在写业务功能之前必须补齐的基础组件，按**数据库层 → 接口层 → 外部调用 → 缓存 → 可观测性**五个维度梳理。

---

## 一、数据库层

### 1. DDL 建表脚本

- **解决什么问题：** docs/specs/data-model.md 定义了 12 张表，但项目中零条 SQL，启动后数据库是空的，任何业务开发都无从做起。
- **实现位置：** `hify-app/src/main/resources/db/schema-mysql.sql` + `schema-h2.sql`（dev profile 用）
- **要点：**
  - 覆盖全部 12 张表：`user`, `model_provider`, `model`, `knowledge`, `knowledge_document`, `knowledge_chunk`（pgvector）, `agent`, `tool`, `agent_tool`, `workflow`, `workflow_node`, `session`, `message`, `log`
  - 包含所有索引和 FK 约束
  - H2 兼容版本（pgvector 相关部分用标准字段替代）
  - MySQL 用 `spring.sql.init.mode=always` 在首次启动时自动执行
- **优先级：** 🔴 最高 — 硬前置依赖
- **已完成：** ✅

### 2. Jackson 序列化配置（`JacksonConfig`）

- **解决什么问题：** api-spec 要求空值字段不序列化（`NON_NULL`）、datetime 统一格式，当前未配置，全局序列化行为不可控，前后端对接会出格式不一致的问题。
- **实现位置：** `hify-common: com.hify.common.config.JacksonConfig`
- **要点：**
  - `ObjectMapper.setSerializationInclusion(Include.NON_NULL)`
  - 注册 `JavaTimeModule`，禁用 `WRITE_DATES_AS_TIMESTAMPS`
  - 日期格式统一为 `yyyy-MM-dd HH:mm:ss`
  - `code` / `message` / `data` 字段始终保留（即使为 null）
- **优先级：** 🔴 最高 — 所有 API 的 JSON 输出依赖此配置
- **已完成：** ✅

### 3. 传统分页工具（`PageUtils`）

- **解决什么问题：** MyBatis-Plus `Page<T>` 的 `total`/`current`/`size` 字段与项目 `PageResult<T>` 对应，但需要统一的转换入口，避免各 Service 各自写 setter 赋值。同时 `PageResult.from()` 作为静态工厂确保分页响应结构一致。
- **实现位置：** `hify-common: com.hify.common.util.PageUtils`
- **要点：**
  - `PageUtils.toPageResult(List<T> items, Page<?> mpPage) → PageResult<T>`
  - 从 MyBatis-Plus `Page` 自动提取 `total`/`current`/`size`
  - `PageResult` 已有 `total`/`page`/`size` 字段，无需改动结构
  - 被所有业务模块的列表查询共用
  - 项目前期用传统分页（`page`/`pageSize`），后期个别接口（如消息列表）若出现深页性能问题再单独改为游标
- **优先级：** 🟡 高 — 列表查询接口通用依赖
- **已完成：** ✅

---

## 二、接口层

### 4. JSON 工具类（`JsonUtil`）

- **解决什么问题：** Workflow JSON 配置解析、Agent prompt 模板处理、工具调用参数序列化等多处需要 JSON 操作，各模块各自 `new ObjectMapper()` 会导致配置不一致。
- **实现位置：** `hify-common: com.hify.common.util.JsonUtil`
- **要点：**
  - `toJson(Object) → String`
  - `fromJson(String, Class<T>) → T`
  - `fromJsonList(String, Class<T>) → List<T>`
  - 内部复用 Spring 容器的 `ObjectMapper`（确保全局配置一致）
- **优先级：** 🟡 高 — 几乎所有模块都需要的工具
- **已完成：** ✅

### 5. SSE 推送工具（`SseUtil`）

- **解决什么问题：** 对话引擎和 Agent 运行时都需要通过 SSE 流式推送（逐 token 输出、工具调用中间状态、错误通知），`ServerSentEvent.Builder` 拼接逻辑会在多处重复。
- **实现位置：** `hify-common: com.hify.common.util.SseUtil`
- **要点：**
  - `tokenEvent(text)` / `toolCallEvent(name, args)` / `errorEvent(msg)` / `doneEvent()`
  - 统一 SSE 事件格式（`event:` 字段区分类型）
  - chat 和 agent 模块直接复用
- **优先级：** 🟡 高 — chat/agent 模块核心依赖

### 6. 系统常量（`Constants`）

- **解决什么问题：** 魔术字符串散布各处：缓存 key 前缀、默认分页大小、最大 Token 限制、系统默认时区等，后期修改时要全局搜索替换。
- **实现位置：** `hify-common: com.hify.common.constant.Constants`
- **要点：**
  - `CACHE_PREFIX` — 缓存 key 前缀
  - `DEFAULT_PAGE_SIZE` / `MAX_PAGE_SIZE` — 分页限制
  - `MAX_AGENT_ITERATIONS` — Agent 迭代上限
  - `TOKEN_LIMITS` — Token 相关限制
  - `DATE_FORMAT` / `TIMEZONE` — 格式化常量
- **优先级：** 🟡 高 — 所有模块引用

---

## 三、外部调用（LLM）

### 7. LLM 线程池配置（`LlmThreadPoolConfig`）

- **解决什么问题：** LLM 调用是长阻塞 IO（30-120 秒/次），如果共用 Tomcat worker 线程，几条并发 SSE 连接就能耗尽线程池，导致健康检查和其他 API 不可用。
- **实现位置：** `hify-model: com.hify.model.config.LlmThreadPoolConfig`
- **要点：**
  - core=10, max=50, queue=200, CallerRunsPolicy 拒绝策略
  - 所有 LLM 调用通过 `@Async("llmExecutor")` 隔离执行
  - 线程名前缀 `llm-` 便于监控识别
- **优先级：** 🔴 最高 — 防止 LLM 慢调用拖垮整个应用
- **已完成：** ✅

### 8. 模型客户端配置（`HttpClientConfig`）

- **解决什么问题：** 不同模型（OpenAI 300ms vs Ollama 本地 10ms）延迟差异巨大，统一超时无法合理适配。
- **实现位置：** `hify-model: com.hify.model.config.HttpClientConfig`
- **要点：**
  - 从 `ModelProvider` 配置中读取超时参数，为每个 Provider 创建专属 `RestClient` / `WebClient.Builder`
  - 连接超时 5s，读取超时 60s（非流式）/ 180s（流式）
  - 缓存 `ChatClient` 实例避免频繁重建
  - 适配 OpenAI / Ollama / vLLM / DeepSeek 等 OpenAI-compatible 接口
- **优先级：** 🔴 最高 — LLM 调用的基础设施
- **已完成：** ✅

### 9. LLM 调用门面 + Resilience4j 熔断降级

- **解决什么问题：** LLM 服务不稳定、限流频繁，不做保护会导致调用方堆积、资源耗尽。需按 Provider 维度做差异化保护。
- **实现位置：** `hify-model: com.hify.model.llm.LlmInvoker` + `resilience4j.yml`
- **要点：**
  - `LlmInvoker` — 统一调用入口，注入 ChatClient 缓存
  - `ProviderNameResolver` — 按 Provider 名称动态选择断路器实例
  - `RateLimitHandler` — 解析 429 的 `Retry-After` 头
  - `LlmRetryListener` — 重试事件监控
  - 断路器：滑动窗口 5-20，失败率阈值 40-60%，等待时间 15-60s
  - 舱壁：每个 Provider 最大并发数 5-20
  - 重试：3 次，指数退避 1s/2s/4s，jitter ±25%，仅对连接/超时/5xx，跳过 4xx
  - 流式 SSE 简化保护：仅断路器，无重试，超时由 `Flux#timeout` 控制
- **优先级：** 🟡 高 — 业务能稳定运行的前提，但可在第一个 LLM 调用场景时同步实现

---

## 四、缓存

### 10. 缓存 Key 约定（`CacheConstants`）

- **解决什么问题：** `RedisConfig` 和 `RedisUtil` 已封装，但业务代码直接裸写 key 字符串会导致大量分散的魔术值，后期改命名规则代价极高。
- **实现位置：** `hify-common: com.hify.common.constant.CacheConstants`
- **要点：**
  - `SESSION_PREFIX = "hify:session:"`
  - `AGENT_CONFIG_PREFIX = "hify:agent:"`
  - `CHAT_HISTORY_PREFIX = "hify:chat:"`
  - `SSE_STATE_PREFIX = "hify:sse-state:"`
  - `MODEL_LIST_PREFIX = "hify:model:"`
- **优先级：** 🟢 中 — 写业务缓存逻辑前定义即可

### 11. 会话热数据缓存策略

- **解决什么问题：** data-model 指出会话热数据放 Redis、全量历史存 MySQL，这个策略直接决定 chat 模块的代码结构，需提前设计边界。
- **设计决策（不立即写代码）：**
  - 最近 N 条消息存 Redis List（`MAX_RECENT_MSGS = 50`）
  - 冷历史按需从 MySQL 加载
  - 活跃会话 TTL 30 分钟，滑动续期
  - 非活跃会话过期后只存 MySQL
  - SSE 流式过程中，中间状态存 Redis（如正在调用的工具名）
- **优先级：** 🟢 中 — 设计先行，实现随 chat 模块一起

---

## 五、可观测性

### 12. 调用日志基础组件（`LogRecord` + `LogService` + `LogMapper`）

- **解决什么问题：** data-model 将 `log` 表划在 hify-common，意味着所有模块的调用日志都通过 common 统一记录。目前实体、Service、Mapper 全缺，chat/agent 模块连日志写在哪里都不知道。
- **实现位置：** `hify-common: com.hify.common.log.LogRecord`, `LogService`, `LogMapper`
- **要点：**
  - `LogRecord` 实体：调用时间、Agent ID、用户 ID、session ID、输入摘要、输出摘要、工具链（JSON）、Token 总消耗、耗时(ms)、状态、traceId
  - `LogService.record(LogRecord)` 异步写入接口（`@Async`），由各业务模块注入使用
  - `LogMapper` 批量插入优化 + 游标分页查询
- **优先级：** 🟡 高 — 所有模块的日志出口

### 13. Actuator 健康检查扩展

- **解决什么问题：** Actuator 已暴露 health/info/metrics/env，但 health 端点只检查应用进程是否存活。MySQL/Redis/pgvector 挂了仍然返回 UP，部署时无法及时发现。
- **实现位置：** `hify-app: com.hify.health.*`
- **要点：**
  - 扩展 `HealthIndicator`：数据源连接、Redis 连接、pgvector 连接
  - Docker Compose `depends_on` 的 `condition: service_healthy` 能正确工作
  - dev profile 下跳过（不依赖外部服务）
- **优先级：** 🟢 中 — 对开发调试和部署都很实用

### 14. 请求链路追踪（`TraceFilter`）

- **解决什么问题：** 一个对话请求经过 ChatController → AgentService → LlmInvoker → 外部 LLM → 工具调用，路径跨越 4-5 层，出问题时日志无法串联。
- **实现位置：** `hify-common: com.hify.common.log.TraceFilter`
- **要点：**
  - 拦截所有 `/api/**` 请求，生成本次请求的 `traceId`（UUID 前 8 位，可读性优先）
  - 通过 SLF4J MDC 贯穿所有日志（`MDC.put("traceId", ...)`）
  - 配置 `logback-spring.xml`：日志格式输出 `%X{traceId}`
  - `traceId` 通过 SSE header 推给前端，方便用户反馈问题时定位
  - `LogRecord.traceId` 关联调用日志
- **优先级：** 🟡 高 — 排障的基石，越早做越好

### 15. Micrometer 业务指标埋点

- **解决什么问题：** Spring Boot Actuator 自带 JVM metrics，但业务指标（LLM 调用耗时 P50/P99、工具调用成功率、SSE 连接数）需要自定义埋点，后期接入 Prometheus + Grafana 时需要这些数据。
- **实现位置：** `hify-model`、`hify-chat` 中注入 `MeterRegistry`
- **要点：**
  - `llm.call.duration`（Timer，tag: provider/model）
  - `llm.call.error`（Counter，tag: provider/error_type）
  - `agent.iteration.count`（Counter）
  - `sse.connections.active`（Gauge）
  - `tool.call.success` / `tool.call.failure`（Counter，tag: tool_type）
- **优先级：** 🟢 中 — 早期埋点，后期接入 Prometheus 时零改动

---

## 优先级汇总

| 优先级 | 组件 | 模块 |
|---|---|---|
| 🔴 最高 | 1. DDL 建表脚本 | hify-app |
| 🔴 最高 | 2. JacksonConfig | hify-common |
| 🔴 最高 | 7. LlmThreadPoolConfig | hify-model |
| 🔴 最高 | 8. HttpClientConfig | hify-model |
| 🟡 高 | 3. PageUtils | hify-common |
| 🟡 高 | 4. JsonUtil | hify-common |
| 🟡 高 | 5. SseUtil | hify-common |
| 🟡 高 | 6. Constants | hify-common |
| 🟡 高 | 9. LlmInvoker + Resilience4j | hify-model |
| 🟡 高 | 12. LogRecord + LogService + LogMapper | hify-common |
| 🟡 高 | 14. TraceFilter | hify-common |
| 🟢 中 | 10. CacheConstants | hify-common |
| 🟢 中 | 11. 会话热数据缓存策略 | hify-chat |
| 🟢 中 | 13. Actuator 健康检查扩展 | hify-app |
| 🟢 中 | 15. Micrometer 业务指标 | 各业务模块 |

## 实现依赖链

```
DDL 建表 ──→ JacksonConfig
    │
    └──→ JsonUtil
    │
    LogRecord ──→ TraceFilter ──→ 日志可追踪
    │
    Constants ──→ CacheConstants ──→ RedisUtil 可规范使用
    │
    LlmThreadPoolConfig ──→ HttpClientConfig ──→ LlmInvoker ──→ 模型模块可跑
```

## 不做的事情

- **不做 Spring Security / Shiro 集成** — 当前定位是团队内部工具，不引入认证授权框架
- **不做 Flyway / Liquibase 数据库迁移** — 一人维护，SQL 脚本版本管理用 Git 即可
- **不做 OpenFeign / gRPC** — 单体应用，模块间直接通过 Service 接口调用
- **不做分布式链路追踪（如 Zipkin / Jaeger）** — 阶段 0 用 MDC traceId 足够，阶段 2 再考虑
- **不做配置中心（如 Nacos / Apollo）** — `.env` + `application.yml` 足够，后期有需要再拆分
