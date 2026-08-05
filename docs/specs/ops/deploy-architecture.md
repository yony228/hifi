# Hify 部署架构设计

**阶段：** 一期，50 人内部使用  
**部署方式：** Docker 镜像 + Kubernetes 编排  
**前提：** 模块化单体 → 一个 `hify-app` 镜像，不拆微服务

---

## 一、组件清单

```
┌─────────────────────────────────────────────────────────────────────┐
│                          Kubernetes Cluster                          │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                     Namespace: hify                           │  │
│  │                                                               │  │
│  │  ┌─────────────────────┐  ┌──────────────────────────────┐   │  │
│  │  │    Nginx Ingress    │  │         cert-manager         │   │  │
│  │  │  (TLS 终止 + 路由)   │  │    (Let's Encrypt 自动续签)   │   │  │
│  │  └────────┬────────────┘  └──────────────────────────────┘   │  │
│  │           │                                                   │  │
│  │     ┌─────┴──────────────┐                                    │  │
│  │     │                    │                                    │  │
│  │     ▼                    ▼                                    │  │
│  │  ┌──────────┐    ┌──────────────┐                            │  │
│  │  │hify-web  │    │  hify-app    │                            │  │
│  │  │(nginx)   │    │(Spring Boot) │                            │  │
│  │  │replicas:1│    │ replicas: 2  │                            │  │
│  │  └──────────┘    └──┬───┬───┬──┘                            │  │
│  │                     │   │   │                                 │  │
│  │          ┌──────────┘   │   └──────────┐                     │  │
│  │          ▼              ▼              ▼                     │  │
│  │  ┌──────────────┐ ┌──────────┐ ┌──────────────┐             │  │
│  │  │    MySQL     │ │  Redis   │ │ PostgreSQL   │             │  │
│  │  │  (业务数据)   │ │ (缓存/会话)│ │ (pgvector)  │             │  │
│  │  │  replicas:1  │ │replicas:1│ │ replicas: 1  │             │  │
│  │  │  StatefulSet │ │Deployment│ │ StatefulSet  │             │  │
│  │  │  10Gi PVC    │ │ 2Gi 内存  │ │ 20Gi PVC     │             │  │
│  │  └──────────────┘ └──────────┘ └──────────────┘             │  │
│  │                                                               │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                      │
│                        ┌──────────────┐                              │
│                        │  外部 LLM API │                              │
│                        │ (OpenAI/Claude│                              │
│                        │  Gemini/Ollama)│                              │
│                        └──────────────┘                              │
└─────────────────────────────────────────────────────────────────────┘
```

| 组件 | 类型 | 副本数 | 资源 | 说明 |
|---|---|---|---|---|
| **Nginx Ingress Controller** | K8s Ingress | 集群级 | 共享 | TLS 终止、域名路由、WebSocket/SSE 代理 |
| **cert-manager** | K8s 插件 | 集群级 | 共享 | Let's Encrypt 自动签发和续签 TLS 证书 |
| **hify-web** | Deployment | 1 | 128Mi / 256Mi | Nginx 镜像，挂载 Vue 静态文件 |
| **hify-app** | Deployment | 2 | 512Mi / 1Gi | Spring Boot JAR，JVM 堆 512m |
| **MySQL** | StatefulSet | 1 | 512Mi / 1Gi, 10Gi PVC | 业务数据：Agent 配置、会话、日志、模型配置 |
| **Redis** | Deployment | 1 | 256Mi / 512Mi | 会话热数据缓存、流式状态 |
| **PostgreSQL + pgvector** | StatefulSet | 1 | 256Mi / 512Mi, 20Gi PVC | 向量存储 + 文本片段 |

---

## 二、请求流转

### 2.1 页面访问（静态资源）

```
用户浏览器
    │ https://hify.internal.example.com
    ▼
Nginx Ingress (TLS 终止)
    │ path: /          → hify-web:80
    ▼
hify-web (Nginx 容器，serve Vue 静态文件)
    │ index.html / app.js / app.css
    ▼
用户浏览器 (加载 SPA)
```

