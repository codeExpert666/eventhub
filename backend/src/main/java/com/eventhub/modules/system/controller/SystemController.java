package com.eventhub.modules.system.controller;

import com.eventhub.common.response.ApiResponse;
import com.eventhub.modules.system.domain.EchoInfo;
import com.eventhub.modules.system.domain.PingInfo;
import com.eventhub.modules.system.dto.EchoRequest;
import com.eventhub.modules.system.service.SystemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统示例接口。
 * 它不承载具体业务，而是用于验证基础工程的统一响应、参数校验和异常处理闭环。
 */
@Tag(name = "System", description = "系统基础能力接口")
@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    private final SystemService systemService;

    public SystemController(SystemService systemService) {
        this.systemService = systemService;
    }

    @Operation(summary = "系统探活", description = "验证应用启动、统一响应体和基础服务信息")
    @GetMapping("/ping")
    public ApiResponse<PingInfo> ping() {
        return ApiResponse.success(systemService.ping());
    }

    @Operation(summary = "回显示例", description = "验证请求体校验和统一异常处理")
    @PostMapping("/echo")
    public ApiResponse<EchoInfo> echo(@Valid @RequestBody EchoRequest request) {
        return ApiResponse.success(systemService.echo(request));
    }
}
