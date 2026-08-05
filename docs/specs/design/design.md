# Hify 工程设计文档

## 一、工程架构

### 1.1 总体结构

```
hify/
├── hify-app/                    ← 启动模块：Spring Boot 入口 + 全局装配
├── hify-agent/                  ← Agent Runtime：ReAct Loop / Function Call 策略
├── hify-workflow/               ← Workflow 编排引擎：JSON 解析 + 节点执行
├── hify-chat/                   ← 对话引擎：会话管理 + 消息路由 + SSE 流式
├── hify-knowledge/              ← 知识库 RAG：分段 + 向量化 + 检索
├── hify-tool/                   ← 工具系统：搜索 / 代码沙箱 / HTTP / MCP
├── hify-model/                  ← 多模型管理：LLM + Embedding 配置
├── hify-common/                 ← 共享模块：工具 / 异常 / 常量 / 日志（纯基础能力）
├── hify-web/                    ← Vue 3 + Vite 前端 SPA
├── deploy/                      ← Dockerfile + nginx.conf + docker-compose
├── docs/
└── pom.xml                      ← 父 POM（dependencyManagement）
```

**启动模块 `hify-app` 显式声明对全部业务模块的依赖，负责全局 Spring 装配。** 这是"装配车间"模式——依赖列表本身就是系统模块清单，一目了然。

### 1.2 模块职责与依赖方向

#### 1.2.1 依赖全景图

```
┌─────────────────────────────────────────────────────────────┐
│                         hify-web                            │
│                       (Vue 3 SPA)                           │
└──────────────────────────┬──────────────────────────────────┘
                           │ HTTP 调用
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                         hify-app                            │
│                    (Spring Boot 启动 + 装配)                  │
│             显式依赖下方全部模块，保证 classpath 完整           │
└───┬──────────┬──────────┬──────────┬──────────┬─────────────┘
    │          │          │          │          │
    ▼          ▼          ▼          ▼          ▼
┌───────┐ ┌───────┐ ┌────────┐ ┌────────┐ ┌──────────────┐
│hify-  │ │hify-  │ │hify-   │ │hify-   │ │hify-common   │
│model  │ │tool   │ │knowledge│ │chat    │ │┌────────────┐ │
│       │ │       │ │        │ │        │ ││工具/异常/常量│ │
│LLM配置│ │工具注册│ │分段    │ │会话管理│ ││日志基础能力  │ │
│Embed  │ │MCP发现│ │向量化  │ │消息路由│ │└────────────┘ │
│配置   │ │沙箱执行│ │检索召回│ │SSE推送 │ └───────┬───────┘
└──┬────┘ └──┬────┘ └───┬────┘ └──┬──┬──┘         ▲
   │         │          │        │  │            │
   │         │          │        │  │            │ 所有模块
   │         │          │        │  │            │ 都依赖 common
   │         │          │    ┌───┘  └────┐       │
   │         │          │    │           │       │
   │         │          │    ▼           ▼       │
   │         │          │  ┌────────┐ ┌──────────┐
   │         │          │  │hify-   │ │hify-     │
   │         │          │  │agent   │ │workflow  │
   │         │          │  │        │ │          │
   │         │          │  │ReAct   │ │JSON解析   │
   │         │          └──┤FuncCall│ │节点编排   │
   │         │             │调模型  │ │┌───────┐  │
   │         └─────────────┤调工具  │ ││LlmNode│  │
   │                       │注知识库│ ││Agent  │──┤
   └───────────────────────┤        │ ││Node   │  │
                           └────────┘ │└───────┘  │
                                      └────────────┘
                                      workflow → agent
                                      (AgentNode 调 AgentService)
```

#### 1.2.2 依赖矩阵

| 模块 ↓ 依赖 → | common | model | tool | knowledge | agent | workflow |
|---|---|---|---|---|---|---|
| **common** | - | - | - | - | - | - |
| **model** | ✅ | - | - | - | - | - |
| **tool** | ✅ | - | - | - | - | - |
| **knowledge** | ✅ | ✅ | - | - | - | - |
| **agent** | ✅ | ✅ | ✅ | ✅ | - | - |
| **workflow** | ✅ | ✅ | - | - | ✅ | - |
| **chat** | ✅ | - | - | - | ✅ | ✅ |
| **app** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |

#### 1.2.3 每条依赖的理由

