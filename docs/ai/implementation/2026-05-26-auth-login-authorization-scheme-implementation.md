# 登录响应授权方案字段命名调整实现说明

## 1. 本次改动解决了什么问题

- 解决 `LoginResponse.tokenType=Bearer` 与 `JwtClaims.tokenType=access` 同名但语义不同的问题。
- 将登录响应中的客户端授权方案字段改为 `authorizationScheme`，让字段名直接表达 HTTP `Authorization` 头使用的 scheme。
- 保持 JWT 内部 `JwtClaims.tokenType` 不变，继续服务于 JWT `typ=access` 的用途校验。

## 2. 改动内容
- 新增了什么
  - 新增设计文档：`docs/ai/design/2026-05-26-auth-login-authorization-scheme-design.md`。
  - 新增本实现说明文档。
- 修改了什么
  - `LoginResponse` record component 从 `tokenType` 改为 `authorizationScheme`。
  - `AuthServiceImpl` 中返回登录响应时继续传入 `Bearer`，并将内部常量名从 `TOKEN_TYPE_BEARER` 改为 `AUTHORIZATION_SCHEME_BEARER`。
  - `AuthIntegrationTest` 登录成功断言从 `$.data.tokenType` 改为 `$.data.authorizationScheme`。
  - 更新认证相关文档示例中的登录响应字段名。
- 删除了什么
  - 未删除业务逻辑。
  - 登录响应不再输出旧字段 `tokenType`。

## 3. 为什么这样设计
- 关键设计原因
  - `Bearer` 是 HTTP Authorization scheme，不是 JWT 内部 token 用途类型，用 `authorizationScheme` 可以减少概念冲突。
  - `JwtClaims.tokenType` 已经准确表达 JWT 内部 `typ=access`，用户明确要求保持不变，因此不修改 JWT 签发、解析和测试。
  - 直接修改 record component 名称即可让 Jackson 输出新的 JSON 字段，改动面小且行为清晰。
- 与项目当前阶段的匹配点
  - 当前项目处于学习型/简历型阶段，优先保证概念清晰和契约可读。
  - 该改动只触及 auth 模块的响应 VO、登录组装和测试，不引入额外兼容层或复杂配置。

## 4. 替代方案
- 方案 A：同时返回 `tokenType` 和 `authorizationScheme`
  - 优点：旧客户端可以平滑迁移。
  - 未采用原因：当前没有真实外部客户端兼容压力，双字段会继续保留概念混淆。
- 方案 B：Java 字段改名，但通过 `@JsonProperty("tokenType")` 保持旧 JSON 字段
  - 优点：内部代码更清晰，外部 API 不破坏。
  - 未采用原因：用户要求改的是 `LoginResponse` 中的字段名，且本次目标就是让响应契约也消除歧义。
- 方案 C：把 `JwtClaims.tokenType` 也重命名为 `tokenPurpose`
  - 优点：内部 JWT 语义也更直观。
  - 未采用原因：用户明确要求 `JwtClaims` 保持不变，本次不扩大修改范围。

## 5. 测试与验证
- 跑了哪些测试
  - `mvn -Dtest=AuthIntegrationTest,JwtCodecTest test`
  - `mvn test`
- 手工验证了哪些场景
  - 本次未启动服务做手工 API 验证，使用集成测试覆盖登录响应 JSON 契约。
- 结果如何
  - 目标测试通过：29 个用例，Failures: 0，Errors: 0，Skipped: 0。
  - 全量测试通过：69 个用例，Failures: 0，Errors: 0，Skipped: 0。
  - 登录成功响应断言已覆盖 `$.data.authorizationScheme == "Bearer"`。
  - JWT 解析断言仍覆盖 `claims.tokenType() == JwtClaims.ACCESS_TOKEN_TYPE`。

## 6. 已知限制
- 当前版本还缺什么
  - 本次是破坏性 API 字段变更，未提供旧字段 `tokenType` 的兼容输出。
- 哪些地方后面需要继续演进
  - 如果后续出现真实前端或第三方客户端，需要在接口变更前补充 API 版本策略或兼容窗口。

## 7. 对后续版本的影响
- 对简历可用版的价值
  - 登录响应契约更准确地区分“HTTP 授权方案”和“JWT 内部用途类型”，有利于面试时解释认证模型边界。
- 对微服务 / 云原生演进的影响
  - 后续认证服务拆分时，外部 API 契约和内部 token claim 语义更清晰，减少跨服务误用。
