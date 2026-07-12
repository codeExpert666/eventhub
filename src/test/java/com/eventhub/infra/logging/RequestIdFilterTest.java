package com.eventhub.infra.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.eventhub.common.constant.RequestContextConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * RequestIdFilter 行为测试。
 * <p>
 * 该测试直接执行过滤器，重点验证 requestId 在单个 HTTP 请求生命周期内的职责边界：
 * 生成或复用、写入 MDC、写入 request attribute、写入 response header，以及请求结束后的 MDC 清理。
 * </p>
 */
class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.remove(RequestContextConstants.REQUEST_ID_MDC_KEY);
    }

    @Test
    void shouldGenerateRequestIdWhenHeaderMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            Object requestId = servletRequest.getAttribute(RequestContextConstants.REQUEST_ID_ATTRIBUTE);

            assertNotNull(requestId);
            assertEquals(requestId, MDC.get(RequestContextConstants.REQUEST_ID_MDC_KEY));
            assertFalse(requestId.toString().isBlank());
        });

        String responseHeaderRequestId = response.getHeader(RequestContextConstants.REQUEST_ID_HEADER);
        assertNotNull(responseHeaderRequestId);
        assertEquals(request.getAttribute(RequestContextConstants.REQUEST_ID_ATTRIBUTE), responseHeaderRequestId);
        assertNull(MDC.get(RequestContextConstants.REQUEST_ID_MDC_KEY));
    }

    @Test
    void shouldReuseSafeIncomingRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String incomingRequestId = "client-req_001";
        request.addHeader(RequestContextConstants.REQUEST_ID_HEADER, incomingRequestId);

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            assertEquals(incomingRequestId, servletRequest.getAttribute(RequestContextConstants.REQUEST_ID_ATTRIBUTE));
            assertEquals(incomingRequestId, MDC.get(RequestContextConstants.REQUEST_ID_MDC_KEY));
        });

        assertEquals(incomingRequestId, response.getHeader(RequestContextConstants.REQUEST_ID_HEADER));
        assertNull(MDC.get(RequestContextConstants.REQUEST_ID_MDC_KEY));
    }

    @Test
    void shouldRegenerateUnsafeIncomingRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String unsafeRequestId = "unsafe request id ###";
        request.addHeader(RequestContextConstants.REQUEST_ID_HEADER, unsafeRequestId);

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            Object requestId = servletRequest.getAttribute(RequestContextConstants.REQUEST_ID_ATTRIBUTE);

            assertNotNull(requestId);
            assertNotEquals(unsafeRequestId, requestId);
            assertEquals(requestId, MDC.get(RequestContextConstants.REQUEST_ID_MDC_KEY));
        });

        assertNotEquals(unsafeRequestId, response.getHeader(RequestContextConstants.REQUEST_ID_HEADER));
        assertEquals(
                request.getAttribute(RequestContextConstants.REQUEST_ID_ATTRIBUTE),
                response.getHeader(RequestContextConstants.REQUEST_ID_HEADER)
        );
        assertNull(MDC.get(RequestContextConstants.REQUEST_ID_MDC_KEY));
    }
}
