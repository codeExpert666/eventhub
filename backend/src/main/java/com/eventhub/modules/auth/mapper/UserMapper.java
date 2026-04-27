package com.eventhub.modules.auth.mapper;

import com.eventhub.modules.auth.entity.UserEntity;
import com.eventhub.modules.auth.enums.UserStatus;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * users 表数据访问入口。
 * 当前项目阶段使用 JdbcTemplate 保持 SQL 可见，便于学习表结构、索引和业务查询之间的关系。
 */
@Repository
@RequiredArgsConstructor
public class UserMapper {

    private static final RowMapper<UserEntity> USER_ROW_MAPPER = (resultSet, rowNum) -> new UserEntity(
            resultSet.getLong("id"),
            resultSet.getString("username"),
            resultSet.getString("email"),
            resultSet.getString("password_hash"),
            UserStatus.valueOf(resultSet.getString("status")),
            toLocalDateTime(resultSet.getTimestamp("created_at")),
            toLocalDateTime(resultSet.getTimestamp("updated_at"))
    );

    private final JdbcTemplate jdbcTemplate;

    /**
     * 判断用户名是否已经存在。
     *
     * @param username 用户名
     * @return true 表示已存在
     */
    public boolean existsByUsername(String username) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username = ?",
                Integer.class,
                username
        );
        return count != null && count > 0;
    }

    /**
     * 判断邮箱是否已经存在。
     *
     * @param email 邮箱
     * @return true 表示已存在
     */
    public boolean existsByEmail(String email) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = ?",
                Integer.class,
                email
        );
        return count != null && count > 0;
    }

    /**
     * 创建用户并返回数据库生成的主键。
     *
     * @param username 用户名
     * @param email 邮箱
     * @param passwordHash BCrypt 密码哈希
     * @return 新用户主键
     */
    public Long insert(String username, String email, String passwordHash) {
        String sql = """
                INSERT INTO users (username, email, password_hash, status)
                VALUES (?, ?, ?, ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            /*
             * 显式指定只回填 id。
             * H2 在 MySQL 模式下可能把带默认值的时间字段也作为 generated keys 返回，
             * 如果不限定列名，GeneratedKeyHolder#getKey 会因为返回多列而失败。
             */
            PreparedStatement statement = connection.prepareStatement(sql, new String[]{"id"});
            statement.setString(1, username);
            statement.setString(2, email);
            statement.setString(3, passwordHash);
            statement.setString(4, UserStatus.ENABLED.name());
            return statement;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to retrieve generated user id");
        }
        return key.longValue();
    }

    /**
     * 根据用户名或邮箱查询用户。
     *
     * @param usernameOrEmail 用户名或邮箱
     * @return 用户记录
     */
    public Optional<UserEntity> findByUsernameOrEmail(String usernameOrEmail) {
        try {
            UserEntity user = jdbcTemplate.queryForObject(
                    """
                            SELECT id, username, email, password_hash, status, created_at, updated_at
                            FROM users
                            WHERE username = ? OR email = ?
                            LIMIT 1
                            """,
                    USER_ROW_MAPPER,
                    usernameOrEmail,
                    usernameOrEmail
            );
            return Optional.ofNullable(user);
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    /**
     * 根据用户主键查询用户。
     *
     * @param userId 用户主键
     * @return 用户记录
     */
    public Optional<UserEntity> findById(Long userId) {
        try {
            UserEntity user = jdbcTemplate.queryForObject(
                    """
                            SELECT id, username, email, password_hash, status, created_at, updated_at
                            FROM users
                            WHERE id = ?
                            """,
                    USER_ROW_MAPPER,
                    userId
            );
            return Optional.ofNullable(user);
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    /**
     * 查询全部用户摘要所需的基础数据。
     *
     * @return 用户记录列表
     */
    public List<UserEntity> findAll() {
        return jdbcTemplate.query(
                """
                        SELECT id, username, email, password_hash, status, created_at, updated_at
                        FROM users
                        ORDER BY id ASC
                        """,
                USER_ROW_MAPPER
        );
    }

    /**
     * 更新用户状态。
     *
     * @param userId 用户主键
     * @param status 目标状态
     * @return 受影响行数
     */
    public int updateStatus(Long userId, UserStatus status) {
        return jdbcTemplate.update(
                """
                        UPDATE users
                        SET status = ?, updated_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                        """,
                status.name(),
                userId
        );
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
