package com.eventhub.modules.auth.entity;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * roles 表对应的持久化对象。
 * 阶段 1 的权限判断只依赖稳定的角色编码，例如 USER、ADMIN。
 * 字段必须与 roles 表字段保持一一对应，方便审查 MyBatis 映射是否完整。
 */
@Getter
@Setter
@NoArgsConstructor
public class RoleEntity {

    /**
     * roles.id，角色自增主键。
     */
    private Long id;

    /**
     * roles.code，稳定角色编码，例如 USER、ADMIN。
     */
    private String code;

    /**
     * roles.name，角色展示名称。
     */
    private String name;

    /**
     * roles.description，角色说明。
     */
    private String description;

    /**
     * roles.created_at，创建时间，由数据库默认值写入。
     */
    private LocalDateTime createdAt;
}
