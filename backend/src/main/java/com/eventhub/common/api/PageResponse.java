package com.eventhub.common.api;

import java.util.List;
import java.util.Objects;

import lombok.Getter;

/**
 * 通用分页响应值对象。
 *
 * <p>
 * 该结构只表达分页协议本身，不关心具体业务数据类型。活动、场次、订单和用户列表都可以复用它，
 * 从而让前端在消费分页接口时获得稳定的响应字段。
 * </p>
 */
@Getter
public final class PageResponse<T> {

    private final List<T> items;
    private final int page;
    private final int size;
    private final long total;
    private final long totalPages;
    private final boolean hasNext;
    private final boolean hasPrevious;

    /**
     * 私有构造器用于收口分页响应的创建入口。
     *
     * <p>
     * {@code totalPages}、{@code hasNext} 和 {@code hasPrevious} 都是由分页请求与总数推导出来的元数据。
     * 如果公开全参构造器，调用方就可能绕过统一计算规则，构造出“总页数为 3 但没有下一页”等不一致响应。
     * 因此外部代码只能通过 {@link #of(List, PageRequest, long)} 创建分页响应。
     * </p>
     */
    private PageResponse(
            List<T> items,
            int page,
            int size,
            long total,
            long totalPages,
            boolean hasNext,
            boolean hasPrevious
    ) {
        Objects.requireNonNull(items, "items must not be null");
        if (page < 1) {
            throw new IllegalArgumentException("page must be greater than or equal to 1");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be greater than or equal to 1");
        }
        if (total < 0) {
            throw new IllegalArgumentException("total must be greater than or equal to 0");
        }
        if (totalPages < 0) {
            throw new IllegalArgumentException("totalPages must be greater than or equal to 0");
        }
        this.items = List.copyOf(items);
        this.page = page;
        this.size = size;
        this.total = total;
        this.totalPages = totalPages;
        this.hasNext = hasNext;
        this.hasPrevious = hasPrevious;
    }

    /**
     * 根据当前页数据、分页请求和总数构造标准分页响应。
     *
     * <p>
     * totalPages 使用向上取整；当 total 为 0 时返回 0，表示没有任何可展示页。
     * hasNext 不依赖当前页实际返回条数，而是根据页码和总页数判断，避免最后一页刚好满 size 时误判。
     * </p>
     *
     * @param items       当前页数据
     * @param pageRequest 分页请求
     * @param total       满足查询条件的总记录数
     * @param <T>         当前页数据类型
     * @return 标准分页响应
     */
    public static <T> PageResponse<T> of(List<T> items, PageRequest pageRequest, long total) {
        Objects.requireNonNull(pageRequest, "pageRequest must not be null");
        long totalPages = calculateTotalPages(total, pageRequest.size());
        return new PageResponse<>(
                items,
                pageRequest.page(),
                pageRequest.size(),
                total,
                totalPages,
                pageRequest.page() < totalPages,
                totalPages > 0 && pageRequest.page() > 1
        );
    }

    private static long calculateTotalPages(long total, int size) {
        if (total == 0) {
            return 0;
        }
        return (total + size - 1) / size;
    }
}
