package com.eventhub.modules.system.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.eventhub.infra.logging.RequestIdFilter;

/**
 * {@link SystemController} 的 Web 层测试。
 *
 * <p>
 * 这类测试对刚接触 Spring Boot 测试的同学来说，最容易困惑的一点是：
 * 它看起来像“单元测试”，但实际上更接近“轻量集成测试”。
 *
 * <p>
 * 原因在于这里并不是直接 new 一个 Controller 再手动调用方法，
 * 而是让 Spring Boot 启动测试用应用上下文，把 Controller、Filter、异常处理器、
 * JSON 序列化组件、参数校验能力等一整套 Web 基础设施都装配起来，
 * 然后再通过 {@link MockMvc} 在内存里发起 HTTP 请求并断言响应结果。
 *
 * <p>
 * 因此，这个测试类主要用于验证以下链路是否真的打通：
 * 请求进入 -> Spring MVC 路由分发 -> Controller 参数绑定 -> 参数校验
 * -> Service 调用 -> 统一响应包装 -> Filter / 异常处理器介入 -> 最终 HTTP 响应输出。
 *
 * <p>
 * 当前文件优先覆盖的是项目基础工程最重要的几条能力：
 * 统一响应体、请求参数校验、非法 JSON 请求体处理、请求 ID 透传/重建、
 * Actuator 健康检查、应用信息端点，以及 OpenAPI 文档是否已成功暴露。
 */
// 启动完整的 Spring Boot 测试上下文，而不是只创建某一个孤立对象。
@SpringBootTest
// 在测试上下文中自动配置 MockMvc，便于我们以 HTTP 的方式调用 Controller。
@AutoConfigureMockMvc
// 强制使用 test 配置环境，避免测试误读到本地或生产配置。
@ActiveProfiles("test")
class SystemControllerTest {

    /**
     * Spring 提供的 MVC 测试入口。
     *
     * <p>
     * {@link MockMvc} 不会真的启动 Tomcat 之类的外部 Servlet 容器，
     * 但它会按 Spring MVC 的真实处理流程来执行请求，
     * 因此非常适合做控制器层和 Web 基础设施的测试。
     *
     * <p>
     * 你可以把它先理解成“内存里的 HTTP 客户端”：
     * 我们用它构造请求、发送到 Spring MVC，再对返回的状态码、响应头、JSON 内容做断言。
     */
    @Autowired
    private MockMvc mockMvc;

    /**
     * 验证探活接口在成功场景下是否遵循统一响应协议。
     *
     * <p>
     * 这个测试重点不是业务复杂度，而是确认最基础的成功响应链路是否完整：
     * 第一，请求能够正确路由到 {@code /api/v1/system/ping}；
     * 第二，控制器返回的结果会被包装成统一响应体；
     * 第三，请求经过 {@link RequestIdFilter} 后，响应头里会带上请求 ID。
     */
    @Test
    void pingShouldReturnWrappedSuccessResponse() throws Exception {
        mockMvc.perform(get("/api/v1/system/ping"))
                // Controller 正常执行时，HTTP 层应返回 200 OK。
                .andExpect(status().isOk())
                // 请求经过 RequestIdFilter 后，响应头中必须存在 X-Request-Id。
                .andExpect(header().exists("X-Request-Id"))
                // code 是项目自定义的应用层状态码，不等同于 HTTP 状态码。
                .andExpect(jsonPath("$.code").value("COMMON-000"))
                // message 表示统一响应体中的默认成功消息。
                .andExpect(jsonPath("$.message").value("成功"))
                // data 则承载真正的业务结果，这里验证服务名是否被正确返回。
                .andExpect(jsonPath("$.data.serviceName").value("eventhub-backend"));
    }

