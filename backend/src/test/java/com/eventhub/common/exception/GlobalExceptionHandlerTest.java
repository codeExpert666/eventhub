package com.eventhub.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.eventhub.common.api.ErrorCode;
import com.eventhub.common.api.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * GlobalExceptionHandler 行为测试。
 * <p>
 * 本测试类不启动 Spring 容器，而是直接调用异常处理器方法，
 * 用较低成本验证全局异常映射的核心契约。这样可以避免验证时
 * 绑定到完整 Web 上下文、数据库迁移或 OpenAPI 初始化流程上。
 * </p>
 * <p>
 * 考虑到本测试类不启动 Spring 容器，不走 Filter 链，
 * RequestIdFilter 便不会执行，MDC 里通常没有 requestId。
 * 因此测试过程中不会对 requestId 进行验证，
 * requestId 的正确性应由集成测试覆盖
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

    /**
     * 验证未被具体分支识别的异常会落入统一兜底响应。
     * <p>
     * 阶段 0 的验收点要求“未知异常返回统一格式”。这里直接调用处理器方法，
     * 可以用最小成本锁定兜底契约：HTTP 状态是 500，响应体错误码是 INTERNAL_ERROR，
     * 且不会把异常堆栈或内部实现细节直接暴露给调用方。
     * </p>
     */
    @Test
    void unexpectedExceptionShouldReturnUnifiedInternalErrorResponse() {
        RuntimeException exception = new RuntimeException("simulated unexpected failure");

        ResponseEntity<ApiResponse<Void>> responseEntity = handler.handleUnexpectedException(exception);
        ApiResponse<Void> body = responseEntity.getBody();

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, responseEntity.getStatusCode());
        assertNotNull(body);
        assertEquals(ErrorCode.INTERNAL_ERROR.getCode(), body.code());
        assertEquals(ErrorCode.INTERNAL_ERROR.getDefaultMessage(), body.message());
        assertNull(body.data());
        assertNotNull(body.timestamp());
    }
}
