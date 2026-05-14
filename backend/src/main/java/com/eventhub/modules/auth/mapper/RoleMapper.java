package com.eventhub.modules.auth.mapper;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.eventhub.modules.auth.entity.RoleEntity;

/**
 * roles 与 user_roles 表数据访问入口。
 * 角色相关 SQL 集中维护在 {@code resources/mapper/auth/RoleMapper.xml}，
 * 服务层只依赖这里暴露的接口方法，避免直接拼装权限关系 SQL。
 *
 * <p>
 * Mapper 返回值按查询语义选择：
 * <ul>
 *     <li>按唯一条件查询单条记录时，结果可能存在也可能不存在，使用 {@link Optional} 明确表达“0 或 1 条”。</li>
 *     <li>查询多条记录时，使用 {@link List}，没有数据时返回空列表，不再额外包一层 Optional。</li>
 *     <li>关键写操作返回受影响行数，方便服务层校验是否真的完成预期写入。</li>
 * </ul>
 */
@Mapper
public interface RoleMapper {

    /**
     * 根据角色编码查询角色。
     * 角色编码具有唯一业务含义，查询结果最多一条；如果数据库中不存在该角色，则返回 Optional.empty()。
     *
     * @param code 角色编码
     * @return 角色记录容器，有值表示找到角色，空值表示角色不存在
     */
    Optional<RoleEntity> findByCode(@Param("code") String code);

    /**
     * 查询某个用户拥有的角色编码。
     * 一个用户可能没有角色，也可能拥有多个角色；没有角色时返回空列表，而不是 Optional<List<String>>。
     *
     * @param userId 用户主键
     * @return 角色编码列表
     */
    List<String> findRoleCodesByUserId(@Param("userId") Long userId);

    /**
     * 为用户绑定角色。
     * 默认角色绑定是注册闭环的关键写操作，因此返回受影响行数供服务层校验。
     *
     * @param userId 用户主键
     * @param roleId 角色主键
     * @return 受影响行数，正常应为 1
     */
    int addRoleToUser(@Param("userId") Long userId, @Param("roleId") Long roleId);
}
