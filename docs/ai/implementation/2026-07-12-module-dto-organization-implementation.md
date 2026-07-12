# 模块 DTO 组织规范与代码迁移实现说明

## 1. 本次改动解决了什么问题

本次改动分为两个连续阶段，共同完成同一个模块 DTO 组织重构：

- 阶段一：将“移除响应 `vo`、统一使用请求/响应 DTO”的方案沉淀为设计、accepted ADR、`AGENTS.md` 强制规则、功能开发检查、代码复盘检查和专用执行提示词。
- 阶段二：将 `auth`、`system` 的存量响应类型迁入 `dto/response`，统一响应类型命名，收敛 `TokenService` 入参，并同步当前状态文档和 OpenAPI 回归测试。

最终解决的问题包括：

- 消除 `dto` 与响应 `vo` 的概念重叠，以及 View Object / Value Object 的缩写歧义。
- 修正同级 `dto/query`、`dto/request` 分类重叠的问题，统一为 `dto/request`、`dto/response`。
- 让后续活动、场次、票种、订单、支付、通知等模块持续遵循同一组织原则。
- 将含义模糊的 `UserInfo`、`PingInfo`、`EchoInfo` 改为 `UserResponse`、`PingResponse`、`EchoResponse`。
- 避免接口响应 DTO 泄漏到 token 内部技术边界。
- 保证代码、规则、ADR、路线图、面试材料和 OpenAPI 文档处于一致的最终状态。

## 2. 改动内容

- 新增了什么
  - 新增设计文档：`docs/ai/design/2026-07-12-module-dto-organization-design.md`。
  - 新增 ADR：`docs/ai/adr/2026-07-12-module-dto-organization-convention.md`。
  - 新增专用执行提示词：`prompts/module-dto-organization-refactor.md`。
  - 新增 `modules/auth/dto/response` 下的 `LoginResponse`、`TokenPairResponse`、`UserResponse`。
  - 新增 `modules/system/dto/response` 下的 `PingResponse`、`EchoResponse`。
  - 新增 `OpenApiContractTest`，集中验证跨模块响应 Schema 和 `$ref`。
  - 新增本文作为本次重构唯一的最终实现说明。
- 修改了什么
  - `AGENTS.md` 新增模块传输对象强制组织规则，并在迁移完成后清理存量例外说明。
  - `prompts/feature-task.md` 新增新功能开发时的 DTO 组织要求。
  - `prompts/review-task.md` 新增旧 `vo`、同级 `dto/query` 和响应 DTO 边界泄漏检查。
  - 更新 auth、system 的 Controller、Service、Service 实现、Mapper 结果注释和相关泛型/import，统一使用新的响应 DTO。
  - 将 `getUserInfo`、`toUserInfo`、`userInfo` 等内部命名同步调整为 `UserResponse` 语义。
  - 将 `TokenService.issueAccessToken(UserInfo, String)` 改为 `issueAccessToken(Long userId, String sessionId)`，登录与 refresh 流程传入 `userResponse.id()`。
  - 更新 README、阶段路线图和 stage-0/stage-1 面试材料中的当前目录树、请求/响应 DTO 术语及类型引用。
  - 将配套 ADR 和设计文档的代码迁移状态更新为 completed。
  - 将全局 OpenAPI Schema 断言从 `SystemControllerTest` 迁入职责独立的 `OpenApiContractTest`。
- 删除了什么
  - 删除 auth 模块原 `vo` 下的 `LoginResponse`、`TokenPairResponse`、`UserInfo`。
  - 删除 system 模块原 `vo` 下的 `PingInfo`、`EchoInfo`。
  - 删除迁移后为空的模块 `vo` 目录；没有保留旧 package、旧类、继承桥接、弃用包装或 OpenAPI Schema 别名。
  - 合并并删除阶段性的 governance/refactor 两份实现说明，避免同一重构主题存在重复文档和相互矛盾的当前限制。

## 3. 为什么这样设计

- `dto/request` 与 `dto/response` 按数据穿越 HTTP 边界的方向分类，语义互斥且可从包路径直接判断。
- 查询参数绑定对象仍属于请求，因此 `AdminUserQueryRequest` 继续留在 `dto/request`，不创建同级 `dto/query`。
- 项目尚未上线，没有调用方兼容负担，直接改名可以避免长期维护重复类型和旧 Schema 别名。
- `TokenService` 签发 access token 只需要用户 ID；传入 `Long userId` 比依赖 `UserResponse` 更准确，同时保持 `JwtClaims` 内容、随机 JTI、session ID、token TTL 与 refresh 轮换流程不变。
- Entity、Mapper 参数/结果、安全模型和 `common/api` 继续保留在原有边界，本次没有扩大为业务分层重构。
- `AGENTS.md` 是持久规则入口，功能提示词负责开发前约束，review 提示词负责开发后审计，三者共同避免规则只存在于单篇 ADR。
- 设计、规则治理和 Java 迁移服务于同一个 ADR。最终保留一份实现说明，比按智能体或执行阶段拆成两份文档更便于复盘；阶段历史仍由 Git 提交记录保留。
- OpenAPI 是全局契约，将跨模块 Schema 断言放在专用测试中，可以避免 auth 类型变化错误地归因到 system Controller 测试。

