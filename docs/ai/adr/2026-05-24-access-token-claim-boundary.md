# ADR：access token 只保存最小认证声明并关联服务端会话

## 标题
access token 只保存最小认证声明并关联服务端会话

## 状态
- accepted

## 背景
登录流程开始接入 `auth_sessions` 后，access token 需要能与服务端会话关联，便于后续 logout、refresh token 轮换和设备会话管理继续演进。同时，当前认证过滤器会在解析 JWT 后通过用户 ID 回库加载最新用户状态和角色，已有行为可以保证禁用用户和权限变更更快生效。

需要明确 access token 中到底写哪些 claim，以及是否把角色、权限、用户资料或 token version 一并写入 JWT。

## 决策
本次选择：

- access token 使用 JWT。
- access token 写入：
  - `iss`
  - `sub`
  - `iat`
  - `exp`
  - `jti`
  - `sid`
  - `typ=access`
- access token 不写入角色、权限、用户名、邮箱、用户状态。
- access token 暂不写入 `ver`。
- `sid` 对应 `auth_sessions.session_id`，用于把短期 access token 与服务端认证会话关联。
- `typ=access` 作为 token 类型边界，解析 access token 时必须校验。

## 备选方案
- 方案 1：只保留 `sub`，不新增 claim。
- 方案 2：新增 `jti`、`sid`、`typ`，但不写角色权限。
- 方案 3：把角色和权限写入 access token。
- 方案 4：同时写入 `ver`，用用户级 token version 支持全端失效。

## 决策理由
选择方案 2，原因如下：

- `jti` 可以唯一标识某个 access token，后续接入 denylist、审计或问题排查时有稳定抓手。
- `sid` 可以把 access token 与 `auth_sessions` 关联，为后续单设备 logout 和设备会话管理保留上下文。
- `typ=access` 能避免后续 refresh token、一次性 token 或其他 token 类型被误用到 access token 解析链路。
- 不写角色和权限，可以继续沿用当前“解析 sub 后回库加载最新用户状态和角色”的安全模型。
- 当前项目没有 `users.token_version` 字段；`auth_sessions.version` 服务于 refresh token 轮换乐观锁，不等价于用户级 access token 版本，因此本次不写 `ver`。

## 影响
- 好处：
  - access token 与服务端会话建立关联。
  - JWT 仍保持轻量，不承载会快速变化的权限数据。
  - 后续 denylist、审计、单设备吊销和 refresh token 轮换都有扩展点。
- 代价：
  - 每次受保护请求仍需要回库加载用户状态和角色，不是完全自包含 JWT。
  - 当前不写 `ver`，暂不能通过用户级版本号实现改密或全端失效。
  - logout 即时失效仍需要后续 denylist 或 session 校验补充。
- 后续可能需要调整的地方：
  - 如果新增 `users.token_version`，可在 access token 写入 `ver` 并在认证过滤器校验。
  - 如果引入网关或资源服务，可评估是否把部分低敏、低变化的授权上下文下沉到 token。
  - 如果接入 Redis denylist，可使用 `jti` 作为 denylist key 的一部分。
