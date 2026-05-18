# Record 与 Lombok 边界重构实现说明

## 1. 本次改动解决了什么问题
- 审视当前项目所有 Java `record` 后，明确哪些类型继续适合 record，哪些类型更适合普通类。
- 解决 `PageResponse` 作为 record 时公开 canonical constructor 的问题：
  - 调用方原本可以绕过 `PageResponse.of(...)`，手工传入 `totalPages`、`hasNext`、`hasPrevious`。
  - 这些派生字段一旦和 `page/size/total` 不一致，会直接影响前端分页控件。
- 收敛普通类的访问风格：
  - `PageResponse` 不再同时暴露 `items()` 与 `getItems()` 两套方法。
  - 普通类统一使用 JavaBean getter。
- 收敛 Lombok 注解：
  - `PageResponse` 只保留当前确实需要的 `@Getter`。
  - 移除当前没有实际调用方的 `@EqualsAndHashCode`、`@ToString`。
- 重新评估 `AuthenticatedPrincipal` 后，确认它当前仍适合作为 record：
  - 它只保存最小身份快照。
  - 没有隐藏构造入口、复杂行为、`UserDetails` 或租户上下文需求。
- 将前序多份同主题文档合并，确保本次重构在 `docs/ai/design`、`docs/ai/implementation`、`docs/ai/adr` 每个层面只保留一份文档。

## 2. 改动内容
- 新增了什么
  - 本次文档整理没有新增新的代码类型或依赖。
- 修改了什么
  - 将 `PageResponse<T>` 从 `record` 改为 `final class`。
  - `PageResponse` 构造器改为私有，只允许通过 `of(items, pageRequest, total)` 创建实例。
  - `PageResponse` 通过 Lombok `@Getter` 提供 JavaBean getter，保证 Jackson 序列化后的 JSON 字段仍为 `items/page/size/total/totalPages/hasNext/hasPrevious`。
  - `PageResponseTest` 使用 `getItems()`、`getPage()`、`isHasNext()` 等 JavaBean getter。
  - `AuthenticatedPrincipal` 保持 record，并保留 compact constructor 中的非空校验和权限集合防御性复制。
  - 调用方继续使用 `AuthenticatedPrincipal` 的 `userId()`、`username()`、`authorities()` record accessor。
  - 更新汇总设计文档：`docs/ai/design/2026-05-18-record-boundary-refactor-design.md`。
  - 更新汇总 ADR：`docs/ai/adr/2026-05-18-record-boundary-guideline.md`。
  - 更新汇总实现说明：`docs/ai/implementation/2026-05-18-record-boundary-refactor-implementation.md`。
- 删除了什么
  - 删除 `PageResponse` 的 public record canonical constructor。
  - 删除 `PageResponse` 的 record 风格访问方法：`items()`、`page()`、`size()`、`total()`、`totalPages()`、`hasNext()`、`hasPrevious()`。
  - 删除 `PageResponse` 上当前用不到的 Lombok `@EqualsAndHashCode`、`@ToString`。
  - 未删除 `AuthenticatedPrincipal` 的 record 声明。
  - 删除同一次重构拆出的重复文档：
    - `docs/ai/design/2026-05-18-lombok-boilerplate-refactor-design.md`
    - `docs/ai/design/2026-05-19-record-style-accessor-removal-design.md`
    - `docs/ai/design/2026-05-19-authenticated-principal-record-retention-design.md`
    - `docs/ai/implementation/2026-05-18-lombok-boilerplate-refactor-implementation.md`
    - `docs/ai/implementation/2026-05-19-record-style-accessor-removal-implementation.md`
    - `docs/ai/implementation/2026-05-19-authenticated-principal-record-retention-implementation.md`
    - `docs/ai/adr/2026-05-19-authenticated-principal-record-retention.md`

