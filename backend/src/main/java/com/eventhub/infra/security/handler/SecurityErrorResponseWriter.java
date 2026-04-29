package com.eventhub.infra.security.handler;

import com.eventhub.common.api.ApiResponse;
import com.eventhub.common.api.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * Spring Security 异常响应写出器。
 * 认证失败和授权失败发生在 Controller 之前，无法依赖全局异常处理器捕获，
 * 因此这里手动写出与项目一致的 ApiResponse JSON 结构。
 */
@Component
@RequiredArgsConstructor
public class SecurityErrorResponseWriter {

    private final ObjectMapper objectMapper;

    /**
     * 写出统一失败响应。
     *
     * @param response HTTP 响应
     * @param errorCode 错误码
     * @param message 错误描述
     * @throws IOException 写响应体失败
     */
    public void write(HttpServletResponse response, ErrorCode errorCode, String message) throws IOException {
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.failure(errorCode, message, null));
    }
}
