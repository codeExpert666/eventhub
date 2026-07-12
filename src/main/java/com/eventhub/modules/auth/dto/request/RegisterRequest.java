package com.eventhub.modules.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 用户注册请求。
 * 校验规则放在 DTO 边界，确保非法输入不会进入密码加密、建表写入等业务流程。
 *
 * @param username 用户名
 * @param email    邮箱
 * @param password 明文密码，只在请求入口短暂存在，服务层会立即转换为 BCrypt 哈希
 */
@Schema(description = "用户注册请求")
public record RegisterRequest(
        @Schema(description = "用户名", example = "alice")
        @NotBlank(message = "username 不能为空")
        @Size(min = 3, max = 32, message = "username 长度必须在 3 到 32 个字符之间")
        @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "username 只能包含字母、数字和下划线")
        String username,

        @Schema(description = "邮箱", example = "alice@example.com")
        @NotBlank(message = "email 不能为空")
        @Email(message = "email 格式不合法")
        @Size(max = 128, message = "email 长度不能超过 128 个字符")
        String email,

        @Schema(description = "密码，至少 8 位并同时包含字母和数字", example = "Password123")
        @NotBlank(message = "password 不能为空")
        @Size(min = 8, max = 72, message = "password 长度必须在 8 到 72 个字符之间")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "password 至少包含字母和数字")
        String password) {
}
