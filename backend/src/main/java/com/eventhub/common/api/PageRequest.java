package com.eventhub.common.api;

/**
 * 通用分页请求值对象。
 *
 * <p>
 * 项目内列表查询统一使用从 1 开始的页码，Controller 负责把 HTTP 查询参数转换为该对象，
 * Mapper 层再通过 {@link #offset()} 转换为数据库需要的偏移量。这样可以避免每个业务模块重复维护
 * page、size、offset 的边界规则。
 * </p>
 *
 * @param page 从 1 开始的页码
 * @param size 每页条数
 */
public record PageRequest(int page, int size) {

    /**
     * 默认页码。对外 API 使用 1-based page，和多数管理后台分页控件保持一致。
     */
    public static final int DEFAULT_PAGE = 1;

    /**
     * 默认每页条数。20 条能兼顾管理端首屏可读性和后端查询成本。
     */
    public static final int DEFAULT_SIZE = 20;

    /**
     * 单页最大条数。该上限用于防止调用方用超大 size 绕过分页意图。
     */
    public static final int MAX_SIZE = 100;

    public PageRequest {
        if (page < 1) {
            throw new IllegalArgumentException("page must be greater than or equal to 1");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be greater than or equal to 1");
        }
        if (size > MAX_SIZE) {
            throw new IllegalArgumentException("size must be less than or equal to " + MAX_SIZE);
        }
    }

    /**
     * 构造分页请求。
     *
     * @param page 从 1 开始的页码
     * @param size 每页条数
     * @return 分页请求
     */
    public static PageRequest of(int page, int size) {
        return new PageRequest(page, size);
    }

    /**
     * 构造默认分页请求。
     *
     * @return 默认第一页、默认页大小的分页请求
     */
    public static PageRequest defaultPage() {
        return new PageRequest(DEFAULT_PAGE, DEFAULT_SIZE);
    }

    /**
     * 将 1-based page 转换为 SQL LIMIT/OFFSET 所需的 0-based offset。
     *
     * @return 当前页起始记录偏移量
     */
    public long offset() {
        return (long) (page - 1) * size;
    }
}
