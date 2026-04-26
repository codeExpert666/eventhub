# 活动预约与票务平台建议路线图

> 项目主题：活动预约与票务平台
> 项目定位：以后端为主的学习型 / 简历型 Java 项目
> 演进路线：模块化单体 → 事件驱动 → 微服务 → 云原生
> 核心目标：通过一个真实业务闭环，系统学习现代 Java 后端开发、并发控制、幂等、缓存、测试、文档、部署与架构演进。

---

## 总体目标定位

本项目建议分成两个层级推进：

1. **简历可用版**
   先实现一个完整、稳定、可演示、可讲述的模块化单体系统。重点不是堆技术，而是把业务闭环、事务边界、幂等、防超卖、支付回调、库存释放、缓存、测试和文档做扎实。

2. **进阶加分版**
   在单体业务闭环稳定之后，再引入 Kafka、Outbox、异步事件、微服务拆分、Kubernetes、可观测等能力。进阶阶段应作为已有系统的架构演进，而不是推倒重写。

最终你应该能在简历中描述为：

> 基于 Java、Spring Boot、MySQL、Redis、Docker Compose 实现活动预约与票务平台，支持用户认证、RBAC、活动/场次/票种管理、用户下单、库存扣减、订单幂等、模拟支付回调、订单超时关闭、库存释放、通知记录、操作日志、热点缓存和接口文档。项目通过数据库条件更新与事务控制解决并发超卖，通过 Redis SETNX、请求哈希和唯一索引实现下单幂等，通过订单状态机和条件更新处理支付回调与订单超时关闭的并发竞争，并可进一步演进到 Kafka 事件驱动、微服务与 Kubernetes 部署。

---

# 简历可用版

---

## 阶段 0：项目骨架与工程基础

### 目标

创建一个可运行、可测试、可本地一键启动的模块化单体后端工程，为后续业务开发打基础。

### 范围

- Spring Boot 后端基础工程。
- Maven profile：dev / test / prod。
- 统一响应体。
- 统一异常处理。
- 参数校验。
- 错误码规范。
- 基础日志配置。
- requestId / traceId 透传。
- OpenAPI 文档。
- Actuator 健康检查。
- Dockerfile。
- Docker Compose 启动 MySQL、Redis、应用。
- Flyway 初始化 migration。
- 基础 README。

### 非范围

- 注册登录。
- RBAC。
- 活动、订单、支付等业务逻辑。
- Kafka、微服务、Kubernetes。

### 推荐实现

#### 目录结构

```text
eventhub
├── backend
│   └── src
│       ├── main
│       │   ├── java/com/eventhub
│       │   │   ├── EventhubApplication.java
│       │   │   ├── common
│       │   │   │   ├── api
│       │   │   │   ├── exception
│       │   │   │   ├── validation
│       │   │   │   ├── security
│       │   │   │   ├── redis
│       │   │   │   └── util
│       │   │   ├── modules
│       │   │   │   ├── system
│       │   │   │   ├── auth
│       │   │   │   ├── user
│       │   │   │   ├── event
│       │   │   │   ├── inventory
│       │   │   │   ├── order
│       │   │   │   ├── payment
│       │   │   │   ├── notification
│       │   │   │   └── audit
│       │   │   └── infra
│       │   │       ├── config
│       │   │       ├── openapi
│       │   │       ├── persistence
│       │   │       └── scheduler
│       │   └── resources
│       │       ├── application.yml
│       │       ├── application-dev.yml
│       │       ├── application-prod.yml
│       │       └── db/migration
│       └── test
│           ├── java
│           └── resources
│               └── application-test.yml
├── docs
│   ├── ai
│   │   ├── design
│   │   ├── implementation
│   │   └── adr
│   ├── interview
│   ├── roadmap
│   └── templates
├── docker
├── docker-compose.yml
├── Dockerfile
├── pom.xml
└── README.md
```

每个具体模块（module）内部的分层结构如下：

```text
modules/{moduleName}
├── controller
├── service
│   └── impl（简单 service 可不写接口）
├── domain（service 逻辑复杂时才考虑此结构）
├── mapper
├── entity
├── dto
│   ├── request
│   └── query
├── vo
├── converter
├── enums
└── exception
```

模块边界如下：

| 模块           | 职责                        |
|--------------|---------------------------|
| system       | 系统基础能力                    |
| auth         | 注册、登录、JWT、鉴权              |
| user         | 用户资料、角色、账号状态              |
| event        | 活动、场次、票种管理与查询             |
| inventory    | 库存扣减、锁定、释放、确认售出           |
| order        | 订单、订单明细、订单状态机             |
| payment      | 模拟支付、支付记录、支付回调            |
| notification | 通知记录、站内通知、异步通知预留          |
| audit        | 管理员操作日志、业务日志              |
| common       | 统一响应、异常、校验、工具类            |
| infra        | Redis、OpenAPI、调度任务、外部依赖配置 |

