-- V1 基线迁移：初始化后端基础工程可运行所需的最小数据库对象。
--
-- 这份脚本的目标不是一次性建立完整业务模型，而是先提供一条可验证的 Flyway
-- 基线迁移，确保项目在最早阶段已经具备：
-- 1. 数据库建表能力；
-- 2. 基础初始化留痕能力；
-- 3. 后续继续演进活动、订单、支付等业务表时可依赖的迁移起点。
--
-- 当前仅保留一张极小的系统表，用来记录“基础工程已经初始化完成”这一事实。
-- 该表属于系统元数据，不承载正式业务域数据。
CREATE TABLE system_bootstrap_record (
    -- 自增主键。
    -- 使用代理键而不是直接把 environment 设计成主键，可以为后续保留历史记录、
    -- 补充更多初始化场景或扩展审计字段留下空间。
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    -- 环境标识，例如 local / dev / test / prod / shared。
    -- 当前阶段先用短字符串表达，便于脚本初始化与人工排查；
    -- 如果后续环境模型收敛，再考虑升级为更严格的枚举约束或字典表。
    environment VARCHAR(32) NOT NULL,

    -- 初始化说明。
    -- 用于描述这条记录为什么被写入，帮助后续快速判断该记录是否来自基础工程初始化、
    -- 演示数据准备，还是某次运维补录。
    note VARCHAR(128) NOT NULL,

    -- 创建时间。
    -- 默认使用数据库当前时间，确保无需应用层参与即可记录迁移落库时间，
    -- 方便排查某个环境何时完成了基线初始化。
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 为 environment 建立普通索引，支持按环境快速筛选初始化记录。
--
-- 这里刻意不加唯一约束：
-- 1. 当前表更偏向“系统留痕”，而不是“每个环境只能存在一条配置”的强约束模型；
-- 2. 后续如果需要记录多次重建、补录或分阶段初始化，允许保留多条同环境记录；
-- 3. 若未来业务确认必须“一环境仅一条”，应通过新的迁移脚本显式增加唯一约束，
--    而不是直接修改既有迁移的业务语义。
CREATE INDEX idx_system_bootstrap_record_environment
    ON system_bootstrap_record (environment);

-- 写入一条基础初始化记录，作为 V1 迁移成功执行的最小可见证据。
--
-- 这里使用 shared 作为默认环境标识，表示当前数据库已经完成一次共享环境的基础工程初始化。
-- 后续本地联调、问题排查或演示时，可以直接查询该表确认：
-- 1. Flyway 是否至少执行到了 V1；
-- 2. 数据库是否具备最基本的读写能力；
-- 3. 当前库是否曾被这套后端基础工程初始化过。
INSERT INTO system_bootstrap_record (environment, note)
VALUES ('shared', 'backend foundation initialized');
