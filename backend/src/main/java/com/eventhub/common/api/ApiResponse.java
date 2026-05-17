package com.eventhub.common.api;

import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一响应体。
 * 业务接口统一使用该结构返回，便于前端接入和后续统一错误码治理。
 * 该类型同时承载协议层成功/失败结果、业务数据以及请求追踪信息，
 * 让控制器和全局异常处理器都可以通过同一套结构对外响应。
 *
 * <p>该类型只负责表达响应数据结构，不主动读取 MDC、Servlet request 或 Spring Web 上下文。
 * HTTP 响应中的 requestId 由 Web 出口层统一补充，避免非 HTTP 场景隐式依赖线程上下文。</p>
 *
 * <p>响应的业务语义字段在工厂方法构造后保持不变，requestId 则允许由 HTTP 出口层后置填充。
 * 因此这里使用普通 class，而不是 record。</p>
 *
 * <p>{@link Getter} 会为字段生成标准 JavaBean getter，供测试断言与 Jackson 序列化使用；
 * {@link AllArgsConstructor} 只生成私有全参构造器，避免外部绕过静态工厂方法创建响应对象。</p>
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiResponse<T> {

    /**
     * 应用层响应码。成功时通常为 {@code COMMON-000}，失败时对应具体错误码。
     */
    private final String code;

    /**
     * 面向调用方的响应描述。成功时通常为默认成功文案，失败时为校验或业务错误提示。
     */
    private final String message;

    /**
     * 业务数据载荷。查询类接口通常返回具体数据，纯操作型接口可为 {@code null}。
     */
    private final T data;

    /**
     * 当前 HTTP 请求的唯一追踪标识；构造阶段可为空，由 HTTP 出口层统一填充。
     */
    private String requestId;

    /**
     * 当前响应生成时间，用于帮助调用方和日志系统对齐时序。
     */
    private final OffsetDateTime timestamp;

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
                null,
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
                null,
                OffsetDateTime.now()
        );
    }

    /**
     * 为当前响应对象补充 requestId。
     * <p>
     * requestId 来自 HTTP 请求 attribute，由 Web 出口层在响应写出前统一设置。
     * 返回 {@code this} 是为了方便 Advice 直接返回当前响应对象，不再创建额外副本。
     * </p>
     *
     * @param requestId 当前 HTTP 请求的追踪标识
     * @return 当前响应对象
     */
    public ApiResponse<T> withRequestId(String requestId) {
        this.requestId = requestId;
        return this;
    }
}
