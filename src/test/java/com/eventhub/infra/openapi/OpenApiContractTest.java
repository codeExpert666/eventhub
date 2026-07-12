package com.eventhub.infra.openapi;

import static org.hamcrest.Matchers.hasItems;
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
 * 全局 OpenAPI 契约回归测试。
 *
 * <p>
 * 该测试不归属于某个具体业务 Controller，而是验证 Springdoc 汇总全部模块后生成的全局文档。
 * 将跨模块 Schema 断言集中在这里，可以避免 auth 响应模型调整导致 system 模块测试因职责外变化而失败。
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * 验证当前接口和重命名后的响应 DTO 已被正确收集为 OpenAPI Schema。
     *
     * <p>
     * 除了检查 Schema 组件存在，还检查文档中至少存在指向这些组件的 {@code $ref}，
     * 避免只生成孤立组件却没有任何接口真正引用它们。
     * </p>
     */
    @Test
    void openApiDocumentShouldContainCurrentResponseSchemas() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/system/ping']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/system/echo']").exists())
                .andExpect(jsonPath("$.components.schemas.UserResponse").exists())
                .andExpect(jsonPath("$.components.schemas.PingResponse").exists())
                .andExpect(jsonPath("$.components.schemas.EchoResponse").exists())
                .andExpect(jsonPath("$..['$ref']", hasItems(
                        "#/components/schemas/UserResponse",
                        "#/components/schemas/PingResponse",
                        "#/components/schemas/EchoResponse")));
    }
}
