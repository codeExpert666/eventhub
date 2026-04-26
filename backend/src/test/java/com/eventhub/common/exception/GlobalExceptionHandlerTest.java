package com.eventhub.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.eventhub.common.error.ErrorCode;
import com.eventhub.common.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * GlobalExceptionHandler 行为测试。
 * <p>
 * 本测试类不启动 Spring 容器，而是直接调用异常处理器方法，
 * 用较低成本验证全局异常映射的核心契约。这样可以避免把本次 404 分支验证
 * 绑定到完整 Web 上下文、数据库迁移或 OpenAPI 初始化流程上。
 * </p>
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    /**
     * 验证缺失静态资源不会被当成系统内部错误处理。
     * <p>
     * 浏览器访问任意页面或接口文档时，经常会自动请求 {@code /favicon.ico}。
     * 如果项目没有提供这个静态资源，Spring MVC 会抛出 {@link NoResourceFoundException}。
     * 该场景本质是资源不存在，应稳定返回 404，而不是落入未知异常兜底分支返回 500。
     * </p>
     */
    @Test
    void noResourceFoundShouldReturnUnifiedNotFoundResponse() {
        NoResourceFoundException exception = new NoResourceFoundException(HttpMethod.GET, "favicon.ico");

        ResponseEntity<ApiResponse<Void>> responseEntity = handler.handleNoResourceFound(exception);
        ApiResponse<Void> body = responseEntity.getBody();

        assertEquals(HttpStatus.NOT_FOUND, responseEntity.getStatusCode());
        assertNotNull(body);
        assertEquals(ErrorCode.NOT_FOUND.getCode(), body.code());
        assertEquals("请求的资源不存在", body.message());
        assertNull(body.data());
        assertNotNull(body.timestamp());
    }
}
