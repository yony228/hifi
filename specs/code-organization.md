# Hify 代码组织规范

**目标受众：AI 编码 Agent。本文档定义精确规则，不得模糊解释。**

---

## 一、文件系统映射

### 1.1 Maven 模块与包路径的强制对应

```
{module-name} → src/main/java/com/hify/{module-package}/
```

| Maven 模块 | Java 包路径 |
|---|---|
| hify-agent | `com.hify.agent` |
| hify-workflow | `com.hify.workflow` |
| hify-chat | `com.hify.chat` |
| hify-knowledge | `com.hify.knowledge` |
| hify-tool | `com.hify.tool` |
| hify-model | `com.hify.model` |
| hify-common | `com.hify.common` |
| hify-app | `com.hify`（启动类） |

**规则：**
- 任何 `.java` 文件的包声明必须与上述对应关系一致
- 禁止将文件放入其他模块的包路径下
- `hify-app` 下只放 `HifyApplication.java` 和全局 `config/` 包，不放任何业务代码

---

## 二、业务模块内部分层（Agent / Workflow / Chat / Knowledge / Tool / Model 通用）

### 2.1 固定子包结构

每个业务模块必须包含以下子包结构：

```
com.hify.{module}/
├── controller/           ← HTTP 层（必须）
├── service/              ← 业务逻辑层（必须）
├── mapper/               ← 数据访问层（必须）
├── entity/               ← 数据库实体（必须）
└── dto/                  ← 数据传输对象（必须）
    ├── request/          ← 请求 DTO（必须）
    └── response/         ← 响应 DTO（必须）
```

**一个模块的最小文件集合：**

```
com.hify.{module}/
├── controller/{Module}Controller.java
├── service/{Module}Service.java
├── service/impl/{Module}ServiceImpl.java
├── mapper/{Module}Mapper.java
├── entity/{Entity}.java               ← 至少一张表对应一个 Entity
└── dto/
    ├── request/Create{Entity}Request.java
    └── response/{Entity}Response.java
```

### 2.2 Controller 层——精确规则

**职责：** 接收 HTTP 请求 → 参数校验 → 调用 Service → 组装统一响应。**零业务逻辑。**

```java
// 正确示例
@RestController
@RequestMapping("/api/v1/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @PostMapping
    public ApiResponse<AgentResponse> create(@Valid @RequestBody CreateAgentRequest request) {
        AgentResponse result = agentService.create(request);  // 直接透传，不加工
        return ApiResponse.ok(result);
    }

    @GetMapping
    public ApiResponse<List<AgentResponse>> list() {
        List<AgentResponse> result = agentService.listAll();
        return ApiResponse.ok(result);
    }
}
```

**强制规则：**

| 规则编号 | 规则内容 | 违规示例 |
|---|---|---|
| C-01 | Controller 只持有 Service 依赖，不得注入 Mapper | `private final AgentMapper mapper;` ❌ |
| C-02 | 不得包含 if/else 业务判断 | `if (request.getType() == "react") { ... }` ❌ |
| C-03 | 不得直接操作数据库或 Redis | `redisTemplate.opsForValue().get(...)` ❌ |
| C-04 | 不得调用其他模块的 Controller | `otherController.xxx()` ❌ |
| C-05 | 每个方法必须返回 `ApiResponse<T>` 包装 | `return agent;` ❌ → `return ApiResponse.ok(agent);` ✅ |
| C-06 | 入参 DTO 必须加 `@Valid` 校验 | `@RequestBody CreateAgentRequest request` ❌ |
| C-07 | 类注解固定：`@RestController` + `@RequestMapping("/api/v1/{module}")` + `@RequiredArgsConstructor` | 缺失任意一个 ❌ |

### 2.3 Service 层——精确规则

**职责：** 实现所有业务逻辑、编排调用、管理事务。**这是系统中唯一包含业务判断的层。**

**强制分接口和实现：**

```
com.hify.agent.service/
├── AgentService.java              ← 接口（必须）
└── impl/
    └── AgentServiceImpl.java       ← 实现（必须）
```

**接口定义规范：**

```java
// 接口：只定义方法签名 + 业务级 Javadoc
public interface AgentService {
    /**
     * 创建 Agent 配置并持久化。
     * @param request 包含 name、systemPrompt、modelId、toolIds、knowledgeId
     * @return 创建完成的 Agent 视图，含自增 ID
     */
    AgentResponse create(CreateAgentRequest request);

    AgentResponse getById(Long id);

    List<AgentResponse> listAll();

    void update(Long id, UpdateAgentRequest request);

    void delete(Long id);
}
```

