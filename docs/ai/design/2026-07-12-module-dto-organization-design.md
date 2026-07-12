# 模块传输 DTO 组织规范与现有代码迁移设计

> 状态：设计已批准，项目规则已落地，Java 代码迁移待后续任务实施。

## 1. 背景

- 当前业务模块使用 `dto/request` 保存请求对象，使用 `vo` 保存响应对象。
- DTO（Data Transfer Object）本身既可以表达输入，也可以表达输出；继续用 `vo` 专门表示响应，会造成概念重复。
- `VO` 在 Java 后端语境中既可能表示 View Object，也可能表示 DDD Value Object。随着活动、票种、订单等领域逐步完善，这种缩写会让接口返回模型与领域值对象产生歧义。
- 阶段 1 路线图曾规划同级的 `dto/request` 与 `dto/query`，但实际的 `AdminUserQueryRequest` 位于 `dto/request`。这说明 `query` 与 `request` 不是互斥分类：HTTP 查询参数本身也是请求。
- 项目当前尚未上线，没有外部调用方或生成客户端兼容负担，因此可以直接收敛包名和类型名，不保留兼容类型、旧包转发类或旧 OpenAPI Schema 名称。
- 本轮只沉淀设计、ADR、协作规则和执行提示词，不迁移 Java 文件。现有 `vo` 代码是明确的待迁移存量，不构成后续模块继续使用 `vo` 的先例。

## 2. 目标

- 所有业务模块统一使用以下传输对象目录：

```text
modules/<module>/dto
├── request
└── response
```

- `dto/request` 保存所有进入 HTTP 边界的复合请求模型，包括 JSON 请求体、表单参数和查询参数绑定对象。
- 查询型请求通过 `*QueryRequest` 命名表达读取语义，仍放在 `dto/request`，不建立同级 `dto/query`。
- `dto/response` 保存所有由接口向调用方输出的业务数据模型，类型统一使用 `*Response` 后缀。
- 不再用模块级 `vo` 包表达接口响应。
- 明确 DTO、领域值对象、持久化参数、持久化结果和安全模型的边界，避免 `dto` 成为所有数据载体的收纳包。
- 将该约定写入 `AGENTS.md`，使其对后续所有模块开发和评审持续生效。
- 为现有 `auth`、`system` 模块给出可直接执行的迁移映射和验证策略。

成功标准：

- 规则层面：`AGENTS.md`、ADR、设计文档和通用任务提示词使用同一套术语和判断标准。
- 后续代码迁移完成后：`backend/src/main/java/com/eventhub/modules` 下不存在用于响应模型的 `vo` 包，也不存在同级 `dto/query` 包。
- 请求和响应类型能够仅根据包路径和类型后缀判断传输方向。
- API 路径、HTTP 方法、JSON 字段、错误码和业务行为保持不变。

## 3. 非目标

- 本轮不移动或重命名任何 Java 文件，不修改 import、方法签名、测试或运行配置。
- 不引入 CQRS 框架，也不新增 `application/command`、`application/query` 等应用层抽象。
- 不把 Entity、Mapper 参数、Mapper 查询结果、JWT Claims、认证主体或领域模型迁入 `dto`。
- 不迁移 `common/api` 下的 `ApiResponse`、`PageRequest`、`PageResponse`；它们是跨模块统一 API 协议和分页模型。
- 不调整数据库表、索引、Flyway 脚本、缓存、事务、幂等或权限规则。
- 不改变任何接口的 JSON 字段以配合 Java 类型改名。
- 不机械改写历史设计文档和历史实现说明；历史文档继续记录当时的真实状态，新 ADR 负责声明旧约定已被后续决策替代。

## 4. 影响范围

### 4.1 当前待迁移代码

- `modules.auth`
  - `vo.LoginResponse` -> `dto.response.LoginResponse`
  - `vo.TokenPairResponse` -> `dto.response.TokenPairResponse`
  - `vo.UserInfo` -> `dto.response.UserResponse`
  - `dto.request.AdminUserQueryRequest` 及其他请求类型保持目录不变
- `modules.system`
  - `vo.PingInfo` -> `dto.response.PingResponse`
  - `vo.EchoInfo` -> `dto.response.EchoResponse`
  - `dto.request.EchoRequest` 保持目录不变
- 受 import 和类型名调整影响的 Controller、Service、Service 实现、Mapper 结果注释和测试。
- `TokenService.issueAccessToken(UserInfo, String)` 当前只读取 `UserInfo.id()`，后续应收敛为 `issueAccessToken(Long userId, String sessionId)`，避免内部 token 服务反向依赖接口响应 DTO。

