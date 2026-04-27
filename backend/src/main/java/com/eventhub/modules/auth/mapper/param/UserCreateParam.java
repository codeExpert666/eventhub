package com.eventhub.modules.auth.mapper.param;

import com.eventhub.modules.auth.enums.UserStatus;
import lombok.Getter;
import lombok.Setter;

/**
 * 创建用户时传给 MyBatis 的持久化参数对象。
 *
 * <p>这里不用 {@code UserEntity} 的原因是：{@code UserEntity} 是不可变 record，更适合作为数据库行快照；
 * 而插入用户时需要让 MyBatis 把数据库生成的自增主键回填到对象上，所以需要一个可写的参数对象承接 {@code id}。</p>
 */
@Getter
@Setter
public class UserCreateParam {

    /**
     * 数据库生成的用户主键。
     * 插入前为空，执行 {@code UserMapper.insert} 后由 MyBatis 根据 generated keys 回填。
     */
    private Long id;

    /**
     * 规范化后的用户名。
     */
    private String username;

    /**
     * 规范化后的邮箱。
     */
    private String email;

    /**
     * BCrypt 密码哈希，永远不保存明文密码。
     */
    private String passwordHash;

    /**
     * 新用户默认状态。
     */
    private UserStatus status;

    private UserCreateParam(String username, String email, String passwordHash, UserStatus status) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.status = status;
    }

    /**
     * 构造一个默认启用的普通用户插入参数。
     *
     * @param username 用户名
     * @param email 邮箱
     * @param passwordHash BCrypt 密码哈希
     * @return 可交给 MyBatis 执行插入的参数对象
     */
    public static UserCreateParam enabledUser(String username, String email, String passwordHash) {
        return new UserCreateParam(username, email, passwordHash, UserStatus.ENABLED);
    }
}
