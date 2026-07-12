package com.eventhub.modules.auth.mapper;

import java.time.LocalDateTime;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.eventhub.modules.auth.entity.AuthSessionEntity;
import com.eventhub.modules.auth.enums.AuthSessionStatus;

/**
 * auth_sessions 表数据访问入口。
 *
 * <p>
 * 该 Mapper 只提供认证会话持久化的最小能力，后续 refresh API、logout 吊销和设备会话列表
 * 都应优先复用这些按业务语义命名的方法，而不是在服务层直接拼装 SQL。
 * </p>
 */
@Mapper
public interface AuthSessionMapper {

    /**
     * 创建服务端认证会话。
     *
     * @param session 会话实体，MyBatis 会把数据库生成的 id 回填到该对象中
     * @return 受影响行数
     */
    int insert(AuthSessionEntity session);

    /**
     * 根据会话标识查询会话。
     *
     * @param sessionId 会话标识
     * @return 会话记录
     */
    Optional<AuthSessionEntity> findBySessionId(@Param("sessionId") String sessionId);

    /**
     * 根据 refresh token 哈希查询会话。
     * refresh API 会先对客户端提交的明文 token 做哈希，再通过该方法定位服务端会话。
     *
     * @param refreshTokenHash refresh token 哈希
     * @return 会话记录
     */
    Optional<AuthSessionEntity> findByRefreshTokenHash(@Param("refreshTokenHash") String refreshTokenHash);

    /**
     * 使用旧 refresh token 哈希和旧 version 条件轮换 refresh token。
     *
     * <p>
     * 该 SQL 是 refresh 并发安全的最终防线：同一个旧 token 被并发提交时，第一笔成功更新后会改变
     * refresh_token_hash 和 version，后续请求即使持有旧会话快照也无法再次命中条件。
     * </p>
     *
     * @param sessionId           会话标识
     * @param oldRefreshTokenHash 旧 refresh token 哈希
     * @param oldVersion          refresh 前会话版本
     * @param newRefreshTokenHash 新 refresh token 哈希
     * @param refreshedAt         本次 refresh 时间
     * @param refreshExpiresAt    新 refresh token 过期时间
     * @return 受影响行数，1 表示轮换成功
     */
    int rotateRefreshToken(
            @Param("sessionId") String sessionId,
            @Param("oldRefreshTokenHash") String oldRefreshTokenHash,
            @Param("oldVersion") Integer oldVersion,
            @Param("newRefreshTokenHash") String newRefreshTokenHash,
            @Param("refreshedAt") LocalDateTime refreshedAt,
            @Param("refreshExpiresAt") LocalDateTime refreshExpiresAt);

    /**
     * 更新会话最近活跃时间。
     *
     * @param sessionId  会话标识
     * @param lastSeenAt 最近活跃时间
     * @return 受影响行数
     */
    int updateLastSeenAt(
            @Param("sessionId") String sessionId,
            @Param("lastSeenAt") LocalDateTime lastSeenAt);

    /**
     * 吊销单个会话。
     *
     * @param sessionId    会话标识
     * @param revokedAt    吊销时间
     * @param revokeReason 吊销原因
     * @return 受影响行数
     */
    int revokeBySessionId(
            @Param("sessionId") String sessionId,
            @Param("revokedAt") LocalDateTime revokedAt,
            @Param("revokeReason") String revokeReason);

    /**
     * 更新会话状态。
     * 该方法主要服务 Mapper 覆盖测试和后续批量状态维护；业务侧吊销单会话优先使用 revokeBySessionId。
     *
     * @param sessionId 会话标识
     * @param status    目标状态
     * @return 受影响行数
     */
    int updateStatus(
            @Param("sessionId") String sessionId,
            @Param("status") AuthSessionStatus status);
}