| 依赖链 | 理由 |
|---|---|
| **所有模块 → common** | 工具类、异常、日志等基础能力，所有模块都需要 |
| **knowledge → model** | 分段后需调用 Embedding 接口做向量化，依赖模型管理 |
| **agent → model** | ReAct 循环中需调用 LLM，通过 model 模块获取模型实例 |
| **agent → tool** | 运行时从 tool registry 获取工具并调用 |
| **agent → knowledge** | 对话时检索知识库，注入上下文 |
| **workflow → model** | LlmNode 直接调用 LLM，需通过 model 模块 |
| **workflow → agent** | AgentNode 在运行时调用 AgentService 执行子 Agent |
| **chat → agent** | 用户选择 Agent 时，路由到 AgentService |
| **chat → workflow** | 用户选择 Workflow 时，路由到 WorkflowService |

#### 1.2.4 关键澄清

**hify-app 显式依赖全部模块，而不是只依赖 chat。**

原因：系统中有两类 Controller——**管理端 API** 和 **运行时 API**。

| 类型 | 示例 | 路由方式 |
|---|---|---|
| 管理端 API | `POST /api/v1/agent/save` | 直接打到 `AgentController`，不走 ChatService |
| 运行时 API | `POST /api/v1/chat/stream` | 走 `ChatService` → Agent / Workflow |

各模块的管理端 Controller（Agent 配置页、知识库上传页、工具注册页等）由 Spring 直接暴露，不经过 chat 模块路由。因此 hify-app 必须显式依赖所有暴露 Controller 的模块——如果只依赖 chat，仅靠 Maven 传递依赖撑起 classpath，一旦 agent 不再依赖 tool（例如工具改为由 workflow 注入），tool 模块就静默丢失，ToolController 运行时 404。

**正确做法：hify-app 的 pom.xml 列出全部模块依赖，作为系统模块清单。** 多写几行 XML 换确定性。

**workflow 的 HttpNode 不依赖 tool 模块。** HttpNode 是几行 `RestTemplate`，直接发 HTTP 请求；tool 模块的 `HttpCustomTool` 是给 Agent 用的，走 Function Calling。两个场景，两套实现。

**chat 不直接依赖 model / tool / knowledge。** Chat 只做会话管理和路由，模型调用等细节由下层模块（agent / workflow）封装。

### 1.3 模块内部包结构

每个业务模块内部采用**扁平领域分包**（方案 B），不做 DDD 分层子包：

```
hify-agent/
└── src/main/java/com/hify/agent/
    ├── AgentController.java       ← REST 接口
    ├── AgentService.java          ← 业务逻辑
    ├── AgentMapper.java           ← MyBatis 数据访问
    ├── Agent.java                 ← Entity
    ├── AgentConfigDTO.java        ← DTO
    ├── strategy/
    │   ├── ReactLoop.java
    │   └── FunctionCallStrategy.java
    └── ToolInvoker.java
```

模块内 Controller / Service / Mapper / Entity 直接放在同一包下，**不再按 `api/domain/infra` 划分第二层子包**。当单一模块膨胀到 15+ 文件时，再局部引入 DDD 分层。

`hify-common` 按功能分子包：

```
hify-common/
└── src/main/java/com/hify/common/
    ├── config/                    ← Spring 全局配置
    │   ├── JacksonConfig.java
    │   ├── RedisConfig.java
    │   └── WebMvcConfig.java
    ├── exception/                 ← 异常类 + 全局处理器
    │   ├── BizException.java
    │   ├── ErrorCode.java
    │   └── GlobalExceptionHandler.java
    ├── log/                       ← 调用日志（基础能力，非业务）
    │   ├── LogService.java
    │   ├── LogMapper.java
    │   └── LogRecord.java
    ├── constant/
    │   └── Constants.java
    └── util/
        ├── JsonUtil.java
        └── SseUtil.java
```

### 1.4 前端结构

```
hify-web/
└── src/
    ├── api/                       ← 按后端模块拆分接口调用
    │   ├── agent.ts
    │   ├── workflow.ts
    │   ├── chat.ts
    │   ├── knowledge.ts
    │   ├── tool.ts
    │   ├── model.ts
    │   └── log.ts
    ├── views/                     ← 按管理控制台页面拆分
    │   ├── chat/                  ← Chat 对话页
    │   ├── agent/                 ← Agent 配置页
    │   ├── workflow/              ← Workflow 配置页
    │   ├── knowledge/             ← 知识库管理页
    │   ├── tool/                  ← 工具配置页
    │   ├── model/                 ← 模型管理页
    │   ├── log/                   ← 日志查看页
    │   └── settings/              ← 系统设置页
    ├── components/                ← 共享组件
    ├── stores/                    ← Pinia 状态
    ├── router/
    └── utils/
```