    /**
     * 验证系统模块不再通过 /api/v1/system/** 路径通配公开所有请求方法。
     *
     * <p>
     * 当前只有 GET /ping 和 POST /echo 是明确公开接口。
     * 如果未登录用户使用 DELETE 访问同一命名空间下的路径，应该先被安全规则拦截为 401，
     * 避免未来在 system 命名空间中新增写接口时，因为路径级 permitAll 被意外公开。
     */
    @Test
    void systemNamespaceShouldNotPermitUnsupportedMethodWithoutToken() throws Exception {
        mockMvc.perform(delete("/api/v1/system/ping"))
                .andExpect(status().isUnauthorized())
                // 401 在进入 Controller 前由 Spring Security 返回，仍带 requestId 表示 RequestIdFilter
                // 已足够靠前。
                .andExpect(header().exists(RequestIdFilter.HEADER_NAME))
                .andExpect(jsonPath("$.code").value("AUTH-401"));
    }

    /**
     * 验证回显接口在合法请求体下是否能够成功返回。
     *
     * <p>
     * 这个测试演示了控制器测试里最常见的 POST JSON 场景：
     * 先声明请求体内容类型，再传入 JSON 字符串，
     * 最后断言返回值中的业务字段是否和输入匹配。
     *
     * <p>
     * 因为这里的接口只是示例回显接口，所以断言重点是：
     * 请求体能否被正确绑定、控制器能否正常进入业务逻辑、统一响应体是否保持约定结构。
     */
    @Test
    void echoShouldReturnWrappedSuccessResponse() throws Exception {
        mockMvc.perform(post("/api/v1/system/echo")
                // 告诉 Spring 当前请求体使用 JSON 传输。
                .contentType(MediaType.APPLICATION_JSON)
                // 这里直接内联 JSON，方便在测试中清楚看到输入内容。
                .content("""
                        {
                          "message": "hello eventhub",
                          "tag": "bootstrap"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON-000"))
                // 回显接口会把请求中的 message/tag 原样写回响应 data。
                .andExpect(jsonPath("$.data.message").value("hello eventhub"))
                .andExpect(jsonPath("$.data.tag").value("bootstrap"));
    }

    /**
     * 验证 Bean Validation 在 Web 请求链路中是否生效。
     *
     * <p>
     * 这里传入空字符串 {@code message}，目的是故意触发 {@code EchoRequest}
     * 上声明的校验注解。测试通过后说明：
     * 第一，请求已经被正确绑定到 DTO；
     * 第二，{@code @Valid} 已经触发参数校验；
     * 第三，校验异常最终被统一异常处理器转换成了项目约定的错误响应格式。
     */
    @Test
    void echoShouldRejectBlankMessage() throws Exception {
        mockMvc.perform(post("/api/v1/system/echo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "message": "",
                          "tag": "bootstrap"
                        }
                        """))
                // 参数校验失败属于客户端请求错误，因此 HTTP 状态应为 400。
                .andExpect(status().isBadRequest())
                // 这里仍然返回统一响应体，只是 code/message/data 表示失败语义。
                .andExpect(jsonPath("$.code").value("COMMON-400"))
                .andExpect(jsonPath("$.message").value("请求体参数校验失败"))
                // data.message 中承载的是更细的字段级校验错误信息。
                .andExpect(jsonPath("$.data.message").value("message 不能为空"));
    }