**实现类规则：**

```java
// 正确示例
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    private final AgentMapper agentMapper;          // ✅ 注入本模块 Mapper
    private final ModelService modelService;         // ✅ 注入其他模块 Service（接口，不是实现）
    private final ToolService toolService;           // ✅ 跨模块只调 Service
    private final LogService logService;             // ✅ common 中的基础能力

    @Override
    @Transactional
    public AgentResponse create(CreateAgentRequest request) {
        // 1. 校验引用存在（业务逻辑）
        modelService.getById(request.getModelId());  // 不存在会抛异常

        // 2. 组装 Entity
        Agent agent = Agent.from(request);

        // 3. 持久化
        agentMapper.insert(agent);

        // 4. 写日志（横切关注点）
        logService.write(LogRecord.create("agent", agent.getId(), "创建成功"));

        // 5. 返回 DTO
        return AgentResponse.from(agent);
    }
}
```

**强制规则：**

| 规则编号 | 规则内容 | 违规示例 |
|---|---|---|
| S-01 | Service 必须定义接口，放在 `service/` 下，实现放在 `service/impl/` 下 | 只有 `AgentServiceImpl.java` 没有 `AgentService.java` ❌ |
| S-02 | 实现类必须加 `@Service` + `@RequiredArgsConstructor` | 缺失注解 ❌ |
| S-03 | 实现类可注入：本模块 Mapper、其他模块 Service 接口、common 组件 | 注入其他模块 Mapper ❌ |
| S-04 | **绝对禁止注入其他模块的 Mapper** | `private final ToolMapper toolMapper;` ❌ |
| S-05 | **绝对禁止注入其他模块的 ServiceImpl** | `private final AgentServiceImpl impl;` ❌ |
| S-06 | 写操作（INSERT/UPDATE/DELETE）必须加 `@Transactional` | `create()` 缺 `@Transactional` ❌ |
| S-07 | 读操作不加 `@Transactional`（提升性能） | `getById()` 加 `@Transactional` ❌ |
| S-08 | 方法返回值必须是 DTO，不得返回 Entity | `public Agent create(...)` ❌ → `public AgentResponse create(...)` ✅ |
| S-09 | 方法入参如果是复杂对象，用 DTO，禁止用 Entity | `public void update(Agent agent)` ❌ → `public void update(Long id, UpdateAgentRequest req)` ✅ |
| S-10 | Entity → DTO 转换在 Service 层完成（调用 Entity 上的 `toResponse()` 或 DTO 上的 `from(entity)`） | 把 Entity 直接返回给 Controller ❌ |
| S-11 | 跨模块调用前必须校验引用存在性 | 直接拿 modelId 组装，不调 `modelService.getById()` 校验 ❌ |

### 2.4 Mapper 层——精确规则

**职责：** 数据库 CRUD。**一个 Mapper 对应一张表。零业务逻辑。**

```java
// 正确示例
@Mapper
public interface AgentMapper {

    @Insert("INSERT INTO agent (name, config_json, model_id, knowledge_id, created_at, updated_at) " +
            "VALUES (#{name}, #{configJson}, #{modelId}, #{knowledgeId}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Agent agent);

    @Select("SELECT * FROM agent WHERE id = #{id}")
    Agent findById(Long id);

    @Select("SELECT * FROM agent ORDER BY updated_at DESC")
    List<Agent> findAll();

    @Update("UPDATE agent SET name = #{name}, config_json = #{configJson}, " +
            "model_id = #{modelId}, knowledge_id = #{knowledgeId}, updated_at = NOW() WHERE id = #{id}")
    int update(Agent agent);

    @Delete("DELETE FROM agent WHERE id = #{id}")
    int deleteById(Long id);
}
```

**强制规则：**

| 规则编号 | 规则内容 | 违规示例 |
|---|---|---|
| M-01 | 接口必须加 `@Mapper` 注解 | 缺 `@Mapper` ❌ |
| M-02 | 方法名必须语义化：`insert` / `findById` / `findAll` / `update` / `deleteById` | `save` / `get` / `list` ❌ |
| M-03 | 入参和返回值必须是 Entity，不是 DTO | `int insert(CreateAgentRequest req)` ❌ |
| M-04 | 不得包含业务判断（if/else/switch） | Mapper XML 里有 `<if test="type == 'react'">` ❌ |
| M-05 | 每个 SQL 做且只做一件事：单表 CRUD | 一条 SQL JOIN 三张表做业务聚合 ❌ |
| M-06 | 禁止跨模块注入 Mapper | 在 `hify-agent` 里注入 `ModelMapper` ❌ |
| M-07 | 简单 SQL 用注解、复杂 SQL（三表以上关联）用 XML | 20 行的动态 SQL 塞在 `@Select` 注解里 ❌ |