前端 `api/` 和 `views/` 按后端模块一一对应，减少前后端上下文切换成本。

### 1.5 前端技术栈

| 层面 | 选型 | 说明 |
|---|---|---|
| 框架 | Vue 3 | Composition API + `<script setup>` |
| 构建工具 | Vite | 开发 HMR，生产 Rollup |
| 语言 | TypeScript | 严格模式 |
| 状态管理 | Pinia | 按模块拆分 store |
| 路由 | Vue Router 4 | History 模式 |
| UI 组件库 | Element Plus | 管理后台生态最成熟 |
| 图标 | @element-plus/icons-vue | 与 Element Plus 配套 |
| CSS 方案 | Scoped CSS + 全局变量 | 不引入 Tailwind 等 CSS 框架 |
| HTTP 客户端 | Axios | 统一拦截器 + 超时 |
| 代码规范 | ESLint + Prettier | Vue + TS preset |
| 测试 | 先不写 | 后期补 |

**选型原则：** 管理后台样式量少（Element Plus 覆盖大部分 UI），减少非必要依赖。HTTP 拦截用 Axios 是因为内置超时和标准拦截器 API，比手写 fetch 封装代码更少。

---

## 二、工程规范

### 2.1 模块依赖规范

1. **单向依赖。** `hify-chat → hify-workflow → hify-agent → hify-common`。Maven 多模块结构在编译期强制约束，禁止反向依赖。
2. **模块间只通过 Service 接口通信。** 禁止跨模块直接注入 Mapper。例如 `hify-workflow` 需要 Agent 数据，注入 `AgentService`，不得注入 `AgentMapper`。
3. **禁止跨模块的 Entity 引用。** 每个模块各有自己的 Entity 和 Mapper。即使两个模块操作同一张数据库表，各自定义查询，不共享 Mapper。

### 2.2 common 模块纪律

`hify-common` 只允许包含**纯基础能力**，不含任何业务逻辑：

- 全局 Spring 配置（Jackson、Redis、WebMvc 等）
- 异常类（BizException、ErrorCode）
- 全局异常处理器（GlobalExceptionHandler）
- 调用日志（LogService、LogMapper、LogRecord）—— 日志是横切关注点，不是业务模块
- 纯工具类（JsonUtil、SseUtil）
- 常量定义（Constants）

**严禁放入：** 业务 Entity、业务 Mapper、业务 Service、业务 DTO。一旦发现 common 里出现业务对象，立即评估是否该抽出一个新模块。

### 2.3 命名规范

| 类型 | 规范 | 示例 |
|---|---|---|
| Maven 模块 | `hify-{module}` | `hify-agent` |
| 基础包名 | `com.hify.{module}` | `com.hify.agent` |
| Controller | `{Module}Controller` | `AgentController` |
| Service | `{Module}Service` | `AgentService` |
| Mapper | `{Module}Mapper` | `AgentMapper` |
| Entity | 业务名词 | `Agent`、`KnowledgeBase` |
| DTO | `{Entity}DTO` 或 `{Action}{Entity}Request/Response` | `AgentConfigDTO`、`CreateAgentRequest` |
| 前端 API 文件 | `{module}.ts` | `agent.ts` |
| 前端视图目录 | `{module}/` | `views/agent/` |

### 2.4 REST API 规范

- URL 前缀：`/api/v1/{module}/{resource}`
- 示例：`GET /api/v1/agent/list`、`POST /api/v1/chat/sessions`
- 请求/响应统一为 JSON
- 统一响应格式：

```json
{
  "code": 0,
  "message": "ok",
  "data": { ... }
}
```

- 流式接口（SSE）路径：`/api/v1/chat/stream`，返回 `text/event-stream`

### 2.5 数据库访问规范

- 业务数据库（MySQL）：每模块独立 Mapper，禁止跨模块注入
- 向量存储（pgvector）：仅 `hify-knowledge` 模块操作
- 缓存（Redis）：通过 Spring Cache 抽象，避免模块直接操作 RedisTemplate
- 禁止在 Service 层拼接 SQL 字符串，复杂查询写在 Mapper XML 中