    /**
     * 验证“请求体本身就不是合法 JSON”时，系统是否仍然返回统一错误响应。
     *
     * <p>
     * 这和上一个测试的区别很重要：
     * 上一个测试是“JSON 合法，但字段值不满足业务/校验规则”；
     * 这个测试是“连 JSON 语法都不合法，Spring 在反序列化阶段就失败了”。
     *
     * <p>
     * 对初学者来说，可以把两者先区分成：
     * 参数校验失败 = 请求结构能读懂，但内容不符合约束；
     * 请求体格式错误 = 请求内容连解析都无法完成。
     */
    @Test
    void echoShouldRejectMalformedJsonWithUnifiedResponse() throws Exception {
        mockMvc.perform(post("/api/v1/system/echo")
                .contentType(MediaType.APPLICATION_JSON)
                // 故意缺少结束大括号，模拟前端传来损坏的 JSON。
                .content("""
                        {
                          "message": "hello"
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"))
                .andExpect(jsonPath("$.message").value("请求体格式不合法"))
                // 这里断言的是全局异常处理器返回的可读错误描述。
                .andExpect(jsonPath("$.data.body").value("请求体缺失或 JSON 格式错误"));
    }

    /**
     * 验证请求 ID 过滤器会拒绝不安全的外部请求 ID，并重新生成安全值。
     *
     * <p>
     * 这个测试主要覆盖 {@link RequestIdFilter} 的安全边界：
     * 如果客户端传入的请求 ID 不符合项目允许的格式，就不能原样透传到日志和响应中，
     * 否则可能污染日志、影响链路追踪字段的稳定性。
     */
    @Test
    void pingShouldRegenerateUnsafeRequestId() throws Exception {
        mockMvc.perform(get("/api/v1/system/ping")
                // 这里故意传入包含空格和非法字符的请求 ID。
                .header(RequestIdFilter.HEADER_NAME, "unsafe request id ###"))
                .andExpect(status().isOk())
                .andExpect(header().exists(RequestIdFilter.HEADER_NAME))
                // 断言响应头存在且不是原始非法值，表示过滤器做了重新生成。
                .andExpect(header().string(RequestIdFilter.HEADER_NAME,
                        org.hamcrest.Matchers.not("unsafe request id ###")));
    }

    /**
     * 验证 Actuator 健康检查端点是否已经在测试环境中可用。
     *
     * <p>
     * 这个接口通常不属于业务 Controller，但它对服务可观测性非常重要。
     * 如果这里返回 {@code UP}，至少说明应用已成功启动，基础健康检查端点可访问。
     */
    @Test
    void healthEndpointShouldBeAvailable() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    /**
     * 验证健康检查端点的 HEAD 请求也被显式放行。
     *
     * <p>
     * 部分负载均衡、代理或部署平台会用 HEAD 做轻量探测；
     * 安全配置中单独放行 HEAD，可以避免这些基础设施请求被错误识别为未登录业务请求。
     */
    @Test
    void healthEndpointShouldPermitHeadRequest() throws Exception {
        mockMvc.perform(head("/actuator/health"))
                .andExpect(status().isOk());
    }

    /**
     * 验证 Actuator 信息端点是否已经按阶段 0 规划暴露。
     *
     * <p>
     * {@code /actuator/info} 主要用于承载应用版本、构建信息或自定义运行说明。
     * 当前阶段不强制写入具体 info 内容，但端点本身必须可访问，方便后续逐步补充构建元数据。
     */
    @Test
    void infoEndpointShouldBeAvailable() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());
    }

    /**
     * 验证 OpenAPI 文档端点已经成功生成，并包含当前系统模块接口。
     *
     * <p>
     * 这说明 Swagger/OpenAPI 相关配置已经生效，
     * 并且 {@link SystemController} 上声明的接口被正确收集到了文档模型里。
     */
    @Test
    void openApiDocumentShouldContainSystemPingEndpoint() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/system/ping']").exists());
    }

    /**
     * 验证缺失静态资源会返回统一 404 响应，而不是被当成未知系统异常处理。
     *
     * <p>
     * 浏览器访问 {@code /v3/api-docs}、Swagger UI 或普通页面时，
     * 通常会自动额外请求 {@code /favicon.ico}。当前项目没有提供该图标文件，
     * 所以 Spring MVC 会把它识别为“静态资源不存在”。
     *
     * <p>
     * 这个测试覆盖的是完整 Web 链路：
     * 请求进入 MockMvc -> Spring MVC 静态资源处理器查找失败
     * -> 抛出资源不存在异常 -> 全局异常处理器转换成统一 404 响应。
     */
    @Test
    void missingFaviconShouldReturnUnifiedNotFoundResponse() throws Exception {
        mockMvc.perform(get("/favicon.ico"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON-404"))
                .andExpect(jsonPath("$.message").value("请求的资源不存在"));
    }
}
