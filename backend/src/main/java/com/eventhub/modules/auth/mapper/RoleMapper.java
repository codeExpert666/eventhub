package com.eventhub.modules.auth.mapper;

import com.eventhub.modules.auth.entity.RoleEntity;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * roles 与 user_roles 表数据访问入口。
 * 角色相关 SQL 集中维护在 {@code resources/mapper/auth/RoleMapper.xml}，
 * 服务层只依赖这里暴露的接口方法，避免直接拼装权限关系 SQL。
 */
@Mapper
public interface RoleMapper {

    /**
     * 根据角色编码查询角色。
     *
     * @param code 角色编码
     * @return 角色记录
     */
    Optional<RoleEntity> findByCode(@Param("code") String code);

    /**
     * 查询某个用户拥有的角色编码。
     *
     * @param userId 用户主键
     * @return 角色编码列表
     */
    List<String> findRoleCodesByUserId(@Param("userId") Long userId);

    /**
     * 为用户绑定角色。
     *
     * @param userId 用户主键
     * @param roleId 角色主键
     */
    void addRoleToUser(@Param("userId") Long userId, @Param("roleId") Long roleId);
}
