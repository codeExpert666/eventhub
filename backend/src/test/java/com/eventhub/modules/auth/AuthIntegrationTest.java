package com.eventhub.modules.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.eventhub.infra.security.jwt.JwtClaims;
import com.eventhub.infra.security.jwt.JwtCodec;
import com.eventhub.modules.auth.entity.AuthSessionEntity;
import com.eventhub.modules.auth.enums.AuthSessionStatus;
import com.eventhub.modules.auth.mapper.AuthSessionMapper;
import com.eventhub.modules.auth.service.RefreshTokenHasher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
    private JwtCodec jwtCodec;

    @Autowired
    private AuthSessionMapper authSessionMapper;

    @Autowired
    private RefreshTokenHasher refreshTokenHasher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
                        "password", "Password123"))))
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
                        "password", "Password123"))))
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
                        "password", "Password123"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH-409"))
                .andExpect(jsonPath("$.message").value("邮箱已存在"));
    }

    /**
     * 验证并发注册同一账号时，数据库唯一约束仍然是最终一致性防线。
     * 两个请求即使同时进入注册流程，也只能有一个创建成功，另一个必须被转换为稳定的 409 业务冲突。
     */
    @Test
    void concurrentRegisterShouldCreateOnlyOneAccount() throws Exception {
        String username = nextUsername("race");
        String email = nextEmail(username);
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> registerTask = () -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to start concurrent registration");
            }
            return registerAndReturnStatus(username, email);
        };

        try {
            Future<Integer> firstResult = executorService.submit(registerTask);
            Future<Integer> secondResult = executorService.submit(registerTask);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();

            start.countDown();

            assertThat(List.of(
                    firstResult.get(10, TimeUnit.SECONDS),
                    secondResult.get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
        } finally {
            executorService.shutdownNow();
        }
    }

    /**
     * 验证登录成功后返回双 token、创建 ACTIVE 认证会话，并且 refresh token 明文不会落库。
     */
    @Test
    void loginShouldReturnTokenPairAndCreateActiveSession() throws Exception {
        String username = nextUsername("loginok");
        JsonNode registeredUser = register(username, nextEmail(username));
        long userId = registeredUser.path("id").asLong();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of(
                        "usernameOrEmail", username,
                        "password", "Password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON-000"))
                .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                .andExpect(jsonPath("$.data.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn", greaterThan(0)))
                .andExpect(jsonPath("$.data.refreshExpiresIn").value(2_592_000))
                .andExpect(jsonPath("$.data.sessionId", notNullValue()))
                .andExpect(jsonPath("$.data.user.username").value(username))
                .andReturn();

        JsonNode loginData = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        String accessToken = loginData.path("accessToken").asText();
        String refreshToken = loginData.path("refreshToken").asText();
        String sessionId = loginData.path("sessionId").asText();
        JwtClaims claims = jwtCodec.parseAccessToken(accessToken);

        assertThat(refreshToken).isNotBlank();
        assertThat(claims.tokenId()).isNotBlank();
        assertThat(claims.sessionId()).isEqualTo(sessionId);
        assertThat(claims.tokenType()).isEqualTo(JwtClaims.ACCESS_TOKEN_TYPE);

        AuthSessionEntity session = authSessionMapper.findBySessionId(sessionId).orElseThrow();
        assertThat(session.getUserId()).isEqualTo(userId);
        assertThat(session.getStatus()).isEqualTo(AuthSessionStatus.ACTIVE);
        assertThat(session.getRefreshTokenHash()).isNotEqualTo(refreshToken);
        assertThat(authSessionMapper.findByRefreshTokenHash(refreshTokenHasher.hash(refreshToken))).isPresent();
    }

    /**
     * 验证密码错误不会泄露账号是否存在，只返回统一认证失败。
     */
    @Test
    void loginShouldRejectWrongPassword() throws Exception {
        String username = nextUsername("badpwd");
        JsonNode registeredUser = register(username, nextEmail(username));
        long userId = registeredUser.path("id").asLong();
        long sessionCountBeforeLogin = countAuthSessionsByUserId(userId);

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of(
                        "usernameOrEmail", username,
                        "password", "WrongPassword123"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-401"))
                .andExpect(jsonPath("$.message").value("账号或密码错误"));
        assertThat(countAuthSessionsByUserId(userId)).isEqualTo(sessionCountBeforeLogin);
    }

    /**
     * 验证不存在账号与密码错误使用同一认证失败响应，并且不会创建认证会话。
     */
    @Test
    void loginShouldRejectUnknownAccount() throws Exception {
        long sessionCountBeforeLogin = countAuthSessions();

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of(
                        "usernameOrEmail", nextUsername("missing"),
                        "password", "Password123"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-401"))
                .andExpect(jsonPath("$.message").value("账号或密码错误"));
        assertThat(countAuthSessions()).isEqualTo(sessionCountBeforeLogin);
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

        long sessionCountBeforeLogin = countAuthSessionsByUserId(userId);
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of(
                        "usernameOrEmail", username,
                        "password", "Password123"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH-403"))
                .andExpect(jsonPath("$.message").value("用户已被禁用"));
        assertThat(countAuthSessionsByUserId(userId)).isEqualTo(sessionCountBeforeLogin);
    }

    /**
     * 验证请求体中的未知状态值会在 Jackson 枚举反序列化阶段被拒绝。
     */
    @Test
    void updateUserStatusShouldRejectUnknownEnumValue() throws Exception {
        long userId = registerAndReturnUserId("unknownstatus");
        String adminToken = loginAndExtractToken("admin", "Admin123456");

        mockMvc.perform(patch("/api/v1/admin/users/{userId}/status", userId)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("status", "LOCKED"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"))
                .andExpect(jsonPath("$.message").value("请求体格式不合法"))
                .andExpect(jsonPath("$.data.body").value("请求体缺失或 JSON 格式错误"));
    }

    /**
     * 验证请求体中的 null 状态能完成绑定，但会被 Bean Validation 的 @NotNull 拦截。
     */
    @Test
    void updateUserStatusShouldRejectNullStatus() throws Exception {
        long userId = registerAndReturnUserId("nullstatus");
        String adminToken = loginAndExtractToken("admin", "Admin123456");

        mockMvc.perform(patch("/api/v1/admin/users/{userId}/status", userId)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"))
                .andExpect(jsonPath("$.message").value("请求体参数校验失败"))
                .andExpect(jsonPath("$.data.status").value("status 不能为空"));
    }

    /**
     * 验证请求体枚举字符串必须使用接口约定的大写枚举名，不接受大小写错误的输入。
     */
    @Test
    void updateUserStatusShouldRejectLowercaseEnumValue() throws Exception {
        long userId = registerAndReturnUserId("lowerstatus");
        String adminToken = loginAndExtractToken("admin", "Admin123456");

        mockMvc.perform(patch("/api/v1/admin/users/{userId}/status", userId)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("status", "disabled"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"))
                .andExpect(jsonPath("$.message").value("请求体格式不合法"))
                .andExpect(jsonPath("$.data.body").value("请求体缺失或 JSON 格式错误"));
    }

    /**
     * 验证 Jackson 全局配置已禁止数字 ordinal，避免 {"status":0} 被解释为第一个枚举常量。
     */
    @Test
    void updateUserStatusShouldRejectNumericEnumOrdinal() throws Exception {
        long userId = registerAndReturnUserId("ordinalstatus");
        String adminToken = loginAndExtractToken("admin", "Admin123456");

        mockMvc.perform(patch("/api/v1/admin/users/{userId}/status", userId)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"))
                .andExpect(jsonPath("$.message").value("请求体格式不合法"))
                .andExpect(jsonPath("$.data.body").value("请求体缺失或 JSON 格式错误"));
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
     * 验证登出接口被明确建模为登录后接口。
     *
     * <p>
     * 当前登出不在服务端吊销 token，但它仍表达“当前用户主动结束本地登录态”的协议语义，
     * 因此必须要求请求已经携带合法 Bearer token。
     */
    @Test
    void logoutShouldRequireAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
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
        JwtClaims claims = new JwtClaims(
                registeredUser.path("id").asLong(),
                "expired-token-id",
                "expired-session-id",
                JwtClaims.ACCESS_TOKEN_TYPE
        );
        String expiredToken = jwtCodec.generateAccessToken(claims, Duration.ofSeconds(-5));

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
                .param("username", "admin")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.total", greaterThan(0)))
                .andExpect(jsonPath("$.data.totalPages", greaterThan(0)))
                .andExpect(jsonPath("$.data.items[0].username").value("admin"))
                .andExpect(jsonPath("$.data.items[0].roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.data.items[0].roles[1]").value("USER"));
    }

    /**
     * 验证管理员用户列表支持 page/size 分页参数，并默认优先返回新注册用户。
     */
    @Test
    void adminUsersShouldSupportPaginationParameters() throws Exception {
        String username = nextUsername("page");
        register(username, nextEmail(username));
        String adminToken = loginAndExtractToken("admin", "Admin123456");

        mockMvc.perform(get("/api/v1/admin/users")
                .param("page", "1")
                .param("size", "1")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.total", greaterThan(1)))
                .andExpect(jsonPath("$.data.totalPages", greaterThan(1)))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.hasPrevious").value(false))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].username").value(username));
    }

    /**
     * 验证管理员用户列表支持按用户名、邮箱和状态组合筛选。
     */
    @Test
    void adminUsersShouldFilterByUsernameEmailAndStatus() throws Exception {
        String username = nextUsername("filter");
        String email = nextEmail(username);
        register(username, email);
        String adminToken = loginAndExtractToken("admin", "Admin123456");

        mockMvc.perform(get("/api/v1/admin/users")
                .param("username", username)
                .param("email", email)
                .param("status", "ENABLED")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].username").value(username))
                .andExpect(jsonPath("$.data.items[0].email").value(email))
                .andExpect(jsonPath("$.data.items[0].status").value("ENABLED"))
                .andExpect(jsonPath("$.data.items[0].roles[0]").value("USER"));
    }

    /**
     * 验证管理员用户列表支持按 createdAt 和 updatedAt 时间范围筛选。
     */
    @Test
    void adminUsersShouldFilterByCreatedAtAndUpdatedAtRange() throws Exception {
        LocalDateTime from = LocalDateTime.now().minusMinutes(1);
        String username = nextUsername("timerange");
        register(username, nextEmail(username));
        LocalDateTime to = LocalDateTime.now().plusMinutes(1);
        String adminToken = loginAndExtractToken("admin", "Admin123456");

        mockMvc.perform(get("/api/v1/admin/users")
                .param("username", username)
                .param("createdAtFrom", formatDateTime(from))
                .param("createdAtTo", formatDateTime(to))
                .param("updatedAtFrom", formatDateTime(from))
                .param("updatedAtTo", formatDateTime(to))
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].username").value(username));
    }

    /**
     * 验证非法分页参数会在 Controller 边界被拦截为统一 400 响应。
     */
    @Test
    void adminUsersShouldRejectInvalidPaginationParameters() throws Exception {
        String adminToken = loginAndExtractToken("admin", "Admin123456");

        mockMvc.perform(get("/api/v1/admin/users")
                .param("page", "0")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));

        mockMvc.perform(get("/api/v1/admin/users")
                .param("size", "101")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));

        mockMvc.perform(get("/api/v1/admin/users")
                .param("status", "LOCKED")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));

        mockMvc.perform(get("/api/v1/admin/users")
                .param("createdAtFrom", "2026-05-18T10:00:00")
                .param("createdAtTo", "2026-05-17T10:00:00")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
    }

    private JsonNode register(String username, String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of(
                        "username", username,
                        "email", email,
                        "password", "Password123"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private long registerAndReturnUserId(String usernamePrefix) throws Exception {
        String username = nextUsername(usernamePrefix);
        return register(username, nextEmail(username)).path("id").asLong();
    }

    private int registerAndReturnStatus(String username, String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of(
                        "username", username,
                        "email", email,
                        "password", "Password123"))))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private String loginAndExtractToken(String usernameOrEmail, String password) throws Exception {
        return loginAndReturnData(usernameOrEmail, password)
                .path("accessToken")
                .asText();
    }

    private JsonNode loginAndReturnData(String usernameOrEmail, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of(
                        "usernameOrEmail", usernameOrEmail,
                        "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data");
    }

    private long countAuthSessions() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM auth_sessions", Long.class);
    }

    private long countAuthSessionsByUserId(long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_sessions WHERE user_id = ?",
                Long.class,
                userId
        );
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

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private String nextUsername(String prefix) {
        return prefix + SEQUENCE.incrementAndGet();
    }

    private String nextEmail(String username) {
        return username + "@example.com";
    }
}
