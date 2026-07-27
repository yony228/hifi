# Hify 数据模型设计

> 面向 20-50 人团队内部使用，一人开发维护。MySQL 为主存储，pgvector 存向量。

---

## 1. 领域模型

### 1.1 总览

```
用户域      user

模型域      model_provider ──1:N──→ model

知识域      knowledge ──1:N──→ knowledge_document ──1:N──→ knowledge_chunk

Agent 域    agent ──N:M──→ tool（通过 agent_tool）

工作流域    workflow ──1:N──→ workflow_node ──N:1──→ agent

对话域      session ──1:N──→ message

观测域      log
```

### 1.2 各域表清单

**用户域**

| 表名 | 说明 |
|---|---|
| `user` | 用户账号、基本信息（归属 `hify-common`，作为基础设施级实体） |

**模型域**

| 表名 | 说明 |
|---|---|
| `model_provider` | 模型提供商（OpenAI / Ollama / vLLM / DeepSeek ...） |
| `model` | 具体模型（gpt-4o / llama3 / deepseek-v3 ...），N:1 归属 provider |

**知识域**

| 表名 | 说明 |
|---|---|
| `knowledge` | 知识库元信息（名称、分段参数等） |
| `knowledge_document` | 知识库下的文档记录（PDF / Markdown / TXT） |
| `knowledge_chunk` | 文档分段文本 + embedding 向量，存于 pgvector |

**Agent 域**

| 表名 | 说明 |
|---|---|
| `agent` | Agent 定义（system_prompt、runtime 参数等） |
| `tool` | 工具定义（搜索 / 代码沙箱 / HTTP / MCP） |
| `agent_tool` | Agent 与工具的 N:M 关联 |

**工作流域**

| 表名 | 说明 |
|---|---|
| `workflow` | Workflow 定义（JSON 编排配置） |
| `workflow_node` | Workflow 节点（LLM / 条件分支 / HTTP / Agent 子节点），N:1 归属 workflow，可引用 agent |

**对话域**

| 表名 | 说明 |
|---|---|
| `session` | 对话会话 |
| `message` | 会话中的消息记录 |

**观测域**

| 表名 | 说明 |
|---|---|
| `log` | 每次 Agent/Workflow 调用的完整记录 |

### 1.3 跨域关系

```
user ──1:N──→ agent          用户拥有 Agent
user ──1:N──→ workflow       用户拥有 Workflow
user ──1:N──→ knowledge      用户拥有知识库
user ──1:N──→ session        用户发起会话
user ──1:N──→ log            用户触发调用

agent ──N:1──→ model         Agent 绑定模型
agent ──N:1──→ knowledge     Agent 绑定知识库

session ──N:1──→ agent       会话绑定 Agent/Workflow
log ──N:1──→ agent           日志关联 Agent
log ──N:1──→ session         日志关联会话
log ──N:1──→ model           日志记录使用的模型

workflow_node ──N:1──→ agent      工作流节点引用 Agent
workflow_node ──N:1──→ workflow   节点归属工作流
```

### 1.4 核心表概要

#### user
| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT | 主键 |
| `username` | VARCHAR(64) | 用户名，UNIQUE |
| `password_hash` | VARCHAR(256) | 密码哈希 |
| `display_name` | VARCHAR(128) | 显示名称 |
| `status` | TINYINT | 0-禁用 1-启用 |
| `created_at` | DATETIME | |
| `updated_at` | DATETIME | |

#### model_provider
| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT | 主键 |
| `name` | VARCHAR(128) | 提供商名称，UNIQUE |
| `base_url` | VARCHAR(512) | API endpoint |
| `api_key` | VARCHAR(512) | 加密存储 |
| `provider_type` | VARCHAR(64) | openai / ollama / vllm / custom |
| `created_at` | DATETIME | |
| `updated_at` | DATETIME | |

