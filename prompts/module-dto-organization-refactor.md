# 模块 DTO 组织结构迁移执行提示词

请使用 `$backend-design-first` skill 完成 EventHub 模块 DTO 组织结构迁移。

这是一个已经完成设计评审并获得批准的执行任务，不需要重新选择包结构方案。请先读取并以以下文件为不可随意偏离的设计基线：

1. `AGENTS.md`
2. `.agents/skills/backend-design-first/SKILL.md`
3. `docs/ai/design/2026-07-12-module-dto-organization-design.md`
4. `docs/ai/adr/2026-07-12-module-dto-organization-convention.md`
5. `docs/ai/implementation/2026-07-12-module-dto-organization-implementation.md`
6. `docs/templates/implementation-note-template.md`

读取后先用简短摘要说明你理解的目标、范围、风险和执行顺序；如果仓库现状没有与基线发生实质冲突，直接继续实现，不要再次停下来等待方案确认。

## 目标

将当前 `auth`、`system` 模块的请求和响应传输对象统一到：

```text
modules/<module>/dto
├── request
└── response
```

迁移完成后，模块不再使用 `vo` 表示响应，也不创建与 `request` 同级的 `dto/query`。

## 已确认前提

- 项目尚未上线，不存在外部调用方或生成客户端兼容要求。
- 不保留旧 package、旧类、继承桥接、弃用包装类或旧 OpenAPI Schema 别名。
- 可以直接把 `UserInfo`、`PingInfo`、`EchoInfo` 重命名为对应的 `*Response`。
- 允许 OpenAPI Schema 组件名随 Java 类型名更新。
- API 路径、HTTP 方法、JSON 字段、错误码和业务行为仍必须保持不变。

## 必须完成的代码迁移

### auth 模块

```text
com.eventhub.modules.auth.vo.LoginResponse
-> com.eventhub.modules.auth.dto.response.LoginResponse

com.eventhub.modules.auth.vo.TokenPairResponse
-> com.eventhub.modules.auth.dto.response.TokenPairResponse

com.eventhub.modules.auth.vo.UserInfo
-> com.eventhub.modules.auth.dto.response.UserResponse
```

- `dto/request` 下现有请求类型保持在原目录。
- `AdminUserQueryRequest` 是 HTTP 查询请求，继续放在 `dto/request`，不要移动到新的 `dto/query`。
- 将 `getUserInfo`、`toUserInfo`、`userInfo` 等与旧类型绑定的私有方法、变量、泛型和注释同步调整为 `UserResponse` 语义。
- 更新 Controller、Service、Service 实现、Mapper 结果注释、测试和其他调用方中的 import 与类型引用。

### system 模块

```text
com.eventhub.modules.system.vo.PingInfo
-> com.eventhub.modules.system.dto.response.PingResponse

com.eventhub.modules.system.vo.EchoInfo
-> com.eventhub.modules.system.dto.response.EchoResponse
```

- `EchoRequest` 继续位于 `dto/request`。
- 更新 `SystemController`、`SystemService`、测试、Javadoc 和其他引用。

### TokenService 边界清理

当前 `TokenService.issueAccessToken(UserInfo userInfo, String sessionId)` 的实现只使用用户 ID。迁移时必须将它收敛为最小必要入参：

```java
String issueAccessToken(Long userId, String sessionId);
```

- `TokenServiceImpl` 直接使用 `userId` 构造 `JwtClaims`。
- 登录和 refresh 流程传入 `userResponse.id()`。
- 不要让 `TokenService` 依赖 `UserResponse`，也不要为此新建没有实际价值的中间 DTO。
- 保持 JWT Claims 内容、access token 签发行为和 refresh token 轮换逻辑不变。

## 明确非目标

- 不修改数据库表、索引、Flyway 脚本或测试数据。
- 不修改缓存、事务、幂等、并发控制或权限规则。
- 不引入 CQRS、Adapter 分层或新的重量级依赖。
- 不把 Entity、Mapper 参数、Mapper 结果、安全模型或领域模型迁入 `dto`。
- 不移动 `common/api` 下的 `ApiResponse`、`PageRequest`、`PageResponse`。
- 不顺手重构与本次包结构无关的业务代码。
- 不提交或推送 Git，除非用户另行明确要求。

## 文档同步要求

代码迁移完成后，更新描述当前代码状态的活文档：

- `README.md`
- `docs/roadmap/stage-0-project-foundation.md`
- `docs/roadmap/stage-1-auth-jwt-rbac.md`
- `docs/roadmap/stage-2-event-session-ticket-query.md`
- `docs/interview/stage-0-foundation/`
- `docs/interview/stage-1-auth-jwt-rbac/`

要求：

- 当前目录树统一改为 `dto/request + dto/response`。
- “DTO/VO”统一改为语义准确的“请求/响应 DTO”。
- 当前类型引用同步使用 `UserResponse`、`PingResponse`、`EchoResponse`。
- 不对 `docs/ai/design`、`docs/ai/implementation`、`docs/ai/adr` 中的历史文档做无差别全局替换；它们应保留当时的实现事实，由 2026-07-12 ADR 声明约定演进。
- 新增或更新最终实现说明：
  - `docs/ai/implementation/2026-07-12-module-dto-organization-implementation.md`
- 实现说明必须遵循模板，并分别记录规则治理和 Java 代码迁移两个实施阶段，最终只保留这一份实现说明。

## 验证要求

先做结构和编译验证，再运行目标测试与完整测试：

```bash
find src/main/java/com/eventhub/modules -type d -name vo
rg -n 'com\.eventhub\.modules\..*\.vo|\b(UserInfo|PingInfo|EchoInfo)\b' \
  src/main src/test README.md docs/roadmap docs/interview

mvn -q -DskipTests compile
mvn -q -Dtest=SystemControllerTest,AuthIntegrationTest test -Ptest
mvn -q test -Ptest

git diff --check
```

完成状态应满足：

- `find ... -name vo` 没有输出。
- 主代码、测试和当前状态文档中不存在旧 `.vo` package 和三个旧类型名。
- 不存在与 `request` 同级的 `dto/query`。
- 编译、目标测试和完整测试全部通过。
- system、注册、登录、refresh、当前用户和管理员用户列表的 JSON 字段保持不变。
- 如果运行环境允许，检查 `/v3/api-docs`，确认新的 Response Schema 引用完整可解析。
- 如果某项验证受环境限制，必须写明限制、已完成的替代验证和剩余风险，不能默认为通过。

## 实施原则

- 按最小可验证步骤执行：先迁移一个模块并编译，再迁移另一个模块，最后统一文档和完整测试。
- 使用文件移动和精确替换，不保留重复实现。
- 更新既有规范注释和 Javadoc，使 package、类型名和职责描述保持一致；不要为简单 import 迁移堆砌无意义注释。
- 发现与设计基线不一致的真实仓库状态时，先以证据说明冲突；只有冲突会实质改变设计时才停下来请求用户决策。

## 最终输出格式

完成后严格按 `AGENTS.md` 输出：

1. 设计摘要
2. 代码改动摘要
3. 为什么这样设计
4. 替代方案
5. 风险与后续优化
6. 已更新的文档列表
7. 验证结果

最终总结中必须明确：是否仍有旧 `vo` / `dto/query` / 旧类型引用、测试执行结果、OpenAPI 检查结果，以及是否存在未完成事项。
