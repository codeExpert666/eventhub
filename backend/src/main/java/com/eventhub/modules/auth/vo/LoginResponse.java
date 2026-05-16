package com.eventhub.modules.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 登录成功响应。
 * accessToken 是后续访问受保护接口的凭证，调用方需要放入 Authorization Bearer 请求头。
 *
 * @param accessToken JWT access token
 * @param tokenType   token 类型，当前固定为 Bearer
 * @param expiresIn   token 有效秒数
 * @param user        当前登录用户摘要
 */
@Schema(description = "登录成功响应")
public record LoginResponse(
        @Schema(description = "JWT access token")
        String accessToken,

        @Schema(description = "token 类型", example = "Bearer")
        String tokenType,

        @Schema(description = "有效秒数", example = "1800")
        long expiresIn,

        @Schema(description = "当前登录用户")
        UserInfo user
) {
}
