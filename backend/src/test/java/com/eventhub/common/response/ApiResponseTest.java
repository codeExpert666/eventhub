package com.eventhub.common.response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.eventhub.common.error.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * ApiResponse 工厂方法测试。
 * <p>
 * 本测试类不启动 Spring 容器，而是直接调用 {@link ApiResponse} 的静态工厂方法，
 * 用最小成本验证统一响应体的基础契约。这样既能覆盖纯 Java 层面的响应组装逻辑，
 * 也能避免把测试结果和 Web 层、过滤器链、序列化等更重的集成环境绑定在一起。
 * </p>
 * <p>
 * 当前重点验证无参 {@link ApiResponse#success()} 工厂方法的语义：
 * 当业务操作成功但没有返回数据时，响应仍然必须使用成功错误码、成功文案、
 * 空业务数据、当前请求追踪标识以及生成时间，避免后续重构时破坏统一成功响应约定。
 * </p>
 */
class ApiResponseTest {

    /**
     * 每个测试用例结束后清理 MDC 中的 requestId。
     * <p>
     * MDC 本质上依赖线程上下文保存日志追踪信息，测试运行器可能复用同一个线程执行多个用例。
     * 如果不主动清理，前一个测试写入的 requestId 可能泄漏到后一个测试，
     * 导致测试结果受执行顺序影响，也会掩盖生产代码中 requestId 读取逻辑的问题。
     * </p>
     */
    @AfterEach
    void clearMdc() {
        MDC.remove(RequestIdFilter.MDC_KEY);
    }

    /**
     * 验证无参 {@link ApiResponse#success()} 会返回一个“不携带业务数据”的成功响应。
     * <p>
     * 该方法覆盖的是统一响应体中很常见的一类场景：例如删除成功、状态切换成功、
     * 心跳成功等操作只需要告诉调用方“处理成功”，不需要额外返回业务对象。
     * 因此这里既要确认 {@code data} 为 {@code null}，也要确认成功响应的其他公共字段没有缺失。
     * </p>
     */
    @Test
    void successWithoutDataShouldReturnVoidSuccessResponse() {
        // 模拟 RequestIdFilter 在真实 HTTP 请求进入系统时写入的 requestId。
        // ApiResponse 不直接依赖 HttpServletRequest，而是从 MDC 读取当前线程绑定的追踪标识。
        MDC.put(RequestIdFilter.MDC_KEY, "req-success-no-data");

        // 调用无参 success 工厂方法，表达“操作成功，但没有业务数据需要返回”。
        ApiResponse<Void> response = ApiResponse.success();

        // 成功响应必须使用 ErrorCode.SUCCESS 中定义的应用层响应码，而不是随意硬编码字符串。
        assertEquals(ErrorCode.SUCCESS.getCode(), response.code());
        // 成功文案也应来自 ErrorCode.SUCCESS，保证统一响应口径集中维护。
        assertEquals(ErrorCode.SUCCESS.getDefaultMessage(), response.message());
        // 无参 success 的核心语义：响应成功，但 data 为空，类型上通过 Void 明确“不返回业务载荷”。
        assertNull(response.data());
        // requestId 应从 MDC 透传到响应体，保证响应 JSON、响应头和日志可以被同一个追踪标识串起来。
        assertEquals("req-success-no-data", response.requestId());
        // timestamp 由工厂方法在构造响应时即时生成，这里只关心它存在，不绑定具体时间以避免脆弱测试。
        assertNotNull(response.timestamp());
    }
}