#### model
| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT | 主键 |
| `provider_id` | BIGINT | FK → model_provider.id |
| `model_name` | VARCHAR(256) | 模型标识（gpt-4o / deepseek-v3） |
| `model_type` | VARCHAR(32) | llm / embedding |
| `is_default` | TINYINT | 是否默认模型 |
| `context_window` | INT | 上下文窗口大小（tokens） |
| `max_output_tokens` | INT | 最大输出 tokens |
| `created_at` | DATETIME | |
| `updated_at` | DATETIME | |

#### knowledge
| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT | 主键 |
| `user_id` | BIGINT | FK → user.id |
| `name` | VARCHAR(256) | 知识库名称 |
| `description` | TEXT | 描述 |
| `chunk_size` | INT | 分段大小（tokens） |
| `chunk_overlap` | INT | 分段重叠大小 |
| `embedding_model_id` | BIGINT | FK → model.id，向量化模型 |
| `created_at` | DATETIME | |
| `updated_at` | DATETIME | |

#### knowledge_document
| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT | 主键 |
| `knowledge_id` | BIGINT | FK → knowledge.id |
| `file_name` | VARCHAR(512) | 原始文件名 |
| `file_type` | VARCHAR(32) | pdf / markdown / txt |
| `file_size` | BIGINT | 文件字节数 |
| `chunk_count` | INT | 分段数量 |
| `status` | TINYINT | 0-处理中 1-就绪 2-失败 |
| `created_at` | DATETIME | |
| `updated_at` | DATETIME | |

#### knowledge_chunk（pgvector）
| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGSERIAL | 主键 |
| `document_id` | BIGINT | FK → knowledge_document.id |
| `chunk_index` | INT | 分段序号 |
| `chunk_text` | TEXT | 分段原文 |
| `embedding` | VECTOR | 向量（维度按模型定） |

#### agent
| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT | 主键 |
| `user_id` | BIGINT | FK → user.id |
| `name` | VARCHAR(256) | Agent 名称 |
| `description` | TEXT | 描述 |
| `model_id` | BIGINT | FK → model.id |
| `knowledge_id` | BIGINT | FK → knowledge.id，可空 |
| `system_prompt` | TEXT | 系统提示词 |
| `runtime_strategy` | VARCHAR(32) | react / function_call |
| `max_iterations` | INT | 最大迭代次数 |
| `temperature` | DECIMAL(3,2) | 0.0-2.0 |
| `top_p` | DECIMAL(3,2) | 0.0-1.0 |
| `status` | TINYINT | 0-禁用 1-启用 |
| `created_at` | DATETIME | |
| `updated_at` | DATETIME | |

#### tool
| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT | 主键 |
| `name` | VARCHAR(256) | 工具名称 |
| `tool_type` | VARCHAR(32) | web_search / code_exec / http / mcp |
| `config_json` | JSON | 工具配置（每种类型结构不同） |
| `status` | TINYINT | 0-禁用 1-启用 |
| `created_at` | DATETIME | |
| `updated_at` | DATETIME | |

#### agent_tool
| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT | 主键 |
| `agent_id` | BIGINT | FK → agent.id |
| `tool_id` | BIGINT | FK → tool.id |
| `created_at` | DATETIME | |

> UNIQUE (agent_id, tool_id)

#### workflow
| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT | 主键 |
| `user_id` | BIGINT | FK → user.id |
| `name` | VARCHAR(256) | Workflow 名称 |
| `description` | TEXT | 描述 |
| `config_json` | JSON | 完整编排定义 |
| `status` | TINYINT | 0-禁用 1-启用 |
| `created_at` | DATETIME | |
| `updated_at` | DATETIME | |

#### workflow_node
| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT | 主键 |
| `workflow_id` | BIGINT | FK → workflow.id |
| `node_key` | VARCHAR(128) | 节点标识（JSON 中引用用） |
| `node_type` | VARCHAR(32) | llm / condition / http / agent |
| `agent_id` | BIGINT | FK → agent.id（node_type=agent 时） |
| `config_json` | JSON | 节点配置（prompt / 参数 / 条件表达式等） |
| `sort_order` | INT | 排序 |
| `created_at` | DATETIME | |
| `updated_at` | DATETIME | |

