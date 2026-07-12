package com.eventhub.infra.web;

import com.eventhub.common.api.ApiResponse;
import com.eventhub.common.constant.RequestContextConstants;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 在 HTTP 响应写出前，为统一响应体补充 requestId。
 * <p>
 * requestId 的生成、复用和生命周期由 {@link com.eventhub.infra.logging.RequestIdFilter} 负责；
 * 当前 Advice 只在 Web 出口层读取 request attribute，并且只处理已经是 {@link ApiResponse} 的响应体。
 * 非统一响应体，例如 Actuator、OpenAPI、字符串响应或文件下载，会原样返回。
 * </p>
 */
@RestControllerAdvice
public class ApiResponseRequestIdAdvice implements ResponseBodyAdvice<Object> {

    /**
     * 对所有响应先放行到 beforeBodyWrite，再由 body 类型判断是否需要处理。
     * 这样可以兼容 Controller 直接返回 ApiResponse 以及异常处理器返回 ResponseEntity<ApiResponse> 的场景。
     *
     * @param returnType Controller 或异常处理器声明的返回类型
     * @param converterType 当前响应使用的消息转换器类型
     * @return true 表示进入响应写出前处理
     */
    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    /**
     * 在响应体写出前为 ApiResponse 补充 requestId。
     *
     * @param body 当前待写出的响应体，可能为 null 或任意类型
     * @param returnType Controller 或异常处理器声明的返回类型
     * @param selectedContentType 响应内容类型
     * @param selectedConverterType 当前响应使用的消息转换器类型
     * @param request 当前 HTTP 请求抽象
     * @param response 当前 HTTP 响应抽象
     * @return 补充 requestId 后的 ApiResponse，或原始非 ApiResponse 响应体
     */
    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {
        if (!(body instanceof ApiResponse<?> apiResponse)) {
            return body;
        }

        String requestId = resolveRequestId(request);
        if (requestId == null || requestId.isBlank()) {
            return apiResponse;
        }

        return apiResponse.withRequestId(requestId);
    }

    /**
     * 从 Servlet request attribute 读取 requestId。
     * <p>
     * 这里刻意不读取 MDC，也不使用 RequestContextHolder，确保响应体 requestId 的来源是
     * RequestIdFilter 显式写入的 HTTP request attribute。
     * </p>
     * <p>
     * 这几个请求对象的关系如下：
     * {@link ServerHttpRequest} 是 Spring 在消息转换和响应写出阶段提供的 HTTP 请求抽象，
     * ResponseBodyAdvice 只依赖这个抽象，因此方法参数不直接暴露 Servlet API；
     * {@link ServletServerHttpRequest} 是 Spring MVC 在 Servlet 技术栈下对该抽象的具体适配器，
     * 内部包装了容器传入的 {@link HttpServletRequest}；
     * {@link HttpServletRequest} 是 Jakarta Servlet 原生请求对象，也是 RequestIdFilter 写入
     * request attribute 的位置。因此只有在当前请求确实来自 Servlet MVC 时，才能向下转换为
     * ServletServerHttpRequest，并从其中取出原生 HttpServletRequest 读取 attribute。
     * </p>
     *
     * @param request 当前 HTTP 请求抽象
     * @return request attribute 中的 requestId；不存在时返回 null
     */
    private String resolveRequestId(ServerHttpRequest request) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return null;
        }

        HttpServletRequest httpServletRequest = servletRequest.getServletRequest();
        Object requestId = httpServletRequest.getAttribute(RequestContextConstants.REQUEST_ID_ATTRIBUTE);
        if (requestId instanceof String value) {
            return value;
        }
        return null;
    }
}
