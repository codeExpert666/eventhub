package com.eventhub.modules.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * token pair 响应。
 * 用于 refresh 成功后返回新的 access token 与 refresh token，字段命名与登录响应保持一致。
 *
 * @param accessToken         JWT access token
 * @param refreshToken        opaque refresh token
 * @param authorizationScheme HTTP Authorization 授权方案，当前固定为 Bearer
 * @param expiresIn           access token 有效秒数
 * @param refreshExpiresIn    refresh token 有效秒数
 * @param sessionId           服务端认证会话标识
 * @param user                当前用户摘要
 */
@Schema(description = "token pair 响应")
public record TokenPairResponse(
        @Schema(description = "JWT access token")
        String accessToken,

        @Schema(description = "opaque refresh token")
        String refreshToken,

        @Schema(description = "HTTP Authorization 授权方案", example = "Bearer")
        String authorizationScheme,

        @Schema(description = "access token 有效秒数", example = "1800")
        long expiresIn,

        @Schema(description = "refresh token 有效秒数", example = "2592000")
        long refreshExpiresIn,

        @Schema(description = "服务端认证会话标识")
        String sessionId,

        @Schema(description = "当前用户")
        UserInfo user
) {
}
