# Hify 扩展路径：50 → 几千人

**核心原则：每一步只改必须改的，不改不该改的。触发条件用可观测指标，不用直觉。**

---

## 总览

```
50 人                200 人               1000 人              5000 人
   │                    │                    │                    │
   ▼                    ▼                    ▼                    ▼
┌──────────┐      ┌──────────┐        ┌──────────┐        ┌──────────┐
│ 阶段 0   │ ───→ │ 阶段 1   │ ─────→ │ 阶段 2   │ ─────→ │ 阶段 3   │
│ 基础部署  │      │ 数据拆分  │        │ 异步解耦  │        │ 服务提取  │
│          │      │          │        │          │        │          │
│ 单体     │      │ 单体     │        │ 单体+    │        │ 多服务   │
│ 2 副本   │      │ 多副本   │        │ 读写分离  │        │ 消息驱动  │
│ 单 DB    │      │ 读副本   │        │ 异步日志  │        │ 独立向量  │
│          │      │          │        │ MQ 引入   │        │ 独立日志  │
└──────────┘      └──────────┘        └──────────┘        └──────────┘
```

| 阶段 | 用户数 | app 副本 | MySQL | Redis | PostgreSQL | 新增组件 |
|---|---|---|---|---|---|---|
| 0 | 50 | 2 | 1 | 1 | 1 | - |
| 1 | 50-200 | 3-4 | 1 主 + 1 读 | 1 | 1 | - |
| 2 | 200-1000 | 5-8 (HPA) | 1 主 + 2 读 | 1 (哨兵) | 1 | - |
| 3 | 1000-5000 | 8+ (HPA) | 1 主 + N 读 | 哨兵集群 | 独立实例 | RabbitMQ + 日志服务 + 向量服务 |

---

## 阶段 0：基础部署（当前）

**用户数：** ≤50  
**目标：** 能跑，部署简单，故障恢复一条命令。

### 架构

```
Ingress → hify-app(×2) → MySQL + Redis + PostgreSQL
```

### 配置

| 项目 | 值 | 原因 |
|---|---|---|
| app 副本 | 2 | 滚动更新不断流，不是扛流量 |
| JVM 堆 | 256-512MB | 50 人 SSE 峰值 ~10 连接 |
| MySQL | 单实例 10Gi | 业务数据总量 <1Gi |
| Redis | 单实例 256MB | 只存会话，不持久化 |
| PostgreSQL | 单实例 20Gi | 向量 + 文本片段 |

### 监控基线

```
关键指标（当前值）:
  - QPS:                   3-5
  - SSE 并发连接:          5-10
  - MySQL 慢查询 (>100ms): 0
  - hify-app GC pause:     <50ms
  - 平均 API 响应:         <200ms (不含 LLM)
  - P99 API 响应:          <2s
```

---

## 阶段 1：数据拆分（50 → 200 人）

### 触发条件

以下**任一**指标持续超过阈值 3 天以上：

| 指标 | 阈值 | 怎么观测 | 含义 |
|---|---|---|---|
| MySQL CPU 持续使用率 | >60% | `SHOW STATUS` + K8s metrics | 读压力挤压写能力 |
| MySQL 慢查询 (>500ms) | 日均 >10 条 | 慢查询日志 | 表数据量增大，索引不够用 |
| hify-app P99 延迟 | >5s | Actuator `/metrics` | 数据库等待时间变长 |
| SSE 并发连接峰值 | >30 | Redis `sse:*` key 计数 | 长连接开始消耗显著线程 |

### 改什么

**1. MySQL 读写分离**

```
hify-app
    │
    ├── 写 (INSERT/UPDATE/DELETE) → MySQL 主库
    │     └── agent, workflow, model_config, knowledge,
    │         chat_session, chat_message, tool_config, call_log
    │
    └── 读 (SELECT) → MySQL 读副本
          └── 同上所有表
```

