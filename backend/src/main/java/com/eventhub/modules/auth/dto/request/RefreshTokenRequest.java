package com.eventhub.modules.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * refresh token 续期请求。
 * refresh token 明文只允许通过本次请求短暂进入服务端调用栈，持久化时必须转换为哈希。
 *
 * @param refreshToken 客户端保存的 opaque refresh token
 */
@Schema(description = "refresh token 续期请求")
public record RefreshTokenRequest(
        @Schema(description = "opaque refresh token")
        @NotBlank(message = "refreshToken 不能为空")
        @Size(max = 128, message = "refreshToken 长度不能超过 128 个字符")
        String refreshToken
) {
}
