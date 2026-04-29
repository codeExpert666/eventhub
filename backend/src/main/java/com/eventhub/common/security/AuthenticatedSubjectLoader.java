package com.eventhub.common.security;

/**
 * 当前认证主体加载接口。
 * infra.security 只依赖这个窄接口完成认证上下文装配，不直接依赖 AuthService、UserEntity 或 UserInfo。
 */
public interface AuthenticatedSubjectLoader {

    /**
     * 根据 token subject 中的主体标识加载最新认证主体。
     * 实现方需要负责确认主体仍然存在、状态可用，并返回最新权限集合。
     *
     * @param subjectId 认证主体标识
     * @return 当前认证主体
     */
    AuthenticatedSubject loadBySubjectId(Long subjectId);
}
