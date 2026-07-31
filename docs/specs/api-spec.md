# Hify API 接口规范

> 面向前后端协作和 AI 编码 Agent，定义精确的接口约定。所有 Controller 必须遵守本规范。

---

## 1. 路径约定

### 1.1 URL 结构

```
/api/v{version}/{module}/{resource}
```

| 部分 | 约定 | 示例 |
|---|---|---|
| 前缀 | `/api` | — |
| 版本 | `v1`, `v2`（大版本号，不跟随小版本） | `/api/v1` |
| 模块 | 小写，与 Maven 模块名一致 | `agent`, `chat`, `workflow` |
| 资源 | 复数名词 | `/api/v1/agent/agents` |

### 1.2 CRUD 路由模板

每个业务资源的 Controller 遵循统一路由模板：

| 操作 | HTTP 方法 | 路径 | 示例 |
|---|---|---|---|
| 新增 | `POST` | `/{resource}` | `POST /api/v1/agent/agents` |
| 单查 | `GET` | `/{resource}/{id}` | `GET /api/v1/agent/agents/1` |
| 列表 | `GET` | `/{resource}` | `GET /api/v1/agent/agents` |
| 更新 | `PUT` | `/{resource}/{id}` | `PUT /api/v1/agent/agents/1` |
| 删除 | `DELETE` | `/{resource}/{id}` | `DELETE /api/v1/agent/agents/1` |

### 1.3 动作类接口

非标准 CRUD 的动作用 `{动词}+{名词}` 子资源路径：

| 操作 | 示例 |
|---|---|
| 启用/禁用 | `PUT /api/v1/agent/agents/1/toggle` |
| 测试连接 | `POST /api/v1/model/models/1/test-connection` |
| 检索测试 | `POST /api/v1/knowledge/knowledges/1/search` |
| 流式对话 | `POST /api/v1/chat/sessions/1/stream`（SSE） |

### 1.4 强制规则

| 编号 | 规则 | 违规示例 |
|---|---|---|
| P-01 | 资源名用复数，小写蛇形 | `/agent` ❌ → `/agents` ✅ |
| P-02 | 路径中不使用动词（动作类除外） | `/createAgent` ❌ → `POST /agents` ✅ |
| P-03 | 多级嵌套不超过 2 层 | `/agents/1/tools/1/configs/1` ❌ |
| P-04 | 查询参数用 camelCase | `?pageSize=10` ✅ `?page_size=10` ❌ |
| P-05 | 不使用文件扩展名 | `/agents.json` ❌ → `/agents` ✅ |
| P-06 | 路径不以 `/` 结尾 | `/api/v1/agent/agents/` ❌ |

### 1.5 查询参数命名

| 场景 | 参数名 | 示例 |
|---|---|---|
| 传统分页 | `page`, `pageSize` | `?page=1&pageSize=20` |
| 关键词搜索 | `keyword` | `?keyword=test` |
| 状态筛选 | `status` | `?status=1` |
| 时间范围 | `startTime`, `endTime` | `?startTime=2026-01-01&endTime=2026-07-01` |
| 排序 | `sortBy`, `sortDir` | `?sortBy=createdAt&sortDir=desc` |

---

## 2. 统一响应格式

### 2.1 成功响应

```json
{
  "code": 0,
  "message": "ok",
  "data": { ... }
}
```

