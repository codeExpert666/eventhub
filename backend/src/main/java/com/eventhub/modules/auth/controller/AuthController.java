package com.eventhub.modules.auth.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.eventhub.common.api.ApiResponse;
import com.eventhub.common.security.AuthenticatedSubject;
import com.eventhub.modules.auth.dto.request.LoginRequest;
import com.eventhub.modules.auth.dto.request.RegisterRequest;
import com.eventhub.modules.auth.service.AuthService;
import com.eventhub.modules.auth.vo.LoginResponse;
import com.eventhub.modules.auth.vo.UserInfo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 认证接口控制器。
 * 负责暴露注册、登录和无状态登出入口，具体账号校验、密码加密和 token 签发由服务层完成。
 *
 * <p>请求体入参约定：
 * {@code @RequestBody} 负责把 HTTP JSON 请求体反序列化为请求 DTO；
 * {@code @Valid} 负责触发 DTO 字段上的 Jakarta Bean Validation 规则，例如 {@code @NotBlank}、{@code @Email}
 * 和 {@code @Size}。注册和登录属于强输入约束接口，因此不能只依赖 {@code @RequestBody}；否则 JSON 虽然可以被绑定为
 * Java 对象，但 DTO 上声明的字段校验不会在 Controller 边界自动执行，非法输入可能继续进入服务层。
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
     * <p>
     * {@code @AuthenticationPrincipal} 会从 Spring Security 当前请求的 {@code Authentication.principal}
     * 中取出登录主体，并自动注入到 Controller 方法参数里。当前项目中，这个 principal 是
     * {@link AuthenticatedSubject}，由 JwtAuthenticationFilter 在 JWT 校验成功后写入 SecurityContext。
     * </p>
     *
     * <p>
     * 该注解只负责“取当前用户”，不负责“要求必须登录”。真正要求登出接口必须登录的是
     * SecurityConfiguration 中对 {@code POST /api/v1/auth/logout} 配置的 {@code authenticated()} 规则。
     * 因此即使当前方法暂时没有使用 authenticatedSubject 参数，它仍然清楚表达了接口语义：
     * 这是当前已登录用户的登出入口，也为后续记录登出日志、审计操作或 token 吊销预留当前用户信息。
     * </p>
     *
     * @param authenticatedSubject 当前认证主体，来源于 SecurityContext 中 Authentication.principal
     * @return 空成功响应
     */
    @Operation(summary = "用户登出", description = "无状态 JWT 登出入口，客户端应删除本地 token")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal AuthenticatedSubject authenticatedSubject) {
        return ApiResponse.success();
    }
}
