package com.eventhub.modules.auth.service;

import com.eventhub.common.api.PageResponse;
import com.eventhub.infra.security.principal.AuthenticatedPrincipal;
import com.eventhub.modules.auth.dto.request.AdminUserQueryRequest;
import com.eventhub.modules.auth.dto.request.LoginRequest;
import com.eventhub.modules.auth.dto.request.RegisterRequest;
import com.eventhub.modules.auth.dto.request.UpdateUserStatusRequest;
import com.eventhub.modules.auth.vo.LoginResponse;
import com.eventhub.modules.auth.vo.UserInfo;

/**
 * 认证授权应用服务接口。
 *
 * <p>
 * Controller 依赖接口而不是具体实现，方便后续把 auth 能力拆成独立服务时，
 * 保持 HTTP 层契约稳定，并逐步把实现替换成远程调用、消息驱动或独立认证服务适配器。
 * </p>
 */
public interface AuthService {

    /**
     * 注册普通用户。
     *
     * @param request 注册请求
     * @return 注册后的用户摘要
     */
    UserInfo register(RegisterRequest request);

    /**
     * 用户登录。
     *
     * @param request 登录请求
     * @return 登录 token 与用户摘要
     */
    LoginResponse login(LoginRequest request);

    /**
     * 用户登出。
     *
     * @param principal 当前认证主体
     */
    void logout(AuthenticatedPrincipal principal);

    /**
     * 根据当前认证主体查询用户资料。
     *
     * @param principal 当前认证主体
     * @return 当前用户摘要
     */
    UserInfo currentUser(AuthenticatedPrincipal principal);

    /**
     * 分页查询用户。
     *
     * @param request 分页与筛选查询参数
     * @return 用户摘要分页结果
     */
    PageResponse<UserInfo> listUsers(AdminUserQueryRequest request);

    /**
     * 管理员更新用户状态。
     *
     * @param userId  用户主键
     * @param request 状态更新请求
     * @return 更新后的用户摘要
     */
    UserInfo updateStatus(Long userId, UpdateUserStatusRequest request);
}
