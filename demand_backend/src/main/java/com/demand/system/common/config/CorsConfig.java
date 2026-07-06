package com.demand.system.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Configuration
public class CorsConfig {

    /**
     * 允许的跨域来源，多个用逗号分隔。
     * 生产环境必须配置具体域名，不可使用通配符。
     * 通过 application-{profile}.yml 的 cors.allowed-origins 配置。
     * 开发环境使用 allowedOriginPatterns 支持通配，防止 "Invalid CORS request"。
     */
    @Value("${cors.allowed-origins:http://localhost:5170,http://127.0.0.1:5170}")
    private String allowedOrigins;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        // 注意: setAllowCredentials(true) 后 addAllowedOrigin("http://...") 会被浏览器拒绝，
        // 必须使用 addAllowedOriginPattern 来配合凭证模式。
        config.setAllowCredentials(true);

        // 从配置读取白名单，支持多个来源（逗号分隔）
        for (String origin : allowedOrigins.split(",")) {
            String trimmed = origin.trim();
            if (!trimmed.isEmpty()) {
                config.addAllowedOriginPattern(trimmed);
            }
        }

        config.addAllowedHeader("*");
        config.addAllowedMethod("*");

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
