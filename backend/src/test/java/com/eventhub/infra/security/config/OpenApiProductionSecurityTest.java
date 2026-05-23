package com.eventhub.infra.security.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 生产 profile 下 OpenAPI / Swagger UI 暴露面的安全回归测试。
 *
 * <p>
 * 该测试使用 {@code prod} profile 启动真实的 Spring Security Filter Chain，
 * 但通过测试属性把生产数据库、Redis 和 JWT 密钥占位符替换成可自包含的测试值。
 * 这样既能验证生产配置行为，又不会依赖开发者本机的真实生产级外部服务。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:eventhub-prod-openapi;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379",
        "spring.data.redis.password=",
        "management.health.redis.enabled=false",
        "eventhub.security.jwt.secret=eventhub-prod-openapi-test-secret"
})
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class OpenApiProductionSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * 生产环境关闭 springdoc 后，OpenAPI JSON 路径不应继续作为公开资源暴露。
     */
    @Test
    void openApiDocumentShouldRequireAuthenticationInProduction() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-401"));
    }

    /**
     * 生产环境关闭 Swagger UI 后，浏览器调试页面入口也不应继续公开访问。
     */
    @Test
    void swaggerUiShouldRequireAuthenticationInProduction() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-401"));
    }

    /**
     * OpenAPI 加固不应误伤基础探活；生产环境仍允许负载均衡或平台探测健康状态。
     */
    @Test
    void healthEndpointShouldRemainPublicButHideDetailsInProduction() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }
}
