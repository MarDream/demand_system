package com.demand.system.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * B1: 统一访问日志过滤器
 * - 请求进入时生成 8 位 traceId 并写入 MDC，确保本次请求内所有业务日志（含异常）都带同一 traceId
 * - 请求结束时记录 method / uri / status / 耗时 / 客户端 IP / traceId，补齐后台响应日志缺口
 * - 置于最高优先级，早于 RateLimitFilter 等执行，使下游日志均携带 traceId
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AccessLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AccessLogFilter.class);
    private static final String TRACE_ID = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        // 文档/健康检查类接口噪音较大，跳过记录（但仍放行）；业务 API 一律记录
        String uri = request.getRequestURI();
        boolean skipLog = uri.startsWith("/actuator")
                || uri.startsWith("/v3/api-docs")
                || uri.startsWith("/swagger-ui");

        MDC.put(TRACE_ID, traceId);
        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long cost = System.currentTimeMillis() - start;
            if (!skipLog) {
                int status = response.getStatus();
                String clientIp = getClientIp(request);
                log.info("access | method={} uri={} status={} cost={}ms ip={} traceId={}",
                        request.getMethod(), uri, status, cost, clientIp, traceId);
            }
            MDC.remove(TRACE_ID);
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
