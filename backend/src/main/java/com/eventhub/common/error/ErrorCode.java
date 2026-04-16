package com.eventhub.common.error;

import org.springframework.http.HttpStatus;

/**
 * 统一错误码定义。
 * 当前只保留基础工程所需的最小集合，后续业务模块可以在此基础上继续扩展。
 */
public enum ErrorCode {

    SUCCESS(HttpStatus.OK, "COMMON-000", "success"),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "COMMON-400", "请求参数不合法"),
    BUSINESS_ERROR(HttpStatus.BAD_REQUEST, "COMMON-401", "业务处理失败"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON-500", "系统内部错误");

    private final HttpStatus httpStatus;
    private final String code;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String code, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