> UNIQUE (workflow_id, node_key)

#### session
| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT | 主键 |
| `user_id` | BIGINT | FK → user.id |
| `agent_id` | BIGINT | FK → agent.id |
| `title` | VARCHAR(256) | 会话标题（自动生成或手动） |
| `context_window` | INT | 当前上下文窗口大小 |
| `message_count` | INT | 消息计数（冗余，用于列表展示） |
| `status` | TINYINT | 0-已结束 1-进行中 |
| `created_at` | DATETIME | |
| `updated_at` | DATETIME | |

#### message
| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT | 主键 |
| `session_id` | BIGINT | FK → session.id |
| `role` | VARCHAR(32) | user / assistant / tool |
| `content` | TEXT | 消息内容 |
| `tool_calls` | JSON | 工具调用记录（role=assistant 且调用了工具时） |
| `token_count` | INT | token 消耗 |
| `created_at` | DATETIME | |

#### log
| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT | 主键 |
| `user_id` | BIGINT | FK → user.id |
| `agent_id` | BIGINT | FK → agent.id |
| `session_id` | BIGINT | FK → session.id |
| `model_id` | BIGINT | FK → model.id |
| `user_input` | TEXT | 用户输入 |
| `model_output` | TEXT | 最终输出 |
| `tool_calls` | JSON | 工具调用链 |
| `token_usage` | INT | 总 token 消耗 |
| `duration_ms` | INT | 耗时（毫秒） |
| `status` | TINYINT | 0-失败 1-成功 |
| `error_msg` | TEXT | 错误信息 |
| `created_at` | DATETIME | |

---

## 2. 公共字段约定

所有 MySQL 业务表统一遵循以下约定：

### 2.1 主键

- 统一使用 `id BIGINT AUTO_INCREMENT PRIMARY KEY`
- 不暴露业务含义，保持无意义主键
- 跨表关联统一用 `xxx_id BIGINT` 外键字段

### 2.2 时间戳

| 字段 | 类型 | 说明 |
|---|---|---|
| `created_at` | DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间，业务时间，不随更新变化 |
| `updated_at` | DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | 更新时间，每次 UPDATE 自动刷新 |

### 2.3 软删除

- 不设全局 `is_deleted` 字段
- 按需在需要回收站/恢复功能的表上加 `deleted_at DATETIME NULL`，NULL 表示未删除
- 唯一索引包含 `deleted_at`，避免已删除数据阻碍新数据写入（MySQL 中 NULL 值不参与唯一约束比较）
- 当前阶段暂不需要软删除的表：`model_provider`、`model`、`tool`（硬删除即可）

### 2.4 状态字段

- 统一用 `status TINYINT NOT NULL DEFAULT 1`
- 0 表示禁用/不可用，1 表示启用/正常
- 不做美剧式多状态，保持布尔语义

### 2.5 枚举值

- 不使用 MySQL ENUM 类型
- 用 `VARCHAR(32)` 或 `VARCHAR(64)` 存储，应用层校验
- 原因：ENUM 加值的 ALTER TABLE 是 DDL 重操作，VARCHAR 无此问题

### 2.6 命名规范

| 规则 | 示例 |
|---|---|
| 表名：小写蛇形，单数 | `agent`、`model_provider` |
| 字段名：小写蛇形 | `user_id`、`created_at` |
| 外键字段：`关联表名_id` | `agent_id`、`session_id` |
| 索引名：`idx_表名_字段` | `idx_agent_user_id` |
| 唯一索引：`uk_表名_字段` | `uk_agent_tool`（agent_id, tool_id） |

---

## 3. 性能规范

### 3.1 索引设计原则

#### 3.1.1 外键一律加索引

所有 FK 字段必须建普通索引（MySQL 不会自动为外键建索引），覆盖全部跨域查询：

