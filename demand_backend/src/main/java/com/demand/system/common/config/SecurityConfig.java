package com.demand.system.common.config;

import com.demand.system.common.filter.RateLimitFilter;
import com.demand.system.common.utils.JwtUtils;
import com.demand.system.module.auth.security.UserPrincipal;
import com.demand.system.module.rbac.support.RbacPermissionResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(RbacPermissionResolver rbacPermissionResolver) {
        return new JwtAuthenticationFilter(jwtSecret, rbacPermissionResolver);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter, RateLimitFilter rateLimitFilter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(request -> {
                var corsConfig = new org.springframework.web.cors.CorsConfiguration();
                corsConfig.addAllowedOriginPattern("*");
                corsConfig.addAllowedHeader("*");
                corsConfig.addAllowedMethod("*");
                corsConfig.setAllowCredentials(true);
                corsConfig.setMaxAge(3600L);
                return corsConfig;
            }))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(org.springframework.http.HttpMethod.OPTIONS).permitAll()
                .requestMatchers("/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                .requestMatchers("/api/v1/public/**").permitAll()
                .requestMatchers(
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/swagger-ui/index.html",
                    "/webjars/**",
                    "/v3/api-docs/**",
                    "/v3/api-docs.yaml",
                    "/doc.html"
            ).permitAll()
                .anyRequest().authenticated()
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
