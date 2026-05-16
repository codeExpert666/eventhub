package com.eventhub.infra.security.filter;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.eventhub.common.security.AuthenticatedSubject;
import com.eventhub.common.security.AuthenticatedSubjectLoader;
import com.eventhub.infra.jwt.JwtTokenProvider;
import com.eventhub.infra.jwt.model.AccessTokenClaims;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * JWT 认证过滤器。
 *
 * <p>
 * 在无状态 JWT 认证模式下，服务端不会用 HttpSession 保存“当前登录用户”。
 * 客户端登录成功后会拿到 access token，之后每次请求都需要在 HTTP Header 中携带：
 * </p>
 *
 * <pre>
 * Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
 * </pre>
 *
 * <p>
 * 这个过滤器的职责就是在请求进入 Controller 之前完成认证：
 * 读取 Authorization Bearer token，校验 JWT，加载最新用户状态和权限，
 * 最后把认证结果写入 Spring Security 的 {@link SecurityContextHolder}。
 * 后续的授权判断，例如 hasRole("ADMIN") 或 authenticated()，
 * 都会基于 SecurityContext 中的 Authentication 来判断。
 * </p>
 *
 * <p>
 * 该类继承 {@link OncePerRequestFilter}，表示在一次 HTTP 请求处理过程中最多执行一次，
 * 避免请求转发、异常派发等场景导致同一个过滤器重复执行。
 * </p>
 */
