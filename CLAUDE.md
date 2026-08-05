# Hify

## 项目概述

**Hify** 是基于 Dify 核心思路的轻量级 AI Agent 平台。面向团队内部 20-50 人使用，一人开发维护。

- **做什么：** Workflow 简化编排（JSON 配置） + 对话引擎 + Agent Runtime（ReAct Loop + 工具调用） + 多模型管理 + 知识库（RAG） + MCP 工具接入 + 管理控制台 + 调用日志
- **不做什么：** 可视化 Workflow 画布拖拽、权限系统/RBAC、多应用类型（只有 Agent）、插件市场、标注系统、仪表盘、计费、SSO/SAML
- **一句话：** Workflow 和 Agent 用 JSON 定义，绑定知识库和工具，Spring Boot 后端 + Vue 3 前端，Docker Compose 一键部署

## 技术栈

| 层 | 选型 |
|---|---|
| 后端框架 | Spring Boot 3 |
| LLM 调用 | Spring AI（ChatClient、Function Calling、MCP Client、Embedding） |
| Agent 逻辑 | 手写 ReAct loop，不引入 LangChain / LlamaIndex |
| ORM | MyBatis |
| 业务数据库 | MySQL（Agent 配置、日志、知识库元信息） |
| 向量存储 | pgvector（PostgreSQL，只存向量 + 文本片段） |
| 缓存 | Redis（会话缓存、流式状态） |
| 前端 | Vue 3 + Vite |
| 流式输出 | SSE（Spring WebFlux Flux → EventSource） |

## 功能模块

### Workflow（简化编排）
- 用 JSON 配置替代可视化画布拖拽，定义 Agent 执行流程
- 节点类型：LLM 节点、条件分支节点、HTTP 请求节点、Agent 子节点
- 节点间按顺序或条件串联，支持变量在节点间传递
- 一个 Workflow = 一段 JSON，版本管理直接用 Git
- 前端不引入拖拽库，Vue 表单编辑 JSON 配置即可

### Agent Runtime
- 一个 Agent = Workflow 中的一个 Agent 节点或独立 JSON 配置
- 核心字段：model、system_prompt、tools[]、knowledge_id、runtime 参数
- ReAct / Function Call 两种推理策略，可配置切换
- 迭代上限内执行「推理 → 工具调用 → 观察 → 推理」循环

### 对话引擎
- 会话管理：创建会话、切换会话、恢复历史会话
- 消息路由：用户输入 → 匹配 Agent/Workflow → 执行 → 流式返回
- 多轮对话：持久化对话历史，自动管理上下文窗口（超出截断）
- 流式编排：SSE 逐 token 推送，工具调用中间状态实时展示
- 会话存储：Redis 缓存热数据 + MySQL 持久化全量历史

### 知识库（RAG）
- 文件上传：PDF / Markdown / TXT
- 简易分段：固定长度 + overlap 滑动窗口
- 向量化：Spring AI Embedding，OpenAI-compatible 接口
- 检索：对话时自动召回相关片段注入上下文

### 工具系统
- 网页搜索（SerpAPI / Bing API Key）
- 代码执行（Docker 沙箱隔离 Python）
- HTTP 自定义工具（URL + Method + Headers + Body 模板）
- MCP 工具接入（配置 Server 地址，自动发现并注册工具）

### 管理控制台
Vue 3 SPA 统一入口，侧边栏导航，包含以下页面：

- **Chat 对话页**：选择 Agent → 多轮对话 → 流式输出，展示工具调用中间过程，历史会话列表与回看
- **Agent 配置页**：Agent/Workflow 的 CRUD，JSON 编辑器 + 表单辅助填写
- **模型管理页**：LLM / Embedding 模型配置的增删改查
- **知识库管理页**：知识库创建、文件上传、分段参数配置、检索测试
- **工具配置页**：搜索 API Key、代码沙箱参数、HTTP 工具注册、MCP Server 配置
- **日志查看页**：调用记录列表、按 Agent/时间筛选、详情展开
- **系统设置页**：全局参数、环境状态查看

