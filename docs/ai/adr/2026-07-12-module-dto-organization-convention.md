# ADR：模块传输对象统一使用 request / response DTO

## 标题

模块传输对象统一放入 `dto/request` 与 `dto/response`，停止使用 `vo` 表示接口响应

## 状态

- accepted
- 决策日期：2026-07-12
- 代码迁移状态：pending

## 背景

EventHub 当前将请求对象放在模块的 `dto/request`，将响应对象放在模块的 `vo`。这种约定存在三个问题：

- DTO 是跨边界传输数据的对象，既可以表达请求，也可以表达响应；`dto` 与“响应 VO”并不是同一层级的互斥概念。
- `VO` 既可能表示 View Object，也可能表示 DDD Value Object，未来引入金额、邮箱、票数等领域值对象时容易混淆。
- 路线图曾规划同级 `dto/query`，而实际查询请求 `AdminUserQueryRequest` 位于 `dto/request`。查询参数本身也是请求，因此 `query` 与 `request` 不能作为同级分类稳定执行。

项目尚未上线，没有调用方兼容负担，可以在当前阶段直接统一包结构和类型命名，不保留旧包、旧类型或旧 OpenAPI Schema 别名。

## 决策

项目采用以下长期规则：

1. 每个业务模块的 HTTP 传输对象统一放在 `modules/<module>/dto`。
2. `dto` 默认只使用两个直接子包：
   - `dto/request`：所有进入 HTTP 边界的复合请求模型。
   - `dto/response`：所有由 HTTP 接口输出的业务数据模型。
3. 查询参数绑定对象也是请求 DTO，使用 `*QueryRequest` 命名并放在 `dto/request`；禁止创建与 `request` 同级的 `dto/query`。
4. 普通请求类型使用 `*Request` 后缀，响应类型使用 `*Response` 后缀。
5. 模块根部不再使用 `vo` 包表示响应，也不新增 `*VO` 响应类型。
6. 如果单个模块的请求 DTO 确实增长到影响可读性，可以在设计说明中论证后细分 `dto/request/query` 与 `dto/request/command`；这种细分不能演变为同级 `dto/query`。
7. `ApiResponse`、`PageRequest`、`PageResponse` 等跨模块统一协议继续留在 `common/api`，不复制到业务模块。
8. DTO 不是通用数据对象目录：
   - Entity 留在 `entity`。
   - Mapper 入参和结果投影留在 `mapper/param`、`mapper/result`。
   - JWT Claims、认证主体等留在安全边界。
   - 领域模型和值对象留在 `domain` 或语义明确的领域包。
9. DDD Value Object 应使用 `domain/valueobject` 或聚合内的明确名称，不使用含义模糊的模块根部 `vo`。
10. 应用 Service 可以返回面向接口的响应 DTO；内部协作服务如果只需要响应 DTO 的少数字段，应改为接收最小必要参数或专用内部模型，不能把响应 DTO 当作通用业务模型传播。
11. 新模块的设计文档必须列出请求 DTO、响应 DTO及其包路径；代码评审必须检查是否违反本 ADR。
12. 本 ADR 自接受之日起立即约束新代码。当前 `auth`、`system` 中的存量 `vo` 是待迁移技术债，不构成规则例外。

当前代码迁移基线以 `docs/ai/design/2026-07-12-module-dto-organization-design.md` 为准。

## 备选方案

- 方案 1：保留 `dto` 表示请求、`vo` 表示响应。
- 方案 2：使用同级 `dto/query`、`dto/request`、`dto/response`。
- 方案 3：所有请求和响应类型直接平铺在 `dto`。
- 方案 4：改为 `web/request` 与 `web/response`，同时引入完整 Web Adapter 分层。
- 方案 5：立即引入 CQRS，使用独立 command/query 应用模型替代现有请求 DTO。

## 决策理由

- 选择 `request/response` 是按数据传输方向分类，两者互斥且开发者容易判断。
- `*QueryRequest` 同时表达“这是读取型请求”和“这是 HTTP 输入”，不需要额外同级 `query` 包。
- 移除响应 `vo` 能避免 View Object 与 Value Object 的缩写冲突，为后续领域建模保留清晰语义。
- 保留 `common/api`、Mapper 模型、安全模型和领域模型的现有边界，可以避免为了表面统一把所有数据载体都塞进 `dto`。
- 当前仍是模块化单体学习阶段，`dto/request + dto/response` 足以支撑清晰分层；完整 Adapter 或 CQRS 分层会增加没有现实收益的转换和样板代码。
- 项目尚未上线，直接完成干净迁移比维护兼容类型更符合当前阶段。

## 影响

- 好处
  - 所有模块使用统一、可预测的传输对象目录。
  - 新开发者可以从包路径和类型后缀直接识别请求或响应方向。
  - 避免 `VO` 的双重语义，为领域值对象留下明确位置。
  - HTTP DTO、持久化模型、安全模型和领域模型的边界更容易评审。
  - 活动、场次、票种、订单、支付、通知等后续模块可以复用同一规则。
- 代价
  - 现有 `auth`、`system` 需要迁移 5 个响应类型并更新调用方、测试和文档。
  - `UserInfo`、`PingInfo`、`EchoInfo` 改名会改变 OpenAPI Schema 组件名；当前没有兼容要求，因此接受该变化。
  - 包名和类型名更明确会带来少量命名冗余，例如 `dto.response.LoginResponse`，但换取了跨文件阅读时的清晰度。
- 后续可能需要调整的地方
  - 当单模块请求 DTO 数量明显增长时，可重新评估是否需要 `dto/request/query` 与 `dto/request/command`。
  - 当 Service 需要被 HTTP 之外的入口复用时，可引入独立 `application/command`、`application/query`，但不能直接把 Web DTO 扩张为应用层通用模型。
  - 如果未来采用 OpenAPI First 或生成式 transport model，应通过新 ADR 重新评估手写 DTO 的边界。