**单条数据：**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 1,
    "name": "客服 Agent",
    "modelId": 2,
    "createdAt": "2026-07-27T10:30:00"
  }
}
```

**列表数据（分页）：**

```json
{
  "code": 0,
  "message": "ok",
  "data": [
    { "id": 1, "name": "客服 Agent" },
    { "id": 2, "name": "翻译 Agent" }
  ],
  "total": 100,
  "page": 1,
  "size": 20
}
```

**无数据返回（创建/更新/删除成功）：**

```json
{
  "code": 0,
  "message": "ok",
  "data": null
}
```

### 2.2 错误响应

```json
{
  "code": 40001,
  "message": "Agent 不存在",
  "data": null
}
```

`data` 字段在错误响应中固定为 `null`，错误细节通过 `code` + `message` 传达，不放入 `data`。

### 2.3 Java 实现

```java
// 位置：com.hify.common.dto.ApiResponse
@Data
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data);
    }

    public static <T> ApiResponse<T> error(ErrorCode error) {
        return new ApiResponse<>(error.getCode(), error.getMessage(), null);
    }
}
```

**Controller 使用：**

```java
@RestController
@RequestMapping("/api/v1/agent/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @GetMapping("/{id}")
    public ApiResponse<AgentResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(agentService.getById(id));
    }

    @PostMapping
    public ApiResponse<AgentResponse> create(@Valid @RequestBody CreateAgentRequest request) {
        return ApiResponse.ok(agentService.create(request));
    }
}
```

---

## 3. 分页规范

### 3.1 统一传统分页

项目前期数据量小（最大的表年增长 ~10 万行），传统 `page`/`pageSize` 分页
足以覆盖所有场景，且 MyBatis-Plus 的 `PaginationInnerInterceptor` 开箱即用，
Element Plus 的 `el-pagination` 组件原生适配。

后期如果个别接口（如对话消息）出现深页性能问题，再针对性改为游标分页，不影响其他接口。

**请求参数：**

| 参数 | 类型 | 说明 |
|---|---|---|
| `page` | Integer | 页码，从 1 开始，默认 1 |
| `pageSize` | Integer | 每页条数，默认 20，最大 100 |

**响应结构（`PageResult<T>`）：**

| 字段 | 类型 | 说明 |
|---|---|---|
| `data` | Array\<T\> | 当前页数据列表 |
| `total` | Long | 总记录数 |
| `page` | Integer | 当前页码 |
| `size` | Integer | 每页条数 |

### 3.2 Java 实现

使用 MyBatis-Plus 的 `Page<T>` 配合 `PaginationInnerInterceptor`，Service 层通过
`PageUtils.toPageResult()` 转换为 `PageResult<T>`。

```java
// Controller
@GetMapping("/agents")
public PageResult<AgentResponse> list(
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "20") Integer pageSize) {
    return agentService.listAgents(page, pageSize);
}

