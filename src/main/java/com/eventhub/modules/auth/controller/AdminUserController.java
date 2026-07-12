package com.eventhub.modules.auth.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.eventhub.common.api.ApiResponse;
import com.eventhub.common.api.PageResponse;
import com.eventhub.modules.auth.dto.request.AdminUserQueryRequest;
import com.eventhub.modules.auth.dto.request.UpdateUserStatusRequest;
import com.eventhub.modules.auth.dto.response.UserResponse;
import com.eventhub.modules.auth.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 管理员用户管理接口。
 * 当前仅提供阶段 1 验证 RBAC 所需的最小能力，后续可继续补分页、搜索和审计日志。
 *
 * <p>
 * 类级别的 {@link Validated} 用于启用 Spring 方法级参数校验。当前请求对象的字段校验主要由方法参数上的
 * {@link Valid} 触发；这里保留 {@link Validated} 是为了后续在 {@code @PathVariable}、
 * {@code @RequestParam} 等简单参数上直接声明校验约束（如 {@code @Positive}、{@code @Min}）时能够生效。
 * </p>
 */
@Tag(name = "Admin User", description = "管理员用户管理接口")
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AuthService authService;

    /**
     * 分页查询用户列表。
     *
     * @param request 分页与筛选查询参数
     * @return 用户摘要分页结果
     */
    @Operation(summary = "分页查询用户列表", description = "管理员分页查看平台用户列表")
    @GetMapping
    public ApiResponse<PageResponse<UserResponse>> listUsers(@Valid @ModelAttribute AdminUserQueryRequest request) {
        return ApiResponse.success(authService.listUsers(request));
    }

    /**
     * 更新用户状态。
     *
     * @param userId  用户主键
     * @param request 状态更新请求
     * @return 更新后的用户摘要
     */
    @Operation(summary = "更新用户状态", description = "管理员启用或禁用用户")
    @PatchMapping("/{userId}/status")
    public ApiResponse<UserResponse> updateStatus(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserStatusRequest request) {
        return ApiResponse.success(authService.updateStatus(userId, request));
    }
}
