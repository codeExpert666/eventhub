package com.eventhub.modules.auth.entity;

import java.time.LocalDateTime;

import com.eventhub.modules.auth.enums.UserStatus;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * users 表对应的持久化对象。
 * 该对象只表达数据库行数据，不直接承载密码校验、token 签发或权限判断等业务流程。
 * 字段必须与 users 表字段保持一一对应，避免读取模型和写入参数对象再次分叉。
 */
@Getter
@Setter
@NoArgsConstructor
public class UserEntity {

    /**
     * users.id，用户自增主键。
     * 插入前为空，执行 {@code UserMapper.insert} 后由 MyBatis generated keys 回填。
     */
    private Long id;

    /**
     * users.username，规范化后的登录用户名。
     */
    private String username;

    /**
     * users.email，规范化后的登录邮箱。
     */
    private String email;

    /**
     * users.password_hash，BCrypt 密码哈希，永远不保存明文密码。
     */
    private String passwordHash;

    /**
     * users.status，用户状态。
     */
    private UserStatus status;

    /**
     * users.created_at，创建时间，由数据库默认值写入。
     */
    private LocalDateTime createdAt;

    /**
     * users.updated_at，更新时间，由数据库默认值或更新语句维护。
     */
    private LocalDateTime updatedAt;

    /**
     * 构造一个默认启用的普通用户持久化对象。
     * 该工厂只填充注册插入需要显式写入的字段，主键和时间字段交给数据库生成。
     *
     * @param username     用户名
     * @param email        邮箱
     * @param passwordHash BCrypt 密码哈希
     * @return 可交给 MyBatis 执行插入的用户实体
     */
    public static UserEntity enabledUser(String username, String email, String passwordHash) {
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setStatus(UserStatus.ENABLED);
        return user;
    }
}
