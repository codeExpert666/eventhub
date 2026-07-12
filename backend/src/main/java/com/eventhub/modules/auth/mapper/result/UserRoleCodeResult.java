package com.eventhub.modules.auth.mapper.result;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 用户角色编码批量查询结果行。
 *
 * <p>
 * 该对象不是领域实体，只承接 user_roles 与 roles JOIN 后的扁平结果，
 * 供服务层按 userId 分组后组装 UserResponse.roles。
 * </p>
 */
@Getter
@Setter
@NoArgsConstructor
public class UserRoleCodeResult {

    /**
     * 用户主键，对应 user_roles.user_id。
     */
    private Long userId;

    /**
     * 角色编码，对应 roles.code。
     */
    private String roleCode;
}
