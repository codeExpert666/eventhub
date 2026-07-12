package com.eventhub.infra.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eventhub.common.api.ApiResponse;
import com.eventhub.common.constant.RequestContextConstants;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * ApiResponseRequestIdAdvice 行为测试。
 * <p>
 * 该测试不启动 Spring 容器，直接验证 Advice 的最小职责：
 * 只为 ApiResponse 补充 requestId，不包装或修改其他类型的响应体。
 * </p>
 */
class ApiResponseRequestIdAdviceTest {

    private final ApiResponseRequestIdAdvice advice = new ApiResponseRequestIdAdvice();

    @Test
    void shouldInjectRequestIdIntoApiResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestContextConstants.REQUEST_ID_ATTRIBUTE, "req-advice");
        ApiResponse<String> body = ApiResponse.success("pong");

        Object result = advice.beforeBodyWrite(
                body,
                null,
                MediaType.APPLICATION_JSON,
                MappingJackson2HttpMessageConverter.class,
                new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(new MockHttpServletResponse())
        );

        assertTrue(result instanceof ApiResponse<?>);
        ApiResponse<?> response = (ApiResponse<?>) result;
        assertSame(body, response);
        assertEquals("pong", response.getData());
        assertEquals("req-advice", response.getRequestId());
    }

    @Test
    void shouldKeepNonApiResponseBodyUnchanged() {
        Map<String, String> body = Map.of("status", "UP");

        Object result = advice.beforeBodyWrite(
                body,
                null,
                MediaType.APPLICATION_JSON,
                MappingJackson2HttpMessageConverter.class,
                new ServletServerHttpRequest(new MockHttpServletRequest()),
                new ServletServerHttpResponse(new MockHttpServletResponse())
        );

        assertSame(body, result);
    }

    @Test
    void shouldKeepNullBodyUnchanged() {
        Object result = advice.beforeBodyWrite(
                null,
                null,
                MediaType.APPLICATION_JSON,
                MappingJackson2HttpMessageConverter.class,
                new ServletServerHttpRequest(new MockHttpServletRequest()),
                new ServletServerHttpResponse(new MockHttpServletResponse())
        );

        assertNull(result);
    }
}
