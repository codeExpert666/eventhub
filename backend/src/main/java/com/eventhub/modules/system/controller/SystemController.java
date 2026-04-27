package com.eventhub.modules.system.controller;

import com.eventhub.common.api.ApiResponse;
import com.eventhub.modules.system.dto.request.EchoRequest;
import com.eventhub.modules.system.vo.EchoInfo;
import com.eventhub.modules.system.vo.PingInfo;
import com.eventhub.modules.system.service.SystemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统基础能力控制器。
 * 该控制器不承载活动、订单、支付等核心业务，
 * 而是作为基础工程的示例入口，用于验证统一响应体、参数校验、异常处理和 OpenAPI 文档是否工作正常。
 * 由于它足够简单，通常也会被用来帮助新同学快速理解当前项目的 Controller -> Service 基本调用路径。
 *
 * <p>{@link Tag} 是 OpenAPI/Swagger 提供的控制器级注解，用来给一组接口打“分组标签”。
 * Swagger UI 会按照 {@code name} 将接口归类展示，{@code description} 则用于说明这一组接口的整体职责。
 * 这里声明 {@code System}，表示当前控制器下的接口都属于“系统基础能力”这一组。
 *
 * <p>{@link Operation} 是 OpenAPI/Swagger 提供的方法级注解，用来描述某一个具体接口。
 * 其中 {@code summary} 用于展示接口的简短标题，适合让调用方快速扫一眼就知道接口用途；
 * {@code description} 用于补充更完整的说明，帮助前端、测试和后续维护者理解接口的验证目标与使用语义。
 *
 * <p>{@link RequiredArgsConstructor} 会为所有 {@code final} 字段生成构造器。
 * 在 Spring 中，单构造器 Bean 可以省略 {@code @Autowired}，因此这里仍然是标准的构造器注入，
 * 只是把原先手写的构造器交给 Lombok 在编译期生成。
 */
@Tag(name = "System", description = "系统基础能力接口")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    /**
     * 系统模块应用服务。
     * 控制器只负责处理 HTTP 请求协议、参数绑定和响应包装，
     * 具体的探活信息组装、回显内容构造等逻辑交给服务层处理，以保持分层职责清晰。
     */
    private final SystemService systemService;

    /**
     * 系统探活接口。
     * 该接口通常用于快速确认服务是否已经成功启动，以及统一响应体是否能够正常返回。
     * 返回值中的服务名、激活环境和服务端时间都由服务层统一组装，控制器只负责暴露 HTTP GET 入口。
     *
     * <p>这里的 {@link Operation} 会把“系统探活”展示到 Swagger UI 中，
     * 让接口使用者无需阅读代码，也能直接在接口文档里看到该接口的用途和验证目标。
     *
     * @return 使用统一响应体包装后的探活信息
     */
    @Operation(summary = "系统探活", description = "验证应用启动、统一响应体和基础服务信息")
    @GetMapping("/ping")
    public ApiResponse<PingInfo> ping() {
        return ApiResponse.success(systemService.ping());
    }

    /**
     * 回显示例接口。
     * 该接口主要用于演示三件事：
     * 第一，请求体如何通过 {@link RequestBody} 绑定到 {@link EchoRequest}；
     * 第二，{@link Valid} 如何在进入业务逻辑前触发参数校验；
     * 第三，控制器如何继续沿用统一响应体对结果进行包装。
     *
     * <p>如果请求参数不满足 {@link EchoRequest} 上声明的校验规则，
     * Spring Validation 会先抛出校验异常，再交给全局异常处理器转换成统一错误响应，
     * 因此这个接口也是验证“请求校验 -> 异常处理 -> 统一响应”链路是否打通的重要示例。
     *
     * @param request 回显请求体，包含消息内容以及可选标签
     * @return 使用统一响应体包装后的回显结果
     */
    @Operation(summary = "回显示例", description = "验证请求体校验和统一异常处理")
    @PostMapping("/echo")
    public ApiResponse<EchoInfo> echo(@Valid @RequestBody EchoRequest request) {
        return ApiResponse.success(systemService.echo(request));
    }
}
