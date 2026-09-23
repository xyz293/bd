package com.xiaoa.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 统一处理浏览器跨域请求，包括预检请求。
 *
 * <p>允许的来源通过 {@code xiaoa.cors.allowed-origins} 配置，不能使用通配符，
 * 避免开启凭证后将接口暴露给任意站点。</p>
 */
@Configuration
@ConditionalOnProperty(name = "xiaoa.cors.enabled", havingValue = "true", matchIfMissing = true)
public class CorsConfig {

    @Value("${xiaoa.cors.allowed-origins:http://localhost:3000,http://localhost:5173,http://127.0.0.1:3000,http://127.0.0.1:5173}")
    private String allowedOrigins;

    @Value("${xiaoa.cors.allow-credentials:true}")
    private boolean allowCredentials;

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public CorsFilter corsFilter() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(parseAllowedOrigins());
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept", "Origin",
                "X-Tenant-Id", "X-Requested-With"));
        configuration.setExposedHeaders(Collections.singletonList("Content-Disposition"));
        configuration.setAllowCredentials(allowCredentials);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return new CorsFilter(source);
    }

    private List<String> parseAllowedOrigins() {
        if (!StringUtils.hasText(allowedOrigins)) {
            return Collections.emptyList();
        }
        return Arrays.stream(allowedOrigins.split(","))
                .map(origin -> origin.trim())
                .filter(origin -> StringUtils.hasText(origin))
                .filter(origin -> !"*".equals(origin))
                .collect(Collectors.toList());
    }
}