// Service
public PageResult<AgentResponse> listAgents(int page, int pageSize) {
    Page<Agent> mpPage = new Page<>(page, pageSize);
    agentMapper.selectPage(mpPage, new LambdaQueryWrapper<Agent>().eq(Agent::getStatus, 1));
    List<AgentResponse> items = mpPage.getRecords().stream()
            .map(AgentResponse::from).toList();
    return PageUtils.toPageResult(items, mpPage);
}
```

### 3.3 分页响应示例

**请求：** `GET /api/v1/agent/agents?page=1&pageSize=20`

```json
{
  "code": 0,
  "message": "ok",
  "data": [
    { "id": 1, "name": "客服 Agent" },
    { "id": 2, "name": "翻译 Agent" }
  ],
  "total": 42,
  "page": 1,
  "size": 20
}
```

### 3.4 分页强制规则

| 编号 | 规则 |
|---|---|
| PG-01 | 所有列表接口必须分页，不提供"全量查询"端点 |
| PG-02 | 统一使用 `page`/`pageSize` 参数，camelCase 命名 |
| PG-03 | `pageSize` 上限 100，超过的请求在拦截器中抛异常 |
| PG-04 | 列表接口返回 `total`，前端据此显示总页数和跳页 |
| PG-05 | 列表 SQL 禁止 `SELECT *`，只查展示所需字段（详情接口再全量查） |
| PG-06 | Service 层统一用 `PageUtils.toPageResult()` 做 `Page<T>` → `PageResult<T>` 转换 |

---

## 4. 空值处理

### 4.1 序列化策略

Jackson 全局配置：**非空才序列化**，`null` 字段不出现在 JSON 中。

```java
// com.hify.common.config.JacksonConfig
@Configuration
public class JacksonConfig {
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
}
```

### 4.2 各场景约定

| 场景 | 策略 | 示例 |
|---|---|---|
| 对象字段为 null | 字段不出现在 JSON 中 | `{ "name": "test" }` ← `knowledgeId` 为 null，不出现 |
| 数组为空 | 返回 `[]`，不出现在 JSON 中 | 按 `NON_NULL`，null 数组省略 |
| 字符串为空 | 返回 `""` | `{ "description": "" }` |
| 布尔为 false | 正常返回 `false` | `{ "enabled": false }` |
| 顶层的 code/message/data | 永远不省略，即使为 null | `{ "code": 0, "message": "ok", "data": null }` |

### 4.3 强制规则

| 编号 | 规则 |
|---|---|
| NV-01 | Jackson 配置 `NON_NULL`，默认省略 null 字段 |
| NV-02 | Response DTO 中集合类型字段初始化 `= Collections.emptyList()`，即使用不到也要初始化 |
| NV-03 | 前端收到响应后，对缺失字段按 `undefined` 处理，不应假设有默认值 |
| NV-04 | 更新接口只更新请求中出现的字段（部分更新语义），未传字段保持不变 |

### 4.4 部分更新约定（PATCH 语义）

`PUT` 接口按部分更新处理——只更新 JSON body 中出现的字段：

```json
PUT /api/v1/agent/agents/1
{ "name": "新名称" }
```

仅更新 `name` 字段，`systemPrompt`、`modelId` 等保持不变。显式传 `null` 表示清空该字段：

```json
PUT /api/v1/agent/agents/1
{ "knowledgeId": null }
```

表示解除 Agent 与知识库的绑定。

---

## 5. 错误码

### 5.1 错误码结构

五位数，`{模块}{类别}{序号}`：

```
AB CDE
│  └── 序号（001-999）
└───── 模块（01-99）
```

不用 HTTP 状态码区分业务错误——所有业务错误统一返回 **HTTP 200**，通过响应 `code` 字段区分。仅 4xx 留给 Spring Security 认证拦截和 404 路由不匹配。

| 类别 | code 前缀 | HTTP 状态码 | 说明 |
|---|---|---|---|
| 成功 | `00000` | 200 | `code = 0`，固定值 |
| 客户端错误 | `1xxxx` | 200 | 参数校验、业务规则冲突、资源不存在 |
| 服务端错误 | `2xxxx` | 200 | 数据库异常、模型调用失败、外部服务不可用 |

### 5.2 客户端错误（1xxxx）

| code | message | 使用场景 |
|---|---|---|
| `10001` | 参数校验失败 | `@Valid` 校验不通过，`message` 拼接具体字段 |
| `10002` | 请求体不可读 | JSON 格式错误或字段类型不匹配 |
| `10003` | 分页 size 超限 | `size > 100` |
| `11001` | 资源不存在 | 所有 `getById` 查不到数据时，`message` 带资源名 |
| `11002` | 资源名称重复 | 唯一约束冲突，`message` 带具体名称 |
| `11003` | 资源被引用无法删除 | 删除被其他资源引用的数据时 |
| `12001` | 模型连接失败 | 模型连通性测试失败 |
| `12002` | 模型响应超时 | LLM 调用超时 |
| `13001` | 知识库文件格式不支持 | 上传了非 PDF/Markdown/TXT 文件 |
| `13002` | 知识库文件为空 | 上传文件无有效文本内容 |
| `14001` | Agent 迭代超限 | ReAct 循环达到 maxIterations |
| `14002` | Agent 工具调用失败 | 工具返回错误 |

### 5.3 服务端错误（2xxxx）

| code | message | 使用场景 |
|---|---|---|
| `20001` | 系统内部错误 | 未预期的 RuntimeException，全局兜底 |
| `20002` | 数据库操作失败 | MyBatis 执行异常 |
| `20003` | 外部服务调用失败 | HTTP 工具或 MCP Server 调用失败 |
| `20004` | 模型调用失败 | Spring AI ChatClient 抛出异常 |
| `20005` | 向量化服务异常 | Embedding 接口不可用 |
| `20006` | 文件读写失败 | 知识库文件解析或存储异常 |

### 5.4 Java 实现

```java
// 位置：com.hify.common.exception.ErrorCode
public enum ErrorCode {
    // 通用
    SUCCESS(0, "ok"),
    PARAM_INVALID(10001, "参数校验失败"),
    REQUEST_BODY_UNREADABLE(10002, "请求体不可读"),
    PAGE_SIZE_EXCEEDED(10003, "分页 size 超限"),

