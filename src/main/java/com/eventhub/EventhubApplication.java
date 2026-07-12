package com.eventhub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 应用入口。
 * 当前保持单体单模块结构，后续按业务模块复杂度继续扩展 controller/service/dto/vo/domain/mapper 等分层。
 * 其中 domain 只在模块确实出现领域规则、状态流转或核心业务对象时引入，避免基础示例模块过早建模。
 */
@SpringBootApplication
public class EventhubApplication {

    public static void main(String[] args) {
        SpringApplication.run(EventhubApplication.class, args);
    }
}
