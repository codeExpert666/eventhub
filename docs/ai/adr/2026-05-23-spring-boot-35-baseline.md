# 采用 Spring Boot 3.5.14 作为当前后端基线

## 标题
采用 Spring Boot 3.5.14 作为当前后端基线

## 状态
- accepted

## 背景
当前项目仍使用 Spring Boot `3.3.2`，所属 3.3.x 分支已经结束开源支持。继续停留在该版本会使后端基础设施逐步脱离官方维护窗口，也会影响后续安全修复、依赖升级和简历项目的技术可信度。

项目已经规划后续评估 Spring Boot 4.x 和 Java 21，但这两类升级都会带来更大的构建、测试和生态兼容影响。为了让演进过程可验证、可复盘，本次先处理 Spring Boot 3.x 内部的受支持版本升级。

## 决策
当前阶段将后端 Spring Boot 父工程升级到 `3.5.14`，并同步升级与该基线直接相关的兼容依赖：

- `springdoc-openapi` 升级到兼容 Spring Framework 6.2 的 2.8.x 版本。
- Flyway 升级到 11.x 分支，保持迁移引擎与当前 Spring Boot 3.5 依赖组合更一致。
- Java 基线暂时保持 17，避免本次同时改变运行时 JDK 和 Docker 镜像。
- MyBatis Spring Boot Starter 暂时保持 3.0.x，Boot 4.x 之前不切换到 4.x 分支。

## 备选方案
- 方案 1：继续停留在 Spring Boot 3.3.2。
- 方案 2：只升级 Spring Boot 父工程，不升级 springdoc / Flyway。
- 方案 3：直接升级到 Spring Boot 4.0.6。
- 方案 4：本次同时升级 Java 21。

## 决策理由
- Spring Boot 3.5.14 能让项目回到受支持的 3.x 基线，同时保留 Spring Boot 3 系列的 API 和生态稳定性。
- 旧 `springdoc 2.6.0` 与 Spring Framework 6.2 组合存在 OpenAPI 运行时兼容风险，必须随 Boot 3.5 同步升级。
- 直接升级 Boot 4 会改变更大的框架面，适合在 Boot 3.5 稳定后单独评估。
- Java 21 是后续推荐方向，但它涉及本地开发环境、Docker、CI 和 Maven 构建约束，拆成下一步能降低排障复杂度。

## 影响
- 好处：
  - 后端框架回到受支持的 Spring Boot 3.x 基线。
  - OpenAPI、Flyway 等关键基础设施依赖与新框架组合保持可验证兼容。
  - 为后续 Java 21 和 Spring Boot 4 迁移提供更清晰的中间状态。
- 代价：
  - 父工程升级会带来一批托管依赖版本变化，需要通过测试和后续手工回归持续观察行为差异。
  - 保持 Java 17 意味着本次还没有用上 Java 21 LTS 的长期运行时收益。
- 后续可能需要调整的地方：
  - 单独推进 Java 21 基线升级。
  - 单独评估生产环境关闭 Swagger UI / OpenAPI 文档端点。
  - 在 Java 21 基线稳定后，再评估 Spring Boot 4.0.6 迁移。
