package com.eventhub.common.response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.eventhub.common.error.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * ApiResponse 工厂方法测试。
 * 这里重点验证无参 success 工厂方法的语义，避免后续重构时破坏统一成功响应约定。
 */
class ApiResponseTest {

    @AfterEach
    void clearMdc() {
        MDC.remove(RequestIdFilter.MDC_KEY);
    }

    @Test
    void successWithoutDataShouldReturnVoidSuccessResponse() {
        MDC.put(RequestIdFilter.MDC_KEY, "req-success-no-data");

        ApiResponse<Void> response = ApiResponse.success();

        assertEquals(ErrorCode.SUCCESS.getCode(), response.code());
        assertEquals(ErrorCode.SUCCESS.getDefaultMessage(), response.message());
        assertNull(response.data());
        assertEquals("req-success-no-data", response.requestId());
        assertNotNull(response.timestamp());
    }
}
