# 引入 Lombok 与现有代码规范化设计

## 1. 背景
- 当前后端基础工程已经包含统一响应、统一异常、请求追踪、系统探活和 OpenAPI 基础能力。
- 部分类仍保留手写构造器、getter 和日志字段，这些代码本身不承载业务语义，但会在后续模块增长后持续增加样板成本。
- Lombok 可以在编译期生成这些样板代码，适合作为当前单体后端继续演进前的工程规范补充。

## 2. 目标
- 在 `backend` 模块中规范引入 Lombok，并保证 Maven 编译、IDE 识别和 Spring Boot 打包边界清晰。
- 使用 Lombok 替换现有代码中适合自动生成的样板代码，包括构造器注入、只读字段 getter 和日志字段。
- 保持现有 API 契约、错误码、异常语义、测试断言和运行时行为不变。
- 明确本项目的 Lombok 使用边界，避免为了使用 Lombok 而牺牲 Java `record` 的不可变表达。

## 3. 非目标
- 不新增业务功能，不修改活动、订单、库存、支付等后续阶段业务模型。
- 不调整数据库表结构、索引、Flyway 迁移脚本或缓存配置。
- 不把现有 `record` 请求/响应模型改成普通 POJO。
- 不引入 `@Data`、`@Setter` 等会扩大可变性的注解作为默认规范。

## 4. 影响范围
- Maven 构建：`backend/pom.xml` 增加 Lombok 依赖、注解处理和 Spring Boot 打包排除配置。
- Lombok 全局配置：新增 `backend/lombok.config`，约束配置边界并标记生成代码。
- 公共契约：`ErrorCode`、`BusinessException`。
- 基础设施：`GlobalExceptionHandler`。
- 系统模块：`SystemController`、`SystemService`。
- 不涉及数据库表、缓存、外部接口或消息契约。

## 5. 领域建模
- 本次没有新增业务领域实体。
- `ErrorCode` 继续作为统一错误码枚举，承载 HTTP 状态、应用层 code 和默认提示。
- `BusinessException` 继续作为业务异常对象，绑定 `ErrorCode` 并交给全局异常处理器转换为统一响应。
- `EchoRequest`、`PingInfo`、`EchoInfo` 继续使用 Java `record`，表示只读 DTO/VO 数据载体。
- 无新增状态流转。

## 6. API 设计
- 不新增接口。
- 不修改现有接口路径：
  - `GET /api/v1/system/ping`
  - `POST /api/v1/system/echo`
- 不修改统一响应体字段：`code`、`message`、`data`、`requestId`、`timestamp`。
- 不修改错误码或异常场景。
- Lombok 生成的 `getHttpStatus()`、`getCode()`、`getDefaultMessage()`、`getErrorCode()` 必须保持与原手写方法同名同语义。

## 7. 数据设计
- 不调整表结构。
- 不新增索引或唯一约束。
- 不涉及数据一致性变化。
- Lombok 只参与 Java 编译期代码生成，不改变持久化模型。

## 8. 关键流程
- 正常流程：
  1. Maven 读取 Lombok 依赖和编译插件配置。
  2. 编译阶段由 annotation processor 生成构造器、getter 和日志字段。
  3. Spring 容器继续通过单构造器方式完成依赖注入。
  4. 运行时仍使用编译后的普通字节码，不需要 Lombok 作为运行时依赖。
- 异常流程：
  - 如果 Lombok 注解处理未生效，编译会直接失败，例如找不到构造器或 getter。
  - 通过 `mvn validate` 和测试可以提前暴露这类问题。
- 状态流转：
  - 本次无业务状态机变化。

## 9. 并发 / 幂等 / 缓存
- 不涉及库存扣减、订单提交、支付回调等并发场景。
- 不新增幂等键或防重复提交逻辑。
- 不新增缓存读写。
- `GlobalExceptionHandler` 的日志字段由 `@Slf4j` 生成，仍是静态日志器，不引入共享可变状态。

## 10. 权限与安全
- 不新增接口或权限入口。
- 不改变鉴权、鉴别或角色约束。
- `lombok.config` 使用 `config.stopBubbling = true`，避免父目录或用户环境中的 Lombok 配置意外影响当前模块。
- Spring Boot 打包排除 Lombok，减少无意义的运行时依赖暴露。

## 11. 测试策略
- 单元测试：
  - 运行现有 `ApiResponseTest`、`BusinessExceptionTest`、`GlobalExceptionHandlerTest`，确认统一响应、业务异常和异常处理契约不变。
- 集成测试：
  - 运行现有 `SystemControllerTest`，确认构造器注入和 Web 层链路不受影响。
- 构建验证：
  - 运行 `mvn -q -DskipTests validate`，确认 Lombok 注解处理和 Maven 配置可用。
  - 运行 `mvn -q test`，确认全部现有测试通过。
- 接口验证：
  - 本次不改变接口契约，优先依赖现有 MockMvc 测试覆盖。
- 异常场景验证：
  - 业务异常空 `ErrorCode` 的历史错误消息仍需保持为 `errorCode must not be null`。

## 12. 风险与替代方案
- 当前方案风险：
  - IDE 如果没有启用 annotation processing，可能出现编辑器误报，但 Maven 编译应保持可靠。
  - Lombok 生成代码降低了部分显式代码可见性，需要通过注释和文档说明团队使用边界。
- 备选方案 A：继续全部手写构造器、getter 和日志字段。
  - 优点是显式，缺点是后续业务模块扩大后样板代码明显增加。
- 备选方案 B：全面使用 Lombok POJO 替换 `record`。
  - 优点是风格表面统一，缺点是会削弱 DTO/VO 的不可变语义，并引入不必要的可变对象风险。
- 备选方案 C：使用 `@Data` 作为通用注解。
  - 优点是写法最省，缺点是会默认生成 setter，容易让请求/响应模型和错误码对象变得可变，不符合当前项目的清晰分层与最小可变性原则。
- 本次选择小范围使用 `@RequiredArgsConstructor`、`@Getter`、`@Slf4j`，因为它们能减少样板，同时不会扩大对象可变性或改变业务契约。
