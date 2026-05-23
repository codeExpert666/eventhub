# 阶段 0 实现复盘

## 1. 已完成内容

阶段 0 已经完成一个可运行、可测试、可本地复现的后端基础工程闭环：

- 新增 `backend/` Spring Boot Maven 工程。
- 建立 `common / infra / modules.system` 分层。
- 实现统一响应体 `ApiResponse` 和错误码 `ErrorCode`。
- 实现全局异常处理 `GlobalExceptionHandler`。
- 接入 Bean Validation，覆盖请求体校验和参数约束校验。
- 实现 `RequestIdFilter`，维护请求级 requestId。
- 新增 `ApiResponseRequestIdAdvice`，在 HTTP 出口层为统一响应体注入 requestId。
- 提供 `SystemController` 的 `ping` 和 `echo` 示例接口。
- 接入 OpenAPI、Actuator、Flyway、MySQL、Redis。
- 提供 `backend/Dockerfile` 和根目录 `docker-compose.yml`。
- 使用 `dev / test / prod` profile 收敛环境命名。
- 补充单元测试和 Spring Boot 集成测试。

## 2. 关键实现映射

统一响应：

- `ApiResponse` 只表达响应协议，不直接读取 MDC 或 Servlet 上下文。
- `ErrorCode` 统一管理应用层错误码、默认文案和 HTTP 状态码。

统一异常：

- `GlobalExceptionHandler` 区分字段校验、方法参数约束、非法 JSON、业务异常、资源不存在和未知异常。
- 未知异常记录完整日志，但返回统一内部错误，避免泄露实现细节。

请求追踪：

- `RequestIdFilter` 校验外部传入的 `X-Request-Id`，非法或缺失时重新生成。
- requestId 同步进入日志 MDC、请求 attribute 和响应头。
- `ApiResponseRequestIdAdvice` 只处理 `ApiResponse`，不影响 Actuator、OpenAPI 或其他非统一响应。

容器化：

- Dockerfile 使用多阶段构建，构建阶段使用 Maven/JDK，运行阶段只保留 JRE 和可执行 Jar。
- Compose 默认启动 `mysql`、`redis`、`backend` 三个服务。
- 后端容器使用 `prod` profile，并通过环境变量注入依赖地址。

## 3. 验证结果

历史实现文档中记录过的验证包括：

- `cd backend && mvn test`
- `cd backend && mvn -q test -Ptest`
- `docker compose config`
- `docker compose up -d --build`
- 容器内访问 `/actuator/health`、`/api/v1/system/ping`、`/actuator/info`、`/v3/api-docs`

测试覆盖的代表性场景：

- `ping` 和 `echo` 成功响应。
- 请求体字段校验失败。
- 非法 JSON 请求体。
- 静态资源不存在返回 404。
- 未知异常兜底返回统一错误。
- requestId 自动生成、合法复用、非法重建。
- 响应头 `X-Request-Id` 与响应体 `requestId` 一致。
- Actuator 等非 `ApiResponse` 响应不被强制包装。

## 4. 已知限制

- 阶段 0 只有系统示例接口，没有真实业务模块。
- Redis 只完成基础接入和健康检查，还没有承载业务缓存、限流、幂等或分布式锁。
- H2 测试环境能降低本地依赖，但不能完全替代 MySQL 方言验证。
- requestId 仍是单服务请求追踪标识，不等同于分布式 traceId。
- Compose 中的数据库和 Redis 密码是本地开发示例值，不能用于真实生产。
- Docker 镜像暂未固定 digest，后续进入 CI/CD 和发布阶段可再加强供应链治理。

## 5. 后续演进

进入阶段 1 后，可以在这个底座上继续扩展：

- 在统一响应和全局异常之上加入认证失败、权限不足等安全错误码。
- 在 requestId 基础上排查注册登录、鉴权、权限校验链路。
- 将 `modules.system` 的示例分层推广到 `modules.user`、`modules.auth` 等业务模块。
- 继续保留 Actuator、OpenAPI 和测试闭环，确保每个业务阶段都可验证。

更远期的云原生演进：

- 接入 OpenTelemetry 或日志聚合，让 requestId 与 traceId 形成更完整链路。
- 将 Dockerfile 和 Compose 经验迁移到 CI 镜像构建与 Kubernetes 部署。
- 根据业务复杂度从单模块单体演进到多模块单体，再评估微服务拆分。

## 6. 面试复盘角度

这个阶段适合用来说明自己不是只会写业务 CRUD，而是理解后端项目从 0 到 1 的工程底座：

- 如何让接口协议统一。
- 如何让异常返回可预测。
- 如何让请求可追踪、可排查。
- 如何让本地环境可复现。
- 如何控制早期复杂度，不提前引入微服务、消息队列和分布式组件。