### 2.2 API 调用（业务请求）

```
用户浏览器
    │ POST https://hify.internal.example.com/api/v1/chat/stream
    ▼
Nginx Ingress
    │ path: /api/*     → hify-app:8080
    │ SSE 需要配置超时
    ▼
hify-app (Pod A 或 B)
    │ Spring Boot 处理请求
    ├──→ MySQL  (Agent 配置、会话历史)
    ├──→ Redis  (会话缓存、流式状态)
    ├──→ PostgreSQL (RAG 向量检索)
    └──→ 外部 LLM API (模型推理，走 hify-app 的 LLM 线程池)
    │
    │ SSE text/event-stream (逐 token 推送)
    ▼
Nginx Ingress → 用户浏览器 (逐 token 展示)
```

### 2.3 数据库交互明细

```
hify-app
    │
    ├── MySQL (docker.io/mysql:8.4)
    │   ├── agent 表          ← Agent CRUD
    │   ├── workflow 表       ← Workflow CRUD
    │   ├── model_config 表   ← 模型配置 CRUD
    │   ├── knowledge 表      ← 知识库元信息
    │   ├── chat_session 表   ← 会话元信息
    │   ├── chat_message 表   ← 对话历史持久化
    │   ├── tool_config 表    ← 工具配置
    │   └── call_log 表       ← 调用日志 (high write)
    │
    ├── Redis (docker.io/redis:7-alpine)
    │   ├── session:{id}      ← 当前会话热数据 (TTL 30min)
    │   ├── sse:{streamId}    ← SSE 连接状态
    │   └── rate_limit:{ip}   ← 简单限流计数器
    │
    └── PostgreSQL + pgvector (pgvector/pgvector:pg17)
        └── knowledge_chunks 表 ← (id, knowledge_id, chunk_text, embedding)
            ├── chunk_text (TEXT)
            └── embedding (vector(1536)) ← OpenAI text-embedding-3-small
```

### 2.4 SSE 流式请求的特殊处理

SSE 是长连接（一次对话可能持续 30-120 秒），需要 Ingress 和 Nginx 配合：

```yaml
# Nginx Ingress 注解（针对 /api/v1/chat/stream 路径）
nginx.ingress.kubernetes.io/proxy-read-timeout: "3600"     # SSE 长连接 60 分钟
nginx.ingress.kubernetes.io/proxy-buffering: "off"          # 关闭缓冲，逐帧推送
nginx.ingress.kubernetes.io/proxy-connect-timeout: "30"     # 连接超时 30 秒
```

---

## 三、各组件职责与配置

### 3.1 Nginx Ingress

**职责：** TLS 终止 + 域名路由 + SSE 代理 + 基础限流。

```
域名: hify.internal.example.com

路由规则:
  /          → hify-web:80        (静态资源)
  /api/*     → hify-app:8080      (API 请求)
  /actuator  → hify-app:8080      (健康检查，可选)
```

**为什么不在 hify-app 里做 TLS？** 让应用容器只处理业务逻辑，TLS 证书管理交给 Ingress + cert-manager，应用容器不感知证书。这是 K8s 部署的标准实践。

### 3.2 hify-web

**职责：** 仅 serve Vue 3 编译后的静态文件。

**为什么不是 Nginx 反代动态请求？** hify-web 只做静态资源，不代理 `/api`。所有 `/api/*` 由 Ingress 直接路由到 hify-app。这样前端容器就是纯文件服务器，无任何动态逻辑。