#### 统一响应体

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {},
  "requestId": "...",
  "timestamp": "2026-04-26T18:22:03.119366+08:00"
}
```

#### 统一错误码

```text
SUCCESS
VALIDATION_ERROR
BUSINESS_ERROR
NOT_FOUND
INTERNAL_ERROR
```

#### 基础接口

```text
GET /api/v1/system/ping
POST /api/v1/system/echo
GET /actuator/health
GET /actuator/info
GET /swagger-ui/index.html
GET /v3/api-docs
```

### 交付物

```text
src/main/java 基础包结构
application-dev.yml
application-test.yml
application-prod.yml
Dockerfile
docker-compose.yml
Flyway V1__init_backend_foundation.sql
README.md
相关设计、实现、ADR文档
```

### 验收标准

- `docker compose up -d` 后 MySQL、Redis、应用可以启动。
- 应用能连接 MySQL 和 Redis。
- Flyway 能自动执行初始化脚本。
- 健康检查接口返回正常。
- OpenAPI 页面可访问。
- 参数校验和异常返回格式统一。

### 测试点

- ping、echo 接口正常。
- 参数校验异常返回统一格式。
- 未知异常返回统一格式。
- Flyway migration 在测试环境可执行。

### 面试表达

> 项目从一开始就建立统一响应、统一异常、参数校验、分环境配置、OpenAPI、健康检查和 Docker Compose 本地环境，保证后续业务开发具有稳定工程基础。

---

### 阶段 1：注册登录、JWT 与 RBAC
目标：完成用户注册、登录、获取当前用户、基于角色的权限控制

范围：
- User/Role/UserRole 数据模型
- 密码加密
- JWT 鉴权
- Spring Security 基础接入
- 管理员种子数据
- 基础权限注解与鉴权流程

非范围：
- OAuth
- 短信/邮件验证码
- 找回密码
- 复杂权限中心

要求：明确表结构、token 策略、异常场景、权限边界、测试点

---

### 阶段 2：活动、场次、票种建模与公开查询接口
目标：完成 Event、Session、TicketType 的核心建模，并提供面向用户侧的活动列表与详情查询能力

范围：活动状态、场次时间窗口、票种价格与库存字段设计、基础列表/详情接口、DTO/VO 设计

非范围：后台管理写接口、下单与支付

要求：明确表结构、状态枚举、接口字段、上架/下架语义、边界条件。

---

### 阶段 3：后台活动管理、场次管理、票种管理接口
目标：完成管理员侧对活动、场次、票种的创建、修改、上下架与查询

范围：
- 管理员接口
- 参数校验
- 状态流转约束
- 时间与价格合法性校验
- 接口权限控制

非范围：下单、支付、通知；要求明确接口清单、请求响应、状态流转规则、关键校验与测试点

---

### 阶段 4：下单/预约、库存扣减、防重复提交、幂等
目标：完成用户创建订单的主链路

范围：
- Order/OrderItem 建模
- 创建订单接口
- 票种库存扣减
- Redis 防重复提交/幂等方案
- 订单状态初始流转
- 我的订单列表与详情

非范围：真实支付接入、选座、优惠券

要求：重点说明并发控制策略、事务边界、Redis key 设计、TTL、失败回滚与测试点

---

### 阶段 5：模拟支付回调、订单状态流转、库存释放
目标：完成从待支付订单到支付成功或关闭释放库存的完整闭环

范围：
- PaymentRecord 建模
- 模拟支付回调接口
- 支付回调幂等
- 订单状态机
- 关闭未支付订单策略
- 失败或超时的库存释放

非范围：
- 真实第三方支付
- 分布式事务
- 消息队列

要求：明确状态流转图、幂等处理、释放库存时机、异常场景与测试点

---

### 阶段 6：通知记录、操作日志、热点缓存
目标：补齐业务闭环中的通知留痕、关键操作日志与活动热点缓存

范围：
- NotificationRecord、OperationLog 建模
- 关键业务动作日志记录
- 活动列表/详情缓存
- 缓存失效策略

非范围：
- MQ
- 短信/邮件真实发送
- 完整审计平台

要求：明确记录时机、表结构、Redis key、TTL、失效策略、允许的不一致窗口与测试点

---

### 阶段 7：基本测试、部署说明、README、面试材料整理
目标：让项目达到简历可用、可演示、可讲述的完成度

范围：
- 关键业务链路测试
- 测试数据准备
- README 运行说明
- Docker Compose 启动说明
- OpenAPI 使用说明
- `docs/interview` 项目讲述材料

非范围：微服务拆分、Kubernetes、Kafka

要求：明确测试层次、主链路覆盖、文档目录输出、面试表达重点与实施步骤

---

## 进阶加分版

---

### 进阶阶段 A：事件驱动
- Kafka 引入
- 支付成功事件
- 通知异步化
- 操作日志异步化
- 解耦订单与通知

---

### 进阶阶段 B：微服务拆分
建议先拆：
- 用户服务
- 活动服务
- 订单服务
- 通知服务

不要一开始就拆太细。

---

### 进阶阶段 C：云原生与可观测
- API Gateway
- 配置管理
- Kubernetes 部署
- Helm
- Prometheus / Grafana
- OpenTelemetry

---
