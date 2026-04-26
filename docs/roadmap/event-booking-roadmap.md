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

## 路线图索引

每个阶段的详细目标、范围、非范围、交付物、验收标准或实施要求，已拆分到独立文档中维护。

### 简历可用版

| 阶段   | 主题                      | 核心目标                                            | 文档                                                                                 |
|------|-------------------------|-------------------------------------------------|------------------------------------------------------------------------------------|
| 阶段 0 | 项目骨架与工程基础               | 创建可运行、可测试、可本地一键启动的模块化单体后端工程。                    | [stage-0-project-foundation.md](./stage-0-project-foundation.md)                   |
| 阶段 1 | 注册登录、JWT 与 RBAC         | 完成用户注册、登录、当前用户查询和基于角色的权限控制。                     | [stage-1-auth-jwt-rbac.md](./stage-1-auth-jwt-rbac.md)                             |
| 阶段 2 | 活动、场次、票种建模与公开查询接口       | 完成 Event、Session、TicketType 建模，并提供用户侧活动列表与详情查询。 | [stage-2-event-session-ticket-query.md](./stage-2-event-session-ticket-query.md)   |
| 阶段 3 | 后台活动管理、场次管理、票种管理接口      | 完成管理员侧对活动、场次、票种的创建、修改、上下架与查询。                   | [stage-3-admin-event-management.md](./stage-3-admin-event-management.md)           |
| 阶段 4 | 下单/预约、库存扣减、防重复提交、幂等     | 完成用户创建订单的主链路，并重点处理并发库存与幂等问题。                    | [stage-4-order-inventory-idempotency.md](./stage-4-order-inventory-idempotency.md) |
| 阶段 5 | 模拟支付回调、订单状态流转、库存释放      | 完成待支付订单到支付成功或关闭释放库存的完整闭环。                       | [stage-5-payment-order-closure.md](./stage-5-payment-order-closure.md)             |
| 阶段 6 | 通知记录、操作日志、热点缓存          | 补齐通知留痕、关键操作日志与活动热点缓存。                           | [stage-6-notification-audit-cache.md](./stage-6-notification-audit-cache.md)       |
| 阶段 7 | 基本测试、部署说明、README、面试材料整理 | 让项目达到简历可用、可演示、可讲述的完成度。                          | [stage-7-demo-readiness.md](./stage-7-demo-readiness.md)                           |

### 进阶加分版

| 阶段     | 主题      | 核心目标                            | 文档                                                                                                 |
|--------|---------|---------------------------------|----------------------------------------------------------------------------------------------------|
| 进阶阶段 A | 事件驱动    | 引入 Kafka 等事件驱动能力，解耦订单、通知和操作日志。  | [advanced-stage-a-event-driven.md](./advanced-stage-a-event-driven.md)                             |
| 进阶阶段 B | 微服务拆分   | 在单体闭环稳定后，按业务边界拆分核心服务。           | [advanced-stage-b-microservices.md](./advanced-stage-b-microservices.md)                           |
| 进阶阶段 C | 云原生与可观测 | 引入网关、配置管理、Kubernetes、监控与链路追踪能力。 | [advanced-stage-c-cloud-native-observability.md](./advanced-stage-c-cloud-native-observability.md) |
