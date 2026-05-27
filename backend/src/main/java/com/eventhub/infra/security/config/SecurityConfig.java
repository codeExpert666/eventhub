package com.eventhub.infra.security.config;

import com.eventhub.infra.security.jwt.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 全局配置。
 *
 * <p>
 * 本项目采用前后端分离的无状态 JWT 认证：登录成功后服务端签发 access token，
 * 后续请求通过 Authorization Bearer Header 携带 token，不使用 Servlet HTTP Session 保存认证上下文。
 * </p>
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(AuthTokenProperties.class)
public class SecurityConfig {

    /**
     * 配置 HTTP 安全过滤器链。
     *
     * @param http                     Spring Security HTTP 配置入口
     * @param jwtAuthenticationFilter  JWT 认证过滤器
     * @param authenticationEntryPoint 认证失败处理入口
     * @param accessDeniedHandler      权限不足处理器
     * @param apiDocsEnabled           通过 {@code @Value("${springdoc.api-docs.enabled:true}")}
     *                                 从配置环境读取 OpenAPI JSON 端点开关；冒号后的 {@code true}
     *                                 表示配置缺失时的默认值，Spring 会自动转换为 boolean
     * @param swaggerUiEnabled         通过 {@code @Value("${springdoc.swagger-ui.enabled:true}")}
     *                                 从配置环境读取 Swagger UI 页面开关；该值会决定 Swagger 页面路径
     *                                 是否被匿名放行
     * @return 安全过滤器链
     * @throws Exception Spring Security 构建异常
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            AuthenticationEntryPoint authenticationEntryPoint,
            AccessDeniedHandler accessDeniedHandler,
            /*
             * @Value 用于读取 application.yml、profile 配置或环境变量中的简单配置项。
             * 这里使用“${配置项:默认值}”形式，让开发环境默认开放接口文档；
             * 如果 prod profile 将 springdoc 对应开关设为 false，则下面不会注册文档路径的 permitAll 规则，
             * 文档端点会继续落到 anyRequest().authenticated() 的默认认证边界内。
             */
            @Value("${springdoc.api-docs.enabled:true}") boolean apiDocsEnabled,
            @Value("${springdoc.swagger-ui.enabled:true}") boolean swaggerUiEnabled) throws Exception {
        http
                /*
                 * 本项目使用 Authorization Header 传递 JWT，不依赖浏览器 Cookie + Session 登录态，
                 * 因此关闭 CSRF，避免前后端分离接口被要求额外提交 CSRF token。
                 */
                .csrf(AbstractHttpConfigurer::disable)
                /*
                 * 无状态会话：Spring Security 不创建也不读取 HttpSession 保存认证信息。
                 * 每个请求都必须依赖本次请求携带的 JWT 建立当前用户上下文。
                 */
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                /*
                 * 授权规则按声明顺序从上到下匹配，命中第一条规则后即停止继续匹配。
                 */
                .authorizeHttpRequests(authorize -> {
                    authorize
                            // 注册和登录必须公开，否则用户无法获得 token。
                            .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                            // 系统基础接口逐个声明公开方法，避免未来新增写接口被路径通配意外放行。
                            .requestMatchers(HttpMethod.GET, "/api/v1/system/ping").permitAll()
                            .requestMatchers(HttpMethod.POST, "/api/v1/system/echo").permitAll()
                            // 基础设施探活和应用信息端点。
                            .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/info").permitAll()
                            .requestMatchers(HttpMethod.HEAD, "/actuator/health", "/actuator/info").permitAll()
                            .requestMatchers(HttpMethod.GET, "/favicon.ico").permitAll();

                    /*
                     * OpenAPI / Swagger UI 只在 springdoc 对应能力启用时公开放行。
                     * prod profile 会关闭这些开关，因此文档路径不会再绕过默认认证边界。
                     */
                    if (apiDocsEnabled) {
                        authorize.requestMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs/**").permitAll();
                    }
                    if (swaggerUiEnabled) {
                        authorize.requestMatchers(HttpMethod.GET, "/swagger-ui.html", "/swagger-ui/**").permitAll();
                    }

                    authorize
                            // 当前登录用户相关接口。
                            .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").authenticated()
                            .requestMatchers(HttpMethod.GET, "/api/v1/me").authenticated()
                            /*
                             * 管理端接口采用 URL 规则和 @PreAuthorize 双层保护。
                             * hasRole("ADMIN") 会匹配 ROLE_ADMIN，因此角色加载时会统一补 ROLE_ 前缀。
                             *
                             * 路径通配符说明：
                             * - * 匹配且必须匹配一层路径片段，可放在路径末尾，例如 /users/*；
                             * - ** 匹配零层或多层后续路径，建议只放在末尾作为前缀兜底，例如 /admin/**。
                             */
                            .requestMatchers(HttpMethod.GET, "/api/v1/admin/users").hasRole("ADMIN")
                            .requestMatchers(HttpMethod.PATCH, "/api/v1/admin/users/*/status").hasRole("ADMIN")
                            .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                            .anyRequest().authenticated();
                })
                /*
                 * UsernamePasswordAuthenticationFilter 是 Spring Security 表单登录的标准过滤器位置。
                 * 当前项目未启用 formLogin，它在这里仅作为顺序锚点：JWT Filter 先解析 Bearer token
                 * 并写入 SecurityContext，后续授权规则才能识别当前用户。
                 */
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 阻止 Spring Boot 创建默认内存用户。
     *
     * <p>
     * {@link UserDetailsService} 通常用于表单登录、HTTP Basic 或 DaoAuthenticationProvider：
     * Spring Security 会通过它按用户名加载用户、密码哈希和权限信息。本项目不走这条认证链，
     * 而是由自定义登录接口校验密码并签发 JWT，后续请求再由 {@link JwtAuthenticationFilter}
     * 根据 Bearer token 建立 {@code SecurityContext}。
     * </p>
     *
     * <p>
     * Spring Boot 不会扫描项目里是否存在登录接口或用户表；它只会根据 Spring 容器中是否已有
     * {@link UserDetailsService}、AuthenticationProvider、AuthenticationManager 等认证相关 Bean
     * 来决定是否自动创建默认内存用户。因此这里声明一个不会返回用户的占位实现：
     * 既让默认内存用户自动配置失效，也能在未来有人误接入默认认证链时快速失败。
     * </p>
     *
     * @return 始终抛出 {@link UsernameNotFoundException} 的占位 UserDetailsService
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("Default in-memory user is disabled: " + username);
        };
    }
}
