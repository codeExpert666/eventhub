package com.eventhub.common.api;

import com.eventhub.infra.logging.RequestIdFilter;
import java.time.OffsetDateTime;
import org.slf4j.MDC;

/**
 * 统一响应体。
 * 业务接口统一使用该结构返回，便于前端接入、日志追踪和后续统一错误码治理。
 * 该记录类型同时承载协议层成功/失败结果、业务数据以及请求追踪信息，
 * 让控制器和全局异常处理器都可以通过同一套结构对外响应。
 *
 * @param code 应用层响应码。成功时通常为 {@code COMMON-000}，失败时对应具体错误码。
 * @param message 面向调用方的响应描述。成功时通常为默认成功文案，失败时为校验或业务错误提示。
 * @param data 业务数据载荷。查询类接口通常返回具体数据，纯操作型接口可为 {@code null}。
 * @param requestId 当前请求的唯一追踪标识，通常由 {@link RequestIdFilter} 注入到 MDC 和响应头中。
 * @param timestamp 当前响应生成时间，用于帮助调用方和日志系统对齐时序。
 */
public record ApiResponse<T>(
        String code,
        String message,
        T data,
        String requestId,
        OffsetDateTime timestamp
) {

    /**
     * 构造一个不携带业务数据的成功响应。
     * 适用于创建成功、删除成功、状态切换成功等只需要表达“操作已成功完成”的场景。
     *
     * @return data 为 null 的成功响应
     */
    public static ApiResponse<Void> success() {
        return success(null);
    }

    /**
     * 构造一个携带业务数据的成功响应。
     *
     * @param data 成功响应携带的业务数据
     * @param <T> 业务数据类型
     * @return 统一成功响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(
                ErrorCode.SUCCESS.getCode(),
                ErrorCode.SUCCESS.getDefaultMessage(),
                data,
                currentRequestId(),
                OffsetDateTime.now()
        );
    }

    /**
     * 构造一个失败响应。
     * 该方法通常由全局异常处理器统一调用，用于把应用层错误码、错误消息和附加错误数据
     * 组装成统一返回结构，避免控制器和异常处理逻辑重复拼装响应字段。
     *
     * @param errorCode 失败场景对应的应用层错误码
     * @param message 返回给调用方的错误提示信息
     * @param data 附加错误数据，例如字段级校验明细；如果没有额外信息可传入 {@code null}
     * @param <T> 附加错误数据类型
     * @return 统一失败响应
     */
    public static <T> ApiResponse<T> failure(ErrorCode errorCode, String message, T data) {
        return new ApiResponse<>(
                errorCode.getCode(),
                message,
                data,
                currentRequestId(),
                OffsetDateTime.now()
        );
    }

    /**
     * 从 MDC 中读取当前请求的 requestId。
     * 请求进入系统时，{@link RequestIdFilter} 会优先复用合法的请求头，
     * 否则生成新的 requestId 并写入 MDC，因此这里可以在响应构造阶段复用同一追踪标识。
     *
     * @return 当前线程绑定的 requestId；如果当前调用不在 HTTP 请求上下文中，可能返回 {@code null}
     */
    private static String currentRequestId() {
        return MDC.get(RequestIdFilter.MDC_KEY);
    }
}
