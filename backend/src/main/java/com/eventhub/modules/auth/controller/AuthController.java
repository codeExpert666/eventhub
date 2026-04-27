package com.eventhub.modules.auth.controller;

import com.eventhub.common.api.ApiResponse;
import com.eventhub.modules.auth.dto.request.LoginRequest;
import com.eventhub.modules.auth.dto.request.RegisterRequest;
import com.eventhub.modules.auth.security.AuthenticatedUser;
import com.eventhub.modules.auth.service.AuthService;
import com.eventhub.modules.auth.vo.LoginResponse;
import com.eventhub.modules.auth.vo.UserInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口控制器。
 * 负责暴露注册、登录和无状态登出入口，具体账号校验、密码加密和 token 签发由服务层完成。
 */
@Tag(name = "Auth", description = "注册登录与认证接口")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 用户注册。
     *
     * @param request 注册请求
     * @return 注册后的用户摘要
     */
    @Operation(summary = "用户注册", description = "创建普通用户并绑定 USER 角色")
    @PostMapping("/register")
    public ApiResponse<UserInfo> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    /**
     * 用户登录。
     *
     * @param request 登录请求
     * @return access token 与当前用户摘要
     */
    @Operation(summary = "用户登录", description = "校验账号密码并签发 JWT access token")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    /**
     * 用户登出。
     * 当前 access token 不落库，服务端无法主动吊销已签发 token，因此该接口表达客户端删除本地 token 的协议语义。
     *
     * @param authenticatedUser 当前登录用户；参数存在即表示请求已经通过认证
     * @return 空成功响应
     */
    @Operation(summary = "用户登出", description = "无状态 JWT 登出入口，客户端应删除本地 token")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return ApiResponse.success();
    }
}
