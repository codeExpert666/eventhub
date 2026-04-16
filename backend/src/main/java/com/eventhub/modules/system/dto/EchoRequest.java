package com.eventhub.modules.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 回显接口请求体。
 */
public record EchoRequest(
        @Schema(description = "回显消息", example = "hello eventhub")
        @NotBlank(message = "message 不能为空")
        @Size(max = 64, message = "message 长度不能超过 64")
        String message,

        @Schema(description = "可选标签", example = "bootstrap")
        @Size(max = 32, message = "tag 长度不能超过 32")
        String tag
) {
}
