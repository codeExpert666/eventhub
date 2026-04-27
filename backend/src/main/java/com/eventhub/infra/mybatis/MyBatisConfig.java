package com.eventhub.infra.mybatis;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis 基础配置。
 *
 * <p>{@link MapperScan} 会在业务模块包下扫描带有 {@link Mapper} 注解的接口，
 * 并为这些接口创建 Spring Bean。这样 Service 可以像注入普通组件一样注入 Mapper，
 * 但真正执行 SQL 时由 MyBatis 根据接口方法和 XML statement 完成。</p>
 */
@Configuration
@MapperScan(basePackages = "com.eventhub.modules", annotationClass = Mapper.class)
public class MyBatisConfig {
}
