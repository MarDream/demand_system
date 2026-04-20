package com.demand.system.common.config;

import com.demand.system.common.utils.JwtUtils;
import com.demand.system.module.auth.security.UserPrincipal;
import jakarta.servlet.FilterChain;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtSecret);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
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
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @RequiredArgsConstructor
    public static class JwtAuthenticationFilter extends OncePerRequestFilter {

        private final String jwtSecret;

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                         HttpServletResponse response,
                                         FilterChain filterChain) throws ServletException, IOException {
            String authorization = request.getHeader("Authorization");
            if (authorization != null && authorization.startsWith("Bearer ")) {
                String token = authorization.substring(7);
                try {
                    if (JwtUtils.isTokenValid(token, jwtSecret)) {
                        Long userId = JwtUtils.getUserId(token, jwtSecret);
                        String username = JwtUtils.getUsername(token, jwtSecret);
                        List<String> roles = JwtUtils.getRoles(token, jwtSecret);

                        List<SimpleGrantedAuthority> authorities = roles.stream()
                                .map(SimpleGrantedAuthority::new)
                                .toList();

                        // 使用UserPrincipal封装用户信息
                        UserPrincipal principal = new UserPrincipal(userId, username, roles);
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(principal, null, authorities);
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                } catch (Exception e) {
                    // Invalid token, continue without authentication
                }
            }
            filterChain.doFilter(request, response);
        }
    }
}
