package com.eventhub.modules.auth.service;

import com.eventhub.modules.auth.vo.UserInfo;

/**
 * auth 模块内的 token 业务语义服务。
 *
 * <p>
 * JWT 的生成、解析、验签等技术细节由 infra 层的 JwtCodec 负责；
 * 本接口负责表达 auth 模块“什么时候签发 token、给谁签发、token 中放哪些业务 claim”。
 * </p>
 */
public interface TokenService {

    /**
     * 为登录成功的用户签发 access token。
     *
     * @param userInfo  登录成功后的用户摘要
     * @param sessionId 服务端认证会话标识
     * @return access token
     */
    String issueAccessToken(UserInfo userInfo, String sessionId);

    /**
     * 生成 opaque refresh token。
     *
     * <p>
     * refresh token 明文只返回给客户端一次，服务端落库时只保存哈希。
     * </p>
     *
     * @return refresh token 明文
     */
    String issueRefreshToken();

    /**
     * 当前 access token 默认有效秒数。
     *
     * @return 有效秒数
     */
    long accessTokenTtlSeconds();

    /**
     * 当前 refresh token 默认有效秒数。
     *
     * @return 有效秒数
     */
    long refreshTokenTtlSeconds();
}
