package com.eventhub.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

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
 * 空业务数据、空 requestId 以及生成时间，避免后续重构时破坏统一成功响应约定。
 * requestId 的 HTTP 注入由 Web 出口层覆盖，不属于该纯模型测试的职责。
 * </p>
 */
class ApiResponseTest {

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
        // 调用无参 success 工厂方法，表达“操作成功，但没有业务数据需要返回”。
        ApiResponse<Void> response = ApiResponse.success();

        // 成功响应必须使用 ErrorCode.SUCCESS 中定义的应用层响应码，而不是随意硬编码字符串。
        assertEquals(ErrorCode.SUCCESS.getCode(), response.getCode());
        // 成功文案也应来自 ErrorCode.SUCCESS，保证统一响应口径集中维护。
        assertEquals(ErrorCode.SUCCESS.getDefaultMessage(), response.getMessage());
        // 无参 success 的核心语义：响应成功，但 data 为空，类型上通过 Void 明确“不返回业务载荷”。
        assertNull(response.getData());
        // ApiResponse 是纯响应模型，构造阶段不隐式读取 MDC 或其他线程上下文。
        assertNull(response.getRequestId());
        // timestamp 由工厂方法在构造响应时即时生成，这里只关心它存在，不绑定具体时间以避免脆弱测试。
        assertNotNull(response.getTimestamp());
    }

    /**
     * 验证 requestId 补充方法会在当前响应对象上设置 requestId，并保留原响应语义。
     * <p>
     * HTTP 出口层补充 requestId 时，只应替换 requestId 字段，不改变 code、message、data 和 timestamp。
     * </p>
     */
    @Test
    void withRequestIdShouldSetRequestIdOnCurrentResponse() {
        ApiResponse<String> response = ApiResponse.success("pong");

        ApiResponse<String> responseWithRequestId = response.withRequestId("req-api-response");

        assertSame(response, responseWithRequestId);
        assertEquals(ErrorCode.SUCCESS.getCode(), responseWithRequestId.getCode());
        assertEquals(ErrorCode.SUCCESS.getDefaultMessage(), responseWithRequestId.getMessage());
        assertEquals("pong", responseWithRequestId.getData());
        assertEquals("req-api-response", responseWithRequestId.getRequestId());
        assertEquals(response.getTimestamp(), responseWithRequestId.getTimestamp());
    }
}