**实现方式：** 不引入 ShardingSphere 等中间件，Spring Boot 的 `RoutingDataSource` + 自定义 `@ReadOnly` 注解足够：

```java
// 简单版读写分离 — 一个人能维护的程度
@Component
public class RoutingDataSource extends AbstractRoutingDataSource {
    @Override
    protected Object determineCurrentLookupKey() {
        return TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                ? "read" : "write";
    }
}
```

配合 Spring 的 `@Transactional(readOnly = true)`——代码组织规范里 `getById()` / `listAll()` 早就要求不加 `@Transactional` 了，加一个 `readOnly = true` 即可自动走读库：

```java
@Transactional(readOnly = true)   // ← 自动路由到读副本
public AgentResponse getById(Long id) { ... }

@Transactional                    // ← 自动路由到主库
public AgentResponse create(CreateAgentRequest req) { ... }
```

**K8s 变更：** MySQL StatefulSet 多一个读副本 Pod，PVC 独立（或基于主库备份初始化）。

**2. hify-app 水平扩容**

```
2 副本 → 3-4 副本
```

200 人 SSE 峰值 ~40 连接，4 副本每个扛 10 连接，Tomcat 默认 200 线程绰绰有余。**不做 HPA**——手动设副本数，200 人规模不需要自动伸缩的复杂度。

**3. Redis 加 maxmemory 上限**

```
maxmemory 256mb → maxmemory 512mb
```

200 人活跃会话增多，给缓存多一点空间。

### 不改什么

| 不改 | 原因 |
|---|---|
| **不拆服务** | 仍然是模块化单体。200 人拆分微服务是过度设计 |
| **不加消息队列** | 同步调用链路仍可接受 |
| **不做 HPA** | 手动副本数 ≈ 成本最低的高可用 |
| **不换 pgvector** | 向量总量仍在几十万级别，单实例 pgvector 性能足够 |
| **不加 Redis 哨兵** | 挂了丢缓存可接受，重启恢复 |

### 阶段 1 验收标准

- 读写分离后主库 CPU <40%
- 所有读接口路由到了读副本（审计日志验证）
- 4 副本下 SSE 连接分布均匀
- P99 延迟回到 <3s

---

## 阶段 2：异步解耦（200 → 1000 人）

### 触发条件

| 指标 | 阈值 | 含义 |
|---|---|---|
| MySQL 主库写入 TPS 持续 | >500/s | `call_log` 的高频写入开始挤压业务写入 |
| `call_log` 表行数 | >1000 万 | 单表过大，查询变慢 |
| SSE 并发连接 | >100 | Tomcat 线程模型开始吃力 |
| 知识库总量 | >100 个 | 向量检索从毫秒级进入百毫秒级 |
| LLM 调用 QPS | >5 | API 配额/成本开始需要精细化管理 |

### 改什么

**1. 日志写入异步化**

`call_log` 是系统中写入频率最高的表（每次 LLM 调用写一条），和业务写入（Agent 配置、会话）共用主库产生竞争。

```
Agent/Workflow 执行完
    │
    ├── 业务数据 (Agent、Session、Message) → MySQL 同步写入（必须可靠）
    │
    └── 调用日志 → RabbitMQ → 消费者批量写入 MySQL
                        │
                        └── 消费者使用独立连接池，不占用业务连接
```

**实现方式：**

```java
// 改造前（同步）
logService.write(LogRecord.create(...));  // 直接 insert，阻塞 LLM 返回

// 改造后（异步）
rabbitTemplate.convertAndSend("hify.log", logRecord);  // 发消息，不阻塞
```

```java
// 消费者 — 批量写日志，减少 MySQL IO
@Component
public class LogConsumer {
    @RabbitListener(queues = "hify.log.queue")
    public void consume(List<LogRecord> batch) {      // 批量消费
        logMapper.batchInsert(batch);
    }
}
```

