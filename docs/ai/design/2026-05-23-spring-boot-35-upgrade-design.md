# Spring Boot 3.5.14 升级设计

## 1. 背景
- 当前后端父工程仍使用 Spring Boot `3.3.2`。Spring Boot 3.3.x 已结束开源支持，继续停留在该分支会让框架、依赖管理和安全修复逐步脱离官方维护窗口。
- EventHub 仍处于单体后端学习型阶段，当前更需要稳定推进受支持的 Spring Boot 3.x 基线，而不是一次性引入 Spring Boot 4.x 或 Java 21 的额外迁移变量。

## 2. 目标
- 将后端 Maven 父工程从 Spring Boot `3.3.2` 升级到 `3.5.14`。
- 同步升级与 Spring Framework 6.2 / Spring Boot 3.5 兼容性直接相关的第三方依赖。
- 保持现有测试通过，尤其是完整 Spring Boot 测试上下文、JWT 鉴权链路、OpenAPI 文档生成和 Flyway 测试迁移链路。

## 3. 非目标
- 本次不升级 Java 基线，仍保持 `java.version=17`。
- 本次不升级到 Spring Boot 4.x，不引入 Spring Framework 7 / Jakarta EE 11 相关迁移。
- 本次不改业务 API、领域模型、数据库表结构、缓存策略、权限规则和 Docker Java 镜像。
- 本次不启用虚拟线程，也不调整 Tomcat、HikariCP、MyBatis 的运行参数。

## 4. 影响范围
- 主要影响模块：`backend/pom.xml`。
- 间接影响：
  - Spring Boot starter 依赖版本由父工程统一升级。
  - Spring Framework、Spring Security、Spring Data、Jackson、Tomcat、Micrometer 等托管依赖会随 Spring Boot 3.5.14 对齐。
  - `springdoc-openapi` 需要从旧版本升级到兼容 Spring Framework 6.2 的版本。
  - Flyway core / MySQL 扩展需要升级到与当前 Boot 3.5 组合更匹配的 11.x 分支。
- 不涉及表、缓存、外部接口或消息契约调整。

## 5. 领域建模
- 本次是技术栈基线升级，不新增或修改业务领域对象。
- 用户、角色、认证、系统探活等现有模型保持不变。
- 不引入新的业务状态，也不改变现有状态语义。

## 6. API 设计
- 不新增 API。
- 不改变现有请求参数、响应结构和错误码。
- 需要重点回归的接口：
  - `/api/v1/system/ping`
  - `/actuator/health`
  - `/v3/api-docs`
  - 现有注册、登录、鉴权相关测试覆盖的接口

## 7. 数据设计
- 不新增表结构调整。
- 不新增索引或唯一约束。
- Flyway 只升级迁移引擎依赖版本，不新增迁移脚本。
- 数据一致性策略保持现状。

## 8. 关键流程
- 正常流程：
  1. Maven 使用 Spring Boot `3.5.14` 父工程解析依赖管理。
  2. 测试 profile 使用 H2 数据源启动 Spring Boot 上下文。
  3. Flyway 在测试启动时执行现有迁移脚本。
  4. MockMvc 覆盖系统、鉴权和 OpenAPI 相关接口。
- 异常流程：
  - 如果 `springdoc` 仍停留在 `2.6.0`，`/v3/api-docs` 在 Spring Framework 6.2 下存在运行时方法签名不兼容风险。
  - 如果 Flyway 版本过旧，后续 MySQL 或 H2 组合升级时更容易出现数据库支持模块兼容问题。
- 状态流转：不涉及业务状态流转。

## 9. 并发 / 幂等 / 缓存
- 不改变请求并发模型。
- 不新增幂等令牌、分布式锁或缓存使用。
- Redis 依赖仍只作为后续缓存、幂等、防重复提交等能力的基础设施入口。

## 10. 权限与安全
- Spring Security 版本会随 Spring Boot 3.5.14 升级，但本次不调整安全配置语义。
- 需要通过现有 Auth 集成测试回归：
  - 未登录访问受保护资源应返回 401。
  - 角色权限不足应返回 403。
  - 登录后携带 JWT 应能访问允许的接口。
- OpenAPI 与 Swagger UI 暂保持当前配置；生产环境是否关闭文档端点后续可单独做安全加固任务。

## 11. 测试策略
- 单元测试：运行完整 Maven test，覆盖 DTO、枚举绑定、异常处理等现有单元测试。
- 集成测试：运行 Spring Boot 测试上下文，覆盖 H2 + Flyway + MyBatis + Security + MockMvc。
- 接口验证：重点依赖 `SystemControllerTest` 覆盖 `/v3/api-docs`，确认 springdoc 与 Spring Framework 6.2 组合可用。
- 异常场景验证：保留现有全局异常、安全异常测试，不因框架升级改变响应契约。

## 12. 风险与替代方案
- 当前方案风险：
  - 父工程升级会带来一批托管依赖版本变化，某些行为差异可能不被现有测试覆盖。
  - Spring Security、Jackson、Tomcat 的默认行为如有细节变化，可能需要后续通过更细的接口回归发现。
- 备选方案：
  - 方案 A：只升级 Spring Boot 父工程，不升级 springdoc 和 Flyway。
  - 方案 B：直接升级到 Spring Boot 4.0.6。
  - 方案 C：本次同时升级 Java 21。
- 不选备选方案的原因：
  - 不选方案 A：已知旧 springdoc 与 Spring Framework 6.2 存在运行时兼容问题，无法保证 OpenAPI 文档接口。
  - 不选方案 B：Spring Boot 4.x 会引入更大的测试注解、依赖生态和框架基线变化，适合作为下一阶段迁移。
  - 不选方案 C：Java 21 是合理方向，但它会同时影响本地 JDK、Docker 镜像、CI/CD 和构建约束；拆成独立步骤更容易定位问题。
