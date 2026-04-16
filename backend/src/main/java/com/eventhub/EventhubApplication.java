package com.eventhub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 应用入口。
 * 当前保持单体单模块结构，后续按业务模块继续扩展 controller/service/domain/mapper 分层。
 */
@SpringBootApplication
public class EventhubApplication {

    public static void main(String[] args) {
        SpringApplication.run(EventhubApplication.class, args);
    }
}