```dockerfile
# hify-web Dockerfile
FROM nginx:1.27-alpine
COPY dist/ /usr/share/nginx/html/
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

```nginx
# nginx.conf — 静态文件 + SPA fallback + gzip
server {
    listen 80;

    # gzip_static：直接 serve Vite 构建时预生成的 .gz 文件，CPU 零开销
    gzip_static on;
    gzip_vary on;                  # 让 CDN/代理按 Accept-Encoding 分别缓存

    # 兜底：如果 .gz 文件不存在，on-the-fly 压缩
    gzip on;
    gzip_comp_level 6;
    gzip_min_length 1024;
    gzip_types text/plain text/css application/json application/javascript
               text/xml application/xml text/javascript image/svg+xml;

    location / {
        root /usr/share/nginx/html;
        try_files $uri $uri/ /index.html;  # Vue Router history mode
    }

    # 静态资源强缓存（文件名含 hash）
    location /assets/ {
        root /usr/share/nginx/html;
        expires 1y;
        add_header Cache-Control "public, immutable";
    }
}
```

**Vite 构建时预压缩（vite.config.ts）：**

```ts
// hify-web/vite.config.ts
import compression from 'vite-plugin-compression';

export default defineConfig({
  plugins: [
    vue(),
    compression({ algorithm: 'gzip', ext: '.gz' }),       // 生成 .gz 文件
  ],
  build: {
    rollupOptions: {
      output: {
        manualChunks: {                                    // 按依赖拆 chunk
          vue: ['vue', 'vue-router', 'pinia'],
          ui: ['element-plus'],
        },
      },
    },
  },
});
```

### 3.3 hify-app

**职责：** 所有业务逻辑。Spring Boot 单体 JAR，JVM 参数调优。

```dockerfile
# hify-app Dockerfile
FROM eclipse-temurin:21-jre-alpine
COPY target/hify-app-*.jar /app/hify-app.jar
EXPOSE 8080
ENTRYPOINT ["java", \
    "-Xms256m", "-Xmx512m", \
    "-XX:+UseG1GC", \
    "-XX:MaxGCPauseMillis=200", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "/app/hify-app.jar"]
```

**JVM 参数推算：**

| 参数 | 值 | 原因 |
|---|---|---|
| `-Xms256m` | 初始堆 256MB | 日常 50 人并发低，从 256m 起步 |
| `-Xmx512m` | 最大堆 512MB | SSE 10 个连接 + 对话上下文，512m 足够 |
| `-XX:+UseG1GC` | G1 GC | 低延迟，适合在线服务 |
| `-XX:MaxGCPauseMillis=200` | GC 暂停上限 | 200ms 用户无感 |
| **资源限制** | request 512Mi / limit 1Gi | 给堆外内存 + OS 留余量 |

**副本数：2。** 不是为了扛流量（50 人 QPS 3-5，1 个实例足够），而是为了**滚动更新不中断服务**。50 人规模不需要 HPA 自动扩缩容。

**健康检查：**

```yaml
livenessProbe:
  httpGet:
    path: /actuator/health
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 10
  periodSeconds: 5
```

### 3.4 MySQL

**职责：** 全部业务数据的唯一真相源。Agent 配置、会话、日志、模型配置。

**为什么是 StatefulSet + PVC 而不是云数据库？** 50 人内部使用，托管 MySQL 实例足够。`docker-compose up` 开发环境也是同一个 MySQL 镜像，保持一致。等用户量涨到几千时再考虑云 RDS。

```yaml
# 关键配置
image: mysql:8.4
env:
  MYSQL_DATABASE: hify
  MYSQL_ROOT_PASSWORD: <from-secret>
resources:
  requests: {memory: 512Mi, cpu: 250m}
  limits:   {memory: 1Gi, cpu: 1000m}
persistence:
  size: 10Gi
```

**为什么不做主从？** 50 人场景不需要。真要高可用时再加，MySQL StatefulSet 变主从不难。初期过度设计是浪费。

### 3.5 Redis

**职责：** 纯缓存 + SSE 状态。**不做持久化**（数据挂了从 MySQL 恢复）。

```yaml
image: redis:7-alpine
args: ["--maxmemory", "256mb", "--maxmemory-policy", "allkeys-lru"]
resources:
  requests: {memory: 256Mi, cpu: 100m}
  limits:   {memory: 512Mi, cpu: 500m}
