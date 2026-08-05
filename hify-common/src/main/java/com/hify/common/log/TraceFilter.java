package com.hify.common.log;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 请求链路追踪过滤器——为每个 API 请求生成 traceId，通过 SLF4J MDC 贯穿所有日志。
 *
 * <p>对应规范 {@code docs/plan/plan-bizBase-1.0.md §14}。</p>
 *
 * <h3>功能</h3>
 * <ul>
 *   <li><b>traceId 生成</b>：UUID 前 8 位，可读性优先（如 {@code "a3f2b1c0"}）</li>
 *   <li><b>MDC 注入</b>：{@link MDC#put(String, String) MDC.put("traceId", ...)}，
 *       配合 logback pattern {@code %X{traceId}} 输出到每行日志</li>
 *   <li><b>响应头透传</b>：{@code X-Trace-Id} 头返回给前端，用户反馈问题时提供</li>
 *   <li><b>清理</b>：请求结束后 {@link MDC#remove(String) MDC.remove("traceId")}，
 *       防止线程池复用时的串号</li>
 * </ul>
 *
 * <h3>覆盖范围</h3>
 * 拦截路径 {@code /api/**}（对齐项目 API 前缀）。Actuator 和静态资源不拦截。
 *
 * <h3>与 LogRecord 关联</h3>
 * 业务代码通过 {@code MDC.get("traceId")} 获取当前请求的 traceId，
 * 填入 {@link LogRecord#setTraceId(String)}，实现调用日志与系统日志的串联。
 *
 * <h3>优先级</h3>
 * {@link Ordered#HIGHEST_PRECEDENCE} —— 确保 traceId 早于任何业务逻辑注入。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceFilter extends OncePerRequestFilter {

    /** MDC key 常量，供其他模块引用。 */
    public static final String MDC_KEY = "traceId";

    /** 响应头名称，前端通过此头获取 traceId。 */
    public static final String RESPONSE_HEADER = "X-Trace-Id";

    /** 拦截路径前缀。 */
    private static final String API_PATH_PREFIX = "/api/";

    /** traceId 长度（UUID 前 8 位） */
    private static final int TRACE_ID_LENGTH = 8;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith(API_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String traceId = generateTraceId();
        try {
            MDC.put(MDC_KEY, traceId);
            response.setHeader(RESPONSE_HEADER, traceId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * 生成本次请求的 traceId。
     * UUID 前 8 位，足够在当天范围内唯一定位一个请求。
     */
    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, TRACE_ID_LENGTH);
    }
}
