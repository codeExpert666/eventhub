package com.eventhub.infra.security.handler;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.eventhub.common.api.ApiResponse;
import com.eventhub.common.api.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Spring Security 异常响应写出器。
 *
 * 认证失败和授权失败发生在 Controller 之前，无法依赖全局异常处理器捕获，
 * 因此这里手动写出与项目一致的 ApiResponse JSON 结构。
 *
 * 典型使用场景：
 * - 用户未登录访问需要认证的接口时，由 AuthenticationEntryPoint 调用；
 * - 用户已登录但权限不足时，由 AccessDeniedHandler 调用。
 */
@Component
@RequiredArgsConstructor
public class SecurityErrorResponseWriter {

    /**
     * Jackson 提供的 JSON 工具类。
     *
     * ObjectMapper 可以把 Java 对象和 JSON 字符串互相转换：
     * - 序列化：Java 对象 -> JSON，例如 ApiResponse 对象写成 {"code": "...", "message": "..."}；
     * - 反序列化：JSON -> Java 对象，例如请求体 JSON 解析成 DTO。
     *
     * 这里通过 Spring 容器注入，而不是自己 new ObjectMapper，原因是：
     * - Spring Boot 会自动配置一个 ObjectMapper Bean；
     * - 这个 Bean 会复用项目统一的 Jackson 配置，例如时间格式、字段命名策略、空值处理等；
     * - 复用同一个 Bean 可以保证 Security 层返回的 JSON 与 Controller 层返回的 JSON 格式一致。
     */
    private final ObjectMapper objectMapper;

    /**
     * 写出统一失败响应。
     *
     * @param response  HTTP 响应
     * @param errorCode 错误码
     * @param message   错误描述
     * @throws IOException 写响应体失败
     */
    public void write(HttpServletResponse response, ErrorCode errorCode, String message) throws IOException {
        // 设置 HTTP 状态码，例如 401 Unauthorized 或 403 Forbidden。
        // 注意这里使用 setStatus，而不是 sendError：
        // sendError 可能触发容器默认错误页或默认错误 JSON，导致响应结构不再符合项目统一的 ApiResponse。
        response.setStatus(errorCode.getHttpStatus().value());

        // 告诉客户端响应体是 JSON。客户端、浏览器或 API 调试工具会根据该值选择正确的解析方式。
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        // 明确使用 UTF-8，避免中文错误信息在某些客户端或容器默认编码下出现乱码。
        response.setCharacterEncoding("UTF-8");

        // 构造项目统一的失败响应体。
        // ApiResponse.failure(...) 返回的是普通 Java 对象，不是 JSON 字符串。
        //
        // objectMapper.writeValue(Writer, Object) 会做两件事：
        // 1. 将 ApiResponse 对象按照 Jackson 规则序列化成 JSON；
        // 2. 把序列化后的 JSON 直接写入 response.getWriter() 对应的 HTTP 响应体。
        //
        // 最终客户端收到的响应会类似：
        // {
        //   "code": "AUTH-401",
        //   "message": "认证失败",
        //   "data": null,
        //   "requestId": "当前请求追踪 ID",
        //   "timestamp": "响应生成时间"
        // }
        objectMapper.writeValue(response.getWriter(), ApiResponse.failure(errorCode, message, null));
    }
}