```

| 配置 | 值 | 原因 |
|---|---|---|
| `maxmemory 256mb` | 内存上限 | 只存会话和 SSE 状态，256MB 很宽裕 |
| `allkeys-lru` | 淘汰策略 | 内存满时淘汰最少使用的 key |
| 不持久化 (RDB/AOF 关闭) | - | 数据可从 MySQL 重建，省 IO |

**Redis 挂了会怎样？** 会话缓存丢失 → 用户刷新页面后从 MySQL 恢复历史会话。SSE 状态丢失 → 当前进行中的对话中断，用户点"重试"。可接受。

### 3.6 PostgreSQL + pgvector

**职责：** 只存向量和文本片段，不做业务查询。

```yaml
image: pgvector/pgvector:pg17
resources:
  requests: {memory: 256Mi, cpu: 250m}
  limits:   {memory: 512Mi, cpu: 1000m}
persistence:
  size: 20Gi
```

**为什么 20Gi？** 假设每个 chunk 512 tokens，向量 1536 维，每个向量 ~6KB。20Gi 可存约 300 万个 chunk，足够几百个知识库。同一个 pgvector 实例可以跑多个数据库，未来拆微服务时向量检索独立部署。

**为什么不复用 MySQL？** pgvector 的向量索引（IVFFlat / HNSW）是 PostgreSQL 特有的。MySQL 做不了向量相似度检索。必须独立实例。

---

## 四、K8s 资源总览

### 4.1 资源预算

| 组件 | 副本 | CPU (req/lim) | 内存 (req/lim) | 存储 |
|---|---|---|---|---|
| hify-web | 1 | 50m / 200m | 128Mi / 256Mi | - |
| hify-app | 2 | 250m / 500m | 512Mi / 1Gi | - |
| MySQL | 1 | 250m / 1000m | 512Mi / 1Gi | 10Gi |
| Redis | 1 | 100m / 500m | 256Mi / 512Mi | - |
| PostgreSQL | 1 | 250m / 1000m | 256Mi / 512Mi | 20Gi |
| **合计** | **6 pods** | **900m / 3.2** | **1.6Gi / 3.3Gi** | **30Gi** |

单节点 4C8G 的机器就能跑。生产环境建议 8C16G，留余量给 K8s 系统组件和 LLM 调用期间的堆外内存。

### 4.2 网络隔离

所有后端组件（MySQL、Redis、PostgreSQL）只暴露 ClusterIP，不对外：

```yaml
# 只暴露 hify-web 和 hify-app 给外部
# 中间件只能集群内访问
mysql-service:    ClusterIP  ← 仅 hify-app 访问
redis-service:    ClusterIP  ← 仅 hify-app 访问
pgvector-service: ClusterIP  ← 仅 hify-app 访问
```

### 4.3 配置与外挂

| 配置类型 | K8s 资源 | 包含内容 |
|---|---|---|
| 环境变量 | ConfigMap | DB 连接串、Redis 地址、PG 地址、日志级别 |
| 敏感信息 | Secret | DB 密码、OpenAI/Claude API Key、JWT Secret |
| TLS 证书 | cert-manager Certificate | 自动管理，不手动创建 |

```yaml
# hify-app 注入示例
env:
  - name: SPRING_DATASOURCE_URL
    valueFrom: {configMapKeyRef: {name: hify-config, key: mysql-url}}
  - name: SPRING_DATASOURCE_PASSWORD
    valueFrom: {secretKeyRef: {name: hify-secret, key: mysql-password}}
  - name: SPRING_REDIS_HOST
    valueFrom: {configMapKeyRef: {name: hify-config, key: redis-host}}
```

---

## 五、关键运维考量

### 5.1 为什么是 2 副本 hify-app，不是 1？

**不是为了性能，是为了部署时不断流。**

```
滚动更新流程：
1. Pod A 收到 SIGTERM → 优雅关闭（等 SSE 连接自然结束，最多 30s）
2. 新 Pod C 启动 → readiness probe 通过 → 接入流量
3. Pod B 收到 SIGTERM → 同步骤 1
4. 新 Pod D 启动 → 完成

