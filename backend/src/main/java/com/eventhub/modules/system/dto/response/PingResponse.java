package com.eventhub.modules.system.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 系统探活接口响应。
 * 该对象用于承载 {@code /api/v1/system/ping} 接口返回的基础运行信息，
 * 让调用方可以快速判断当前服务实例是否正常启动，以及它正在以什么配置环境对外提供服务。
 *
 * <p>
 * 这里使用 Java {@code record} 而不是普通类，
 * 是因为这个对象本质上只是一个只读数据载体，不需要额外的业务行为。
 * {@code record} 能够天然表达“不可变响应模型”的语义（所有字段默认都是 final 的），
 * 同时减少 getter、构造器、{@code equals/hashCode} 等样板代码。
 *
 * @param serviceName    当前服务名称，通常来自 {@code spring.application.name} 配置，
 *                       用于标识是哪一个后端应用在响应探活请求
 * @param activeProfiles 当前实例激活的 Spring Profile 列表，
 *                       用于帮助调用方确认服务运行环境，例如 {@code dev}、{@code test} 或
 *                       {@code prod}
 * @param serverTime     服务端生成本次响应的时间，
 *                       用于体现请求确实到达了当前实例，也可辅助排查时钟、时区或部署状态问题
 */
public record PingResponse(
        String serviceName,
        List<String> activeProfiles,
        OffsetDateTime serverTime) {
}
