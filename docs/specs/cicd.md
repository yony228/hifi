# Hify CI/CD 规范

> 面向一人开发维护、GitLab 托管的单体项目。轻量流程，避免过度工程化。

---

## 一、分支管理

### 1.1 分支模型

```
main ──→ feature/xxx ──→ main
  │                        │
  └────────────────────────┘
         (无 develop 分支，Trunk-Based 简化版)
```

- **`main`**：唯一长期分支，始终可部署。所有变更从 feature 分支合并入。
- **不设 `develop`** — 一人维护，不需要集成层，feature 分支直接合 main。
- **不设 `release`** — Git tag 做版本标记即可。

### 1.2 分支命名规则

```
{type}/{short-description}
```

| 类型 | 格式 | 示例 |
|---|---|---|
| 新功能 | `feature/{简要描述}` | `feature/model-provider-entity` |
| 修复 | `fix/{简要描述}` | `fix/sse-timeout-null-pointer` |
| 文档 | `docs/{简要描述}` | `docs/api-spec-add-error-codes` |
| 重构 | `refactor/{简要描述}` | `refactor/extract-llm-invoker` |
| 紧急修复 | `hotfix/{简要描述}` | `hotfix/health-check-redis-down` |

**命名约束：**
- 使用全小写英文 + 连字符 `-` 分隔，不用下划线或驼峰
- 描述尽量控制在 4-6 个词以内，突出「改什么」而非「怎么改」
- 字母数字 + 连字符，不允许空格和特殊字符

### 1.3 工作流程

```
1. git checkout main && git pull
2. git checkout -b feature/my-feature
3. 开发 + 本地测试（mvn test 全绿）
4. git push origin feature/my-feature
5. 创建 Merge Request（GitLab MR）→ 自己 review 一遍 diff
6. 合并到 main（--squash 可选，视 commit 粒度而定）
7. git checkout main && git pull
```

**无需他人 review**（一人团队），但要求 MR diff 自己扫一遍，确认无意外文件混入。

---

## 二、Commit 规范

### 2.1 格式

```
{type}: {简短描述}

- {要点1}
- {要点2}

{关联文档/issue（可选）}
```

### 2.2 Type 前缀

| 前缀 | 适用场景 |
|---|---|
| `feat:` | 新功能、新组件 |
| `fix:` | Bug 修复 |
| `docs:` | 仅文档变更 |
| `refactor:` | 重构（无功能变化） |
| `test:` | 仅测试变更 |
| `chore:` | 构建、依赖、配置等杂项 |

### 2.3 描述规则

- **首行 60 字符以内**（中文约 30 字），用中文破折号 `—` 引出核心变更
- **不要** `feat: add xxx` 这种英文混写；中文描述即可
- 要点用 `- ` 列举，每条一个变更点
- 关联 plan 文档在末尾标注 `{plan文件名} §{条目号}`

### 2.4 示例

```
feat: HttpClientConfig — 按 Provider 差异化的 RestClient + ChatClient 缓存

- ProviderTypeEnum: 5 种 Provider 超时预设（连接/非流式读/流式读）
- ModelConfig: 模型连通性参数值对象，与实体解耦
- HttpClientConfig: JDK HttpClient + OpenAiApi.Builder 创建 ChatClient
- ConcurrentHashMap 缓存，key=modelId@endpoint
- 28 个测试用例覆盖超时预设、RestClient 构建、缓存/驱逐、参数校验

plan-bizBase-1.0.md §8
```

---

## 三、Code Review Agent（CR Agent）

> 提交前的自动代码审查，由 AI Agent 执行。发现缺陷后由人决定采用哪些修复方案。

### 3.1 定位

CR Agent 是提交前的**质量门禁**。它在每次准备 commit 时自动运行，也可以由人在开发过程中随时唤起。Agent 产出的是**建议**，修复决策权始终在人手里。

### 3.2 审查维度

