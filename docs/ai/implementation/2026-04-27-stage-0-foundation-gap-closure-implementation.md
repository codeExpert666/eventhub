# 阶段 0 基础工程缺口闭环实现说明

## 1. 本次改动解决了什么问题

本次改动对照 `docs/roadmap/stage-0-project-foundation.md` 的最新规划，补齐当前工程与阶段 0 验收标准之间的差距：

- 将默认环境命名从 `local` 收敛为 `dev`，与 `dev / test / prod` 规划保持一致。
- 将基础目录严格对齐阶段 0 规划，把统一响应体与错误码迁入 `common/api`，把 requestId 过滤器迁入 `infra/logging`，把 OpenAPI 配置迁入 `infra/openapi`。
- 将 `system` 模块中的 `PingInfo`、`EchoInfo` 从 `domain` 迁入 `vo`，将 `EchoRequest` 迁入 `dto/request`，让示例模块目录语义与阶段 0 推荐结构一致。
- 补充 Maven profile 入口，方便通过 Maven 命令选择运行环境。
- 新增后端 Dockerfile。
- 将后端应用加入 `docker-compose.yml`，让 `docker compose up -d` 可以启动 MySQL、Redis 和应用。
- 补充 `/actuator/info` 与未知异常兜底响应测试。
- 更新 README、设计文档与 ADR，沉淀本次阶段 0 补齐逻辑。

## 2. 改动内容
- 新增了什么
  - 新增 `backend/src/main/resources/application-dev.yml`。
  - 新增 `backend/Dockerfile`。
  - 新增 `docs/ai/design/2026-04-27-stage-0-foundation-gap-closure-design.md`。
  - 新增 `docs/ai/adr/2026-04-27-stage-0-compose-dev-prod-profile.md`。
  - 新增本实现说明文档。
- 修改了什么
  - `application.yml` 默认 profile 从 `local` 改为 `dev`。
  - `ApiResponse`、`ErrorCode` 迁移到 `com.eventhub.common.api`。
  - `RequestIdFilter` 迁移到 `com.eventhub.infra.logging`。
  - `OpenApiConfig` 迁移到 `com.eventhub.infra.openapi`。
  - `PingInfo`、`EchoInfo` 迁移到 `com.eventhub.modules.system.vo`。
  - `EchoRequest` 迁移到 `com.eventhub.modules.system.dto.request`。
  - `backend/pom.xml` 增加 `dev/test/prod` Maven profile。
  - `docker-compose.yml` 增加 `backend` 服务，通过 Compose 服务名连接 MySQL 和 Redis。
  - `README.md` 更新为阶段 0 完整 Compose 启动方式，并保留“只启动依赖后本机运行后端”的开发路径。
  - `GlobalExceptionHandlerTest` 增加未知异常统一响应测试。
  - `SystemControllerTest` 增加 `/actuator/info` 可访问测试。
  - `application-prod.yml` 中的 Compose 注释从未来示例调整为当前实际用法。
- 删除了什么
  - 删除 `backend/src/main/resources/application-local.yml`，避免 `local/dev` 两套开发环境命名长期并存。

## 3. 为什么这样设计
- 最新阶段 0 规划明确使用 `dev/test/prod`，项目还未上线，因此直接收敛命名比保留兼容别名更清晰。
- 阶段 0 规划已经把 `common/api`、`infra/openapi`、`infra/logging` 写成推荐目录结构，因此基础设施包名也应严格对齐，而不是只按职责相近保留旧目录。
- `PingInfo`、`EchoInfo` 是接口返回给调用方看的视图对象，不承载领域规则；当前 `system` 模块也没有复杂领域状态，因此放入 `vo` 比放入 `domain` 更准确。
- `EchoRequest` 是 Controller 请求体入参，放入 `dto/request` 可以和后续 `dto/query`、`vo` 等目录自然区分。
- Docker Compose 默认启动完整基础闭环，可以让新环境复现、学习演示和阶段验收都使用同一条主路径。
- 后端容器使用 `prod` profile，并通过环境变量注入依赖地址，可以提前建立容器化部署下“不使用 localhost 访问其他容器”的正确习惯。
- Maven profile 只承担启动入口职责，不替代 Spring Boot 的 `application-*.yml` 配置模型，避免把运行配置散落到构建脚本里。
- 通过测试补齐已有验收点，而不是新增测试专用接口，能保持阶段 0 的业务范围干净。

## 4. 替代方案
- 方案 A：继续保留 `application-local.yml`，只在 README 中说明它等价于 dev。
- 方案 B：只新增 Dockerfile，不把后端应用纳入 Compose。
- 方案 C：把后端服务放在 Compose 可选 profile 中，需要额外参数才启动。
- 为什么没有采用：
  - 不选方案 A：路线图、配置、README 会继续存在两套环境命名，学习阶段容易混淆。
  - 不选方案 B：不满足 `docker compose up -d` 后应用也能启动的阶段 0 验收口径。
  - 不选方案 C：阶段 0 目标是降低启动门槛，默认完整启动比可选 profile 更直观。

## 5. 测试与验证
- 跑了哪些测试
  - `cd backend && mvn -q test -Ptest`：通过。
  - `docker compose config`：通过。
  - `docker compose up -d --build`：通过，成功构建 `eventhub-backend:stage0` 并启动 `backend/mysql/redis`。
  - `docker compose ps`：显示 `eventhub-backend`、`eventhub-mysql`、`eventhub-redis` 均处于运行状态，MySQL/Redis 为 healthy。
- 手工验证了哪些场景
  - 容器日志确认应用使用 `prod` profile 启动。
  - 容器日志确认应用连接 MySQL，Flyway 校验 V1 并显示 schema 已在版本 1。
  - 容器内 HTTP 请求验证：
    - `GET /actuator/health` 返回 200 与 `{"status":"UP"}`。
    - `GET /api/v1/system/ping` 返回 200 与统一响应体。
    - `GET /actuator/info` 返回 200。
    - `GET /v3/api-docs` 返回 200，并包含 OpenAPI 文档内容。
- 结果如何
  - 阶段 0 的主要验收链路已打通。
  - 当前 Codex 沙箱中的宿主机 `curl` 无法连到 Docker 映射的 `127.0.0.1:8080`，但 Compose 端口映射、容器日志和容器内 HTTP 请求均证明应用已正常监听并响应。

## 6. 已知限制
- 首次 Docker 镜像构建需要下载 Maven 依赖，本次 `dependency:go-offline` 层耗时较长；后续命中 Docker 缓存后会明显变快。
- Compose 文件中的数据库和 Redis 密码仍是本地开发示例值，不能直接用于真实生产环境。
- 运行镜像暂未内置 curl/wget 形式的容器级健康检查，当前依赖 Actuator 接口、日志和 Compose 状态验证应用可用性。
- 测试环境仍使用 H2，能验证 Flyway 脚本基本可执行，但不能完全替代真实 MySQL 方言测试。

## 7. 对后续版本的影响
- 对简历可用版的价值：
  - 阶段 0 已经形成更完整的“代码、配置、容器、文档、测试”闭环，可直接作为后续业务模块的稳定底座。
  - 面试表达时可以明确说明项目从第一阶段就具备统一响应、异常治理、配置分层、数据库迁移、接口文档、健康检查和容器化本地启动。
- 对微服务 / 云原生演进的影响：
  - `backend/Dockerfile` 和 Compose 应用服务为后续 CI 镜像构建、部署探针、Kubernetes Deployment/Service 改造打下基础。
  - `dev/test/prod` 环境命名统一后，后续接入配置中心或云原生环境变量注入时更容易映射。
