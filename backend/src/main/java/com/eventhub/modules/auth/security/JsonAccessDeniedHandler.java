package com.eventhub.modules.auth.security;

import com.eventhub.common.api.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * 授权失败处理器。
 * 当用户已经登录但角色不满足访问要求时，统一返回 403。
 */
@Component
@RequiredArgsConstructor
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private final SecurityExceptionResponseWriter responseWriter;

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        responseWriter.write(response, ErrorCode.ACCESS_DENIED, "权限不足");
    }
}
