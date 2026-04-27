package com.eventhub.modules.auth.dto.request;

import com.eventhub.modules.auth.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 管理员更新用户状态请求。
 * 当前只允许在 ENABLED 与 DISABLED 之间切换，为后续封禁、解封等管理能力打基础。
 *
 * @param status 目标用户状态
 */
@Schema(description = "更新用户状态请求")
public record UpdateUserStatusRequest(
        @Schema(description = "目标用户状态", example = "DISABLED")
        @NotNull(message = "status 不能为空")
        UserStatus status
) {
}