```sql
-- 示例
CREATE INDEX idx_agent_user_id ON agent(user_id);
CREATE INDEX idx_agent_model_id ON agent(model_id);
CREATE INDEX idx_session_agent_id ON session(agent_id);
CREATE INDEX idx_message_session_id ON message(session_id);
```

#### 3.1.2 查询驱动建索引

以实际查询场景倒推索引，不提前优化：

| 查询场景 | 索引 |
|---|---|
| 用户登录 | `uk_user_username (username)` |
| 我的 Agent 列表 | `idx_agent_user_id (user_id)` |
| 会话的消息时间线 | `idx_message_session_created (session_id, created_at)` |
| 日志按 Agent + 时间筛选 | `idx_log_agent_created (agent_id, created_at)` |
| 日志按用户 + 时间筛选 | `idx_log_user_created (user_id, created_at)` |
| 知识库文档列表 | `idx_knowledge_doc_kid (knowledge_id)` |

#### 3.1.3 联合索引最左前缀

- 筛选条件固定顺序的场景用联合索引替代多个单列索引
- `(session_id, created_at)` 可以覆盖「仅 session_id」的查询，无需再建单列 `session_id` 索引
- 联合索引字段顺序：等值条件在前，范围条件在后

#### 3.1.4 避免过度索引

- 20-50 人规模下，单表万级数据，索引开销远小于维护负担
- 一个表的索引数量控制在 **5 个以内**
- 不为低频查询建索引，不在 `status` 等低基数字段上单独建索引

### 3.2 大表预判与应对策略

#### 3.2.1 大表识别

| 表 | 增长驱动 | 年预估行数 | 风险等级 |
|---|---|---|---|
| `log` | 每次调用 1 行 | ~5-10 万（按日 200 次调用） | **中** |
| `message` | 每轮对话 1-N 行 | ~10-20 万（log × 平均对话轮次） | **中** |
| `knowledge_chunk` | 文档分段 | ~1-5 万 | 低（pgvector 独立库） |
| 其他表 | 用户操作 | 百~千级 | 低 |

#### 3.2.2 应对策略

**log 表 — 唯一需关注的大表**

- **定期归档**：保留近 6 个月热数据，6 个月前的数据归档到 `log_archive` 表或按月分表 `log_202601`
- **分页保护**：列表查询加时间范围限制，不允许全表扫
- **字段瘦身**：`user_input`、`model_output`、`tool_calls` 三个大字段不参与列表查询，列表只查必要字段
- **清理策略**：定时任务按月清理 12 个月前的归档数据

**message 表**

- 随 session 生命周期：session 删除/归档时连带清理
- 会话列表不需要 join message，`session.message_count` 冗余字段已覆盖
- 单会话消息加载用 `session_id + created_at` 联合索引，自然有序

**knowledge_chunk 表**

- 存于 pgvector，与 MySQL 隔离
- 向量检索走 IVFFlat / HNSW 索引，不依赖全表扫描
- 删除知识库时异步清理对应 chunk，避免大事务

#### 3.2.3 不引入分库分表的理由

- 20-50 人规模，单表百万级以下 MySQL 完全胜任
- 分库分表带来的运维复杂度远大于性能收益
- 真到瓶颈时（千级 QPS、千万级行数），先上读写分离 + 缓存，再考虑分表

### 3.3 分页查询注意事项

#### 3.3.1 禁用 LIMIT-offset 大偏移

```sql
-- 错误：offset 越大越慢，MySQL 需要扫描前 N 行再丢弃
SELECT * FROM log WHERE user_id = ? ORDER BY created_at DESC LIMIT 100 OFFSET 10000;

-- 正确：游标分页
SELECT * FROM log WHERE user_id = ? AND created_at < ? ORDER BY created_at DESC LIMIT 100;
```

#### 3.3.2 统一游标分页

