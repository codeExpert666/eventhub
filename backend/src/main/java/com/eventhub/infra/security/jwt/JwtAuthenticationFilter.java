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

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtCodec jwtCodec;
    private final AuthenticatedPrincipalService authenticatedPrincipalService;
    private final AuthenticationEntryPoint authenticationEntryPoint;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String token = resolveBearerToken(request);
        if (token == null) {
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

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    toGrantedAuthorities(principal)
            );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);

            filterChain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException | AuthenticationException exception) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(
                    request,
                    response,
                    new BadCredentialsException("Invalid JWT access token", exception)
            );
        }
    }

    private String resolveBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    private List<SimpleGrantedAuthority> toGrantedAuthorities(AuthenticatedPrincipal principal) {
        return principal.authorities()
                .stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
    }
}
