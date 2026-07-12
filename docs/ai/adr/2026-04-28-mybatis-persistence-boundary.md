# ADR：使用 MyBatis XML Mapper 作为当前单体持久化边界

## 标题
使用 MyBatis XML Mapper 作为当前单体持久化边界

## 状态
- accepted

## 背景
项目已经进入认证授权阶段，并会继续扩展活动、场次、票种、订单、库存、支付、通知与操作日志等业务。当前 `auth` 模块虽然命名为 `mapper`，但实际仍通过 `JdbcTemplate` 直接执行 SQL，并在 Java 类里手写 `RowMapper` 和主键回填逻辑。

这种方式适合基础阶段看清 JDBC 执行链路，但当表和查询变多后，服务层附近会出现大量 SQL 字符串、结果映射和参数绑定细节。为了让项目结构更接近企业后端工程，同时不牺牲 SQL 可见性，需要明确后续持久化边界。

## 决策
当前单体后端选择 MyBatis XML Mapper 作为主要持久化方式：

- 使用 `mybatis-spring-boot-starter` 接入 Spring Boot 自动配置。
- Mapper Java 文件只保留接口方法和必要注释，不直接依赖 `JdbcTemplate`。
- SQL、`resultMap`、主键回填等映射细节集中放在 `resources/mapper/**/*.xml`。
- Java 服务层继续依赖模块内的 Mapper 接口，不直接感知 XML、`SqlSession` 或底层 JDBC API。
- 暂不引入 MyBatis-Plus、JPA 或通用 BaseMapper。

## 备选方案
- 方案 1：继续使用 `JdbcTemplate`。
- 方案 2：使用 MyBatis 注解 SQL。
- 方案 3：引入 MyBatis-Plus。
- 方案 4：迁移到 JPA / Hibernate。

## 决策理由
- MyBatis XML 能让复杂 SQL 保持可读、可审查，也便于后续解释索引、锁、库存扣减和幂等写入。
- Mapper 接口能让服务层依赖稳定契约，避免把 JDBC 细节扩散到业务流程中。
- XML `resultMap` 可以显式描述数据库列与 Java record 构造参数的关系，降低隐式映射带来的学习成本。
- MyBatis-Plus 会提高 CRUD 速度，但当前项目更需要通过真实 SQL 理解业务模型和数据库约束。
- JPA 抽象更高，但对后续票务库存、订单状态机和支付幂等场景来说，直接控制 SQL 更符合当前路线。
- MyBatis 注解 SQL 适合简单查询；随着查询变复杂，注解内长 SQL 的可读性和维护体验会下降。

## 影响
- 好处：
  - 数据访问层结构更贴近企业项目。
  - SQL 与结果映射集中管理，后续 Review 和性能优化更直接。
  - 服务层更干净，只关心业务流程和事务边界。
  - 后续活动、订单、库存模块可以复用同一 Mapper 规范。
- 代价：
  - 新增 XML 文件和 MyBatis 配置，需要维护 namespace 与接口方法的一致性。
  - 初期比 `JdbcTemplate` 多一层框架概念，需要通过注释和文档解释清楚。
  - 如果滥用动态 SQL，仍可能造成 XML 复杂度膨胀，需要在后续模块中保持查询边界清晰。
- 后续可能需要调整的地方：
  - 订单与库存阶段可补充 Mapper 层专项测试，重点验证并发扣减 SQL。
  - 复杂查询增多后，可按模块拆分 XML 并沉淀 SQL 命名规范。
  - 微服务阶段如果拆分服务，各服务可继续沿用 MyBatis，但 Mapper 包与 XML 路径需要随服务边界独立。
