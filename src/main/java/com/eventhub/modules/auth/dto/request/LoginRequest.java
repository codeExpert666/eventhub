package com.eventhub.modules.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 用户登录请求。
 * usernameOrEmail 允许用户用用户名或邮箱登录，服务层会统一按账号标识查询。
 *
 * @param usernameOrEmail 用户名或邮箱
 * @param password        明文密码，只用于本次登录校验
 */
@Schema(description = "用户登录请求")
public record LoginRequest(
        @Schema(description = "用户名或邮箱", example = "alice")
        @NotBlank(message = "用户名或邮箱不能为空")
        @Size(max = 128, message = "用户名或邮箱长度不能超过 128 个字符")
        String usernameOrEmail,

        @Schema(description = "密码", example = "Password123")
        @NotBlank(message = "密码不能为空")
        @Size(max = 72, message = "密码长度不能超过 72 个字符")
        String password
) {
}
