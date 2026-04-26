# 阶段 0 基础工程缺口闭环设计

## 1. 背景
- `docs/roadmap/stage-0-project-foundation.md` 已更新为阶段 0 的最新规划，要求后端工程具备可运行、可测试、可本地一键启动的基础闭环。
- 当前工程已经具备 Spring Boot 基础骨架、统一响应、统一异常、参数校验、错误码、日志、requestId、OpenAPI、Actuator、Flyway、README 和基础测试。
- 仍存在几个与最新规划不完全一致的点：默认环境仍使用 `local`，缺少 `application-dev.yml`，缺少 `backend/Dockerfile`，`docker-compose.yml` 只启动 MySQL/Redis 而未启动应用容器，少量阶段 0 验收点缺少直接测试覆盖。

## 2. 目标
- 将运行环境命名收敛为阶段 0 规划中的 `dev / test / prod`。
- 将基础包结构严格对齐阶段 0 规划：`common/api` 承载统一响应与错误码协议，`infra/openapi` 承载接口文档配置，`infra/logging` 承载 requestId 注入与日志链路能力。
- 将 `system` 模块严格对齐阶段 0 推荐分层：请求模型放入 `dto/request`，接口返回模型放入 `vo`，当前不保留没有领域行为的 `domain` 包。
- 补齐 Maven profile 入口，让 `mvn spring-boot:run -Pdev`、`mvn spring-boot:run -Pprod` 等命令有明确语义。
- 新增后端应用 Dockerfile，并让 `docker compose up -d` 能同时启动 MySQL、Redis 和后端应用。
- 补齐 `/actuator/info` 与未知异常统一返回的低成本测试覆盖。
- 更新 README 与 `docs/ai/` 文档，形成可复盘的阶段 0 验收记录。

成功标准：
- `backend/src/main/resources/application-dev.yml` 存在并作为默认开发环境配置。
- `backend/Dockerfile` 存在，可构建后端应用镜像。
- `modules/system` 使用 `controller / service / dto/request / vo` 目录结构。
- `docker-compose.yml` 包含 `backend`、`mysql`、`redis` 三个服务，并通过容器服务名连接依赖。
- `mvn -q test` 通过。
- `docker compose config` 通过。

## 3. 非目标
- 不实现注册登录、RBAC、活动、订单、支付等业务功能。
- 不引入 Kafka、Kubernetes、配置中心、服务发现或微服务拆分。
- 不新增业务缓存、分布式锁、幂等表或库存并发控制。

## 4. 影响范围
- 涉及模块：
  - `backend/pom.xml`
  - `backend/src/main/resources/application.yml`
  - `backend/src/main/resources/application-dev.yml`
  - `backend/src/main/resources/application-local.yml`
  - `backend/src/main/java/com/eventhub/common/api`
  - `backend/src/main/java/com/eventhub/infra/logging`
  - `backend/src/main/java/com/eventhub/infra/openapi`
  - `backend/src/main/java/com/eventhub/modules/system/dto/request`
  - `backend/src/main/java/com/eventhub/modules/system/vo`
  - `backend/Dockerfile`
  - `docker-compose.yml`
  - `README.md`
  - `backend/src/test/java/com/eventhub/...`
  - `docs/ai/design/`
  - `docs/ai/implementation/`
  - `docs/ai/adr/`
- 涉及表 / 缓存 / 外部接口：
  - MySQL 表结构不调整，继续复用 Flyway V1 基线。
  - Redis 不新增业务 key，只作为基础设施连接与健康检查对象。
  - 外部 HTTP 接口不新增，仅补充已有 Actuator 与异常处理验证。

## 5. 领域建模
- 核心实体：
  - `ApiResponse<T>`：统一响应体，继续承载 `code/message/data/requestId/timestamp`。
  - `ErrorCode`：统一错误码，继续覆盖 `SUCCESS/VALIDATION_ERROR/BUSINESS_ERROR/NOT_FOUND/INTERNAL_ERROR`。
  - `GlobalExceptionHandler`：统一异常映射入口，补测试验证未知异常会落入 `INTERNAL_ERROR`。
  - `SystemController`：阶段 0 示例接口，不新增业务行为。
  - `EchoRequest`：回显接口请求 DTO，放在 `modules/system/dto/request`。
  - `PingInfo` / `EchoInfo`：接口返回 VO，放在 `modules/system/vo`。
  - Compose 服务模型：`backend` 依赖 `mysql` 与 `redis`，通过容器网络服务名访问基础设施。
- 实体关系：
  - `backend` 容器通过 `DB_URL=jdbc:mysql://mysql:3306/eventhub...` 连接 Compose 内的 MySQL。
  - `backend` 容器通过 `REDIS_HOST=redis` 连接 Compose 内的 Redis。
  - Flyway 在应用启动时继续初始化 `system_bootstrap_record`。
- 关键状态：
  - 容器启动状态：`created -> starting -> running`。
  - 应用健康状态：`STARTING -> UP`，依赖异常时表现为 `DOWN` 或启动失败。
  - 本次不新增业务状态机。

