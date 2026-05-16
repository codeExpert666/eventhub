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
     * @param userInfo 登录成功后的用户摘要
     * @return access token
     */
    String issueAccessToken(UserInfo userInfo);

    /**
     * 当前 access token 默认有效秒数。
     *
     * @return 有效秒数
     */
    long accessTokenTtlSeconds();
}