---

## 三、关键架构决策（ADR）

### ADR-001：选择多 Maven 模块而非单模块

**决策：** 采用 Maven 多模块结构，每个业务域一个独立 Maven 模块。

**原因：**
1. **编译器强制依赖方向。** 一个人开发时，深夜写代码不慎让 agent 反向依赖 chat —— Maven 编译直接报错，不靠大脑记忆。这是多模块最大的单人开发收益。
2. **模块即边界。** 每个模块的 pom.xml 明确声明依赖，任何人在 IDE 里展开依赖树就能看懂全局关系。
3. **为未来拆微服务留后路。** 虽然一期不做，但模块拆好了，真要拆时改一下 POM 就能独立 deployable，成本极低。
4. **50 人用和 5000 人用共享同一套代码组织。** 用户量涨了只需加机器、改部署拓扑，代码结构不用变。

**代价：** 8 个模块各有一个 pom.xml，初始搭建和依赖版本管理稍繁琐。通过父 POM 的 `dependencyManagement` 统一版本号来缓解。

### ADR-002：选择领域分包（方案 B）而非传统分层（方案 A）或 DDD 分层（方案 C）

**决策：** 每个业务模块采用扁平领域分包，Controller/Service/Mapper/Entity 同包放置，不做 `api/domain/infra` 二级子层。

**原因：**
1. **一个人开发，改一个功能只动一个包。** 传统分层方案 A（全局 controller/service/mapper）会导致 service 包变成蜘蛛网，模块边界模糊。
2. **DDD 分层方案 C 在目前阶段是过度设计。** 每个模块 5-8 个文件的情况下，引入 `api/domain/infra` 三层子包会让文件跳转次数翻倍，心智负担超过收益。
3. **渐进式引入。** 不否定 DDD 分层的价值——当某个模块膨胀到 15+ 文件时，给该模块内部局部加上 `api/domain/infra`。让复杂度就地消化，而非提前设计。

### ADR-003：Agent 和 Workflow 拆分为两个独立模块

**决策：** Agent 和 Workflow 作为两个平级的一等模块，而非将 Agent 作为 Workflow 的一个子包。

**原因：**
1. **Agent 可以独立运行**（用户直接选择 Agent 对话），不一定经过 Workflow。它是独立的一等公民。
2. **Workflow 是编排层，Agent 是执行层。** Workflow 的 AgentNode 在运行时调用 AgentService，这是单向依赖（workflow → agent），层次清晰。
3. **拆分后 Chat 模块的角色更纯粹**：路由 + 会话 + SSE 推送，不耦合编排或执行逻辑。
4. **独立演进。** Agent 的策略（ReAct vs FunctionCall）、工具调用逻辑和 Workflow 的节点编排、JSON 解析是两套独立关注点，分开后各自改各自的。

### ADR-004：common 而非 shared 命名

**决策：** 共享模块命名为 `hify-common`，而非 `hify-shared`。

**原因：** Java 生态中 `common` 是压倒性惯例（Apache Commons、Spring Commons、commons-lang 等）。`shared` 是前端/JavaScript 圈的习惯命名。让 Java 开发者看着眼熟，减少认知摩擦。

### ADR-005：前端命名为 hify-web

**决策：** 前端模块命名为 `hify-web`。

**原因：** 和后端模块保持统一前缀 `hify-`，`-web` 明确表示 Web 前端。和 `hify-app`（后端启动）形成清晰的前后端对称命名，见名知意。

### ADR-006：部署目录命名为 deploy

**决策：** Docker 相关文件放在根目录 `deploy/` 下。

**原因：** 比 `docker/` 更准确——该目录不仅包含 Dockerfile，还有 nginx.conf、docker-compose.yml 等运维编排文件。`deploy` 语义涵盖面更广，见名知意。

### ADR-007：模块间禁止直接注入 Mapper

**决策：** 跨模块通信只允许注入对方的 Service，禁止注入 Mapper。

**原因：**
1. **防止 ORM 细节泄漏到模块边界外。** Mapper 暴露的是表结构，Service 暴露的是业务能力。后者是稳定的契约，前者随表结构变化。
2. **未来拆微服务时，Service 可以逐步换成 RPC/HTTP 调用。** 如果到处直接调 Mapper，拆的时候连哪些地方用了哪些表都理不清。
3. **一人项目更需要纪律。** 没有 Code Review 来纠正，靠约定和编译器（若 mapper 不 public 暴露）来守住边界。