@Component
// Lombok 会为 final 字段生成构造器，Spring 通过构造器注入下面三个依赖。
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /**
     * Authorization Header 中 Bearer Token 的固定前缀。
     *
     * <p>
     * 例如完整请求头是 {@code Authorization: Bearer xxx.yyy.zzz}，
     * 真正需要交给 JwtTokenProvider 解析的是后面的 {@code xxx.yyy.zzz}。
     * </p>
     */
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * JWT 签发与解析组件。
     *
     * <p>
     * 它只负责密码学层面的校验，例如签名是否合法、token 是否过期、issuer 是否匹配，
     * 不负责查询数据库或操作 Spring Security 上下文。
     * </p>
     */
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 当前登录主体加载器。
     *
     * <p>
     * JWT 中只保存最小身份标识，例如用户 ID。
     * 即使 token 本身合法，也需要重新加载数据库中的用户状态和权限，
     * 这样账号被禁用、角色被调整后，可以在下一次请求时立即生效。
     * </p>
     */
    private final AuthenticatedSubjectLoader authenticatedSubjectLoader;

    /**
     * 认证失败处理入口。
     *
     * <p>
     * JWT 无效、过期、用户不存在或用户被禁用时，请求还没有进入 Controller，
     * 不能依赖全局异常处理器统一包装响应。
     * 因此这里直接委托 AuthenticationEntryPoint 写出统一的 401 ApiResponse。
     * </p>
     *
     * <p>
     * SecurityConfiguration 中也会注入同一个 AuthenticationEntryPoint，
     * 但那是注册给 Spring Security 框架的异常处理流程使用；
     * 这里注入它，是为了在自定义 JWT 过滤器内部已经捕获到认证失败时主动复用同一套 401 响应逻辑。
     * </p>
     */
    private final AuthenticationEntryPoint authenticationEntryPoint;

    /**
     * 执行单次请求的 JWT 认证逻辑。
     *
     * <p>
     * 整体流程：
     * </p>
     * <ol>
     * <li>从 Authorization Header 中提取 Bearer Token。</li>
     * <li>如果没有 Token，直接放行给后续过滤器，由后续授权规则决定是否需要登录。</li>
     * <li>如果有 Token，先解析并校验 JWT。</li>
     * <li>根据 JWT 中的 subjectId 加载最新用户状态和权限。</li>
     * <li>构造 Spring Security 认识的 Authentication，并写入 SecurityContext。</li>
     * <li>如果中途失败，清空上下文并返回 401。</li>
     * </ol>
     *
     * @param request     当前 HTTP 请求
     * @param response    当前 HTTP 响应
     * @param filterChain 后续过滤器链
     * @throws ServletException Servlet 处理异常
     * @throws IOException      IO 异常
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        /*
         * 1. 尝试从请求头中解析 Bearer Token。
         *
         * 这里没有 token 不一定代表“立刻失败”：
         * - 登录、注册、健康检查、Swagger 等公开接口本来就不需要 token；
         * - 受保护接口如果没有 token，会在后续授权阶段触发 SecurityConfiguration 中注册的
         * AuthenticationEntryPoint 返回 401。
         */
        String token = resolveBearerToken(request);
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            /*
             * 2. 校验 JWT，并取出 token 中保存的最小身份声明。
             *
             * parseAccessToken 会校验签名、过期时间、issuer 等信息。
             * 如果 token 被篡改、过期或签发方不匹配，JJWT 会抛出 JwtException。
             */
            AccessTokenClaims claims = jwtTokenProvider.parseAccessToken(token);

            /*
             * 3. 根据 JWT 中的 subjectId 加载当前用户。
             *
             * 这里没有完全信任 token 中的信息，而是重新查询用户状态和权限：
             * - 用户被删除或不存在：认证失败；
             * - 用户被禁用：认证失败；
             * - 用户角色变化：使用最新角色参与本次授权判断。
             */
            AuthenticatedSubject subject = authenticatedSubjectLoader.loadBySubjectId(claims.subjectId());

            /*
             * 4. 构造 Spring Security 的认证对象。
             *
             * UsernamePasswordAuthenticationToken 这个名字容易误导：
             * 它不仅能表示“用户名密码登录请求”，也常用于表示一个已经认证成功的用户。
             *
             * 三个参数分别是：
             * - principal：当前登录主体，这里放项目自定义的 AuthenticatedSubject；
             * - credentials：凭证。JWT 已经校验完成，不需要再保存密码或 token，因此传 null；
             * - authorities：当前用户权限，例如 ROLE_USER、ROLE_ADMIN。
             *
             * 需要注意 UsernamePasswordAuthenticationToken 的两个常见构造语义：
             * - 两参构造器通常表示“待认证的登录请求”，isAuthenticated() 默认为 false；
             * - 三参构造器通常表示“已经认证成功的用户身份”，会把 isAuthenticated() 置为 true。
             *
             * 因此前面完成 JWT 校验、用户状态校验和权限加载后，这里使用带 authorities 的三参构造器，
             * 等价于告诉 Spring Security：当前请求已经完成“你是谁”的认证判断。
             * 后续 authenticated()、hasRole(...)、@PreAuthorize(...) 等授权规则，会继续基于
             * SecurityContext 中这个已认证 Authentication 及其 authorities 判断“你能不能访问”。
             */
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    subject,
                    null,
                    toGrantedAuthorities(subject)
            );

            /*
             * 5. 保存请求来源等细节信息。
             *
             * WebAuthenticationDetailsSource 通常会记录 remoteAddress、sessionId 等 Web 请求信息。
             * 本项目是无状态 JWT，不依赖 sessionId，但保留 details 有助于后续审计或排查问题。
             */
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            /*
             * 6. 把认证结果写入 SecurityContext。
             *
             * SecurityContextHolder 默认使用 ThreadLocal 保存当前请求线程的认证信息。
             * 后续过滤器、Controller、@PreAuthorize、hasRole 等都可以从这里读取当前用户身份。
             */
            SecurityContextHolder.getContext().setAuthentication(authentication);

            /*
             * 7. 认证成功后继续执行后续过滤器链。
             *
             * 注意：这里“认证成功”只代表确认了“你是谁”；
             * 具体“你能不能访问某个接口”，还要交给后续授权规则判断。
             */
            filterChain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException | AuthenticationException exception) {
            /*
             * 8. 认证失败时清空 SecurityContext。
             *
             * 虽然本次请求通常还没有写入成功的 Authentication，
             * 但失败分支主动清空上下文可以避免异常情况下残留错误身份信息。
             */
            SecurityContextHolder.clearContext();

            /*
             * 9. 委托统一认证失败入口返回 401。
             *
             * 这里包装成 BadCredentialsException，是为了把底层 JWT 异常、用户状态异常等
             * 统一表达为“当前请求无法建立有效登录态”。
             *
             * 注意：这和 SecurityConfiguration 中注册 AuthenticationEntryPoint 并不矛盾。
             * SecurityConfiguration 负责告诉 Spring Security“框架流程遇到未认证时怎么响应”；
             * 当前过滤器已经在自己的 try-catch 中捕获到 JWT 认证失败，
             * 所以需要主动调用同一个 AuthenticationEntryPoint，避免异常继续向外抛出后变成不统一的错误响应。
             */
            authenticationEntryPoint.commence(
                    request,
                    response,
                    new BadCredentialsException("Invalid JWT access token", exception)
            );
        }
    }

    /**
     * 从请求头中提取 Bearer Token。
     *
     * <p>
     * 本项目约定客户端把 JWT 放在 Authorization Header 中，
     * 格式必须是 {@code Bearer <token>}。
     * 如果 Header 不存在、前缀不匹配或 token 内容为空，就返回 null。
     * </p>
     *
     * @param request 当前 HTTP 请求
     * @return JWT 字符串；没有有效 Bearer Token 时返回 null
     */
    private String resolveBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }

        /*
         * 去掉 "Bearer " 前缀并 trim：
         * - substring(BEARER_PREFIX.length()) 取出真正的 token；
         * - trim() 容忍前端多传的首尾空格。
         */
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    /**
     * 把项目自己的权限字符串转换成 Spring Security 认识的 GrantedAuthority。
     *
     * <p>
     * Spring Security 的 hasRole("ADMIN") 底层会匹配 {@code ROLE_ADMIN} 权限。
     * AuthenticatedSubjectService 已经负责把角色码转换为 ROLE_ 前缀形式，
     * 这里再包装成 {@link SimpleGrantedAuthority}，供 Spring Security 授权判断使用。
     * </p>
     *
     * @param subject 当前认证主体
     * @return Spring Security 权限列表
     */
    private List<SimpleGrantedAuthority> toGrantedAuthorities(AuthenticatedSubject subject) {
        return subject.authorities()
                .stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
    }
}
