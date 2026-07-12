package com.eventhub.modules.auth.dto.request;

import com.eventhub.modules.auth.enums.UserStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 管理员更新用户状态请求。
 * 当前只允许在 ENABLED 与 DISABLED 之间切换，为后续封禁、解封等管理能力打基础。
 *
 * <p>
 * 该 DTO 由 {@code @RequestBody} 接收 JSON 请求体，枚举字段会先经过 Jackson 反序列化：
 * 合法字符串如 {@code "DISABLED"} 会被转换为 {@link UserStatus#DISABLED}；非法字符串或大小写不匹配会在
 * 反序列化阶段失败，此时还不会进入 {@link jakarta.validation.constraints.NotNull} 校验。空值 {@code null}
 * 可以被绑定为 null，随后由 Bean Validation 返回字段级错误。全局 Jackson 配置已禁止数字 ordinal，避免
 * {@code 0} 这类不稳定输入被解释为枚举声明顺序。
 * </p>
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
