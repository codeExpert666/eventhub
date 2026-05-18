# ADR：Record、普通类与 Lombok 注解使用边界

## 标题
在 EventHub 中保留纯数据快照的 record，仅用普通类表达需要隐藏构造入口的派生状态模型，并克制使用 Lombok

## 状态
- accepted

## 背景
项目已经在 DTO/VO 中使用 Java `record`，并通过前期 Lombok ADR 明确“简单只读 DTO/VO 优先 record”。随着通用 API 层演进，`PageResponse` 开始承担比字段承载更多的职责：

- `PageResponse` 中的分页元数据由 `items`、`PageRequest` 和 `total` 推导而来，不应该由任意调用方手写。
- `PageResponse` 从 record 改为普通类后，需要 JavaBean getter 支撑 Jackson 序列化和内部读取。
- `AuthenticatedPrincipal` 虽然写入 Spring Security 上下文，但当前只保存 `userId`、`username`、`authorities` 三个不可变字段，仍属于最小身份快照。

前序调整曾按小步骤生成多份 ADR/设计/实现说明，但这些内容本质上属于同一次工程取舍：如何划定 record、普通类、访问方法和 Lombok 注解的边界。因此本 ADR 合并记录最终决策。

## 决策
项目采用以下规则：

- 继续使用 `record`：
  - 请求体 DTO，例如登录、注册、状态更新请求。
  - 响应 VO，例如用户摘要、登录响应、system 示例响应。
  - 简单值对象，例如 `PageRequest`。
  - 最小不可变 claims，例如 `JwtClaims`。
  - 当前阶段的最小认证身份快照，例如 `AuthenticatedPrincipal`。
- 使用普通类：
  - 需要隐藏构造入口、只能通过工厂方法维护派生字段一致性的模型，例如 `PageResponse`。
  - 需要 JavaBean setter/default constructor 的绑定模型，例如 GET 查询参数对象。
  - 后续确实出现复杂行为、敏感字段输出控制或框架接口实现需求的模型。
- 使用 Lombok：
  - 只保留当前真实需要的注解。
  - `PageResponse` 当前只保留 `@Getter`。
  - 不使用 `@Data` 表达不可变模型。
  - 暂不为未使用的对象比较或日志输出提前添加 `@EqualsAndHashCode`、`@ToString`。
- 访问风格：
  - 普通类使用 JavaBean getter，例如 `PageResponse#getItems()`、`PageResponse#isHasNext()`。
  - record 使用 record accessor，例如 `AuthenticatedPrincipal#userId()`、`PageRequest#page()`。

本次只将 `PageResponse` 从 `record` 重构为不可变普通类；`AuthenticatedPrincipal` 保持 record。

## 备选方案
- 方案 1：所有 DTO/VO/值对象都继续使用 `record`。
- 方案 2：所有请求、响应和值对象都改为 Lombok 普通类。
- 方案 3：只把已出现明确问题的类型改成普通类，其余 record 保留。
- 方案 4：保留 `PageResponse` 的 record 风格访问兼容方法。
- 方案 5：为普通类提前添加 `@EqualsAndHashCode`、`@ToString`、`@Data` 等 Lombok 注解。

## 决策理由
- 选择方案 3，并配套最小 Lombok 注解策略。
- `record` 对纯数据载体表达清晰、样板代码少，适合当前大多数请求/响应模型和最小身份快照。
- `PageResponse` 的派生字段需要统一计算，普通类可以用私有构造器收敛入口。
- `AuthenticatedPrincipal` 当前没有 `UserDetails`、租户上下文、权限版本或敏感字段输出控制需求，保留 record 更简洁。
- `PageResponse` 的 JavaBean getter 当前确实服务于 Jackson 序列化和测试读取，`@Getter` 是低风险样板替代。
- `@Data` 会生成 setter，不符合不可变模型。
- `@EqualsAndHashCode`、`@ToString` 当前没有明确调用方，提前添加会扩大模型语义。
- 普通类和 record 各自保留一种访问风格，可以减少后续维护时的混乱。

## 影响
- 好处
  - record 使用边界更清晰，后续模型设计更容易复盘。
  - 分页响应元数据由单一工厂方法统一维护，减少业务模块构造不一致响应的风险。
  - Lombok 使用更克制，避免为了减少样板代码而提前暴露 setter、比较语义或字符串输出语义。
  - 认证主体保持简单，避免过早普通类化。
  - 同一次重构在设计、实现、ADR 层面各保留一份文档，便于后续阅读。
- 代价
  - `PageResponse` 仍需显式维护私有构造器校验。
  - Lombok 生成 getter 需要依赖编译期注解处理，代码审查时需要理解注解实际生成的方法。
  - record 与普通类会在项目中并存，需要开发者按职责判断类型选择。
- 后续可能需要调整的地方
  - 如果 `PageResponse` 后续需要反序列化或跨服务传输，可再评估是否需要公开构造器或 Jackson 注解。
  - 如果 `AuthenticatedPrincipal` 后续实现 Spring Security `UserDetails` 或加入租户/组织上下文，应重新评估是否转为普通类。
  - 如果后续出现真实对象比较、日志输出或调试需求，再按具体场景添加 `@EqualsAndHashCode` 或 `@ToString`。
