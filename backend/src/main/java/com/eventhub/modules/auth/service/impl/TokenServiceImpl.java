package com.eventhub.modules.auth.service.impl;

import org.springframework.stereotype.Service;

import com.eventhub.infra.security.jwt.JwtClaims;
import com.eventhub.infra.security.jwt.JwtCodec;
import com.eventhub.modules.auth.service.TokenService;
import com.eventhub.modules.auth.vo.UserInfo;

import lombok.RequiredArgsConstructor;

/**
 * token 业务语义服务实现。
 *
 * <p>
 * 当前阶段 access token 只写入用户 ID 作为 subject。
 * 即使 {@link UserInfo} 中包含用户名、邮箱、状态和角色，这些字段也不会被写入 JWT，
 * 因为最终用户状态和权限必须在每次请求时重新加载最新数据。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class TokenServiceImpl implements TokenService {

    private final JwtCodec jwtCodec;

    @Override
    public String issueAccessToken(UserInfo userInfo) {
        return jwtCodec.generateAccessToken(new JwtClaims(userInfo.id()));
    }

    @Override
    public long accessTokenTtlSeconds() {
        return jwtCodec.accessTokenTtlSeconds();
    }
}