**代价：** 日志从"实时可见"变为"延迟 1-2 秒可见"。日志查看页加一个"刷新"按钮即可。

**2. call_log 表分区**

```sql
-- 按月分区，查询只扫热分区
ALTER TABLE call_log
PARTITION BY RANGE (TO_DAYS(created_at)) (
    PARTITION p202601 VALUES LESS THAN (TO_DAYS('2026-02-01')),
    PARTITION p202602 VALUES LESS THAN (TO_DAYS('2026-03-01')),
    ...
);
```

**3. SSE 连接优化**

Tomcat 200 线程，100 个长连接占一半。切换到 Spring WebFlux 只改 chat 模块的 SSE 推送层：

```
之前: @RestController + SseEmitter (Tomcat 线程每连接一个)
之后: RouterFunction + Flux<ServerSentEvent> (Netty 事件循环, 线程数=CPU核数)
```

**只改 chat 模块的 Controller，不改其他模块。** Spring Boot 同时支持 MVC 和 WebFlux，不用全量迁移。

```java
// 仅 chat 模块的流式端点改用 WebFlux
@Configuration
public class ChatRouter {
    @Bean
    public RouterFunction<ServerSentEvent<String>> sseRoute(ChatHandler handler) {
        return RouterFunctions
                .route(GET("/api/v1/chat/stream"), handler::stream);
    }
}
```

**4. hify-app 引入 HPA**

```
1000 人、QPS 15-25、SSE 峰值 ~200 连接
→ HPA: min=5, max=15, target=70% CPU
→ 业务低峰自动缩到 5 副本，高峰自动扩到 15
```

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
spec:
  minReplicas: 5
  maxReplicas: 15
  metrics:
    - type: Resource
      resource:
        name: cpu
        target: {type: Utilization, averageUtilization: 70}
```

**5. 引入 RabbitMQ**

| 用途 | Exchange | 路由 Key |
|---|---|---|
| 日志异步写入 | `hify.log` | `call_log` |
| SSE 推送（可选） | `hify.chat` | `stream.{streamId}` |

### 不改什么

| 不改 | 原因 |
|---|---|
| **不拆 agent/workflow/chat** | 核心业务逻辑仍在一体。只拆了日志写入路径（异步），不是拆服务 |
| **不换 pgvector** | 100 个知识库 ≈ 百万级向量，pgvector HNSW 仍然毫秒级 |
| **不做 MySQL 分库分表** | 除了 call_log，其他表数据量远未到分库分表级别 |
| **不引入 Redis Cluster** | 哨兵模式足够。单实例 512MB-1GB 能撑 1000 人会话 |

### 阶段 2 验收标准

- 日志写入不阻塞 LLM 返回（从 LLM response 结束到日志入库：异步）
- call_log 按月分区查询 <100ms
- SSE 100 并发下 hify-app CPU <50%
- RabbitMQ 积压 <1000 条

---

## 阶段 3：服务提取（1000 → 5000+ 人）

### 触发条件

| 指标 | 阈值 | 含义 |
|---|---|---|
| MySQL 主库写入 TPS | >2000/s | 单实例物理上限，读写分离已不够 |
| pgvector 查询 P99 | >500ms | 向量量级进入千万级 |
| 日志表日写入量 | >100 万条 | call_log 存储和查询都成瓶颈 |
| hify-app 单 Pod 内存 | >1.5GB | 应用本身膨胀，需要拆分降低单服务复杂度 |
| **团队人数** | **>3 人** | 单体被多个人同时改，冲突增多 |

> 阶段 3 最重要的触发条件是**团队扩到多个人**。一个人维护时模块化单体足够；多个人时，独立服务 ≈ 独立发布节奏。

### 改什么

**1. 日志服务独立部署**

`call_log` 拆成独立服务——不是拆业务模块，只是把 `hify-common/log/` 提升为 `hify-log-service`。

```
之前: hify-app → LogService → MySQL (call_log 表)
      hify-app → LogService → LogController (查询)

