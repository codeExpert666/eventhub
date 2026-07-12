package com.eventhub.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * PageRequest 行为测试。
 * <p>
 * 该对象会被多个列表接口复用，因此这里用纯单元测试锁定 page、size 和 offset 的基础契约，
 * 避免后续某个业务模块为了局部需求破坏通用分页规则。
 * </p>
 */
class PageRequestTest {

    /**
     * 验证默认分页参数和 SQL 偏移量计算。
     */
    @Test
    void defaultPageShouldUseFirstPageAndDefaultSize() {
        PageRequest pageRequest = PageRequest.defaultPage();

        assertEquals(1, pageRequest.page());
        assertEquals(20, pageRequest.size());
        assertEquals(0, pageRequest.offset());
    }

    /**
     * 验证 1-based page 会被转换成数据库分页使用的 0-based offset。
     */
    @Test
    void offsetShouldBeCalculatedFromOneBasedPage() {
        PageRequest pageRequest = PageRequest.of(3, 20);

        assertEquals(40, pageRequest.offset());
    }

    /**
     * 验证非法 page 会被值对象拒绝，避免无效分页参数继续流入 Mapper。
     */
    @Test
    void shouldRejectPageLessThanOne() {
        assertThrows(IllegalArgumentException.class, () -> PageRequest.of(0, 20));
    }

    /**
     * 验证非法 size 会被值对象拒绝，并且单页条数不能超过系统上限。
     */
    @Test
    void shouldRejectInvalidSize() {
        assertThrows(IllegalArgumentException.class, () -> PageRequest.of(1, 0));
        assertThrows(IllegalArgumentException.class, () -> PageRequest.of(1, PageRequest.MAX_SIZE + 1));
    }
}
