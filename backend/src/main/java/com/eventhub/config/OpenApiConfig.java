package com.eventhub.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 基础配置。
 * 当前只暴露项目级元数据，后续业务模块通过注解自动汇总到同一份文档中。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI eventhubOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("EventHub Backend API")
                        .description("活动预约与票务平台单体后端基础工程接口文档")
                        .version("v1")
                        .contact(new Contact().name("EventHub"))
                        .license(new License().name("Apache-2.0")));
    }
}