之后: hify-app → RabbitMQ → hify-log-service → MySQL (call_log 表独占)
      管理控制台 → hify-log-service (查询 API)
      管理控制台 → hify-app (业务 API)
```

```
┌──────────┐   日志写入    ┌──────────┐  批量写入   ┌──────────┐
│ hify-app │ ───→ MQ ───→ │ log-svc  │ ────────→ │  MySQL   │
└──────────┘              └──────────┘            │(call_log)│
                                │                  └──────────┘
       ┌────────────────────────┘
       │ 日志查询 API
       ▼
┌──────────────┐
│  管理控制台    │
└──────────────┘
```

**2. 向量检索独立部署**

pgvector 从共享 PostgreSQL 实例中拆出来，独立部署。

```
之前: hify-app → PostgreSQL(pgvector) [业务DB + 向量DB 混在一起]

之后: hify-app → hify-vector-service → PostgreSQL(pgvector) [独立实例，只做向量]
```

**独立部署 pgvector 三种方式：**

| 方式 | 适用规模 | 改动量 |
|---|---|---|
| 方案 A: pgvector 独立实例 | 千万级向量 | 小（改配置，不改代码） |
| 方案 B: pgvector + PgBouncer 连接池 | 亿级向量 | 中（加连接池） |
| 方案 C: 迁移到 Milvus / Qdrant | 亿级以上 | 大（改 embedding 检索代码） |

5000 人、几百个知识库、千万级向量——**方案 A 足够**。

```java
// hify-vector-service：一个轻量 Spring Boot 服务，只有两个 API
@RestController
public class VectorController {

    @PostMapping("/api/v1/vector/search")
    public List<ChunkResponse> search(@RequestBody SearchRequest req) {
        // 1. 调 embedding 模型生成查询向量
        // 2. pgvector 相似度检索
        // 3. 返回 top-K 文本片段
    }

    @PostMapping("/api/v1/vector/ingest")
    public void ingest(@RequestBody IngestRequest req) {
        // 批量写入 embedding
    }
}
```

**3. 核心服务保留模块化单体**

`hify-app` 继续承载 agent + workflow + chat + model + tool + knowledge（元信息）。日志和向量出去后，`hify-app` 的核心关注点更纯粹：

```
hify-app (核心单体)
    ├── agent         ← ReAct Loop
    ├── workflow      ← JSON 编排
    ├── chat           ← 会话 + SSE
    ├── model          ← 模型配置
    ├── tool           ← 工具注册
    ├── knowledge      ← 知识库元信息（不含向量检索）
    └── common         ← 基础能力（不含 call_log）

hify-log-service (独立服务)
    └── call_log 写入 + 查询

hify-vector-service (独立服务)
    └── embedding + 向量检索
```

**为什么只拆日志和向量，不拆其他？**

```
拆日志：因为它是全系统最高写入量的表，和业务写入有资源竞争。且完全是单向依赖（各模块 → 日志），
        没有循环依赖风险，拆出来零业务侵入。

拆向量：因为嵌入和检索的计算特征（GPU/高内存）和业务逻辑（轻 CPU）完全不同。
        且 pgvector 独立部署有利于向量索引优化。

不拆 agent/workflow/chat：这三个是业务核心，调用链是 chat → workflow → agent → model/tool/knowledge。
        拆任何一个都会引入分布式事务和 RPC 超时链，复杂度 > 收益。
        1000-5000 人的 QPS (25-100) 下，单体 + 合理副本数完全能扛。
```

**4. 引入 API 网关**

三个服务（hify-app, hify-log-service, hify-vector-service）对外暴露 API，管理控制台需要聚合查询。在 Ingress 之前加一层边缘路由即可——不需要独立网关服务：

```
之前:
Ingress → hify-app (所有 API)

