package com.eventhub.modules.auth.controller;

import com.eventhub.common.api.ApiResponse;
import com.eventhub.modules.auth.dto.request.UpdateUserStatusRequest;
import com.eventhub.modules.auth.service.AuthService;
import com.eventhub.modules.auth.vo.UserInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员用户管理接口。
 * 当前仅提供阶段 1 验证 RBAC 所需的最小能力，后续可继续补分页、搜索和审计日志。
 */
@Tag(name = "Admin User", description = "管理员用户管理接口")
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AuthService authService;

    /**
     * 查询用户列表。
     *
     * @return 用户摘要列表
     */
    @Operation(summary = "查询用户列表", description = "管理员查看平台用户列表")
    @GetMapping
    public ApiResponse<List<UserInfo>> listUsers() {
        return ApiResponse.success(authService.listUsers());
    }

    /**
     * 更新用户状态。
     *
     * @param userId 用户主键
     * @param request 状态更新请求
     * @return 更新后的用户摘要
     */
    @Operation(summary = "更新用户状态", description = "管理员启用或禁用用户")
    @PatchMapping("/{userId}/status")
    public ApiResponse<UserInfo> updateStatus(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserStatusRequest request) {
        return ApiResponse.success(authService.updateStatus(userId, request));
    }
}