    // 资源
    NOT_FOUND(11001, "资源不存在"),
    NAME_DUPLICATE(11002, "资源名称重复"),
    REFERENCED(11003, "资源被引用无法删除"),

    // 模型
    MODEL_CONNECT_FAILED(12001, "模型连接失败"),
    MODEL_TIMEOUT(12002, "模型响应超时"),

    // 知识库
    FILE_FORMAT_UNSUPPORTED(13001, "知识库文件格式不支持"),
    FILE_EMPTY(13002, "知识库文件为空"),

    // Agent
    AGENT_MAX_ITERATIONS(14001, "Agent 迭代超限"),
    AGENT_TOOL_FAILED(14002, "Agent 工具调用失败"),

    // 服务端
    INTERNAL_ERROR(20001, "系统内部错误"),
    DB_ERROR(20002, "数据库操作失败"),
    EXTERNAL_SERVICE_FAILED(20003, "外部服务调用失败"),
    MODEL_CALL_FAILED(20004, "模型调用失败"),
    EMBEDDING_FAILED(20005, "向量化服务异常"),
    FILE_IO_ERROR(20006, "文件读写失败");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }
}
```

```java
// 位置：com.hify.common.exception.BizException
public class BizException extends RuntimeException {
    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() { return errorCode; }
}
```

### 5.5 使用示例

```java
// 抛出业务异常——全局异常处理器自动转为 ApiResponse
throw new BizException(ErrorCode.NOT_FOUND, "Agent 不存在: id=" + id);

// 带校验注解的 DTO 校验失败时，由全局异常处理器统一拦截 MethodArgumentNotValidException
// 转为: { "code": 10001, "message": "参数校验失败: name 不能为空", "data": null }

// Controller 中不需要 try-catch，全局异常处理器兜底所有异常
```

### 5.6 强制规则

| 编号 | 规则 |
|---|---|
| EC-01 | 所有业务异常通过 `BizException` 抛出，不允许 Controller 中 try-catch 手动组装错误响应 |
| EC-02 | `message` 字段是人类可读的简短描述，不超过一行 |
| EC-03 | 不在 `message` 中暴露数据库结构或堆栈信息 |
| EC-04 | 不在 `data` 字段中放错误详情，错误信息通过 `code` + `message` 传达 |
| EC-05 | 新增错误码先查本文件是否有可复用的现有码，避免重复定义 |

---

## 6. 其他约定

### 6.1 时间格式

全部时间字段使用 ISO 8601 格式，时区 UTC：

```json
{ "createdAt": "2026-07-27T10:30:00+08:00" }
```

### 6.2 HTTP 方法语义

| 方法 | 语义 | 幂等 |
|---|---|---|
| `GET` | 查询，不改变服务端状态 | ✅ |
| `POST` | 创建新资源或执行动作 | ❌ |
| `PUT` | 更新已有资源（部分更新） | ✅ |
| `DELETE` | 删除资源 | ✅ |

### 6.3 流式接口（SSE）

SSE 接口不走 `ApiResponse` 包装，直接返回 `text/event-stream`：

```
GET /api/v1/chat/sessions/{id}/stream
Content-Type: text/event-stream

data: {"type":"token","content":"你好"}
data: {"type":"tool_call","tool":"web_search","args":{...}}
data: {"type":"tool_result","result":"..."}
data: {"type":"done"}
```

### 6.4 Controller 方法签名模板

```java
@RestController
@RequestMapping("/api/v1/{module}/{resources}")
@RequiredArgsConstructor
public class XxxController {

    private final XxxService xxxService;

    @PostMapping
    public ApiResponse<XxxResponse> create(@Valid @RequestBody CreateXxxRequest request) { ... }

    @GetMapping("/{id}")
    public ApiResponse<XxxResponse> getById(@PathVariable Long id) { ... }

    @GetMapping
    public ApiResponse<PageResponse<XxxResponse>> list(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) { ... }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id,
                                     @Valid @RequestBody UpdateXxxRequest request) { ... }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) { ... }
}
```

---

## 7. 版本记录

| 日期 | 变更 |
|---|---|
| 2026-07-27 | 初始版本：路径约定、统一响应、分页、空值、错误码 |
