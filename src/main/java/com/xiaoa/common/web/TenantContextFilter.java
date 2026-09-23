package com.xiaoa.common.web;

import com.xiaoa.common.context.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 初始化租户上下文。
 *
 * 当前阶段支持通过请求头联调；正式登录态接入后，由认证过滤器在进入业务前写入同一个上下文。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TenantContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantContextFilter.class);

    @Value("${xiaoa.tenant.header-name:X-Tenant-Id}")
    private String tenantHeaderName;

    @Value("${xiaoa.tenant.header-enabled:true}")
    private boolean tenantHeaderEnabled;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            if (tenantHeaderEnabled) {
                String tenantHeader = request.getHeader(tenantHeaderName);
                if (StringUtils.hasText(tenantHeader)) {
                    TenantContext.setTenantId(Long.parseLong(tenantHeader));
                }
            }
            filterChain.doFilter(request, response);
        } catch (NumberFormatException exception) {
            log.warn("Invalid tenant header: {}", request.getHeader(tenantHeaderName));
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "租户标识格式错误");
        } finally {
            TenantContext.clear();
        }
    }
}