### 4.2 规则与文档

- `AGENTS.md`：新增强制模块传输对象组织规则。
- `prompts/feature-task.md`：新增开发阶段 DTO 组织要求。
- `prompts/review-task.md`：新增 DTO 包结构和边界泄漏检查项。
- 新增本设计、配套 ADR、规则落地实现说明和专用代码迁移提示词。
- 代码迁移任务完成后，再更新 README、路线图和 `docs/interview` 中描述当前代码结构的内容。

### 4.3 不受影响的资源

- 数据库表、索引、唯一约束和迁移脚本。
- Redis 与其他缓存。
- 外部服务、消息契约和部署配置。

## 5. 领域建模

本决策不新增业务领域实体，但明确以下工程模型：

- 请求 DTO
  - 表示外部输入进入 HTTP 边界时的数据形态。
  - 放在 `modules/<module>/dto/request`。
  - 普通请求以 `*Request` 命名；查询型请求以 `*QueryRequest` 命名。
  - 可以包含 Bean Validation、Spring MVC 参数绑定和 OpenAPI 注解。
- 响应 DTO
  - 表示业务接口返回给调用方的稳定数据投影。
  - 放在 `modules/<module>/dto/response`。
  - 使用 `*Response` 命名，不使用 `*VO` 或含义模糊的 `*Info` 作为新类型约定。
  - 不暴露密码哈希、内部状态或持久化实现细节。
- 应用查询模型
  - 如果未来 Service 需要脱离 HTTP DTO 的独立查询契约，可在有真实复杂度时引入 `application/query`。
  - 它不属于本次 DTO 目录设计，也不能成为创建同级 `dto/query` 的理由。
- 持久化查询条件和结果投影
  - 分别保留在 `mapper/param`、`mapper/result`，例如 `UserQueryCriteria`。
  - Mapper 不直接依赖带 Web 注解和原始输入语义的请求 DTO。
- 领域值对象
  - 例如未来可能出现的 `Money`、`EmailAddress`、`TicketQuantity`。
  - 放在 `domain/valueobject` 或对应聚合附近的语义明确包中，不使用模块根部的缩写 `vo`。

本次工程决策状态流转为：

```text
方案审视 -> 用户批准 -> 规则与 ADR 生效 -> Java 迁移待执行 -> 测试验证 -> 现状文档同步
```

## 6. API 设计

- 现有接口列表、路径和 HTTP 方法全部保持不变，包括：
  - `GET /api/v1/system/ping`
  - `POST /api/v1/system/echo`
  - `POST /api/v1/auth/register`
  - `POST /api/v1/auth/login`
  - `POST /api/v1/auth/refresh`
  - `POST /api/v1/auth/logout`
  - `GET /api/v1/me`
  - `GET /api/v1/admin/users`
  - `PATCH /api/v1/admin/users/{userId}/status`
- 请求字段、响应 JSON 字段、统一响应包装和错误码不变。
- Java 类型从 `UserInfo`、`PingInfo`、`EchoInfo` 改为 `UserResponse`、`PingResponse`、`EchoResponse` 后，允许 OpenAPI Schema 组件名随之更新；项目未上线，不保留旧 Schema 别名。
- `ApiResponse<T>` 与 `PageResponse<T>` 继续作为 `common/api` 中的跨模块统一包装，不复制到每个模块的 `dto/response`。

## 7. 数据设计

- 不调整表结构、字段、索引、唯一约束或 Flyway 版本。
- 不调整 Entity 与数据库映射。
- 不改变 `UserQueryCriteria` 等 Mapper 参数模型。
- 本次只改变 Java 传输类型的包路径、名称以及一个内部 token 签发方法的最小入参边界，不产生数据迁移或一致性问题。

## 8. 关键流程

### 8.1 后续代码迁移正常流程

1. 读取 `AGENTS.md`、本设计和配套 ADR，确认设计基线已冻结。
2. 盘点 `auth`、`system` 中全部 `vo` 类型及其调用方。
3. 先建立 `dto/response` 并迁移响应类型，统一修正 package 与 import。
4. 将 `UserInfo`、`PingInfo`、`EchoInfo` 分别重命名为 `UserResponse`、`PingResponse`、`EchoResponse`。
5. 同步调整方法返回类型、局部变量、转换方法、泛型、Javadoc 与测试中的类型引用。
6. 将 `TokenService.issueAccessToken` 调整为只接收 `userId` 与 `sessionId`。
7. 删除空的 `vo` 目录，确认没有兼容包装类或旧类型残留。
8. 运行编译、目标测试、完整测试、静态搜索和 OpenAPI 手工验证。
9. 更新 README、路线图、面试材料和代码迁移实现说明，使当前状态文档与代码一致。