### 2.5 Entity 层——精确规则

**职责：** 与数据库表字段一一对应。**POJO，不含任何业务逻辑。**

```java
// 正确示例
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Agent {
    private Long id;
    private String name;
    private String configJson;     // 数据库字段 config_json → Java 驼峰 configJson
    private Long modelId;
    private Long knowledgeId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 唯一允许的"方法"：Entity ↔ DTO 转换的工厂方法 */
    public static Agent from(CreateAgentRequest request) {
        Agent agent = new Agent();
        agent.setName(request.getName());
        agent.setConfigJson(request.getConfigJson());
        agent.setModelId(request.getModelId());
        agent.setKnowledgeId(request.getKnowledgeId());
        return agent;
    }
}
```

**强制规则：**

| 规则编号 | 规则内容 | 违规示例 |
|---|---|---|
| E-01 | 类注解：`@Data` + `@NoArgsConstructor` + `@AllArgsConstructor`（Lombok） | 手写 getter/setter ❌ |
| E-02 | 字段名：数据库下划线 → Java 驼峰。MyBatis 自动映射 | `private Long model_id;` ❌ |
| E-03 | 只能包含静态工厂方法（`from(DTO)`），不得包含任何业务方法 | `public boolean isValid()` ❌ |
| E-04 | 字段类型：主键 `Long`，时间 `LocalDateTime`，JSON 文本 `String` | `Date` ❌ → `LocalDateTime` ✅ |
| E-05 | 一个 Entity 对应一张表，类名 = 表名转驼峰 | `agent_config` → `AgentConfig`，不是 `AgentConf` |

### 2.6 DTO 层——精确规则

**职责：** 定义输入输出的数据结构。**与 Entity 完全解耦。**

**目录结构：**

```
com.hify.{module}/dto/
├── request/
│   ├── Create{Entity}Request.java
│   └── Update{Entity}Request.java
└── response/
    └── {Entity}Response.java
```

**Request DTO 规范：**

```java
// 正确示例
@Data
public class CreateAgentRequest {
    @NotBlank(message = "Agent 名称不能为空")
    private String name;

    @NotBlank(message = "System Prompt 不能为空")
    private String systemPrompt;

    @NotNull(message = "模型 ID 不能为空")
    private Long modelId;

    private List<Long> toolIds = Collections.emptyList();
    private Long knowledgeId;
    private String configJson;
    private AgentStrategy strategy = AgentStrategy.FUNCTION_CALL;  // 枚举，有默认值
}
```

**Response DTO 规范：**

```java
// 正确示例
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentResponse {
    private Long id;
    private String name;
    private String configJson;
    private Long modelId;
    private String modelName;        // 冗余展示字段，Service 层填充
    private Long knowledgeId;
    private String knowledgeName;    // 冗余展示字段
    private List<ToolBrief> tools;   // 嵌套 DTO，不是 Entity
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Entity → Response 转换 */
    public static AgentResponse from(Agent agent) {
        AgentResponse r = new AgentResponse();
        r.setId(agent.getId());
        r.setName(agent.getName());
        r.setConfigJson(agent.getConfigJson());
        r.setModelId(agent.getModelId());
        r.setKnowledgeId(agent.getKnowledgeId());
        r.setCreatedAt(agent.getCreatedAt());
        r.setUpdatedAt(agent.getUpdatedAt());
        return r;
    }
}
```

**强制规则：**

| 规则编号 | 规则内容 | 违规示例 |
|---|---|---|
| D-01 | 入参 DTO 放 `dto/request/`，出参 DTO 放 `dto/response/` | 都堆在 `dto/` 根下 ❌ |
| D-02 | Request DTO 字段必须加 `@NotNull` / `@NotBlank` / `@NotEmpty` 校验注解 | `private String name;` 零校验 ❌ |
| D-03 | Response DTO 可以有冗余展示字段（如 `modelName`），但必须在 Service 层赋值 | Controller 层手动调 `modelService` 填充 ❌ |
| D-04 | Response 中可以嵌套其他 Response DTO，但**绝不嵌套 Entity** | `private List<Tool> tools;` ❌ → `private List<ToolBrief> tools;` ✅ |
| D-05 | 枚举字段必须指定默认值 | `private AgentStrategy strategy;` ❌ → `= AgentStrategy.FUNCTION_CALL;` ✅ |
| D-06 | 集合字段必须初始化为空集合 | `private List<Long> toolIds;` ❌ → `= Collections.emptyList();` ✅ |

