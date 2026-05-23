-- V3 认证会话迁移：为 refresh token、服务端 logout 吊销和设备会话管理建立权威记录。
--
-- 当前阶段仍保持 access token 无状态认证流程不变，本表先只作为后续双 token 模型的持久化基础：
-- 1. 登录成功后可为每个设备创建一条服务端会话；
-- 2. refresh token 只保存哈希，不保存明文；
-- 3. logout、单设备踢下线、全端失效都可以通过更新会话状态完成；
-- 4. 后续 Redis 可以围绕该表做短期缓存或 denylist，但不替代 MySQL 权威记录。
CREATE TABLE auth_sessions (
    -- 自增主键，用于数据库内部关联和人工排查。
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    -- 对外稳定会话标识。
    -- 后续可写入 refresh token claim 或客户端上下文，用于定位某个设备会话。
    session_id VARCHAR(64) NOT NULL,

    -- 会话所属用户。
    -- 与 users.id 建立外键，保证不会出现孤立认证会话。
    user_id BIGINT NOT NULL,

    -- refresh token 哈希。
    -- 只保存哈希值，不保存 refresh token 明文；后续 refresh 时用客户端提交的 token 重新哈希后匹配。
    refresh_token_hash VARCHAR(128) NOT NULL,

    -- 会话状态。
    -- 当前只定义 ACTIVE / REVOKED；过期由 refresh_expires_at 派生，不额外写 EXPIRED 状态。
    status VARCHAR(16) NOT NULL,

    -- 会话签发时间。
    issued_at TIMESTAMP NOT NULL,

    -- refresh token 过期时间。
    -- 查询可过期会话、后台清理和 refresh 校验都会依赖该字段。
    refresh_expires_at TIMESTAMP NOT NULL,

    -- 最近一次 refresh token 轮换成功时间。
    last_refreshed_at TIMESTAMP NULL,

    -- 最近一次观察到该会话活动的时间。
    -- 后续可在 refresh、受保护请求或设备列表展示中维护。
    last_seen_at TIMESTAMP NULL,

    -- 吊销时间。
    -- 只有 REVOKED 状态通常会有值，用于审计 logout、管理员踢下线或安全失效。
    revoked_at TIMESTAMP NULL,

    -- 吊销原因。
    -- 例如 LOGOUT、ADMIN_REVOKE、USER_DISABLED、REFRESH_REUSE_DETECTED。
    revoke_reason VARCHAR(64) NULL,

    -- 客户端 IP 哈希。
    -- 只保存哈希，避免把完整 IP 作为长期明文信息沉淀到会话表。
    client_ip_hash VARCHAR(128) NULL,

    -- User-Agent 哈希。
    -- 用于后续识别设备变动和风控排查，不保存完整 User-Agent 原文。
    user_agent_hash VARCHAR(128) NULL,

    -- User-Agent 摘要。
    -- 保留短展示信息，服务于“设备会话列表”这类用户可见页面。
    user_agent_summary VARCHAR(255) NULL,

    -- 乐观锁版本。
    -- 后续 refresh token 轮换时，可用 version 防止同一个旧 refresh token 并发刷新出多条有效链路。
    version INT NOT NULL DEFAULT 0,

    -- 创建时间。
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 更新时间。
    -- 为兼容 H2 测试环境，后续由应用更新语句显式维护。
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_auth_sessions_session_id UNIQUE (session_id),
    CONSTRAINT uk_auth_sessions_refresh_token_hash UNIQUE (refresh_token_hash),
    CONSTRAINT fk_auth_sessions_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- 支持按用户查看设备会话、全端失效和用户维度安全排查。
CREATE INDEX idx_auth_sessions_user_id
    ON auth_sessions (user_id);

-- 支持快速筛选 ACTIVE / REVOKED 会话。
CREATE INDEX idx_auth_sessions_status
    ON auth_sessions (status);

-- 支持 refresh 校验、过期清理和即将过期会话扫描。
CREATE INDEX idx_auth_sessions_refresh_expires_at
    ON auth_sessions (refresh_expires_at);
