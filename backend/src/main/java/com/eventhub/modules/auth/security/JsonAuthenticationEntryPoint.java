package com.eventhub.modules.auth.security;

import com.eventhub.common.api.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * 认证失败入口。
 * 处理 token 缺失、过期、签名非法、用户禁用等无法建立登录态的场景，并统一返回 401。
 */
@Component
@RequiredArgsConstructor
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final SecurityExceptionResponseWriter responseWriter;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        responseWriter.write(response, ErrorCode.AUTHENTICATION_FAILED, "请先登录或重新登录");
    }
}