### 2.7 领域子包（特殊场景）

当模块内部有独立子域时（如 agent 模块的 ReAct 策略、knowledge 模块的文本分段），允许在模块包下创建**一级领域子包**：

```
com.hify.agent/
├── controller/
├── service/
├── mapper/
├── entity/
├── dto/
└── strategy/                     ← 领域子包（一级，不是 api/domain/infra）
    ├── ReactLoop.java
    ├── FunctionCallStrategy.java
    └── ToolInvoker.java
```

**领域子包规则：**

| 规则编号 | 规则内容 |
|---|---|
| DOM-01 | 领域子包只建一级，**不做 api/domain/infra 三级分层** |
| DOM-02 | 领域子包中的类不直接访问 Controller 或 Mapper |
| DOM-03 | 领域子包中的类可以持有 Service 依赖（被 Service 注入的普通 Bean） |
| DOM-04 | 一个模块的领域子包不超过 3 个。超过说明模块该拆分了 |

---

## 三、hify-common 模块特殊规则

### 3.1 子包结构

```
com.hify.common/
├── config/              ← Spring 全局配置（Jackson、Redis、WebMvc）
├── exception/           ← 异常 + 全局处理器
├── log/                 ← 调用日志（LogService + LogMapper + LogRecord）
├── constant/            ← 全局常量
└── util/                ← 纯静态工具方法
```

### 3.2 强制规则

| 规则编号 | 规则内容 | 违规示例 |
|---|---|---|
| CM-01 | 不放任何业务 Entity | `com.hify.common.entity.Agent` ❌ |
| CM-02 | 不放任何业务 Service | `com.hify.common.service.AgentService` ❌ |
| CM-03 | 不放任何业务 Mapper | `com.hify.common.mapper.AgentMapper` ❌ |
| CM-04 | `config/` 中的类必须加 `@Configuration` | 用 `@Component` 代替 ❌ |
| CM-05 | `util/` 中的方法必须 `public static`，类 `final` + 私有构造器 | 实例方法、可继承 ❌ |
| CM-06 | `exception/` 中 `BizException` 必须继承 `RuntimeException`，构造函数接受 `ErrorCode` 枚举 | 继承 `Exception`（受检异常）❌ |

### 3.3 统一响应 ApiResponse 定义

**位置：** `com.hify.common.dto.ApiResponse`

```java
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

**这个类是唯一的例外**——它放在 `common/dto/` 下，所有模块的 Controller 共用。

---

## 四、跨模块调用

### 4.1 调用链白名单

以下跨模块调用组合**允许**，其他组合**一律禁止**：

| 调用方 | 被调用方 | 注入方式 | 示例 |
|---|---|---|---|
| 任何 ServiceImpl | 其他模块的 Service **接口** | 构造函数注入 | `private final ModelService modelService;` |
| 任何 ServiceImpl | common 中的组件 | 构造函数注入 | `private final LogService logService;` |
| 任何 Controller | 本模块的 Service **接口** | 构造函数注入 | `private final AgentService agentService;` |

**以下调用组合绝对禁止：**

| 禁止行为 | 原因 |
|---|---|
| 注入其他模块的 Mapper | 越过业务层直接访问数据，破坏封装 |
| 注入其他模块的 ServiceImpl | 绕过接口，无法替换实现（测试/Mock/未来拆分） |
| Controller 直接注入其他模块的 Service | Controller 只和本模块 Service 通信，跨模块编排在 Service 层完成 |
| 跨模块 import Entity 类 | Entity 是模块私有的数据表示，跨模块用 Service 返回的 DTO |
| 跨模块 new 对象 | 全部走 Spring DI，不允许手动实例化其他模块的类 |

### 4.2 跨模块调用的检查链

跨模块调用的代码必须遵循以下检查链：

```
1. 注入对方 Service 接口
2. 调用前校验引用存在性（对方 Service 方法内部校验，抛 BizException）
3. 获取对方返回的 DTO（不是 Entity）
4. 用 DTO 中的信息，不访问对方数据库
```

**正确示例：**

```java
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    private final AgentMapper agentMapper;
    private final ModelService modelService;    // ✅ 接口
    private final ToolService toolService;      // ✅ 接口
    private final KnowledgeService knowledgeService; // ✅ 接口

    @Override
    @Transactional
    public AgentResponse create(CreateAgentRequest request) {
        // 校验引用存在——每个 Service 自己的 getById 内部抛 BizException
        modelService.getById(request.getModelId());              // 不存在会炸
        request.getToolIds().forEach(toolService::getById);      // 不存在会炸
        if (request.getKnowledgeId() != null) {
            knowledgeService.getById(request.getKnowledgeId());
        }

        Agent agent = Agent.from(request);
        agentMapper.insert(agent);

        // 组装 Response 时可以用 DTO 补充冗余字段
        ModelResponse model = modelService.getById(request.getModelId());
        return AgentResponse.from(agent, model.getName());  // 传入 modelName
    }
}
```

**错误示例：**

```java
// ❌ 错误 1：注入其他模块 Mapper
private final ModelMapper modelMapper;
modelMapper.findById(request.getModelId());