## 4. 替代方案

- 方案 A：保留旧 `vo` 类型并增加兼容转发类。
  - 未采用。项目尚未上线，不存在兼容收益；兼容层会让新旧规范长期并存。
- 方案 B：创建与 `request` 同级的 `dto/query`。
  - 未采用。HTTP 查询参数也是请求，使用 `*QueryRequest` 已能表达查询语义。
- 方案 C：让 `TokenService` 继续接收 `UserResponse`，或新增只包装用户 ID 的中间 DTO。
  - 未采用。前者造成响应 DTO 边界泄漏，后者没有新增语义，只会制造无价值转换。
- 方案 D：只新增 ADR，不修改 `AGENTS.md` 和通用提示词。
  - 未采用。后续智能体不一定主动找到单篇 ADR，无法形成持久约束和 review 闭环。
- 方案 E：分别保留规则治理实现说明和代码迁移实现说明。
  - 未采用。两者属于同一重构的阶段性执行，分开保留会重复解释设计理由，并让第一份文档持续显示已经过期的“迁移待完成”限制。
- 方案 F：顺便引入 CQRS、Adapter 分层或 ArchUnit 依赖。
  - 未采用。当前规模不需要重量级抽象；既有规则、静态扫描和测试足以关闭目标。

## 5. 测试与验证

- 规则与文档验证
  - 设计文档保持模板规定的 1-12 章节顺序。
  - ADR 保持标题、状态、背景、决策、备选方案、理由和影响结构。
  - `AGENTS.md`、功能提示词、review 提示词和专用执行提示词采用同一套 DTO 规则。
  - 设计与 ADR 均标记 Java 迁移已完成，不再保留待迁移状态。
- 编译和测试
  - `cd backend && mvn -q -DskipTests compile`：通过。
  - `mvn -q -Dtest=OpenApiContractTest,SystemControllerTest,AuthIntegrationTest test -Ptest`：通过。
  - `mvn -q test -Ptest`：通过，87 个测试，0 失败、0 错误、0 跳过。
- 结构与契约验证
  - 静态检查确认模块下没有 `vo` 目录、没有同级 `dto/query`，主代码、测试和当前状态文档没有旧 `.vo` package 或三个旧类型名。
  - 既有 system 与 auth 集成测试验证 system、注册、登录、refresh、当前用户和管理员用户列表等响应字段及主要失败场景保持不变。
  - `OpenApiContractTest` 通过 MockMvc 请求 `/v3/api-docs`，确认 `UserResponse`、`PingResponse`、`EchoResponse` 组件存在且文档包含指向这些组件的 `$ref`。
  - `git diff --check` 通过。

## 6. 已知限制

- 本次没有修改数据库、缓存、事务、幂等、并发控制或权限规则，也没有为这些非目标引入新的验证场景。
- OpenAPI 校验通过测试环境的完整 Spring 上下文与 MockMvc 完成；直接使用 `spring-boot:run -Ptest` 时，测试资源和 test-scope H2 不在运行时 classpath，因此不能把该命令作为 test profile 的手工启动方式。
- 当前通过 ADR、项目规则、review 检查和静态搜索约束 DTO 结构；如果后续模块数量明显增加，可再评估是否增加无重量级依赖的自动化包结构守卫。
- 专用迁移提示词作为可复用执行基线继续保留；当前仓库已经完成该迁移，不应重复执行。

## 7. 对后续版本的影响

- 对简历可用版的价值
  - 请求/响应模型命名和目录统一，能够清楚展示 HTTP、持久化、领域和安全技术边界之间的职责划分。
  - 展示项目通过设计、ADR、持久规则、代码迁移和测试完成了一次完整工程治理闭环。
  - OpenAPI 回归测试会阻止后续重命名或迁移导致响应 Schema 丢失引用。
- 对微服务 / 云原生演进的影响
  - 明确的请求/响应 DTO 结构有利于后续拆分服务契约或迁移到 OpenAPI First。
  - 领域值对象不再与响应 `vo` 混淆，为后续复杂领域建模保留空间。
  - `TokenService` 的最小入参降低了 auth 响应模型与 JWT 技术实现之间的耦合，便于未来独立演进认证基础设施。