整个过程用户无感知。
```

1 副本的话，更新时 Pod 被杀 → 30 秒新 Pod 还没起来 → 用户 502。2 副本消除这个窗口。

### 5.2 为什么中间件不做高可用？

50 人不需要。MySQL 主从、Redis Sentinel、PostgreSQL 流复制——这些都是 500+ 人规模才考虑的事。现在做只会增加运维复杂度。

**如果 MySQL 挂了：** 重启 StatefulSet Pod，数据在 PVC 上不丢。恢复时间 = Pod 重启时间（~30s）。

**如果 Redis 挂了：** 会话缓存丢失，用户刷新页面即可。系统自动降级。

### 5.3 日志与监控

```
hify-app
    │ stdout (JSON 格式)
    ▼
K8s 节点 → 日志收集器 (Fluent Bit / Loki, 或直接 kubectl logs)
    │
    ▼
Spring Boot Actuator (/actuator/health, /actuator/metrics)
    │
    ▼
K8s liveness/readiness probe (每 5-10s 探测)
```

50 人阶段不需要 Prometheus + Grafana 全套。`kubectl logs` + Actuator 端点 + K8s 自带探活足够。

### 5.4 备份策略

| 数据 | 备份方式 | 频率 |
|---|---|---|
| MySQL | `mysqldump` cronjob → 挂载到 NAS 或 S3 兼容存储 | 每日凌晨 3 点 |
| PostgreSQL | `pg_dump` cronjob → NAS/S3 | 每日凌晨 3 点 |
| Redis | **不备份**（可从 MySQL 重建） | - |
| hify-app 日志 | 不需要备份 | - |

备份在 K8s 外执行（CronJob 或节点上的 crontab），不增加应用负载。

---

## 六、部署检查清单

部署前逐项确认：

```text
□ 所有镜像已构建并推送到镜像仓库
□ ConfigMap / Secret 已创建且值正确
□ Ingress TLS 证书由 cert-manager 自动签发
□ hify-app liveness/readiness endpoint 返回 200
□ MySQL / Redis / PostgreSQL Pod 已就绪
□ hify-app 能连上 MySQL、Redis、PostgreSQL
□ Ingress 路由规则生效（/ 到 web, /api 到 app）
□ SSE 流式调用不超时（proxy-read-timeout 3600s + proxy-buffering off）
□ 外部 LLM API Key 有效
□ 资源 limits 不低于预算表
□ 滚动更新：kill 一个 hify-app Pod 后用户请求不受影响
```

---

## 七、环境信息

### 7.1 环境总览

| 环境 | 用途 | 部署方式 | Spring Profile |
|---|---|---|---|
| **Dev** | 本地开发、快速验证 | IDE / `mvn spring-boot:run` | `dev` |
| **Test** | 功能测试、集成测试 | 待定 | `test` |
| **Staging** | 预发布验证 | 待定 | `staging` |
| **Production** | 正式环境 | K8s（见 §一～五） | `default` |

### 7.2 Dev（本地开发）

**目标：** 零外部依赖即可启动，验证 Bean 装配、模块依赖、组件扫描。

**启动方式：**
```bash
mvn spring-boot:run -pl hify-app -Dspring-boot.run.profiles=dev
# 或 IDE 中设置 VM options: -Dspring.profiles.active=dev
```

| 组件 | 策略 | 说明 |
|---|---|---|
| **MySQL** | H2 内存库 | `jdbc:h2:mem:hify_dev;MODE=MYSQL`，数据不持久化 |
| **Redis** | 跳过 | `hify.redis.enabled=false`，`RedisConfig` / `RedisUtil` 不装配 |
| **pgvector** | 排除自动装配 | `PgVectorStoreAutoConfiguration` 排除 |
| **LLM API** | 排除自动装配 | `OpenAiAutoConfiguration` 排除，无 API Key |
| **端口** | `8080` | |
| **H2 Console** | `http://localhost:8080/h2-console` | |
| **Actuator** | `http://localhost:8080/actuator/health` | |
| **日志级别** | `com.hify: DEBUG` | |

