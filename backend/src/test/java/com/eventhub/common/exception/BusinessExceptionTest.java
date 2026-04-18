package com.eventhub.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.eventhub.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

/**
 * BusinessException 行为测试。
 * 这里覆盖默认文案构造、自定义文案构造以及错误码空值保护，避免后续重构破坏异常契约。
 */
class BusinessExceptionTest {

    @Test
    void shouldUseDefaultMessageWhenOnlyErrorCodeIsProvided() {
        BusinessException exception = new BusinessException(ErrorCode.BUSINESS_ERROR);

        assertSame(ErrorCode.BUSINESS_ERROR, exception.getErrorCode());
        assertEquals(ErrorCode.BUSINESS_ERROR.getDefaultMessage(), exception.getMessage());
    }

    @Test
    void shouldPreferCustomMessageWhenMessageIsProvided() {
        BusinessException exception = new BusinessException(ErrorCode.BUSINESS_ERROR, "订单状态不允许支付");

        assertSame(ErrorCode.BUSINESS_ERROR, exception.getErrorCode());
        assertEquals("订单状态不允许支付", exception.getMessage());
    }

    @Test
    void shouldRejectNullErrorCodeForDefaultMessageConstructor() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> new BusinessException(null));

        assertEquals("errorCode must not be null", exception.getMessage());
    }

    @Test
    void shouldRejectNullErrorCodeForCustomMessageConstructor() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new BusinessException(null, "自定义错误信息")
        );

        assertEquals("errorCode must not be null", exception.getMessage());
    }
}
