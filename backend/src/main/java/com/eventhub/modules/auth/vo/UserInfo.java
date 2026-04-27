package com.eventhub.modules.auth.vo;

import com.eventhub.modules.auth.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 对外返回的用户摘要。
 * 该对象不会暴露 passwordHash，只返回接口调用方需要理解身份和权限边界的最小信息。
 *
 * @param id 用户主键
 * @param username 用户名
 * @param email 邮箱
 * @param status 用户状态
 * @param roles 用户角色编码集合
 */
@Schema(description = "用户摘要")
public record UserInfo(
        @Schema(description = "用户 ID", example = "1")
        Long id,

        @Schema(description = "用户名", example = "alice")
        String username,

        @Schema(description = "邮箱", example = "alice@example.com")
        String email,

        @Schema(description = "用户状态", example = "ENABLED")
        UserStatus status,

        @Schema(description = "角色编码集合", example = "[\"USER\"]")
        List<String> roles
) {
    public UserInfo {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
