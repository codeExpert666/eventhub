package com.eventhub.modules.system.domain;

import java.time.OffsetDateTime;

/**
 * 回显接口返回对象。
 */
public record EchoInfo(
        String message,
        String tag,
        OffsetDateTime echoedAt
) {
}
