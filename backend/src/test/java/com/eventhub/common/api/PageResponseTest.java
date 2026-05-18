package com.eventhub.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * PageResponse 行为测试。
 * <p>
 * 该测试聚焦分页响应元数据计算，尤其是 totalPages、hasNext 和 hasPrevious。
 * 这些字段会直接影响前端分页控件是否允许翻页。
 * </p>
 */
class PageResponseTest {

    /**
     * 验证总页数使用向上取整，并正确标识上一页和下一页。
     */
    @Test
    void shouldCalculatePaginationMetadata() {
        PageResponse<String> response = PageResponse.of(
                List.of("u3", "u4"),
                PageRequest.of(2, 2),
                5
        );

        assertEquals(List.of("u3", "u4"), response.getItems());
        assertEquals(2, response.getPage());
        assertEquals(2, response.getSize());
        assertEquals(5, response.getTotal());
        assertEquals(3, response.getTotalPages());
        assertTrue(response.isHasNext());
        assertTrue(response.isHasPrevious());
    }

    /**
     * 验证空结果集不会虚构第一页，totalPages 应明确为 0。
     */
    @Test
    void emptyTotalShouldReturnZeroTotalPages() {
        PageResponse<String> response = PageResponse.of(List.of(), PageRequest.of(1, 20), 0);

        assertEquals(0, response.getTotal());
        assertEquals(0, response.getTotalPages());
        assertFalse(response.isHasNext());
        assertFalse(response.isHasPrevious());
    }

    /**
     * 验证空数据集不会因为请求页码大于 1 而误报存在上一页。
     */
    @Test
    void emptyTotalShouldNotHavePreviousPage() {
        PageResponse<String> response = PageResponse.of(List.of(), PageRequest.of(2, 20), 0);

        assertEquals(0, response.getTotalPages());
        assertFalse(response.isHasNext());
        assertFalse(response.isHasPrevious());
    }

    /**
     * 验证响应会复制 items，防止调用方在构造响应后继续修改列表内容。
     */
    @Test
    void itemsShouldBeImmutableCopy() {
        PageResponse<String> response = PageResponse.of(List.of("admin"), PageRequest.of(1, 20), 1);

        assertThrows(UnsupportedOperationException.class, () -> response.getItems().add("user"));
    }
}
