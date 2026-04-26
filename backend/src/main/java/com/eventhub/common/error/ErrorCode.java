package com.eventhub.common.error;

import org.springframework.http.HttpStatus;

/**
 * 统一错误码定义。
 * 当前只保留基础工程所需的最小集合，后续业务模块可以在此基础上继续扩展。
 */
public enum ErrorCode {

    /**
     * 请求处理成功。
     * 对应 HTTP 200，通常用于统一响应体中的成功分支。
     */
    SUCCESS(HttpStatus.OK, "COMMON-000", "成功"),

    /**
     * 请求参数、请求体格式或基础校验规则不满足约束。
     * 对应 HTTP 400，表示客户端提交的数据不符合接口契约。
     */
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "COMMON-400", "请求参数不合法"),

    /**
     * 请求格式合法，但在业务规则校验或业务执行阶段失败。
     * 当前同样映射到 HTTP 400，用于和参数校验错误做应用层区分。
     */
    BUSINESS_ERROR(HttpStatus.BAD_REQUEST, "COMMON-401", "业务处理失败"),

    /**
     * 请求的资源不存在。
     * 对应 HTTP 404，常见于访问未定义接口或缺失静态资源，例如浏览器自动请求的 favicon.ico。
     */
    NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON-404", "资源不存在"),

    /**
     * 服务端发生未预期异常，调用方通常无法自行恢复。
     * 对应 HTTP 500，用于兜底标识系统内部错误。
     */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON-500", "系统内部错误");

    /**
     * HTTP 协议层状态码。
     * 用于设置响应头中的 HTTP status，便于网关、浏览器和客户端 SDK 按协议语义处理结果。
     */
    private final HttpStatus httpStatus;

    /**
     * 应用层错误码。
     * 用于写入统一响应体中的 code 字段，便于前端、日志和监控系统做更细粒度的错误分类。
     */
    private final String code;

    /**
     * 默认错误描述。
     * 当业务层没有提供更具体的错误消息时，使用该文案作为统一返回给调用方的默认提示。
     */
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
