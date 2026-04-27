package com.eventhub.modules.auth.mapper;

import com.eventhub.modules.auth.entity.UserEntity;
import com.eventhub.modules.auth.enums.UserStatus;
import com.eventhub.modules.auth.mapper.param.UserCreateParam;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * users 表数据访问入口。
 * 当前使用 MyBatis Mapper 接口承接服务层的数据访问契约，具体 SQL 与结果映射集中维护在
 * {@code resources/mapper/auth/UserMapper.xml} 中。
 */
@Mapper
public interface UserMapper {

    /**
     * 判断用户名是否已经存在。
     *
     * @param username 用户名
     * @return true 表示已存在
     */
    boolean existsByUsername(@Param("username") String username);

    /**
     * 判断邮箱是否已经存在。
     *
     * @param email 邮箱
     * @return true 表示已存在
     */
    boolean existsByEmail(@Param("email") String email);

    /**
     * 创建用户并返回数据库生成的主键。
     *
     * @param param 用户创建参数，MyBatis 会把生成的 id 回填到该对象中
     * @return 受影响行数
     */
    int insert(UserCreateParam param);

    /**
     * 根据用户名或邮箱查询用户。
     *
     * @param usernameOrEmail 用户名或邮箱
     * @return 用户记录
     */
    Optional<UserEntity> findByUsernameOrEmail(@Param("usernameOrEmail") String usernameOrEmail);

    /**
     * 根据用户主键查询用户。
     *
     * @param userId 用户主键
     * @return 用户记录
     */
    Optional<UserEntity> findById(@Param("userId") Long userId);

    /**
     * 查询全部用户摘要所需的基础数据。
     *
     * @return 用户记录列表
     */
    List<UserEntity> findAll();

    /**
     * 更新用户状态。
     *
     * @param userId 用户主键
     * @param status 目标状态
     * @return 受影响行数
     */
    int updateStatus(@Param("userId") Long userId, @Param("status") UserStatus status);
}