## 3. 为什么这样设计
- `PageResponse` 的 `totalPages`、`hasNext`、`hasPrevious` 不是独立输入，而是由分页请求和总数推导出来的元数据；普通类 + 私有构造器可以强制所有调用方走统一计算逻辑。
- `AuthenticatedPrincipal` 当前是不可变身份快照，record 比普通类更贴合当前职责。
- 给 `PageResponse` 保留 JavaBean getter，是为了保证从 record 改成普通类后 Jackson 仍能稳定序列化原有字段。
- 没有使用 Lombok 生成构造器或 setter，是因为 `PageResponse` 的关键点在构造边界、防御性复制和不可变性，必须显式保留在源码中。
- 没有保留 `@EqualsAndHashCode`、`@ToString`，因为当前没有对象级比较或日志输出调用方。
- 删除重复文档，是因为本轮几次调整本质上属于同一个重构主题；保留碎片文档会让后续复盘时误以为发生了多次独立架构决策。

## 4. 替代方案
- 方案 A：保留所有 record，只在文档中说明使用边界。
  - 没有采用，因为 `PageResponse` 仍会暴露全参构造入口，无法从代码层面约束派生字段一致性。
- 方案 B：把所有 request/response DTO 和值对象都改为 Lombok 普通类。
  - 没有采用，因为登录、注册、用户摘要、JWT claims、`AuthenticatedPrincipal` 等类型仍是纯数据载体，record 的不可变语义更清晰。
- 方案 C：将 `AuthenticatedPrincipal` 提前改成普通类。
  - 没有采用，因为当前没有额外行为或框架契约需要普通类承载。
- 方案 D：把 `PageResponse` 改为普通类但保留 public 全参构造器。
  - 没有采用，因为这样只能改变语法形式，不能解决派生字段一致性问题。
- 方案 E：使用 Lombok `@Data`。
  - 没有采用，因为 `@Data` 会生成 setter，破坏当前不可变模型边界。
- 方案 F：保留所有分拆文档。
  - 没有采用，因为它们记录的是同一次重构的阶段性中间状态，不符合“每个层面只保留一份文档”的复盘诉求。

## 5. 测试与验证
- 执行目标测试：
  - `mvn -q -Dtest=PageResponseTest,AuthIntegrationTest test`
  - 结果：通过。
  - 覆盖点：分页元数据计算、`items` 不可变性、管理员分页接口 JSON 字段、JWT 认证链路、权限校验、禁用用户旧 token。
- 执行完整测试：
  - `mvn -q test`
  - 结果：通过。
  - Surefire 报告：45 个测试，0 失败，0 错误，0 跳过。
- 文档整理验证：
  - 确认本次重构主题在 `docs/ai/design` 只保留一份设计文档。
  - 确认本次重构主题在 `docs/ai/implementation` 只保留一份实现说明。
  - 确认本次重构主题在 `docs/ai/adr` 只保留一份 ADR。
- 说明：
  - 完整测试中 `GlobalExceptionHandlerTest` 会打印一次模拟异常堆栈，这是测试用例主动构造的兜底异常场景；Maven 结果仍为通过。

## 6. 已知限制
- `PageResponse` 当前只作为服务端响应模型使用，没有提供反序列化构造入口；如果后续需要跨服务反序列化，需要重新评估构造器或 Jackson 注解。
- `AuthenticatedPrincipal` 当前没有实现 Spring Security `UserDetails`；如果后续要接入更标准的用户详情模型，再评估是否从 record 重构为普通类。
- Lombok 生成 getter 不直接出现在源码中，代码审查时需要理解 `@Getter` 的生成结果。
- 当前仍有多个 record 保留，后续新增模型时需要根据 ADR 中的边界判断，而不是机械地统一成某一种写法。

## 7. 对后续版本的影响
- 对简历可用版的价值
  - 展示了 Java record 不是“能用就全用”，而是根据 DTO、值对象、派生状态模型的职责边界做取舍。
  - 展示了 Lombok 的克制使用：只保留当前真实需要的 `@Getter`，而不是为了少写代码提前堆注解。
  - 分页响应模型更稳，不容易被后续业务模块构造出不一致元数据。
  - 认证主体保持简单，符合当前学习型项目的最小复杂度原则。
- 对微服务 / 云原生演进的影响
  - `PageResponse` 的统一分页协议有利于后续活动、订单、票种等列表接口复用。
  - `AuthenticatedPrincipal` 后续可在出现租户、权限版本或资源服务身份协议时再升级。
