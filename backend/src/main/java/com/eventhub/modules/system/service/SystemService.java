package com.eventhub.modules.system.service;

import com.eventhub.modules.system.domain.EchoInfo;
import com.eventhub.modules.system.domain.PingInfo;
import com.eventhub.modules.system.dto.EchoRequest;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * 系统服务层。
 * 这里保留 service 分层，确保后续业务模块继续扩展时不需要重构最外层结构。
 */
@Service
public class SystemService {

    private final Environment environment;

    public SystemService(Environment environment) {
        this.environment = environment;
    }

    public PingInfo ping() {
        return new PingInfo(
                environment.getProperty("spring.application.name", "eventhub-backend"),
                resolveActiveProfiles(),
                OffsetDateTime.now()
        );
    }

    public EchoInfo echo(EchoRequest request) {
        return new EchoInfo(request.message(), request.tag(), OffsetDateTime.now());
    }

    private List<String> resolveActiveProfiles() {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length == 0) {
            return List.of("default");
        }
        return Arrays.asList(activeProfiles);
    }
}
