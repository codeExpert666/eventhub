package com.eventhub.common.exception;

import com.eventhub.common.error.ErrorCode;

/**
 * 自定义业务异常。
 * 后续业务模块可以直接抛出该异常，并复用统一错误响应结构。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
