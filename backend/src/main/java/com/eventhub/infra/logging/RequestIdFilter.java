package com.eventhub.infra.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 为每个请求注入 requestId，并同步写入 MDC 与响应头。
 * 这样日志、统一响应体和外部调用方都能拿到同一个请求标识，方便排查问题。
 * 该过滤器位于请求进入业务链路的较早阶段，负责统一处理 requestId 的接收、校验、生成和清理，
 * 避免控制器、服务层和异常处理器各自维护追踪标识。
 */
@Component
/*
 * Spring Security 的外层代理过滤器也注册在 Servlet 容器过滤器链中。
 * requestId 必须早于安全链路绑定，才能覆盖未认证、无权限等在进入 Controller 前就返回的失败响应。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    /**
     * 外部调用方传入或服务端回写的请求追踪头名称。
     * 如果上游系统已经生成了合法 requestId，则优先复用该请求头的值，方便跨系统串联日志。
     */
    public static final String HEADER_NAME = "X-Request-Id";

    /**
     * MDC 中保存 requestId 时使用的 key。
     * 统一响应体、日志框架布局和其他基础设施组件都可以通过该 key 读取当前请求标识。
     */
    public static final String MDC_KEY = "requestId";

    /**
     * requestId 合法性校验规则。
     * 规则要求首字符必须是字母或数字，后续字符允许字母、数字、点、下划线和短横线，
     * 总长度最多 64 个字符，用于降低日志注入、格式污染和超长请求头带来的风险。
     */
    private static final Pattern REQUEST_ID_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$");

    /**
     * 为当前请求绑定 requestId，并在请求处理结束后清理 MDC。
     * 处理顺序如下：
     * 1. 优先尝试复用调用方传入的请求头；
     * 2. 如果请求头缺失或不合法，则生成新的 requestId；
     * 3. 将 requestId 同步写入 MDC 和响应头；
     * 4. 放行后续过滤器链与业务处理；
     * 5. 在 finally 中清理 MDC，避免线程复用时发生 requestId 串用。
     *
     * @param request 当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param filterChain 过滤器链
     * @throws ServletException Servlet 过滤器处理异常
     * @throws IOException IO 异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = request.getHeader(HEADER_NAME);
        if (!isValidRequestId(requestId)) {
            // 对缺失或不安全的外部 requestId 统一重建，避免污染日志和响应头。
            requestId = UUID.randomUUID().toString().replace("-", "");
        }

        // 将 requestId 放入 MDC，确保同一次请求的日志和统一响应体都能读取到同一个追踪标识。
        MDC.put(MDC_KEY, requestId);
        // 无论 requestId 是复用还是重建，都统一回写到响应头，便于调用方和排障人员获取。
        response.setHeader(HEADER_NAME, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Web 容器通常会复用线程，这里必须清理 MDC，避免后续请求误用上一条请求的 requestId。
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * 判断外部传入的 requestId 是否满足平台约束。
     *
     * @param requestId 调用方传入的请求标识
     * @return true 表示可以直接复用；false 表示需要由服务端重新生成
     */
    private boolean isValidRequestId(String requestId) {
        return requestId != null && REQUEST_ID_PATTERN.matcher(requestId).matches();
    }
}
