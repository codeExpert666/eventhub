package com.eventhub.modules.system.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eventhub.common.response.RequestIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 基础系统接口测试。
 * 这里优先覆盖统一响应体和校验失败场景，确保工程骨架具备最小可验证性。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SystemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void pingShouldReturnWrappedSuccessResponse() throws Exception {
        mockMvc.perform(get("/api/v1/system/ping"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").value("COMMON-000"))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.serviceName").value("eventhub-backend"));
    }

    @Test
    void echoShouldReturnWrappedSuccessResponse() throws Exception {
        mockMvc.perform(post("/api/v1/system/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "hello eventhub",
                                  "tag": "bootstrap"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON-000"))
                .andExpect(jsonPath("$.data.message").value("hello eventhub"))
                .andExpect(jsonPath("$.data.tag").value("bootstrap"));
    }

    @Test
    void echoShouldRejectBlankMessage() throws Exception {
        mockMvc.perform(post("/api/v1/system/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "",
                                  "tag": "bootstrap"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"))
                .andExpect(jsonPath("$.message").value("请求参数校验失败"))
                .andExpect(jsonPath("$.data.message").value("message 不能为空"));
    }

    @Test
    void echoShouldRejectMalformedJsonWithUnifiedResponse() throws Exception {
        mockMvc.perform(post("/api/v1/system/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "hello"
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"))
                .andExpect(jsonPath("$.message").value("请求体格式不合法"))
                .andExpect(jsonPath("$.data.body").value("请求体缺失或 JSON 格式错误"));
    }

    @Test
    void pingShouldRegenerateUnsafeRequestId() throws Exception {
        mockMvc.perform(get("/api/v1/system/ping")
                        .header(RequestIdFilter.HEADER_NAME, "unsafe request id ###"))
                .andExpect(status().isOk())
                .andExpect(header().exists(RequestIdFilter.HEADER_NAME))
                .andExpect(header().string(RequestIdFilter.HEADER_NAME, org.hamcrest.Matchers.not("unsafe request id ###")));
    }

    @Test
    void healthEndpointShouldBeAvailable() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void openApiDocumentShouldContainSystemPingEndpoint() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/system/ping']").exists());
    }
}
