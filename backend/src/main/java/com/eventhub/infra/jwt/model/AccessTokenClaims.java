package com.eventhub.infra.jwt.model;

/**
 * access token 中保存的最小身份声明。
 * 权限不作为最终授权依据写入 token，而是在每次请求时通过 AuthenticatedSubjectLoader 查询最新数据。
 * token 只保留稳定的主体标识，避免把用户名这类可能变化的业务字段固化为 token 快照。
 *
 * @param subjectId token subject 中的主体标识
 */
public record AccessTokenClaims(
        Long subjectId
) {
}
