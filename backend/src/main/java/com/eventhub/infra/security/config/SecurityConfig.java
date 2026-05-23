package com.eventhub.infra.security.config;

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

import com.eventhub.infra.security.jwt.JwtAuthenticationFilter;
import com.eventhub.infra.security.jwt.JwtProperties;

/**
 * Spring Security 全局配置。
 *
 * <p>
 * 本项目采用前后端分离的无状态 JWT 认证：登录成功后服务端签发 access token，
 * 后续请求通过 Authorization Bearer Header 携带 token，不使用服务端 Session 保存登录态。
 * </p>
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
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
                             */
                            .requestMatchers(HttpMethod.GET, "/api/v1/admin/users").hasRole("ADMIN")
                            .requestMatchers(HttpMethod.PATCH, "/api/v1/admin/users/*/status").hasRole("ADMIN")
                            .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                            .anyRequest().authenticated();
                })
                /*
                 * JWT Filter 必须位于 UsernamePasswordAuthenticationFilter 之前，
                 * 这样后续授权规则才能看到已经写入 SecurityContext 的当前用户。
                 */
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 显式关闭 Spring Boot 默认内存用户。
     *
     * <p>
     * 本项目认证入口是自定义注册/登录接口与 JWT Filter，不使用默认表单登录或 Basic 用户。
     * 如果某处误用了默认 {@link UserDetailsService}，这里会明确失败，避免悄悄走到内存用户分支。
     * </p>
     *
     * @return 不提供默认用户的 UserDetailsService
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("Default in-memory user is disabled: " + username);
        };
    }
}