**需要 Redis 时：** 注释 `application-dev.yml` 中的 `hify.redis.enabled`（或改为 `true`），并启动本地 `redis-server`。

**需要 LLM 时：** 注释 `spring.autoconfigure.exclude` 中的 `OpenAiAutoConfiguration`，并设置环境变量 `OPENAI_API_KEY`。

**配置文件：** `hify-app/src/main/resources/application-dev.yml`

---

### 7.3 Test（测试环境）

| 组件 | 策略 | 说明 |
|---|---|---|
| **MySQL** | 待定 | |
| **Redis** | 待定 | |
| **pgvector** | 待定 | |
| **LLM API** | 待定 | |
| **端口** | 待定 | |
| **部署方式** | 待定 | |

**配置文件：** `application-test.yml`（待创建）

---

### 7.4 Staging（预发布环境）

| 组件 | 策略 | 说明 |
|---|---|---|
| **MySQL** | 待定 | |
| **Redis** | 待定 | |
| **pgvector** | 待定 | |
| **LLM API** | 待定 | |
| **端口** | 待定 | |
| **部署方式** | 待定 | |
| **副本数** | 待定 | |

**配置文件：** `application-staging.yml`（待创建）

---

### 7.5 Production（生产环境）

**部署方式：** K8s（详见 §一～五）。

| 组件 | 策略 | 说明 |
|---|---|---|
| **MySQL** | StatefulSet × 1 | `mysql:8.4`，10Gi PVC，见 §3.4 |
| **Redis** | Deployment × 1 | `redis:7-alpine`，256MB 上限，allkeys-lru，不持久化，见 §3.5 |
| **pgvector** | StatefulSet × 1 | `pgvector/pgvector:pg17`，20Gi PVC，见 §3.6 |
| **LLM API** | 外部 API | OpenAI-compatible 接口，API Key 存 K8s Secret |
| **hify-app** | Deployment × 2 | `eclipse-temurin:21-jre-alpine`，JVM 512m，见 §3.3 |
| **hify-web** | Deployment × 1 | `nginx:1.27-alpine`，serve Vue 静态文件，见 §3.2 |
| **端口** | `8080`（app），`80`（web） | |
| **副本数** | app × 2（滚动更新不中断），web × 1 | |
| **资源总预算** | 6 pods / 3.2 CPU / 3.3Gi 内存 / 30Gi 存储 | 见 §4.1 |
| **TLS** | cert-manager + Let's Encrypt | 自动签发续签 |
| **域名** | 待定 | |
| **配置文件** | `application.yml`（default profile） | |

**环境变量（K8s ConfigMap / Secret）：**

| 变量 | 来源 | 示例值 |
|---|---|---|
| `MYSQL_URL` | ConfigMap | `jdbc:mysql://mysql-service:3306/hify?...` |
| `MYSQL_USER` | ConfigMap | `hify` |
| `MYSQL_PASSWORD` | Secret | `***` |
| `REDIS_HOST` | ConfigMap | `redis-service` |
| `REDIS_PORT` | ConfigMap | `6379` |
| `PGVECTOR_HOST` | ConfigMap | `pgvector-service` |
| `PGVECTOR_PORT` | ConfigMap | `5432` |
| `PGVECTOR_DB` | ConfigMap | `hify_vectors` |
| `PGVECTOR_USER` | ConfigMap | `postgres` |
| `PGVECTOR_PASSWORD` | Secret | `***` |
| `OPENAI_API_KEY` | Secret | `***` |
| `JWT_SECRET` | Secret | `***` |
| `SERVER_PORT` | ConfigMap | `8080` |
