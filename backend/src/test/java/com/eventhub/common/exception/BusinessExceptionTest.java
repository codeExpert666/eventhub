package com.eventhub.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.eventhub.common.api.ErrorCode;
import org.junit.jupiter.api.Test;

/**
 * BusinessException 行为测试。
 * <p>
 * {@link BusinessException} 是业务层向外抛出“可预期业务失败”的统一异常类型。
 * 它不会直接决定 HTTP 响应内容，而是把业务错误码和异常文案交给全局异常处理器，
 * 再由全局异常处理器组装成统一响应体。因此，这里的单元测试重点验证异常自身的最小契约：
 * 错误码必须被正确保存，异常消息必须符合构造方法语义，非法的空错误码必须被尽早拒绝。
 * </p>
 * <p>
 * 本测试类不启动 Spring 容器，也不触发 {@code GlobalExceptionHandler}。
 * 这样可以把测试范围收敛在异常对象本身，避免 Web 层响应封装、状态码映射等集成逻辑干扰判断。
 * </p>
 */
class BusinessExceptionTest {

    /**
     * 验证只传入 {@link ErrorCode} 时，异常消息会使用错误码中定义的默认文案。
     * <p>
     * 这是业务层最常见的抛出方式：当错误类别已经足够表达失败原因时，
     * 直接复用 {@link ErrorCode#getDefaultMessage()} 可以减少重复文案，
     * 也能保证相同错误码在不同业务入口中保持一致的默认提示。
     * </p>
     */
    @Test
    void shouldUseDefaultMessageWhenOnlyErrorCodeIsProvided() {
        // 仅传入错误码，表示使用该错误码预设的默认错误描述。
        BusinessException exception = new BusinessException(ErrorCode.BUSINESS_ERROR);

        // assertSame 用于确认异常中保存的是同一个 ErrorCode 枚举实例，而不是其他错误码或转换后的副本。
        assertSame(ErrorCode.BUSINESS_ERROR, exception.getErrorCode());
        // 单参数构造方法应把 ErrorCode.defaultMessage 作为 RuntimeException 的 message。
        assertEquals(ErrorCode.BUSINESS_ERROR.getDefaultMessage(), exception.getMessage());
    }

    /**
     * 验证传入自定义错误文案时，异常消息优先使用调用方提供的业务描述。
     * <p>
     * 有些失败虽然属于同一类错误码，但需要给调用方更具体的上下文，
     * 例如“订单状态不允许支付”“活动已下架”“库存不足”等。
     * 此时错误码仍用于机器可识别的分类，自定义 message 则用于表达当前失败的具体原因。
     * </p>
     */
    @Test
    void shouldPreferCustomMessageWhenMessageIsProvided() {
        // 错误码保持为通用业务错误，自定义 message 提供更贴近当前业务场景的失败原因。
        BusinessException exception = new BusinessException(ErrorCode.BUSINESS_ERROR, "订单状态不允许支付");

        // 自定义文案不应影响错误码归类，全局异常处理器仍可根据该错误码生成统一响应 code/status。
        assertSame(ErrorCode.BUSINESS_ERROR, exception.getErrorCode());
        // 双参数构造方法应优先使用调用方传入的 message，而不是 ErrorCode 的默认文案。
        assertEquals("订单状态不允许支付", exception.getMessage());
    }

    /**
     * 验证默认文案构造方法会拒绝空错误码。
     * <p>
     * {@link BusinessException} 的 errorCode 是全局异常处理器生成统一错误响应的关键字段。
     * 如果允许它为 {@code null}，错误可能会延迟到异常处理阶段才暴露，
     * 造成空指针、错误响应不稳定，或者让真正的业务失败原因变得难以定位。
     * </p>
     */
    @Test
    void shouldRejectNullErrorCodeForDefaultMessageConstructor() {
        // assertThrows 不仅验证会抛出 NullPointerException，也会把异常对象返回，方便继续校验错误信息。
        NullPointerException exception = assertThrows(NullPointerException.class, () -> new BusinessException(null));

        // 固定保护性错误文案，方便调用方和测试快速定位是 errorCode 参数为空。
        assertEquals("errorCode must not be null", exception.getMessage());
    }

    /**
     * 验证自定义文案构造方法同样会拒绝空错误码。
     * <p>
     * 即使调用方提供了自定义 message，也不能绕过 errorCode 的必填约束。
     * 因为 message 只面向人类阅读，errorCode 才是系统进行错误分类、
     * HTTP 状态映射和统一响应码治理的基础。
     * </p>
     */
    @Test
    void shouldRejectNullErrorCodeForCustomMessageConstructor() {
        // 双参数构造方法也必须在构造阶段尽早失败，避免生成一个缺少错误码的业务异常。
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new BusinessException(null, "自定义错误信息")
        );

        // 两个构造方法对空 errorCode 使用同一条保护性错误文案，保持异常契约一致。
        assertEquals("errorCode must not be null", exception.getMessage());
    }
}
