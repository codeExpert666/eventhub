# EventHub

活动预约与票务平台学习型项目。

当前已完成阶段 0 的后端基础工程脚手架，重点是先把单体工程的分层、运行方式和基础设施接入约束搭起来，后续再逐步实现用户、活动、订单和支付模块。

## 当前能力

- `backend/` Spring Boot 单体后端基础工程
- 统一响应体
- 统一异常处理
- 参数校验
- MySQL / Redis 本地依赖
- Flyway 基线迁移
- OpenAPI 文档
- Actuator 健康检查
- 基础日志与 `requestId`

## 目录说明

```text
backend/
  ├── pom.xml
  ├── src/main/java/com/eventhub
  │   ├── common
  │   ├── config
  │   └── modules/system
  ├── src/main/resources
  │   ├── application*.yml
  │   ├── db/migration
  │   └── logback-spring.xml
  └── src/test
docs/
  └── ai
      ├── design
      ├── implementation
      └── adr
docker-compose.yml
```

## 本地启动

前置条件：

- Java 17
- Maven 3.9+
- Docker / Docker Compose

1. 启动基础设施

```bash
docker compose up -d
```

2. 启动后端

```bash
cd backend
mvn spring-boot:run
```

3. 访问地址

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- 健康检查: `http://localhost:8080/actuator/health`
- 系统探活: `http://localhost:8080/api/v1/system/ping`

`prod` profile 不再提供数据库和 Redis 的本地默认值，启动前必须显式注入 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`REDIS_HOST` 等环境变量，避免生产环境误用开发配置。

## 测试

```bash
cd backend
mvn test
```

测试使用 `test` profile，数据源为 H2，避免单元/集成测试依赖本地 MySQL/Redis 容器。

## 文档

- 设计文档：`docs/ai/design/`
- 实现说明：`docs/ai/implementation/`
- ADR：`docs/ai/adr/`
- 路线图：`docs/roadmap/event-booking-roadmap.md`
