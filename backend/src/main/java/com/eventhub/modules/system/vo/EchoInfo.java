package com.eventhub.modules.system.vo;

import java.time.OffsetDateTime;

/**
 * 回显示例接口返回对象。
 * 该对象用于承载 {@code /api/v1/system/echo} 接口的响应数据，
 * 主要目的是把客户端提交的消息内容按统一结构返回出来，
 * 从而帮助开发者验证请求体绑定、参数校验、服务层处理和统一响应封装链路是否正常。
 *
 * <p>这里使用 Java {@code record}，
 * 是因为它本质上属于简单、只读的返回模型，不承担领域行为或复杂状态变更职责。
 * 对于这类“只负责承载数据”的对象，{@code record} 能更直接表达不可变语义，
 * 也能避免引入冗余的样板代码。
 *
 * @param message 本次回显的消息内容，通常直接来自客户端请求体中的 {@code message} 字段
 * @param tag 本次回显附带的可选标签，通常用于演示附加字段在请求与响应之间的透传效果
 * @param echoedAt 服务端生成回显结果的时间，
 *                 用于表明该响应是由服务端在本次请求处理中实时生成的
 */
public record EchoInfo(
        String message,
        String tag,
        OffsetDateTime echoedAt
) {
}
