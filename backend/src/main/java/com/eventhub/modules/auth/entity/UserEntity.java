package com.eventhub.modules.auth.entity;

import java.time.LocalDateTime;

import com.eventhub.modules.auth.enums.UserStatus;

/**
 * users 表对应的持久化对象。
 * 该对象只表达数据库行数据，不直接承载密码校验、token 签发或权限判断等业务流程。
 *
 * @param id           用户主键
 * @param username     用户名
 * @param email        邮箱
 * @param passwordHash BCrypt 密码哈希
 * @param status       用户状态
 * @param createdAt    创建时间
 * @param updatedAt    更新时间
 */
public record UserEntity(
        Long id,
        String username,
        String email,
        String passwordHash,
        UserStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
