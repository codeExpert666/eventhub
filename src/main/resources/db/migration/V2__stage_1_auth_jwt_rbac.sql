-- V2 阶段 1 迁移：建立注册登录、JWT 与 RBAC 所需的账号和角色基础模型。
--
-- 本迁移只覆盖最小可用闭环：
-- 1. users 保存登录账号与 BCrypt 密码哈希；
-- 2. roles 保存平台内置角色；
-- 3. user_roles 建立用户与角色的多对多关系；
-- 4. 初始化 USER / ADMIN 角色与一个本地演示管理员账号。
--
-- 注意：管理员种子账号仅服务本地开发和简历演示。
-- 默认账号：admin
-- 默认密码：Admin123456
-- 生产环境应在首次部署后立即修改密码，或用单独的生产初始化流程替换该种子数据。

CREATE TABLE users (
    -- 用户自增主键。
    -- JWT 的 sub claim 会写入该 id，后续订单、操作日志等业务表也会引用它。
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    -- 登录用户名。
    -- 当前阶段用户名作为公开登录标识之一，需要唯一，避免登录时匹配到多个账号。
    username VARCHAR(32) NOT NULL,

    -- 登录邮箱。
    -- 邮箱同样可以作为登录标识，因此必须唯一。
    email VARCHAR(128) NOT NULL,

    -- BCrypt 密码哈希。
    -- 这里只保存哈希结果，不保存明文密码；长度 100 足以容纳 BCrypt 常见输出并保留升级空间。
    password_hash VARCHAR(100) NOT NULL,

    -- 用户状态。
    -- 阶段 1 只定义 ENABLED / DISABLED。禁用用户不能登录，也不能继续通过旧 token 访问受保护接口。
    status VARCHAR(16) NOT NULL,

    -- 创建时间。
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 更新时间。
    -- 当前为了兼容 H2 测试环境，更新时间由应用层在更新语句中显式维护。
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE roles (
    -- 角色自增主键。
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    -- 稳定角色编码。
    -- Spring Security 会把它转换为 ROLE_USER / ROLE_ADMIN 这类 GrantedAuthority。
    code VARCHAR(32) NOT NULL,

    -- 角色展示名称。
    name VARCHAR(64) NOT NULL,

    -- 角色说明，帮助后续阅读数据库时快速理解角色边界。
    description VARCHAR(255),

    -- 创建时间。
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_roles_code UNIQUE (code)
);

CREATE TABLE user_roles (
    -- 用户角色关系自增主键。
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    -- 用户 id，引用 users.id。
    user_id BIGINT NOT NULL,

    -- 角色 id，引用 roles.id。
    role_id BIGINT NOT NULL,

    -- 关系创建时间。
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_user_roles_user_role UNIQUE (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
);

-- 支持后续按角色反查用户，例如管理员查看某类角色用户时使用。
CREATE INDEX idx_user_roles_role_id
    ON user_roles (role_id);

INSERT INTO roles (code, name, description)
VALUES
    ('USER', '普通用户', '可以创建订单、查看自己的订单并完成模拟支付'),
    ('ADMIN', '管理员', '可以管理活动、场次、票种并查看平台操作日志');

INSERT INTO users (username, email, password_hash, status)
VALUES (
    'admin',
    'admin@eventhub.local',
    '$2y$10$5PN1JiDDf45mbKMEivrs7ucz63JaKGqD8zjS0zoOoqdhoih4byXFy',
    'ENABLED'
);

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.code IN ('USER', 'ADMIN')
WHERE u.username = 'admin';