### 8.2 异常处理流程

- 如果迁移后编译失败，优先通过全仓 `rg` 查找旧 package、旧类型名和泛型引用，不添加旧类型兼容层规避问题。
- 如果 JSON 字段发生变化，应修正 record component、getter 或 Jackson 注解，保持现有接口字段；不能把包重构扩大为业务契约修改。
- 如果 Springdoc Schema 仅发生组件名变化，可接受；如果字段或引用关系异常，必须在迁移任务中修复。
- 如果发现某个类型不是 HTTP 请求或响应 DTO，应停止机械迁移，并按 Entity、Mapper 模型、安全模型或领域模型的真实职责归位。

## 9. 并发 / 幂等 / 缓存

- 本次不涉及库存扣减、订单写入或支付流程，不产生超卖风险。
- 不新增接口或写入操作，不需要新的幂等键。
- 不引入或修改缓存，也没有缓存失效问题。
- refresh token 轮换的并发控制保持不变；`TokenService` 入参收敛只改变内部方法契约，不改变会话状态和数据库条件更新逻辑。

## 10. 权限与安全

- Spring Security 公开路径、认证路径、管理员权限和默认认证规则保持不变。
- JWT Claims 内容和签名逻辑保持不变。
- `TokenService` 只接收签发 access token 实际需要的 `userId` 和 `sessionId`，减少响应 DTO 携带的邮箱、状态、角色等无关数据进入安全技术边界。
- `UserResponse` 继续只输出对调用方安全的用户字段，不暴露 `passwordHash`。
- DTO 包结构调整不能把 `AuthenticatedPrincipal`、`JwtClaims` 等安全模型移动到 `dto`。

## 11. 测试策略

- 编译检查：
  - `cd backend && mvn -q -DskipTests compile`
- 目标测试：
  - `mvn -q -Dtest=SystemControllerTest,AuthIntegrationTest test -Ptest`
  - 覆盖 system 请求/响应、注册、登录、refresh、当前用户、管理员列表、401 和 403。
- 完整测试：
  - `mvn -q test -Ptest`
- 静态结构检查：
  - `find backend/src/main/java/com/eventhub/modules -type d -name vo` 不应返回目录。
  - `rg` 确认主代码和测试中不存在旧 `.vo` package、`UserInfo`、`PingInfo`、`EchoInfo` 引用。
  - 确认不存在同级 `dto/query`。
- API 手工验证：
  - 验证 system、登录、refresh、当前用户和管理员用户列表响应 JSON 字段保持不变。
  - 检查 `/v3/api-docs` 中新响应 Schema 可解析且引用关系正确。
- 文档和补丁检查：
  - `git diff --check`
  - 确认 README、路线图和面试材料中的当前目录结构已在代码迁移后同步更新。

## 12. 风险与替代方案

- 风险：包路径和类型名同时调整，可能漏改 import、泛型、Javadoc 或测试引用。
  - 应对：按模块分步迁移，使用全仓静态搜索并先编译后跑测试。
- 风险：`dto` 被误解为所有数据对象的统一目录。
  - 应对：在 `AGENTS.md` 中明确排除 Entity、Mapper 模型、安全模型和领域模型，并在代码评审提示词中持续检查。
- 风险：未来请求数量增加后 `dto/request` 变得拥挤。
  - 应对：只有出现真实规模时，才能在设计说明中论证后细分为 `dto/request/query` 与 `dto/request/command`；仍不创建同级 `dto/query`。
- 风险：历史文档仍出现 `vo` 或旧类型名。
  - 应对：保留历史真实性，由新 ADR 声明旧约定已被替代；只更新描述当前代码状态的活文档。

备选方案：

- 保留 `dto + vo`：未采用，因为 DTO 已覆盖响应含义，且 `VO` 存在 View Object / Value Object 歧义。
- 使用同级 `dto/query + dto/request + dto/response`：未采用，因为 query 是 request 的子类型，无法形成互斥分类。
- 所有传输类型平铺在 `dto`：未采用，因为活动、订单等模块扩展后可读性会快速下降。
- 使用 `web/request + web/response`：语义清楚，但会扩大为 Web Adapter 分层重构，不符合当前最小闭环原则。
- 立即引入 CQRS command/query：当前业务复杂度不足，过早增加应用层模型和转换成本。