### 日志
- 每次调用记录：时间、Agent、输入、输出、工具链、Token、耗时
- 列表 + 筛选 + 详情

### 多模型管理
- 统一管理所有模型配置：LLM 模型、Embedding 模型
- 每个模型配置 = endpoint + API key + model name，走 OpenAI-compatible 协议
- 一次配置覆盖 OpenAI、Ollama、vLLM、LiteLLM、DeepSeek 等所有兼容接口
- Agent / Workflow / 知识库在配置中引用模型 ID，切换模型无需改业务逻辑
- 支持配置多个同类型模型，用于不同场景（如便宜模型做简单任务、强模型做复杂推理）

## 项目规范文档

编码前务必先阅读以下规范文件，按职责分工查阅：

| 文件 | 用途 | 何时阅读 |
|---|---|---|
| `docs/specs/design.md` | 工程架构设计：模块划分、依赖方向、ADR 架构决策及其原因 | 新建模块、调整模块依赖、做架构决策时 |
| `docs/specs/code-organization.md` | 代码组织规范：每层职责边界、跨模块调用规则、命名规范、自检清单 | 写任何 Java 代码前。面向 AI 执行，规则精确到编号 |
| `docs/specs/llm-invoke-spec.md` | LLM 调用技术方案：线程隔离、超时配置、熔断降级、重试策略 | 编写或修改 hify-model 模块的 LLM 调用逻辑时 |
| `docs/specs/deploy-architecture.md` | 部署架构：K8s 组件拓扑、请求流转、资源配置、运维策略 | 编写 Dockerfile、K8s YAML、或调整部署拓扑时 |
| `docs/specs/scaling-roadmap.md` | 扩展路径：50→5000 人分 4 阶段演进，每阶段触发条件、改什么、不改什么 | 做性能优化、架构升级决策时 |
| `docs/specs/data-model.md` | 数据模型设计：领域模型、表结构、数据域与 Maven 模块映射、公共字段约定、性能规范 | 新增表/字段、调整模块数据边界、索引设计、分页查询时 |
| `docs/specs/api-spec.md` | API 接口规范：路径约定、统一响应格式、游标分页、空值处理、错误码 | 写 Controller、定义 DTO、处理异常、对接前后端接口时 |
| `docs/specs/cicd.md` | CI/CD 规范：分支管理、Commit 格式、CI 流水线、Tag 与部署流程 | 提交代码、打 Tag、部署、CI 配置时 |
| `docs/specs/agent-rules.md` | Agent 协作守则：讨论/实现模式边界、提交权限、代码规范遵循、Plan 驱动 | **每次会话开始前。定义 Agent 什么能做什么不能做** |

## 部署与运维

### 部署架构

```
Docker Compose → app(:8080) + mysql(:3306) + pgvector(:5432) + redis(:6379) + nginx(:80)
```

一条 `docker compose up -d` 启动，volume 持久化数据。

### 运维预期

- 用户规模：20-50 人，QPS 3-5，瓶颈在 LLM 长连接
- 并发 SSE 连接：峰值 ~10 条，Tomcat 默认线程池完全够用
- 监控起步：Spring Boot Actuator（health / metrics / env），后期加 Prometheus + Grafana
- 故障恢复：`docker compose down && docker compose up -d`，数据在 volume 不丢
- 环境变量：`.env` 管理敏感配置（API Key、JWT Secret），`.env.example` 提供模板

## 数据模型

> 完整表结构、领域划分、公共字段约定、性能规范见 **[`docs/specs/data-model.md`](docs/specs/data-model.md)**。下面是模块与表的归属速查。

| Maven 模块 | 表 |
|---|---|
| `hify-common` | `user`, `log` |
| `hify-model` | `model_provider`, `model` |
| `hify-knowledge` | `knowledge`, `knowledge_document`, `knowledge_chunk`（pgvector） |
| `hify-agent` | `agent` |
| `hify-tool` | `tool`, `agent_tool` |
| `hify-workflow` | `workflow`, `workflow_node` |
| `hify-chat` | `session`, `message` |
