package com.eventhub.modules.system.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 回显示例接口请求体。
 * 该对象用于承载 {@code /api/v1/system/echo} 接口接收的 JSON 请求参数，
 * 是控制器层进行请求体绑定、参数校验和接口文档生成的统一入口。
 * 当前请求模型保持轻量，主要用于演示“客户端提交请求 -> Spring 绑定参数 -> Bean Validation 校验 -> 进入服务层处理”的基础闭环。
 *
 * <p>这里使用 Java {@code record}，
 * 是因为该类型本质上只是一个只读请求数据载体，不需要额外的可变状态和行为方法。
 * 对于这种简单 DTO，{@code record} 可以更直接地表达“请求参数集合”的语义，并减少样板代码。
 *
 * <p>{@link Schema} 是 Springdoc OpenAPI / Swagger 提供的模型字段说明注解，
 * 主要作用不是做运行时参数校验，而是告诉接口文档系统“这个字段在文档里应该如何展示”。
 * 例如这里的 {@code description} 用于说明字段含义，{@code example} 用于给出 Swagger UI 中的示例值，
 * 这样前端、测试或其他调用方在查看接口文档时，不必翻源码也能知道这个字段应该传什么。
 *
 * <p>需要注意的是，{@link Schema} 和 {@link NotBlank}/{@link Size} 分工不同：
 * 前者负责文档描述，后者负责运行时校验。
 * 也就是说，即使写了 {@code @Schema}，如果没有校验注解，接口文档会更清楚，但请求参数本身不会因此自动变得合法。
 *
 * @param message 回显消息内容，是本次请求中最核心的输入字段；
 *                既会被写入接口文档示例，也会受到非空和最大长度约束校验
 * @param tag 可选标签字段，用于演示附加参数在请求和响应中的透传效果；
 *            当前不是必填项，但仍然受到最大长度约束，以避免无界输入
 */
public record EchoRequest(
        /*
          通过 {@link Schema} 为 Swagger/OpenAPI 文档补充字段语义和示例值，
          让接口使用者在 Swagger UI 中能直接看到“message”代表什么、推荐如何传值。
         */
        @Schema(description = "回显消息", example = "hello eventhub")
        @NotBlank(message = "message 不能为空")
        @Size(max = 64, message = "message 长度不能超过 64")
        String message,

        /*
          该字段同样使用 {@link Schema} 描述接口文档中的展示方式。
          因为它是可选字段，所以这里只补充文档说明和示例，不增加 {@link NotBlank} 约束。
         */
        @Schema(description = "可选标签", example = "bootstrap")
        @Size(max = 32, message = "tag 长度不能超过 32")
        String tag
) {
}
