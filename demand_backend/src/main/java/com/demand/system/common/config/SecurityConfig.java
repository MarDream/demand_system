package com.demand.system.common.config;

import com.demand.system.common.filter.RateLimitFilter;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.common.result.Result;
import com.demand.system.common.utils.JwtUtils;
import com.demand.system.module.auth.security.UserPrincipal;
import com.demand.system.module.rbac.support.RbacPermissionResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${jwt.secret}")
    private String jwtSecret;

    /**
     * S6 修复: 启动时校验 JWT Secret 长度，HMAC-SHA256 要求密钥至少 32 字节。
     * 避免运行期才出现 WeakKeyException 导致鉴权不可用。
     */
    @PostConstruct
    public void validateJwtSecret() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException("配置项 jwt.secret 不能为空");
        }
        int length = jwtSecret.getBytes(StandardCharsets.UTF_8).length;
        if (length < 32) {
            throw new IllegalStateException(
                "配置项 jwt.secret 长度不足: 当前 " + length + " 字节, HMAC-SHA256 要求至少 32 字节。"
                    + "请生成长度足够的随机字符串, 例如使用 openssl rand -base64 48");
        }
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(RbacPermissionResolver rbacPermissionResolver) {
        return new JwtAuthenticationFilter(jwtSecret, rbacPermissionResolver);
    }

    // ===== BUG-03 修复: 自定义 Security 异常 EntryPoint/AccessDeniedHandler 返回统一 JSON 而非空 body =====
    @Bean
    public AuthenticationEntryPoint jwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        return (request, response, authException) -> writeJson(response, objectMapper,
                HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.AUTH_FAILED, "未登录或登录已过期，请重新登录");
    }

    @Bean
    public AccessDeniedHandler jwtAccessDeniedHandler(ObjectMapper objectMapper) {
        return (request, response, accessDeniedException) -> writeJson(response, objectMapper,
                HttpServletResponse.SC_FORBIDDEN, ErrorCode.FORBIDDEN, "没有访问权限");
    }

    private void writeJson(HttpServletResponse response, ObjectMapper mapper,
                           int status, int bizCode, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Result<Void> body = Result.fail(bizCode, message);
        mapper.writeValue(response.getOutputStream(), body);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter,
                                                   RateLimitFilter rateLimitFilter,
                                                   AuthenticationEntryPoint jwtAuthenticationEntryPoint,
                                                   AccessDeniedHandler jwtAccessDeniedHandler) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(org.springframework.http.HttpMethod.OPTIONS).permitAll()
                .requestMatchers("/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                .requestMatchers("/api/v1/public/**").permitAll()
                .requestMatchers("/api/v1/meta/**").permitAll()
                .requestMatchers(
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/swagger-ui/index.html",
                    "/webjars/**",
                    "/v3/api-docs/**",
                    "/v3/api-docs.yaml",
                    "/doc.html"
            ).permitAll()
                .requestMatchers("/ws/**").permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(eh -> eh
                .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                .accessDeniedHandler(jwtAccessDeniedHandler)
            )
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    public static class JwtAuthenticationFilter extends OncePerRequestFilter {

        private final String jwtSecret;
        private final RbacPermissionResolver rbacPermissionResolver;

        public JwtAuthenticationFilter(String jwtSecret, RbacPermissionResolver rbacPermissionResolver) {
            this.jwtSecret = jwtSecret;
            this.rbacPermissionResolver = rbacPermissionResolver;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                         HttpServletResponse response,
                                         FilterChain filterChain) throws ServletException, IOException {
            String token = resolveToken(request);
            if (token != null && !token.isBlank()) {
                try {
                    if (JwtUtils.isTokenValid(token, jwtSecret)) {
                        Long userId = JwtUtils.getUserId(token, jwtSecret);
                        String username = JwtUtils.getUsername(token, jwtSecret);
                        List<String> roles = rbacPermissionResolver.resolveRoles(userId);
                        List<String> permissions = rbacPermissionResolver.resolvePermissions(userId, roles);

                        LinkedHashSet<String> authorities = new LinkedHashSet<>();
                        authorities.addAll(roles);
                        authorities.addAll(permissions);

                        List<SimpleGrantedAuthority> grantedAuthorities = new ArrayList<>(authorities).stream()
                                .map(SimpleGrantedAuthority::new)
                                .toList();

                        UserPrincipal principal = new UserPrincipal(userId, username, roles, permissions);
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(principal, null, grantedAuthorities);
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                } catch (Exception e) {
                }
            }
            filterChain.doFilter(request, response);
        }

        private String resolveToken(HttpServletRequest request) {
            String authorization = request.getHeader("Authorization");
            if (authorization != null && authorization.startsWith("Bearer ")) {
                return authorization.substring(7);
            }

            if ("GET".equalsIgnoreCase(request.getMethod()) && request.getRequestURI().startsWith("/api/v1/files/")) {
                String accessToken = request.getParameter("accessToken");
                if (accessToken != null && !accessToken.isBlank()) {
                    return accessToken;
                }
            }

            return null;
        }
    }
}
