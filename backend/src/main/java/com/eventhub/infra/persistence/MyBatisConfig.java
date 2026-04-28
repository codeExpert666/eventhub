package com.eventhub.infra.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis 基础配置。
 *
 * <p>这个配置类的核心目的，是把“普通 Java 接口形式的 Mapper”注册成 Spring 容器中的 Bean。
 * 例如 {@code com.eventhub.modules.auth.mapper.UserMapper} 本身只是一个接口，没有实现类；
 * 应用启动时，MyBatis-Spring 会根据这里的扫描规则为它生成代理对象。这样 Service 层就可以像注入
 * 普通 Spring 组件一样注入 Mapper，而真正执行 SQL 时，则由 MyBatis 根据接口方法、方法参数、
 * 以及 {@code resources/mapper/auth/UserMapper.xml} 这类 XML 文件中的 SQL statement 完成数据库访问。</p>
 *
 * <p>这个配置之所以会生效，是因为当前类位于 {@code com.eventhub} 根包之下。
 * Spring Boot 启动类会从根包开始做组件扫描，发现带有 {@link Configuration} 的类后，
 * 会把它当成配置类加载；随后 MyBatis-Spring 读取 {@link MapperScan} 的扫描规则，
 * 扫描符合条件的 Mapper 接口并注册对应代理 Bean。</p>
 *
 * <p>注意：这个类没有显式声明 {@code @Bean} 方法是正常的。
 * 它的配置能力来自类上的注解，属于“声明式配置”；启动阶段框架会读取这些注解并完成 Bean 注册。</p>
 */
/*
 * @Configuration:
 * 1. 表示当前类是 Spring 配置类，会被 Spring 容器识别和加载。
 * 2. 在本项目中，它让下面的 @MapperScan 成为应用启动配置的一部分。
 * 3. 如果没有这个注解，当前类可能只是一个普通 Java 类，Spring 不一定会处理类上的 Mapper 扫描规则。
 */
@Configuration
/*
 * @MapperScan:
 * 这是 MyBatis-Spring 提供的注解，用来批量扫描 Mapper 接口。
 *
 * basePackages = "com.eventhub.modules":
 * 1. 指定从哪个 Java 包开始扫描 Mapper。
 * 2. 这里选择 com.eventhub.modules，是因为业务模块都放在 modules 下面，例如 auth 模块的
 *    UserMapper、RoleMapper 都位于 com.eventhub.modules.auth.mapper 包。
 * 3. 扫描会覆盖该包及其子包，所以后续新增 event、order、payment 等模块时，只要它们仍在
 *    com.eventhub.modules.* 下，就可以沿用这份配置。
 * 4. 不直接扫描 com.eventhub 根包，是为了减少误扫描范围，避免把基础设施、配置类、DTO、VO
 *    或其他接口错误地纳入 Mapper 注册流程。
 *
 * annotationClass = Mapper.class:
 * 1. 表示只有标注了 @Mapper 的接口才会被注册为 MyBatis Mapper Bean。
 * 2. @Mapper 是 MyBatis 提供的标记注解，用来说明“这个接口不是普通业务接口，而是 SQL 映射接口”。
 * 3. 这个过滤条件让 Mapper 注册更显式：新增数据访问接口时，需要主动添加 @Mapper，
 *    也能避免将同一个包下的普通接口误注册成数据库访问代理。
 *
 * 生效后的调用链可以理解为：
 * Service 注入 Mapper 接口 -> Spring 容器提供 MyBatis 代理对象 -> 代理对象根据接口方法找到 XML SQL
 * -> MyBatis 使用 DataSource 执行 SQL -> 将查询结果映射回 Java 对象。
 */
@MapperScan(basePackages = "com.eventhub.modules", annotationClass = Mapper.class)
public class MyBatisConfig {
}
