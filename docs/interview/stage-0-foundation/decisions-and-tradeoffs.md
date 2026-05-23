# 阶段 0 决策与取舍

## 1. 单体单模块起步

决策：阶段 0 采用单体单模块 Spring Boot 工程。

原因：

- 当前业务尚未展开，单体单模块更容易快速建立最小可用闭环。
- 可以先把统一响应、异常治理、配置、数据库迁移和本地运行方式稳定下来。
- 后续仍可按 `modules` 边界逐步演进到多模块单体或微服务。

代价：

- 当前没有 Maven 多模块提供的物理隔离。
- 后续模块复杂后，需要重新梳理边界和依赖方向。

面试表达：

> 我没有在项目一开始就上微服务，而是先用单体单模块建立可运行、可测试、可复现的工程底座。这样可以把学习重点放在业务建模和基础工程规范上，等模块边界稳定后再演进。

来源：[基础阶段采用单体单模块 Spring Boot 工程 ADR](../../ai/adr/2026-04-16-backend-foundation-monolith.md)

## 2. 统一响应与统一异常先行

决策：阶段 0 就引入 `ApiResponse`、`ErrorCode` 和 `GlobalExceptionHandler`。

原因：

- 后续所有业务接口都会依赖统一协议。
- 参数校验、业务异常和未知异常需要有一致的外部表现。
- 统一错误码有利于后续前端接入、接口测试和面试讲述。

代价：

- 需要提前定义错误码边界。
- 如果后续错误码分类不够细，需要继续演进。

面试表达：

> 我把统一响应和统一异常放在业务开发前面，是因为这些是所有接口的横切契约。越晚治理，后续接口越容易出现返回结构不一致、异常泄露和前端适配成本高的问题。

来源：[后端基础工程设计文档](../../ai/design/2026-04-16-backend-foundation-design.md)

## 3. requestId 由 HTTP 出口层注入响应体

决策：`ApiResponse` 不直接读取 MDC，requestId 由 `RequestIdFilter` 和 `ApiResponseRequestIdAdvice` 协作完成。

原因：

- `ApiResponse` 属于 `common/api`，应该是纯响应协议模型。
- MDC 的职责是日志上下文，不应成为响应模型的隐藏输入。
- Filter 适合管理 requestId 生命周期，Advice 适合在 HTTP 响应写出前补充统一响应体。

代价：

- 需要新增一个 Web 出口层组件。
- 直接调用异常处理器的单元测试不会经过 Advice，需要通过 Web 集成测试覆盖 requestId 注入。

面试表达：

> 早期实现里统一响应体直接读 MDC，我后来把它拆开了。Filter 负责生成、校验和清理 requestId，ResponseBodyAdvice 只在 HTTP 出口层给 ApiResponse 补字段。这样 common 层不依赖日志和 Web 上下文，异步任务或非 HTTP 场景也不会被 MDC 隐式影响。

来源：[requestId 由 HTTP 出口层注入统一响应体 ADR](../../ai/adr/2026-05-17-request-id-http-exit-advice.md)

## 4. Compose 默认启动完整基础闭环

决策：`docker compose up -d` 默认启动 MySQL、Redis 和后端应用。

原因：

- 阶段 0 的验收目标是本地一键启动完整基础环境。
- 新环境复现、学习演示和阶段验收都可以使用同一条命令。
- 后端容器通过 Compose 服务名访问 MySQL/Redis，可以提前建立容器网络配置习惯。

代价：

- 首次启动需要构建后端镜像，耗时比只启动数据库和 Redis 更长。
- 本地开发需要区分“完整 Compose 启动”和“只启动依赖后用 IDE/Maven 启动后端”两条路径。

面试表达：

> 我把后端也纳入 Compose，是为了让项目从阶段 0 就具备可复现的运行环境。完整演示时一条命令拉起应用和依赖，日常开发时也可以只启动 MySQL/Redis，再用 IDE 跑后端。

来源：[dev/test/prod 与 Compose 默认启动后端 ADR](../../ai/adr/2026-04-27-stage-0-compose-dev-prod-profile.md)

## 5. dev/test/prod 环境命名

决策：使用 `dev / test / prod` 三套 profile，移除早期 `local` 命名。

原因：

- 路线图、README、配置文件和启动命令保持一致。
- 项目还处于早期，没有生产兼容迁移成本。
- `prod` profile 不提供本地默认数据库和 Redis 地址，避免误连开发环境。

代价：

- 需要同步调整 README、Maven profile 和 Compose 配置。
- 本地开发者需要明确 `dev` 才是默认开发语义。

面试表达：

> 我没有长期保留 local/dev 两套叫法，因为学习项目最怕规则分叉。阶段 0 就把环境命名收敛为 dev/test/prod，后续文档、命令和配置会更一致。

来源：[阶段 0 基础工程缺口闭环设计](../../ai/design/2026-04-27-stage-0-foundation-gap-closure-design.md)

## 6. 测试环境使用 H2

决策：测试 profile 使用 H2，避免单元和集成测试强依赖本地 MySQL/Redis。

原因：

- 可以让 `mvn test` 在没有外部容器的情况下运行。
- 更适合早期快速验证 Controller、异常处理、统一响应和 Flyway 基本链路。

代价：

- H2 不能完全模拟 MySQL 方言。
- 涉及复杂 SQL、索引、事务和锁行为时，仍需要真实 MySQL 集成验证。

面试表达：

> 阶段 0 的测试重点是验证工程链路，而不是数据库方言细节，所以我用 H2 保证测试轻量可运行。但我也明确保留这个限制，后续库存、订单这类强数据库语义场景必须补真实 MySQL 验证。

来源：[H2 测试 profile 文档](../../ai/design/2026-04-25-h2-test-profile-classpath-design.md)

## 7. Dockerfile 多阶段构建

决策：Dockerfile 使用 Maven/JDK 构建阶段和 JRE 运行阶段，并显式使用 Temurin 17 Noble 镜像。

原因：

- 构建工具不进入最终运行镜像。
- 运行镜像更小，职责更清晰。
- 与项目 Java 17 基线保持一致。
- 显式基础发行版便于后续安全扫描和镜像升级。

代价：

- 首次构建需要下载 Maven 依赖和基础镜像。
- 当前尚未固定镜像 digest，后续进入 CI/CD 发布阶段可再加强。

面试表达：

> 我使用多阶段 Dockerfile，把 Maven/JDK 只放在构建阶段，运行阶段只保留 JRE 和应用 Jar。这是一个基础但重要的镜像治理习惯，后续做 CI 或 Kubernetes 部署时可以直接延续。

来源：[Dockerfile 镜像版本对齐设计](../../ai/design/2026-05-05-dockerfile-image-alignment-design.md)
