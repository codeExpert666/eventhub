# 登录响应授权方案字段命名调整设计

## 1. 背景
- 当前登录响应 `LoginResponse.tokenType` 的值为 `Bearer`，表达的是 HTTP `Authorization` 头的授权方案。
- `JwtClaims.tokenType` 的值为 `access`，表达的是 JWT 内部 `typ` claim，用于区分 access token 与后续可能出现的其他用途 token。
- 两者同名但语义不同，容易让维护者误以为登录响应中的 `tokenType` 与 JWT 内部 `typ` claim 是同一个概念。

## 2. 目标
- 将登录响应中的 `tokenType` 重命名为 `authorizationScheme`。
- 保持响应值仍为 `Bearer`，客户端继续使用 `Authorization: Bearer <accessToken>` 访问受保护接口。
- 保持 `JwtClaims.tokenType` 不变，继续表示 JWT 内部用途类型。
- 更新自动化测试和文档示例，避免新旧字段名混用。

## 3. 非目标
- 不调整 JWT payload 中的 `typ=access`。
- 不修改 access token、refresh token 的签发、解析、过期时间和会话创建逻辑。
- 不引入响应字段兼容层，例如同时返回 `tokenType` 和 `authorizationScheme`。
- 不新增数据库表、字段或索引。

## 4. 影响范围
- 涉及模块：
  - `modules.auth.vo.LoginResponse`
  - `modules.auth.service.impl.AuthServiceImpl`
  - `modules.auth` 集成测试
  - 认证相关文档示例
- 涉及表 / 缓存 / 外部接口：
  - 表：无变化。
  - 缓存：无变化。
  - 外部接口：`POST /api/v1/auth/login` 成功响应字段从 `tokenType` 改为 `authorizationScheme`。

## 5. 领域建模
- 核心实体：
  - `LoginResponse`：登录成功返回给客户端的认证结果。
  - `JwtClaims`：access token 内部最小认证声明，本次保持不变。
- 实体关系：
  - `LoginResponse.authorizationScheme=Bearer` 指导客户端拼接 HTTP Authorization 头。
  - `JwtClaims.tokenType=access` 指导服务端校验 JWT 内部用途。
- 关键状态：
  - 无新增状态。

## 6. API 设计
- 接口列表：
  - `POST /api/v1/auth/login`
- 请求参数：
  - 保持不变。
- 响应结构：

```json
{
  "accessToken": "...",
  "refreshToken": "...",
  "authorizationScheme": "Bearer",
  "expiresIn": 1800,
  "refreshExpiresIn": 2592000,
  "sessionId": "...",
  "user": {
    "id": 1,
    "username": "alice",
    "email": "alice@example.com",
    "status": "ENABLED",
    "roles": ["USER"]
  }
}
```

- 字段说明：
  - `authorizationScheme`：HTTP 授权方案，当前固定为 `Bearer`。
  - `accessToken`、`refreshToken`、`expiresIn`、`refreshExpiresIn`、`sessionId`、`user` 语义保持不变。
- 错误码 / 异常场景：
  - 登录失败错误码和异常语义保持不变。
  - 已依赖旧字段 `tokenType` 的客户端需要同步调整字段读取逻辑。

## 7. 数据设计
- 表结构调整：无。
- 索引设计：无。
- 唯一约束：无。
- 数据一致性考虑：
  - 本次仅修改响应字段命名，不影响服务端会话与 token 的一致性。

## 8. 关键流程
- 正常流程：
  1. 用户调用登录接口。
  2. 服务端校验账号密码并创建认证会话。
  3. 服务端签发 access token 和 refresh token。
  4. 响应体返回 `authorizationScheme=Bearer`。
- 异常流程：
  - 登录失败流程保持不变。
- 状态流转：
  - 无新增状态流转。

## 9. 并发 / 幂等 / 缓存
- 是否有超卖风险：不涉及库存。
- 如何防重复提交：不涉及本次改动。
- 缓存放在哪里，为什么：不涉及缓存。

## 10. 权限与安全
- 哪些角色能访问：
  - 登录接口仍然公开访问。
- 鉴权与鉴别约束：
  - `authorizationScheme` 只是响应字段命名调整，不改变 `Authorization: Bearer <accessToken>` 的鉴权方式。
  - `JwtClaims.tokenType` 继续用于服务端校验 JWT `typ=access`，避免其他用途 token 误入 access token 解析链路。

## 11. 测试策略
- 单元测试：
  - JWT 相关单元测试保持不变，确认 `JwtClaims.tokenType` 仍为 `access`。
- 集成测试：
  - 更新登录成功断言，检查 `$.data.authorizationScheme == "Bearer"`。
  - 保留 access token 解析断言，确认 `claims.tokenType() == JwtClaims.ACCESS_TOKEN_TYPE`。
- 接口验证：
  - 可通过登录接口手工确认响应字段已变更。
- 异常场景验证：
  - 登录失败场景不受字段命名影响，依赖现有集成测试覆盖。

## 12. 风险与替代方案
- 当前方案的风险：
  - 这是一次 API 响应字段破坏性变更，旧客户端如果仍读取 `tokenType` 会失败。
- 备选方案：
  - 同时返回 `tokenType` 和 `authorizationScheme` 一段时间，做灰度兼容。
  - 仅修改 Java 代码字段名，但通过 Jackson 注解继续输出旧 JSON 字段 `tokenType`。
- 为什么不选备选方案：
  - 当前项目处于学习型/简历型阶段，尚无真实外部客户端兼容压力。
  - 用户明确要求将 `LoginResponse` 中的 `tokenType` 改为 `authorizationScheme`，直接调整外部契约能最清楚地消除歧义。
