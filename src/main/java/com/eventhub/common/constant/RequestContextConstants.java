package com.eventhub.common.constant;

/**
 * 请求上下文相关常量。
 * <p>
 * 这里集中维护 requestId 在 HTTP header、MDC 和 request attribute 中使用的 key，
 * 避免基础设施组件之间依赖散落的字符串字面量。
 * </p>
 */
public final class RequestContextConstants {

    /**
     * 外部调用方传入或服务端回写的请求追踪头名称。
     */
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    /**
     * MDC 中保存 requestId 时使用的 key，仅用于日志上下文。
     */
    public static final String REQUEST_ID_MDC_KEY = "requestId";

    /**
     * HttpServletRequest attribute 中保存 requestId 时使用的 key，供 HTTP 出口层读取。
     */
    public static final String REQUEST_ID_ATTRIBUTE = "REQUEST_ID";

    private RequestContextConstants() {
    }
}
