package com.eventhub.modules.auth.mapper;

import com.eventhub.modules.auth.entity.RoleEntity;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * roles 与 user_roles 表数据访问入口。
 * 角色相关查询集中在这里，避免服务层直接拼装权限关系 SQL。
 */
@Repository
@RequiredArgsConstructor
public class RoleMapper {

    private static final RowMapper<RoleEntity> ROLE_ROW_MAPPER = (resultSet, rowNum) -> new RoleEntity(
            resultSet.getLong("id"),
            resultSet.getString("code"),
            resultSet.getString("name"),
            resultSet.getString("description"),
            toLocalDateTime(resultSet.getTimestamp("created_at"))
    );

    private final JdbcTemplate jdbcTemplate;

    /**
     * 根据角色编码查询角色。
     *
     * @param code 角色编码
     * @return 角色记录
     */
    public Optional<RoleEntity> findByCode(String code) {
        try {
            RoleEntity role = jdbcTemplate.queryForObject(
                    """
                            SELECT id, code, name, description, created_at
                            FROM roles
                            WHERE code = ?
                            """,
                    ROLE_ROW_MAPPER,
                    code
            );
            return Optional.ofNullable(role);
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    /**
     * 查询某个用户拥有的角色编码。
     *
     * @param userId 用户主键
     * @return 角色编码列表
     */
    public List<String> findRoleCodesByUserId(Long userId) {
        return jdbcTemplate.queryForList(
                """
                        SELECT r.code
                        FROM roles r
                        JOIN user_roles ur ON ur.role_id = r.id
                        WHERE ur.user_id = ?
                        ORDER BY r.code ASC
                        """,
                String.class,
                userId
        );
    }

    /**
     * 为用户绑定角色。
     *
     * @param userId 用户主键
     * @param roleId 角色主键
     */
    public void addRoleToUser(Long userId, Long roleId) {
        jdbcTemplate.update(
                "INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)",
                userId,
                roleId
        );
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