- 列表接口一律使用游标分页（基于 `created_at` + `id`）
- 前端传上一页最后一条的 `created_at` + `id` 作为游标
- 后端返回 `has_more` 标志位，前端判断是否还有下一页
- 不做「总页数」「跳转到第 N 页」这类需要 COUNT 的操作

#### 3.3.3 游标分页实现模板

```sql
-- 第一页
SELECT * FROM log WHERE user_id = ? ORDER BY created_at DESC, id DESC LIMIT 21;

-- 后续页（lastCreatedAt, lastId 来自上一页最后一条）
SELECT * FROM log
WHERE user_id = ?
  AND (created_at < ? OR (created_at = ? AND id < ?))
ORDER BY created_at DESC, id DESC
LIMIT 21;  -- 取 N+1 条判断 has_more
```

#### 3.3.4 列表字段剪裁

- 列表接口只查展示所需字段，禁忌 `SELECT *`
- `log` 列表不查 `user_input`、`model_output`、`tool_calls` 三个 TEXT/JSON 大字段
- 详情接口再全量查询

---

## 4. 数据域 ↔ Maven 模块映射

### 4.1 映射表

| 数据域 | 表 | 归属模块 | 包路径 |
|---|---|---|---|
| 用户域 | `user` | `hify-common` | `com.hify.common.user` |
| 模型域 | `model_provider`, `model` | `hify-model` | `com.hify.model` |
| 知识域 | `knowledge`, `knowledge_document`, `knowledge_chunk` | `hify-knowledge` | `com.hify.knowledge` |
| Agent 域 | `agent` | `hify-agent` | `com.hify.agent` |
| | `tool`, `agent_tool` | `hify-tool` | `com.hify.tool` |
| 工作流域 | `workflow`, `workflow_node` | `hify-workflow` | `com.hify.workflow` |
| 对话域 | `session`, `message` | `hify-chat` | `com.hify.chat` |
| 观测域 | `log` | `hify-common` | `com.hify.common.log` |

### 4.2 映射总图

```
数据域           Maven 模块          表

用户域     →    hify-common          user
模型域     →    hify-model           model_provider, model
知识域     →    hify-knowledge       knowledge, knowledge_document, knowledge_chunk
Agent 域   →    hify-agent           agent
                hify-tool            tool, agent_tool
工作流域   →    hify-workflow        workflow, workflow_node
对话域     →    hify-chat            session, message
观测域     →    hify-common          log
```

### 4.3 关键说明

**Agent 域横跨两个模块。** `agent` 表在 `hify-agent`，`tool` / `agent_tool` 在 `hify-tool`。这是 ADR-003 的刻意设计——Agent 和工具是两个独立域。跨模块查询 `agent_tool` 时，`AgentServiceImpl` 注入 `ToolService` 接口获取工具列表，不得直接注入 `ToolMapper`。

```
AgentServiceImpl → ToolService.listByAgentId(agentId) → List<ToolResponse>
```

**`user` 放入 `hify-common`。** 20-50 人规模下，user 只是登录身份标识，没有 RBAC、没有用户管理后台，复杂度等同于基础设施。放在 `common/user/` 子包下，与其他横切关注点（`common/log/`、`common/exception/`）并列。若未来用户逻辑膨胀（组织架构、权限、多租户），再独立拆出 `hify-user` 模块。

**`log` 也在 `hify-common`。** ADR-008 决策——日志是横切关注点，不是业务模块。`LogService.write(record)` 是纯基础设施能力，零业务判断。

**各模块只持有自己表的 Entity 和 Mapper。** 例如 `hify-agent` 的 `AgentMapper` 只操作 `agent` 表，不会跨到 `tool` 表查数据。跨表数据组装在 Service 层通过调用其他模块的 Service 接口完成。

---

## 5. 版本记录

| 日期 | 变更 |
|---|---|
| 2026-07-27 | 新增 §4「数据域 ↔ Maven 模块映射」；明确 user 表归属 hify-common |
| 2026-07-26 | 初始版本：领域模型、公共字段约定、性能规范 |
