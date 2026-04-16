package com.eventhub.modules.system.domain;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 系统探活返回对象。
 */
public record PingInfo(
        String serviceName,
        List<String> activeProfiles,
        OffsetDateTime serverTime
) {
}
