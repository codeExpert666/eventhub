package com.eventhub.common.response;

import com.eventhub.common.error.ErrorCode;
import java.time.OffsetDateTime;
import org.slf4j.MDC;

/**
 * 统一响应体。
 * 业务接口统一使用该结构返回，便于前端接入、日志追踪和后续统一错误码治理。
 */
public record ApiResponse<T>(
        String code,
        String message,
        T data,
        String requestId,
        OffsetDateTime timestamp
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(
                ErrorCode.SUCCESS.getCode(),
                ErrorCode.SUCCESS.getDefaultMessage(),
                data,
                currentRequestId(),
                OffsetDateTime.now()
        );
    }

    public static <T> ApiResponse<T> failure(ErrorCode errorCode, String message, T data) {
        return new ApiResponse<>(
                errorCode.getCode(),
                message,
                data,
                currentRequestId(),
                OffsetDateTime.now()
        );
    }

    private static String currentRequestId() {
        return MDC.get(RequestIdFilter.MDC_KEY);
    }
}
