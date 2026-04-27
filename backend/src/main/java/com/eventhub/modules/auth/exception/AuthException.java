package com.eventhub.modules.auth.exception;

import com.eventhub.common.api.ErrorCode;
import com.eventhub.common.exception.BusinessException;

/**
 * 认证授权模块业务异常。
 * 用工厂方法集中收敛阶段 1 的账号类错误文案，避免服务层散落硬编码字符串。
 */
public class AuthException extends BusinessException {

    private AuthException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * 用户名重复。
     *
     * @return 账号冲突异常
     */
    public static AuthException duplicateUsername() {
        return new AuthException(ErrorCode.AUTH_CONFLICT, "用户名已存在");
    }

    /**
     * 邮箱重复。
     *
     * @return 账号冲突异常
     */
    public static AuthException duplicateEmail() {
        return new AuthException(ErrorCode.AUTH_CONFLICT, "邮箱已存在");
    }

    /**
     * 用户名或邮箱重复。
     * 用于数据库唯一约束兜底时无法稳定区分具体冲突字段的场景。
     *
     * @return 账号冲突异常
     */
    public static AuthException duplicateAccount() {
        return new AuthException(ErrorCode.AUTH_CONFLICT, "用户名或邮箱已存在");
    }

    /**
     * 账号或密码错误。
     * 登录接口故意不区分账号不存在和密码错误，避免向攻击者泄露账号是否存在。
     *
     * @return 认证失败异常
     */
    public static AuthException badCredentials() {
        return new AuthException(ErrorCode.AUTHENTICATION_FAILED, "账号或密码错误");
    }

    /**
     * 用户已被禁用。
     *
     * @return 访问拒绝异常
     */
    public static AuthException disabledUser() {
        return new AuthException(ErrorCode.ACCESS_DENIED, "用户已被禁用");
    }

    /**
     * 用户不存在。
     *
     * @return 资源不存在异常
     */
    public static AuthException userNotFound() {
        return new AuthException(ErrorCode.NOT_FOUND, "用户不存在");
    }
}