| 维度 | 检查内容 | 严重程度 |
|---|---|---|
| **正确性** (correctness) | 空指针、边界条件、逻辑错误、并发问题、资源泄漏 | 🔴 高 |
| **安全** (security) | SQL 注入、敏感信息泄漏、SSRF、输入校验缺失 | 🔴 高 |
| **简化/复用** (simplification) | 重复代码、可抽取公共方法、过度设计、死代码 | 🟡 中 |
| **效率** (efficiency) | 不必要的循环、N+1 查询、大对象创建、缓存缺失 | 🟡 中 |
| **测试覆盖** (test-coverage) | 新增代码缺少测试、边界条件未覆盖 | 🟡 中 |
| **规范遵循** (convention) | 命名规范、注释缺失、跨模块调用违规 | 🟢 低 |

### 3.3 调用方式

#### 方式一：提交前自动唤起（Agent 主动）

Agent 在完成代码编写、用户说 "帮我提交" 时，**必须先跑 CR 再 commit**：

```
用户: "帮我提交"
Agent:
  1. mvn test（全量测试）
  2. /code-review（CR Agent 审查 diff）
  3. 展示 findings 列表，等待用户选择修复方案
  4. 用户确认后 → git commit
```

#### 方式二：开发过程中手动唤起

用户随时可以通过以下方式触发 CR：

| 命令 | 作用 |
|---|---|
| `/code-review` | 对当前 diff 做全面审查（正确性 + 简化 + 效率） |
| `/code-review --fix` | 审查并自动应用修复 |
| `/simplify` | 仅做简化/复用/效率审查（不查正确性 bug） |
| `/security-review` | 仅做安全审查 |

### 3.4 审查范围

```
CR 审查的是「当前 diff」——即 git diff 中尚未提交的变更。
不扫全项目，不扫未改动的历史代码。
```

### 3.5 产出格式

每次 CR 产出结构化 findings 列表：

```
🔴 正确性 (2)
  ├── LlmInvoker.java:42 — invokeSync 未处理 InterruptedException
  │     场景: 线程池满且线程被中断 → 静默吞异常 → 调用方拿不到结果
  │     建议: catch 中 reset interrupt flag + 返回降级响应
  │
  └── ModelConfig.java:28 — apiKey 可能为 null，OpenAiApi.Builder 抛异常
        场景: Ollama 本地部署 apiKey=null → 构造时 NPE
        建议: apiKey 为 null 时传空字符串 ""

🟡 简化 (1)
  └── HttpClientConfig.java:78-82 — buildRestClientBuilder 日志可抽取为私有方法

🟡 效率 (1)
  └── AgentService.java:156 — 循环内每次 new ObjectMapper()，应复用 JsonUtil

🟢 规范 (1)
  └── ToolController.java:12 — 缺少类级别 Javadoc
```

### 3.6 人选择修复方案

CR Agent **不自动修代码**（除非用户使用了 `--fix`）。产出 findings 后，Agent 用 `AskUserQuestion` 让用户逐条或批量选择：

```
Agent: "CR 发现 5 个问题（2 正确性 / 1 简化 / 1 效率 / 1 规范）。
       请选择要修复的条目："

选项示例（多选）:
  [x] #1 LlmInvoker.java:42 — 未处理 InterruptedException          ← 勾选就修
  [x] #2 ModelConfig.java:28 — apiKey null 导致 NPE                  ← 勾选就修
  [ ] #3 HttpClientConfig.java:78 — 日志可抽取（不影响功能，可延后）
  [x] #4 AgentService.java:156 — 循环内 new ObjectMapper()
  [ ] #5 ToolController.java:12 — 缺少 Javadoc（下次补）
  [ ] 全部修复
  [ ] 全部跳过（仅记录，不做修改）
```

**规则：**
- 正确性和安全问题 — Agent 强烈建议修复，但最终由人决定
- 简化和效率问题 — 由人判断是否影响可维护性
- 规范问题 — 低优先级，可延后
- 用户选择后，Agent 逐条应用修复，修复后重新跑 `mvn test`
- 若用户选择 "全部跳过"，findings 记入 commit message 作为已知问题

### 3.7 提交阻塞规则