// ❌ 错误 2：注入 Impl 而不是接口
private final ModelServiceImpl modelService;

// ❌ 错误 3：用了其他模块的 Entity
Model model = modelService.getById(...);  // getById 返回 ModelResponse 才对
agent.setModelName(model.getName());      // model 如果是 Entity，❌

// ❌ 错误 4：一个 Service 什么都干，不调其他模块 Service
// AgentService 自己拼 SQL 去查 model 表
agentMapper.selectWithModel(id);  // Mapper 跨了表 ❌
```

### 4.3 循环依赖检测

| 规则编号 | 规则内容 |
|---|---|
| CYC-01 | 模块间依赖必须是单向无环图（DAG）。依赖方向见 design.md §1.2 |
| CYC-02 | 如果编译报 `Circular dependency`，需要引入第三个模块解耦，或提取接口到 common |
| CYC-03 | 同一个模块内部，Service 之间可以互相注入（同模块不算跨模块） |

---

## 五、命名速查表

| 场景 | 命名格式 | 示例 |
|---|---|---|
| 类名 | PascalCase | `AgentController` |
| 方法名 | camelCase | `getById(Long id)` |
| 包名 | 全小写 | `com.hify.agent` |
| Mapper 方法：新增 | `insert({Entity})` | `int insert(Agent agent);` |
| Mapper 方法：单查 | `findById(Long id)` | `Agent findById(Long id);` |
| Mapper 方法：列表查询 | `findAll()` / `findBy{条件}(...)` | `List<Agent> findByModelId(Long modelId);` |
| Mapper 方法：更新 | `update({Entity})` | `int update(Agent agent);` |
| Mapper 方法：删除 | `deleteById(Long id)` | `int deleteById(Long id);` |
| Service 方法：新增 | `create({Request})` | `AgentResponse create(CreateAgentRequest req);` |
| Service 方法：单查 | `getById(Long id)` | `AgentResponse getById(Long id);` |
| Service 方法：列表查询 | `listAll()` / `listBy{条件}(...)` | `List<AgentResponse> listAll();` |
| Service 方法：更新 | `update(Long id, {Request})` | `void update(Long id, UpdateAgentRequest req);` |
| Service 方法：删除 | `delete(Long id)` | `void delete(Long id);` |
| Entity | 数据库表名转 UpperCamelCase | `agent_config` → `AgentConfig` |
| Entity 字段 | 数据库列名转 lowerCamelCase | `model_id` → `modelId` |

---

## 六、违反这些规则的自动化检测

每写一个文件后，用以下检查清单自检：

```text
□ 包路径匹配 Maven 模块映射（§1.1）
□ Controller：只注入本模块 Service 接口，无 if/else，返回 ApiResponse<T>（§2.2）
□ Service：有接口 + impl 分离，注入的都是接口不是 Impl（§2.3）
□ Service：返回值是 DTO 非 Entity，写操作有 @Transactional（§2.3）
□ Mapper：只做单表 CRUD，入参出参都是 Entity（§2.4）
□ Entity：只有字段 + from() 工厂方法，无业务逻辑（§2.5）
□ DTO：请求有校验注解，响应不嵌套 Entity（§2.6）
□ 跨模块调用：注入 Service 接口，不注入 Mapper/Impl/Entity（§4.1-4.2）
□ 依赖方向符合 DAG，无循环（§4.3）
```
