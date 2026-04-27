package com.eventhub.modules.auth;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eventhub.modules.auth.enums.UserStatus;
import com.eventhub.modules.auth.security.JwtTokenService;
import com.eventhub.modules.auth.vo.UserInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 阶段 1 认证授权集成测试。
 * 这些用例启动完整 Spring Boot 测试上下文，并通过 MockMvc 覆盖注册、登录、JWT 认证和 RBAC 拦截链路。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenService jwtTokenService;

    /**
     * 验证注册成功后会返回用户摘要，并默认绑定 USER 角色。
     */
    @Test
    void registerShouldCreateUserWithUserRole() throws Exception {
        String username = nextUsername("register");
        String email = nextEmail(username);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "username", username,
                                "email", email,
                                "password", "Password123"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON-000"))
                .andExpect(jsonPath("$.data.username").value(username))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.status").value("ENABLED"))
                .andExpect(jsonPath("$.data.roles[0]").value("USER"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    /**
     * 验证重复用户名会被稳定转换为账号冲突错误。
     */
    @Test
    void registerShouldRejectDuplicateUsername() throws Exception {
        String username = nextUsername("dupuser");
        register(username, nextEmail(username));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "username", username,
                                "email", nextEmail(username + "x"),
                                "password", "Password123"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH-409"))
                .andExpect(jsonPath("$.message").value("用户名已存在"));
    }

    /**
     * 验证重复邮箱会被稳定转换为账号冲突错误。
     */
    @Test
    void registerShouldRejectDuplicateEmail() throws Exception {
        String username = nextUsername("dupemail");
        String email = nextEmail(username);
        register(username, email);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "username", nextUsername("dupemailx"),
                                "email", email,
                                "password", "Password123"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH-409"))
                .andExpect(jsonPath("$.message").value("邮箱已存在"));
    }

    /**
     * 验证登录成功后返回 Bearer access token、过期秒数和当前用户摘要。
     */
    @Test
    void loginShouldReturnAccessToken() throws Exception {
        String username = nextUsername("loginok");
        register(username, nextEmail(username));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "usernameOrEmail", username,
                                "password", "Password123"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON-000"))
                .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn", greaterThan(0)))
                .andExpect(jsonPath("$.data.user.username").value(username));
    }

    /**
     * 验证密码错误不会泄露账号是否存在，只返回统一认证失败。
     */
    @Test
    void loginShouldRejectWrongPassword() throws Exception {
        String username = nextUsername("badpwd");
        register(username, nextEmail(username));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "usernameOrEmail", username,
                                "password", "WrongPassword123"
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-401"))
                .andExpect(jsonPath("$.message").value("账号或密码错误"));
    }

    /**
     * 验证管理员禁用用户后，该用户不能再登录。
     */
    @Test
    void disabledUserShouldNotLogin() throws Exception {
        String username = nextUsername("disabled");
        JsonNode registeredUser = register(username, nextEmail(username));
        long userId = registeredUser.path("id").asLong();

        String adminToken = loginAndExtractToken("admin", "Admin123456");
        mockMvc.perform(patch("/api/v1/admin/users/{userId}/status", userId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("status", "DISABLED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "usernameOrEmail", username,
                                "password", "Password123"
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH-403"))
                .andExpect(jsonPath("$.message").value("用户已被禁用"));
    }

    /**
     * 验证没有 token 时访问受保护接口会进入认证失败入口。
     */
    @Test
    void requestWithoutTokenShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-401"));
    }

    /**
     * 验证普通 USER 角色不能访问管理员用户列表。
     */
    @Test
    void userShouldNotAccessAdminUsers() throws Exception {
        String username = nextUsername("normal");
        register(username, nextEmail(username));
        String token = loginAndExtractToken(username, "Password123");

        mockMvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH-403"));
    }

    /**
     * 验证过期 token 会被 JWT 过滤器识别并转换为 401。
     */
    @Test
    void expiredTokenShouldReturnUnauthorized() throws Exception {
        String username = nextUsername("expired");
        JsonNode registeredUser = register(username, nextEmail(username));
        UserInfo userInfo = new UserInfo(
                registeredUser.path("id").asLong(),
                registeredUser.path("username").asText(),
                registeredUser.path("email").asText(),
                UserStatus.ENABLED,
                List.of("USER")
        );
        String expiredToken = jwtTokenService.generateAccessToken(userInfo, Duration.ofSeconds(-5));

        mockMvc.perform(get("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(expiredToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-401"));
    }

    /**
     * 验证签名被篡改的 token 会被拒绝，避免伪造 claims 绕过认证。
     */
    @Test
    void tamperedTokenShouldReturnUnauthorized() throws Exception {
        String username = nextUsername("tampered");
        register(username, nextEmail(username));
        String token = loginAndExtractToken(username, "Password123");

        mockMvc.perform(get("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tamperToken(token))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-401"));
    }

    /**
     * 验证管理员禁用用户后，该用户已签发但未过期的旧 token 也不能继续访问受保护接口。
     */
    @Test
    void disabledUserOldTokenShouldReturnUnauthorized() throws Exception {
        String username = nextUsername("oldtoken");
        JsonNode registeredUser = register(username, nextEmail(username));
        long userId = registeredUser.path("id").asLong();
        String token = loginAndExtractToken(username, "Password123");

        String adminToken = loginAndExtractToken("admin", "Admin123456");
        mockMvc.perform(patch("/api/v1/admin/users/{userId}/status", userId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("status", "DISABLED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        mockMvc.perform(get("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-401"));
    }

    /**
     * 验证合法 token 可以访问当前用户接口。
     */
    @Test
    void meShouldReturnCurrentUserWithValidToken() throws Exception {
        String username = nextUsername("me");
        register(username, nextEmail(username));
        String token = loginAndExtractToken(username, "Password123");

        mockMvc.perform(get("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(username))
                .andExpect(jsonPath("$.data.roles[0]").value("USER"));
    }

    /**
     * 验证管理员种子账号具备访问管理接口的 ADMIN 权限。
     */
    @Test
    void adminSeedUserShouldAccessAdminUsers() throws Exception {
        String adminToken = loginAndExtractToken("admin", "Admin123456");

        mockMvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].username").value("admin"))
                .andExpect(jsonPath("$.data[0].roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.data[0].roles[1]").value("USER"));
    }

    private JsonNode register(String username, String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "username", username,
                                "email", email,
                                "password", "Password123"
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private String loginAndExtractToken(String usernameOrEmail, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "usernameOrEmail", usernameOrEmail,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("accessToken")
                .asText();
    }

    private String toJson(Map<String, String> payload) throws Exception {
        return objectMapper.writeValueAsString(payload);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String tamperToken(String token) {
        String replacement = token.endsWith("a") ? "b" : "a";
        return token.substring(0, token.length() - 1) + replacement;
    }

    private String nextUsername(String prefix) {
        return prefix + SEQUENCE.incrementAndGet();
    }

    private String nextEmail(String username) {
        return username + "@example.com";
    }
}
