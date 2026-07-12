package com.eventhub.common.exception;

import java.util.Objects;

import com.eventhub.common.api.ErrorCode;

import lombok.Getter;

/**
 * 自定义业务异常。
 * 后续业务模块可以直接抛出该异常，并复用统一错误响应结构。
 */
public class BusinessException extends RuntimeException {

    /**
     * 当前业务异常对应的应用层错误码。
     * 全局异常处理器会基于该字段决定统一响应体中的 code 以及 HTTP 状态。
     *
     * <p>
     * {@link Getter} 会生成 {@code getErrorCode()}，保持原有异常处理器和测试使用的访问契约不变。
     */
    @Getter
    private final ErrorCode errorCode;

    /**
     * 使用错误码中的默认错误描述构造业务异常。
     * 适用于业务层无需覆盖文案、直接复用 ErrorCode 默认提示的场景。
     *
     * @param errorCode 业务错误码，不能为空
     */
    public BusinessException(ErrorCode errorCode) {
        super(requireErrorCode(errorCode).getDefaultMessage());
        this.errorCode = errorCode;
    }

    /**
     * 使用指定错误码和自定义错误描述构造业务异常。
     * 适用于错误类别固定，但需要返回更具体业务提示的场景。
     *
     * @param errorCode 业务错误码，不能为空
     * @param message   自定义错误描述
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = requireErrorCode(errorCode);
    }

    private static ErrorCode requireErrorCode(ErrorCode errorCode) {
        return Objects.requireNonNull(errorCode, "errorCode must not be null");
    }
}
