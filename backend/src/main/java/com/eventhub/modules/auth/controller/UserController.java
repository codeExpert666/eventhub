package com.eventhub.modules.auth.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.eventhub.common.api.ApiResponse;
import com.eventhub.common.security.AuthenticatedSubject;
import com.eventhub.modules.auth.service.AuthService;
import com.eventhub.modules.auth.vo.UserInfo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 当前用户接口控制器。
 * `/api/v1/me` 是前端确认登录态和展示当前用户信息的基础入口。
 */
@Tag(name = "User", description = "当前用户接口")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;

    /**
     * 获取当前登录用户。
     *
     * @param authenticatedSubject Spring Security 上下文中的当前认证主体
     * @return 当前用户摘要
     */
    @Operation(summary = "获取当前用户", description = "根据 Bearer token 返回当前登录用户信息")
    @GetMapping("/me")
    public ApiResponse<UserInfo> me(@AuthenticationPrincipal AuthenticatedSubject authenticatedSubject) {
        return ApiResponse.success(authService.currentUser(authenticatedSubject));
    }
}
