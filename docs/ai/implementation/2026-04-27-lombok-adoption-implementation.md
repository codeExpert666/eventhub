# 引入 Lombok 与现有代码规范化实现说明

## 1. 本次改动解决了什么问题

- 解决基础工程中构造器、getter、日志字段等低业务含义样板代码需要反复手写的问题。
- 为后续活动、订单、库存、支付等模块扩展前建立一条清晰的 Lombok 使用边界。
- 保持现有系统模块、统一响应、统一异常和测试契约不变，只做工程可维护性改造。

## 2. 改动内容
- 新增了什么
  - 在 `backend/pom.xml` 中新增 `org.projectlombok:lombok` 编译期依赖。
  - 显式配置 `maven-compiler-plugin` 的 Lombok annotation processor，保证命令行构建可稳定生成代码。
  - 配置 `spring-boot-maven-plugin` 排除 Lombok，避免编译期工具进入可执行 Jar。
  - 新增 `backend/lombok.config`，限制配置边界并为生成代码添加 `@lombok.Generated` 标记。
  - 新增 ADR：`docs/ai/adr/2026-04-27-lombok-adoption.md`。
- 修改了什么
  - `ErrorCode` 使用 `@Getter` 生成 `getHttpStatus()`、`getCode()`、`getDefaultMessage()`。
  - `BusinessException#errorCode` 使用 `@Getter` 生成 `getErrorCode()`，保留原有空值校验错误消息。
  - `GlobalExceptionHandler` 使用 `@Slf4j` 生成日志字段。
  - `SystemController` 与 `SystemService` 使用 `@RequiredArgsConstructor` 生成构造器，保持构造器注入语义。
- 删除了什么
  - 删除上述类中对应的手写 getter、构造器和日志字段声明。
  - 未删除任何业务接口、测试、配置 profile 或数据库迁移脚本。

## 3. 为什么这样设计
- Lombok 被限定为编译期工具，运行时不参与业务执行，符合当前基础工程“依赖边界清晰”的目标。
- 只采用 `@Getter`、`@RequiredArgsConstructor`、`@Slf4j` 三类低风险注解，减少样板代码的同时不扩大对象可变性。
- 保留 Java `record` 作为 DTO/VO 的默认表达，因为当前 `EchoRequest`、`PingInfo`、`EchoInfo` 都是只读数据载体，`record` 比 Lombok POJO 更直接。
- 构造器注入仍然是 Controller 和 Service 的依赖注入方式，只是由 Lombok 生成构造器，便于后续测试和依赖分析。
- `BusinessException` 没有使用 Lombok 的 `@NonNull` 自动校验，因为当前测试已经明确约束空 `ErrorCode` 的错误消息为 `errorCode must not be null`，需要保持契约稳定。

## 4. 替代方案
- 方案 A：继续全部手写样板代码。
  - 没有采用，因为后续模块增长后，构造器、getter 和日志字段会持续膨胀，阅读成本会被低价值代码稀释。
- 方案 B：全面使用 `@Data`。
  - 没有采用，因为 `@Data` 会默认生成 setter，容易让错误码、请求模型、响应模型变成可变对象，不符合当前项目的最小可变性倾向。
- 方案 C：把所有 `record` 改成 Lombok 普通类。
  - 没有采用，因为现有 DTO/VO 没有复杂行为，`record` 已经清楚表达不可变数据载体语义。
- 方案 D：只加 Lombok 依赖，不显式配置 annotation processor 和打包排除。
  - 没有采用，因为这样容易让命令行构建、IDE 和最终 Jar 的依赖边界变得不够明确。

## 5. 测试与验证
- 在 `backend/` 目录执行 `mvn -q -DskipTests validate`，结果通过。
  - 验证 Lombok 依赖解析、annotation processor 配置和编译链路可用。
- 在 `backend/` 目录执行 `mvn -q test`，结果通过。
  - 覆盖 `ApiResponseTest`、`BusinessExceptionTest`、`GlobalExceptionHandlerTest`、`SystemControllerTest` 等现有测试。
  - 测试输出中 `simulated unexpected failure` 是兜底异常测试主动构造的日志，不代表测试失败。
- 在 `backend/` 目录执行 `mvn -q -DskipTests package`，结果通过。
  - 验证 Spring Boot 可执行 Jar 打包链路不受 Lombok 配置影响。
- 在仓库根目录执行 `jar tf backend/target/backend-0.0.1-SNAPSHOT.jar BOOT-INF/lib/lombok`，无输出。
  - 验证 Lombok 未进入可执行 Jar 的运行时依赖目录。

## 6. 已知限制
- 使用 Lombok 后，部分方法在源码中不可见，初学者需要理解“源码注解 -> 编译期生成字节码”的过程。
- IDE 需要启用 annotation processing，否则编辑器可能误报构造器或 getter 不存在；命令行 Maven 构建已验证通过。
- 当前还没有引入 Checkstyle 或 ArchUnit 之类的规则来强制禁止 `@Data`、`@Setter` 在不合适的位置出现，后续可以在代码规模扩大后补充。

## 7. 对后续版本的影响
- 对简历可用版的价值：
  - 能体现项目不只是堆业务接口，也有基础工程规范和依赖边界意识。
  - 后续实现用户、活动、订单等模块时，可以减少重复构造器和 getter 样板，让代码更聚焦业务规则。
- 对微服务 / 云原生演进的影响：
  - Lombok 作为编译期工具不会影响服务拆分、容器运行或远程调用协议。
  - 当前保留 `record` 和最小可变性原则，有利于后续模块边界、消息契约和 API DTO 的稳定演进。
