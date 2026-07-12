package com.eventhub.modules.auth.enums;

/**
 * 用户账号状态。
 * 阶段 1 只保留最小状态集合：启用和禁用。
 * 登录、JWT 认证和管理员状态切换都会围绕该枚举判断用户是否仍可访问系统。
 */
public enum UserStatus {

    /**
     * 账号启用。
     * 只有该状态的用户可以登录并访问需要认证的接口。
     */
    ENABLED,

    /**
     * 账号禁用。
     * 禁用用户不能登录；即使旧 token 尚未过期，请求期数据库校验也会拒绝继续访问。
     */
    DISABLED
}