## 6. API 设计
- 接口列表：
  - `GET /api/v1/system/ping`
  - `POST /api/v1/system/echo`
  - `GET /actuator/health`
  - `GET /actuator/info`
  - `GET /swagger-ui/index.html`
  - `GET /v3/api-docs`
- 请求参数：
  - 不新增请求参数。
  - `POST /api/v1/system/echo` 继续使用 `message` 必填、`tag` 可选的请求体。
- 响应结构：
  - 业务接口继续使用统一 `ApiResponse`。
  - Actuator 与 OpenAPI 端点保持框架原生响应，不做统一包装。
- 错误码 / 异常场景：
  - 请求体校验失败：`COMMON-400`。
  - 静态资源不存在：`COMMON-404`。
  - 未知异常：`COMMON-500`，本次补直接测试。

## 7. 数据设计
- 表结构调整：
  - 不新增表，不修改既有 Flyway 迁移语义。
- 索引设计：
  - 继续复用 `idx_system_bootstrap_record_environment`。
- 唯一约束：
  - 本次不新增唯一约束。
- 数据一致性考虑：
  - Compose 后端容器使用 `prod` profile，并通过环境变量注入数据库和 Redis 地址，避免容器内误连 `localhost`。
  - Flyway 仍在应用启动阶段执行，保证从空库启动时可以自动生成基础表。

## 8. 关键流程
- 正常流程：
  - 执行 `docker compose up -d`。
  - Compose 启动 MySQL 与 Redis，并等待健康检查通过。
  - Compose 构建并启动 `backend` 服务。
  - `backend` 通过环境变量读取 `prod` profile 连接信息。
  - Spring Boot 启动后执行 Flyway V1 迁移。
  - 调用 `/actuator/health`、`/api/v1/system/ping`、`/v3/api-docs` 验证基础能力。
- 异常流程：
  - MySQL 或 Redis 未就绪时，`backend` 依赖健康检查延后启动；运行中依赖异常会体现在 Actuator 健康状态中。
  - 关键生产环境变量缺失时，`prod` profile 启动失败，避免静默使用开发默认值。
  - 未知异常由 `GlobalExceptionHandler` 兜底返回统一内部错误响应。
- 状态流转：
  - Compose 依赖状态：`mysql/redis healthy -> backend starting -> backend running`。
  - 请求状态：`RECEIVED -> VALIDATED -> HANDLED -> SUCCEEDED` 或 `FAILED`。

## 9. 并发 / 幂等 / 缓存
- 是否有超卖风险：
  - 本阶段没有库存与订单写入，不存在超卖问题。
- 如何防重复提交：
  - 本阶段不实现幂等组件，仅保留 `requestId` 作为后续排查与幂等键设计基础。
- 缓存放在哪里，为什么：
  - Redis 只作为阶段 0 基础设施接入，不保存业务缓存。
  - `ping/echo` 不缓存，避免给示例接口增加没有价值的复杂度。

## 10. 权限与安全
- 哪些角色能访问：
  - 阶段 0 基础接口仍默认开放，定位是本地开发和学习验证。
- 鉴权与鉴别约束：
  - 不接入 Spring Security，不提前引入用户身份模型。
  - `prod` profile 不提供数据库与 Redis 本地默认值，必须由环境变量显式注入。
  - Docker Compose 中的密码仍是本地开发示例值，不代表生产安全配置。

## 11. 测试策略
- 单元测试：
  - `GlobalExceptionHandlerTest` 补充未知异常统一响应测试。
  - 保留 `ApiResponseTest`、`BusinessExceptionTest`。
- 集成测试：
  - `SystemControllerTest` 继续使用 `@SpringBootTest` + `MockMvc` + `test` profile。
  - 补充 `/actuator/info` 端点可访问测试。
- 接口验证：
  - 通过 MockMvc 验证 `ping/echo/health/info/v3-api-docs`。
  - 通过 Compose 配置校验确认容器编排语法有效。
- 异常场景验证：
  - 请求体参数校验失败。
  - 非法 JSON 请求体。
  - 缺失静态资源。
  - 未知异常兜底。

## 12. 风险与替代方案
- 当前方案的风险：
  - `docker compose up -d` 首次启动会构建应用镜像并下载 Maven 依赖，速度比只启动 MySQL/Redis 慢。
  - Compose 内使用 `prod` profile，可以更贴近容器部署，但本地学习时仍需区分“容器启动”和“IDE 直接启动”两条路径。
  - 测试环境继续使用 H2，不能完全替代真实 MySQL 方言验证。
- 备选方案：
  - 方案 A：保留 `local` profile，不改名为 `dev`。
  - 方案 B：只提供 Dockerfile，不把后端加入 `docker-compose.yml`。
  - 方案 C：为后端容器新增单独的 `compose` profile。
- 为什么不选备选方案：
  - 不选方案 A：最新阶段 0 明确写的是 `dev/test/prod`，继续使用 `local` 会让路线图、README 和实际配置长期分叉。
  - 不选方案 B：无法满足 `docker compose up -d` 后应用也能启动的验收标准。
  - 不选方案 C：当前阶段目标是降低启动门槛，默认 Compose 直接拉起完整基础闭环更符合阶段 0 验收口径。
