package com.eventhub.modules.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 登录成功响应。
 * accessToken 是后续访问受保护接口的凭证，调用方需要放入 Authorization Bearer 请求头。
 * refreshToken 是长期续期凭证，只在登录响应中明文返回一次，服务端只保存哈希。
 *
 * @param accessToken      JWT access token
 * @param refreshToken     opaque refresh token
 * @param authorizationScheme HTTP Authorization 授权方案，当前固定为 Bearer
 * @param expiresIn        access token 有效秒数
 * @param refreshExpiresIn refresh token 有效秒数
 * @param sessionId        服务端认证会话标识
 * @param user             当前登录用户摘要
 */
@Schema(description = "登录成功响应")
public record LoginResponse(
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

        @Schema(description = "当前登录用户")
        UserInfo user
) {
}
