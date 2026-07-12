package com.eventhub.modules.auth.mapper.param;

import java.time.LocalDateTime;
import java.util.Locale;

import com.eventhub.modules.auth.enums.UserStatus;

import lombok.Getter;

/**
 * 用户分页查询条件。
 *
 * <p>
 * 该对象是 Service 到 MyBatis Mapper 的参数模型，只包含已经过基础规范化的查询条件。
 * Controller 层的参数校验完成后，再把请求对象转换为该查询条件，避免 XML 中处理 trim、大小写等输入细节。
 * </p>
 */
@Getter
public class UserQueryCriteria {

    private final String username;
    private final String email;

    /**
     * 用户状态精确匹配条件。
     * 在未配置自定义 TypeHandler 时，MyBatis 会使用默认枚举处理器绑定该字段：
     * {@code UserStatus.ENABLED} 会作为字符串 {@code "ENABLED"} 传入 SQL，而不是传入 ordinal 数字。
     * 因此 users.status 的存储值必须与 {@link UserStatus} 枚举常量名保持一致。
     */
    private final UserStatus status;

    /**
     * 创建时间起点。
     * 进入该 Mapper 参数对象前，Controller 请求中的 ISO 日期时间字符串已由 Spring 转成 {@link LocalDateTime}；
     * 传入 MyBatis XML 的 {@code #{criteria.createdAtFrom}} 时，MyBatis 会使用内置 LocalDateTime TypeHandler
     * 通过 JDBC 作为 TIMESTAMP 参数绑定。LocalDateTime 本身不包含时区信息，因此这里只表达本地日期时间。
     */
    private final LocalDateTime createdAtFrom;
    private final LocalDateTime createdAtTo;
    private final LocalDateTime updatedAtFrom;
    private final LocalDateTime updatedAtTo;

    private UserQueryCriteria(
            String username,
            String email,
            UserStatus status,
            LocalDateTime createdAtFrom,
            LocalDateTime createdAtTo,
            LocalDateTime updatedAtFrom,
            LocalDateTime updatedAtTo) {
        this.username = trimToNull(username);
        this.email = normalizeEmailFilter(email);
        this.status = status;
        this.createdAtFrom = createdAtFrom;
        this.createdAtTo = createdAtTo;
        this.updatedAtFrom = updatedAtFrom;
        this.updatedAtTo = updatedAtTo;
    }

    /**
     * 构造用户查询条件。
     *
     * @param username      用户名包含匹配条件
     * @param email         邮箱包含匹配条件
     * @param status        用户状态精确匹配条件
     * @param createdAtFrom 创建时间起点
     * @param createdAtTo   创建时间终点
     * @param updatedAtFrom 更新时间起点
     * @param updatedAtTo   更新时间终点
     * @return 用户查询条件
     */
    public static UserQueryCriteria of(
            String username,
            String email,
            UserStatus status,
            LocalDateTime createdAtFrom,
            LocalDateTime createdAtTo,
            LocalDateTime updatedAtFrom,
            LocalDateTime updatedAtTo) {
        return new UserQueryCriteria(
                username,
                email,
                status,
                createdAtFrom,
                createdAtTo,
                updatedAtFrom,
                updatedAtTo
        );
    }

    private static String normalizeEmailFilter(String email) {
        String trimmed = trimToNull(email);
        if (trimmed == null) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
