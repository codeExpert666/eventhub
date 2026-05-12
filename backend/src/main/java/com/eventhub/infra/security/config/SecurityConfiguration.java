package com.eventhub.infra.security.config;

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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.eventhub.infra.jwt.config.JwtProperties;
import com.eventhub.infra.security.filter.JwtAuthenticationFilter;

/**
 * Spring Security 全局配置。
 *
 * <p>
 * 如果你是第一次接触 Spring Security，可以把这个类理解成“安全规则总入口”：
 * 所有进入后端的 HTTP 请求，都会先经过 Spring Security 构建出的过滤器链，
 * 再决定请求是否可以进入 Controller。
 * </p>
 *
 * <p>
 * 本项目阶段 1 采用无状态 JWT 认证：
 * 登录成功后服务端只签发 Token，不在服务端保存 Session；
 * 后续请求需要在请求头中携带 Token，JWT 过滤器负责解析 Token 并设置当前登录用户。
 * </p>
 *
 * <p>
 * 认证失败和授权失败都交给自定义处理器返回统一 ApiResponse，
 * 避免默认的登录页、重定向或 HTML 错误响应影响前后端 API 对接。
 * </p>
 */
// 声明这是一个配置类，Spring 启动时会扫描并加载其中的 @Bean。
@Configuration
// 开启方法级权限控制，例如后续可在 Service/Controller 方法上使用 @PreAuthorize。
@EnableMethodSecurity
// 让 JwtProperties 中通过 @ConfigurationProperties 定义的 JWT 配置可以被 Spring 管理和注入。
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfiguration {

    /**
     * 配置 HTTP 安全链路。
     *
     * <p>
     * Spring Security 的核心不是单个拦截器，而是一组有顺序的 Filter。
     * 这个方法最终返回的 {@link SecurityFilterChain} 就是本项目的 HTTP 安全过滤器链。
     * </p>
     *
     * <p>
     * 这里以方法参数注入安全组件，而不是配置类构造器注入，
     * 核心目的不是“消灭依赖链”，而是让 SecurityConfiguration 这个配置类本身保持轻量：
     * 它只负责声明如何组装安全过滤器链，不提前持有 JWT Filter、异常处理器等安全组件。
     * </p>
     *
     * <p>
     * 这样做会把依赖约束限定在创建 SecurityFilterChain 这个 Bean 的过程中：
     * Spring 可以先创建并解析配置类，再在调用本方法时解析方法参数需要的 Bean。
     * 因此配置类不容易过早卷入 JWT Filter、业务 Service、配置属性等复杂依赖链，
     * 可以降低循环依赖出现的概率。
     * </p>
     *
     * <p>
     * 需要注意：如果 JWT Filter、JwtTokenProvider、业务 Service 或 SecurityFilterChain
     * 之间已经形成真实闭环，方法参数注入也不能解决，仍需要重新拆分职责或调整依赖方向。
     * </p>
     *
     * @param http                     Spring Security HTTP 配置入口
     * @param jwtAuthenticationFilter  JWT 认证过滤器，负责从请求中解析 Token 并写入 SecurityContext
     * @param authenticationEntryPoint 认证失败入口，注册给 Spring Security 框架流程处理 401 场景
     * @param accessDeniedHandler      授权失败处理器，处理“已登录但权限不足”的 403 场景
     * @return 安全过滤器链
     * @throws Exception Spring Security 构建异常
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            AuthenticationEntryPoint authenticationEntryPoint,
            AccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                /*
                 * 关闭 CSRF 防护。
                 *
                 * CSRF 主要防护“浏览器自动携带 Cookie 导致的跨站请求伪造”。
                 * 本项目的 API 使用 Authorization Header 携带 JWT，且不依赖服务端 Session/Cookie 登录态，
                 * 所以这里关闭 CSRF，避免对前后端分离接口造成额外表单令牌要求。
                 */
                .csrf(AbstractHttpConfigurer::disable)
                /*
                 * 设置会话策略为 STATELESS。
                 *
                 * STATELESS 表示 Spring Security 不创建也不使用 HttpSession 保存认证信息。
                 * 每次请求是否登录，完全由请求中携带的 JWT 决定。
                 */
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                /*
                 * 配置安全异常处理。
                 *
                 * authenticationEntryPoint：注册给 Spring Security 框架使用。
                 * 典型场景是请求没有携带 Token，JWT 过滤器直接放行后，
                 * 后续授权规则发现该接口需要登录，于是由 Spring Security 调用它返回 401。
                 *
                 * accessDeniedHandler：请求已经识别出用户，但用户角色/权限不足时触发。
                 */
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                /*
                 * 配置接口访问规则。
                 *
                 * 规则从上到下匹配，越具体的规则越应该放在前面。
                 * 未声明 HttpMethod 的 requestMatchers 会匹配所有请求方法，因此公开接口尽量显式声明方法，
                 * 避免未来新增写接口时被路径通配规则意外放行。
                 *
                 * permitAll 表示不需要登录即可访问；
                 * authenticated 表示只要登录即可访问；
                 * hasRole("ADMIN") 表示需要 ADMIN 角色，Spring Security 会自动匹配 ROLE_ADMIN。
                 */
                .authorizeHttpRequests(authorize -> authorize
                        // 注册和登录接口必须开放，否则用户无法获取 JWT。
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        // 系统基础接口逐个声明方法，避免 /api/v1/system/** 下未来新增接口被默认公开。
                        .requestMatchers(HttpMethod.GET, "/api/v1/system/ping").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/system/echo").permitAll()
                        // Actuator 健康和基础信息接口开放给部署平台或监控系统探活。
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/info").permitAll()
                        // 部分基础设施或代理可能使用 HEAD 探测端点，因此显式开放健康和信息端点的 HEAD 请求。
                        .requestMatchers(HttpMethod.HEAD, "/actuator/health", "/actuator/info").permitAll()
                        // OpenAPI/Swagger 文档接口开放，方便开发阶段查看和调试 API。
                        .requestMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs/**",
                                "/swagger-ui.html", "/swagger-ui/**")
                        .permitAll()
                        // 浏览器会自动请求 favicon.ico，放行可以避免无意义的 401 日志。
                        .requestMatchers(HttpMethod.GET, "/favicon.ico").permitAll()
                        // 已登录用户接口明确声明，便于从配置中直接看出认证边界。
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/me").authenticated()
                        /*
                         * 当前已存在的管理端接口逐个声明请求方法，并要求 ADMIN 角色。
                         * 注意：hasRole("ADMIN") 内部会自动补 ROLE_ 前缀，
                         * 因此用户权限数据中通常应保存或映射为 ROLE_ADMIN。
                         */
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/users").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/admin/users/*/status").hasRole("ADMIN")
                        /*
                         * 管理端命名空间兜底保护。
                         * 这里故意不限制请求方法：未来新增 /api/v1/admin/** 接口时，
                         * 即使忘记补充上面的精确规则，也不会退化成普通登录用户可访问。
                         */
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        // 兜底规则：除上面显式放行或指定角色的接口外，其他接口都必须先登录。
                        .anyRequest().authenticated())
                /*
                 * 将 JWT 认证过滤器放在 UsernamePasswordAuthenticationFilter 之前。
                 *
                 * UsernamePasswordAuthenticationFilter 是 Spring Security 默认处理表单登录的过滤器。
                 * 本项目不使用默认表单登录，但仍借用它在过滤器链中的位置：
                 * 先让 JWT 过滤器尝试识别当前用户，后续授权规则才能基于当前用户身份判断是否放行。
                 */
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        /*
         * build() 会根据上面的配置生成真正的 SecurityFilterChain Bean。
         * Spring Security 在运行时会用这条链处理匹配到的 HTTP 请求。
         */
        return http.build();
    }

    /**
     * BCrypt 密码编码器。
     *
     * <p>
     * 不要在数据库中保存明文密码。
     * PasswordEncoder 负责把用户输入的原始密码转换成不可逆的哈希值，
     * 注册或修改密码时使用 encode 生成密码哈希，登录时使用 matches 校验原始密码是否匹配已保存哈希。
     * </p>
     *
     * <p>
     * BCrypt 会为每次加密生成随机盐，
     * 即使两个用户使用相同密码，最终存储的哈希值也不同，可以降低彩虹表攻击风险。
     * </p>
     *
     * <p>
     * 因为 encode 每次都会生成新盐，所以登录时不能用“再次 encode 后直接字符串相等”的方式判断密码。
     * BCrypt 的完整哈希字符串里已经包含算法版本、cost 参数和盐；
     * matches 会从数据库中保存的哈希里解析出这些信息，
     * 再用用户本次输入的原始密码重新计算并比较结果。
     * </p>
     *
     * @return 密码编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 显式关闭 Spring Boot 默认内存用户。
     *
     * <p>
     * 如果项目引入了 Spring Security，但没有提供 UserDetailsService，
     * Spring Boot 可能会创建一个默认内存用户并在启动日志中打印随机密码。
     * 这对学习 Spring Security 很方便，但不适合作为本项目真实认证链路的一部分。
     * </p>
     *
     * <p>
     * 本项目阶段 1 的认证入口是自定义注册/登录接口与 JWT Filter，
     * 不使用默认表单登录或 Basic 用户。
     * 因此这里提供一个始终抛出异常的 UserDetailsService，
     * 用来明确表达：默认内存用户被禁用，用户身份应来自业务用户体系。
     * </p>
     *
     * @return 不提供任何默认用户的 UserDetailsService
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            // 如果某处误用了默认 UserDetailsService，会明确失败，避免悄悄走到内存用户认证分支。
            throw new UsernameNotFoundException("Default in-memory user is disabled: " + username);
        };
    }
}
