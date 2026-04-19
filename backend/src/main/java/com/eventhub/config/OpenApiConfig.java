package com.eventhub.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Springdoc OpenAPI 全局配置。
 * 该配置类负责为 Swagger UI / OpenAPI 文档注册项目级基础元数据，
 * 例如文档标题、版本、维护方和开源许可证等信息。
 * 当前仅维护统一的文档首页信息，具体接口定义仍然由各业务控制器上的注解自动汇总生成。
 */
@Configuration
public class OpenApiConfig {

    /**
     * 注册全局 OpenAPI Bean。
     * 该 Bean 会作为接口文档的基础描述信息来源，供 Swagger UI 首页和生成的 OpenAPI 规范复用。
     * 后续如果需要补充鉴权说明、环境地址、分组标签等能力，也可以继续在这里集中扩展。
     *
     * @return 包含项目基础元数据的 OpenAPI 对象
     */
    @Bean
    public OpenAPI eventhubOpenApi() {
        // 这里只声明项目级公共元数据，避免把基础文档信息分散到各个控制器注解中维护。
        return new OpenAPI()
                .info(new Info()
                        .title("EventHub Backend API")
                        .description("活动预约与票务平台单体后端基础工程接口文档")
                        .version("v1")
                        .contact(new Contact().name("EventHub"))
                        .license(new License().name("Apache-2.0")));
    }
}