### ADR-008：调用日志并入 common 模块

**决策：** 不单独设立 `hify-log` 模块，调用日志作为 `hify-common/log/` 子包提供。

**原因：**
1. **日志是横切关注点，不是业务模块。** 和 `BizException`、`JsonUtil` 一样，LogService 是纯基础设施能力——`LogService.write(record)` + `LogMapper.insert()`，零业务判断逻辑。
2. **独立模块没有额外边界价值。** 日志模块只有一个 Service 和一个 Mapper，单独建模块只是多了一份 pom.xml。没有复杂的子域可以拆分，也没有独立的演进方向。
3. **所有模块已经在依赖 common。** 合并后依赖关系不变，各模块无需改动任何代码。

### ADR-009：hify-app 显式依赖全部模块

**决策：** `hify-app` 的 pom.xml 显式声明对所有业务模块的依赖，而不是仅依赖 hify-chat 靠传递依赖撑起 classpath。

**原因：**
1. **系统中存在两类 Controller。** 管理端 API（如 `POST /api/v1/agent/save`）直接打到各模块的 Controller，不经 chat 路由。各模块需要出现在 classpath 上才能被 Spring 扫描到。
2. **传递依赖不可靠。** 如果仅依赖 chat，classpath 上的模块取决于依赖链的传递。一旦某天 `agent` 不再依赖 `tool`（工具改为由 workflow 注入），tool 模块就静默消失——编译不报错，运行时 ToolController 404。
3. **显示依赖即系统清单。** hify-app 的 `<dependencies>` 列表就是系统模块的完整目录，一目了然。多写几行 XML 换来整个依赖链的确定性。
4. **启动模块作为"装配车间"，知道所有模块的存在是合理的。** Spring Boot 的 `@ComponentScan` 本就需要扫描所有模块，显式依赖让这个关系在 Maven 层面可见。

### ADR-010：前端不引入 CSS 框架

**决策：** 使用 Vue SFC 的 `<style scoped>` + 全局 CSS 变量，不引入 Tailwind CSS 或其他 CSS 框架。

**原因：**
1. **管理后台样式量少。** Element Plus 的 Container/Header/Aside/Main 等布局组件覆盖了大部分 UI 结构，真正需要手写 CSS 的场景主要是间距微调、高度计算、溢出滚动，每个组件 20 行 scoped CSS 即可。
2. **Tailwind + Element Plus 存在天然边界。** Tailwind 的 utility class 作用于自己的 `<div>`，但 Element Plus 组件内部 DOM 不可控，定制仍需 `<style scoped>` + `:deep()` 穿透。两种写样式的方式并存增加认知负担。
3. **减少依赖。** 少一个 Tailwind 依赖就少一份版本兼容和维护成本。
4. **降低新人门槛。** 打开 `.vue` 文件看到 `<style scoped>` 里的原生 CSS 即可直接修改，无需先学 Tailwind utility 命名体系。

### ADR-011：前端 HTTP 客户端选用 Axios

**决策：** 使用 Axios 而非原生 fetch 封装。

**原因：**
1. **内置超时。** `timeout: 30000` 一行配置解决，fetch 需要手写 `AbortController` + `setTimeout`。
2. **标准拦截器 API。** `interceptors.request/response` 是标准 hook 点，后续加 token、全局 loading、错误统一提示都在拦截器里加函数即可，不修改核心请求逻辑。
3. **代码更少。** 封装后 `http.ts` 约 20 行（vs fetch 封装约 60 行）。
4. **社区主流选择。** Vue/React 生态中 Axios 仍是压倒性主流，遇到问题搜索效率高。

### ADR-012：前端代码规范选用 ESLint + Prettier

**决策：** 使用 ESLint + Prettier 经典组合，不引入 Biome。

**原因：**
1. **Vue SFC 支持成熟。** `eslint-plugin-vue` 提供 50+ 条 Vue 专属规则（组件命名、prop 类型、v-for key 等），Biome 一条都没有。
2. **项目规模下速度差异无意义。** 20-30 个 .vue 文件，ESLint 3 秒和 Biome 0.1 秒没有实际差别。
3. **社区主流。** 遇到配置问题 Google 即有答案，Biome + Vue 3 + Element Plus 的组合几乎没有人写过。