| 情况 | 行为 |
|---|---|
| **零 findings** | 直接提交 |
| **有 🔴 正确性/安全 finding → 用户拒绝修复** | 仍可提交（人决策优先），但 commit message 末尾标注已知问题 |
| **仅 🟡🟢 finding** | 展示给用户，不阻塞，用户选择是否修 |
| **测试未通过** | 硬阻塞，不能提交 |

### 3.8 示例：完整提交流程

```
用户: "帮我提交 HttpClientConfig"

Agent:
  → mvn test
  ✅ 72 tests passed

  → /code-review（审查 diff）
  🔍 发现 3 个问题：
     🔴 #1 HttpClientConfig.java:107 — null apiKey 导致 NPE
     🟡 #2 ProviderTypeEnum.java:45 — fromDbValue 可用 Map 缓存
     🟢 #3 ModelConfig.java:52 — toString 可加 apiKey 掩码

  → AskUserQuestion: "修复哪些？"
     用户勾选: #1, #3（跳过 #2，性能优化延后）

  → 应用修复 #1, #3
  → mvn test（重新验证）
  ✅ 72 tests passed

  → git commit -m "feat: HttpClientConfig — ..."
  ✅ 提交完成
```

### 3.9 不做的事情

- **不做风格审查**（如缩进、空行）— 保持 IDE 默认格式化即可
- **不做性能基准对比** — 阶段 0 不需要
- **不做架构层面的深度审查** — 架构问题在 plan 阶段解决
- **不自动修正确性 bug** — 始终需要人确认（`--fix` 除外）

---

## 四、CI 流水线

### 4.1 触发条件

- **push 到任意分支** → 编译 + 全量测试
- **MR 到 main** → 编译 + 全量测试（和 push 行为一致）

### 4.2 GitLab CI 最小配置（`.gitlab-ci.yml`）

```yaml
stages:
  - test

variables:
  MAVEN_OPTS: "-Dmaven.repo.local=$CI_PROJECT_DIR/.m2/repository"

cache:
  paths:
    - .m2/repository

test:
  stage: test
  image: maven:3.9-eclipse-temurin-21
  script:
    - mvn test --batch-mode
  rules:
    - if: $CI_PIPELINE_SOURCE == "merge_request_event"
    - if: $CI_PIPELINE_SOURCE == "push"
```

### 4.3 不做的事情

- **不做 Docker image 构建/推送** — 本地 `docker compose build` 足够，生产部署时手动操作
- **不做 deploy 阶段** — `docker compose up -d` 在服务器上手动执行
- **不做代码覆盖率门禁** — 阶段 0 不需要
- **不做 lint / checkstyle** — 保持 IDE 默认格式化即可

---

## 五、Tag 与版本

### 5.1 版本号

`{major}.{minor}.{patch}` 语义化版本：

| 段 | 何时递增 |
|---|---|
| major | 平台定位变化、不兼容变更 |
| minor | 新功能模块上线 |
| patch | Bug 修复、小优化 |

### 5.2 打 Tag

```bash
git tag -a v1.0.0 -m "v1.0.0: 基础组件完成（bizBase 计划 1-15）"
git push origin v1.0.0
```

---

## 六、部署

### 6.1 环境

| 环境 | 用途 | 部署方式 |
|---|---|---|
| 本地开发 | IDE 直接跑 `HifyApplication` | `dev` profile，H2 内存库 |
| 生产 | 20-50 人团队使用 | `docker compose up -d`，MySQL + pgvector + Redis |

### 6.2 部署流程

```
1. git pull origin main
2. git checkout v1.0.0（或直接 main）
3. docker compose down
4. docker compose build --no-cache
5. docker compose up -d
6. curl http://localhost:8080/api/v1/health → 确认 UP
```

### 6.3 回滚

```
docker compose down
git checkout v{上一个版本tag}
docker compose up -d
```

---

## 七、版本记录

| 日期 | 变更 |
|---|---|
| 2026-08-05 | 新增 §三「Code Review Agent」：提交前自动审查 + 手动唤起，人选择修复方案 |
| 2026-08-05 | 初始版本：分支管理、Commit 规范、CI 流水线、Tag 与部署 |