之后:
Ingress
    ├── /api/v1/agent/**          → hify-app
    ├── /api/v1/workflow/**       → hify-app
    ├── /api/v1/chat/**           → hify-app (SSE 流式)
    ├── /api/v1/model/**          → hify-app
    ├── /api/v1/tool/**           → hify-app
    ├── /api/v1/knowledge/**      → hify-app (元信息)
    ├── /api/v1/vector/**         → hify-vector-service
    └── /api/v1/log/**            → hify-log-service
```

**为什么不用 Spring Cloud Gateway？** 5000 人规模，Ingress 级别的路由足够。独立网关服务 = 多一个需要监控和调优的组件，不值得。

**5. 前端 BFF 层（可选）**

如果管理控制台页面需要跨服务聚合数据（如"日志详情页同时显示 Agent 名称和日志内容"），加一个轻量 BFF：

```
管理控制台 → BFF (hify-app 内的 /api/v1/bff/**)
                ├── 调 hify-app Agent API → 取 Agent 名称
                └── 调 hify-log-service → 取日志内容
                → 聚合后返回
```

BFF 不单独部署，作为 `hify-app` 的一个额外 Controller，避免前端的 API 调用从一个变成多个。

### 不改什么

| 不改 | 原因 |
|---|---|
| **agent/workflow/chat 不拆** | 核心业务逻辑的调用链是 DAG，拆了引入分布式事务 |
| **MySQL 不分库分表** | 除了 call_log 已拆走，其余表数据量可控 |
| **不加 Redis Cluster** | 哨兵 + 合理 maxmemory 足够 |
| **不引入服务网格 (Istio/Linkerd)** | 3 个服务不需要 sidecar 的复杂度 |
| **不做跨区域部署** | 内部系统，单集群足够 |
| **不引入配置中心 (Nacos/Apollo)** | ConfigMap + Secret 仍然够用 |

---

## 四、快速判断矩阵

在哪个阶段，遇到什么问题，按这个矩阵选方案：

| 症状 | 原因 | 方案 | 所在阶段 |
|---|---|---|---|
| API 整体变慢，MySQL CPU 高 | 读请求挤占写能力 | MySQL 读副本 + 读写分离 | 阶段 1 |
| 用户多了 Pod 重启频繁 OOM | 单 Pod 内存不够 | 垂直扩容 JVM 堆 + 水平扩容副本 | 阶段 1 |
| LLM 返回后页面还要转圈才出日志 | call_log 同步写入阻塞 | 日志异步化 (MQ) | 阶段 2 |
| 知识库检索从即搜即出变等 1-2 秒 | 向量量级增大 | pgvector HNSW 索引优化 → 独立部署 | 阶段 2-3 |
| SSE 断连、Pod CPU 高但请求量没涨 | Tomcat 线程被长连接耗尽 | chat 模块换 WebFlux | 阶段 2 |
| 多人同时改代码频繁冲突 | 单体耦合 | 按月拆出独立服务（从日志/向量开始） | 阶段 3 |
| 团队 4+ 人，发布互相阻塞 | 单体发布节奏 | 独立服务 = 独立发布 | 阶段 3 |

## 五、每一步的成本

| 阶段 | 新增组件 | 新增 Pod 数 | 新增月费 (8C16G×1) | 改造工时 |
|---|---|---|---|---|
| 0 → 1 | MySQL 读副本 | +1 | ~零（同一节点） | 2-3 人天 |
| 1 → 2 | RabbitMQ, 更多 app 副本 | +3-5 | ~零 | 1-2 人周 |
| 2 → 3 | log-svc, vector-svc | +2-4 | 可能需要加节点 | 2-4 人周 |

**对于"一个人开发维护"的现实：** 阶段 1 和阶段 2 的改造都在单人可控范围内。阶段 3 的拆分假设团队已扩张到 3+ 人，分摊到多人。每个阶段间隔至少 3-6 个月——是渐进演化，不是一次性重写。
