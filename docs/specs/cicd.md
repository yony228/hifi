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

## 三、CI 流水线

### 3.1 触发条件

- **push 到任意分支** → 编译 + 全量测试
- **MR 到 main** → 编译 + 全量测试（和 push 行为一致）

### 3.2 GitLab CI 最小配置（`.gitlab-ci.yml`）

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

### 3.3 不做的事情

- **不做 Docker image 构建/推送** — 本地 `docker compose build` 足够，生产部署时手动操作
- **不做 deploy 阶段** — `docker compose up -d` 在服务器上手动执行
- **不做代码覆盖率门禁** — 阶段 0 不需要
- **不做 lint / checkstyle** — 保持 IDE 默认格式化即可

---

## 四、Tag 与版本

### 4.1 版本号

`{major}.{minor}.{patch}` 语义化版本：

| 段 | 何时递增 |
|---|---|
| major | 平台定位变化、不兼容变更 |
| minor | 新功能模块上线 |
| patch | Bug 修复、小优化 |

### 4.2 打 Tag

```bash
git tag -a v1.0.0 -m "v1.0.0: 基础组件完成（bizBase 计划 1-15）"
git push origin v1.0.0
```

---

## 五、部署

### 5.1 环境

| 环境 | 用途 | 部署方式 |
|---|---|---|
| 本地开发 | IDE 直接跑 `HifyApplication` | `dev` profile，H2 内存库 |
| 生产 | 20-50 人团队使用 | `docker compose up -d`，MySQL + pgvector + Redis |

### 5.2 部署流程

```
1. git pull origin main
2. git checkout v1.0.0（或直接 main）
3. docker compose down
4. docker compose build --no-cache
5. docker compose up -d
6. curl http://localhost:8080/api/v1/health → 确认 UP
```

### 5.3 回滚

```
docker compose down
git checkout v{上一个版本tag}
docker compose up -d
```

---

## 六、版本记录

| 日期 | 变更 |
|---|---|
| 2026-08-05 | 初始版本：分支管理、Commit 规范、CI 流水线、Tag 与部署 |
