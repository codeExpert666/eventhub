package com.eventhub.infra.security.jwt;

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

import com.eventhub.infra.security.principal.AuthenticatedPrincipal;
import com.eventhub.modules.auth.security.AuthenticatedPrincipalService;

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
 * 该过滤器负责 HTTP 请求层的认证装配：
 * 从 Authorization Header 读取 Bearer token，调用 {@link JwtCodec} 完成 JWT 技术校验，
 * 再通过 auth 模块的 {@link AuthenticatedPrincipalService} 加载最新用户状态和权限，
 * 最后把认证结果写入 Spring Security 上下文。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /**
     * Authorization Header 中访问令牌的标准前缀。
     *
     * <p>这里只接受 Bearer token，避免把 Basic、Digest 等其他认证方案误判为 JWT。</p>
     */
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * JWT 编解码组件，负责校验签名、过期时间、token 类型等技术层面的可信性。
     */
    private final JwtCodec jwtCodec;

    /**
     * 认证主体加载服务，负责根据 JWT 中的用户标识读取数据库中的最新用户状态与权限。
     */
    private final AuthenticatedPrincipalService authenticatedPrincipalService;

    /**
     * Spring Security 认证失败入口，统一输出 401 响应，保持未认证场景的错误格式一致。
     */
    private final AuthenticationEntryPoint authenticationEntryPoint;

    /**
     * 每个请求只执行一次的 JWT 认证入口。
     *
     * <p>
     * 过滤器只在请求携带 Bearer token 时尝试认证；没有 token 的请求继续交给后续过滤器，
     * 由 Spring Security 的授权规则判断该接口是否允许匿名访问。
     * </p>
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String token = resolveBearerToken(request);
        if (token == null) {
            // 未携带 Bearer token 不代表认证失败，可能访问的是公开接口或后续过滤器处理的认证方式。
            filterChain.doFilter(request, response);
            return;
        }

        try {
            /*
             * JwtCodec 只负责 token 本身是否可信；用户是否仍存在、是否禁用、拥有哪些角色，
             * 继续交给 AuthenticatedPrincipalService 查询数据库最新状态。
             */
            JwtClaims claims = jwtCodec.parseAccessToken(token);
            AuthenticatedPrincipal principal = authenticatedPrincipalService.loadByUserId(claims.subjectId());

            /*
             * Spring Security 使用 Authentication 保存当前请求的认证结果。
             * credentials 置为 null，避免在安全上下文中保留原始 token。
             */
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    toGrantedAuthorities(principal)
            );
            // details 保存请求来源等 Web 上下文信息，便于审计日志或后续安全扩展使用。
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);

            filterChain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException | AuthenticationException exception) {
            /*
             * token 无效、解析异常、用户状态异常都视为认证失败。
             * 先清理上下文，避免异常请求复用到残留认证信息，再交给统一入口返回 401。
             */
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(
                    request,
                    response,
                    new BadCredentialsException("Invalid JWT access token", exception)
            );
        }
    }

    /**
     * 从请求头中解析 Bearer token。
     *
     * @param request 当前 HTTP 请求
     * @return 去除 Bearer 前缀后的 token；如果请求未携带有效 Bearer token，则返回 {@code null}
     */
    private String resolveBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    /**
     * 将业务侧的权限字符串转换为 Spring Security 可识别的权限对象。
     *
     * @param principal 已认证用户主体
     * @return Spring Security 授权判断使用的权限集合
     */
    private List<SimpleGrantedAuthority> toGrantedAuthorities(AuthenticatedPrincipal principal) {
        return principal.authorities()
                .stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
    }
}
