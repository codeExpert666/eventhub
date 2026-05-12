package com.eventhub.modules.auth.entity;

import java.time.LocalDateTime;

/**
 * roles 表对应的持久化对象。
 * 阶段 1 的权限判断只依赖稳定的角色编码，例如 USER、ADMIN。
 *
 * @param id          角色主键
 * @param code        角色编码
 * @param name        角色名称
 * @param description 角色说明
 * @param createdAt   创建时间
 */
public record RoleEntity(
        Long id,
        String code,
        String name,
        String description,
        LocalDateTime createdAt) {
}
