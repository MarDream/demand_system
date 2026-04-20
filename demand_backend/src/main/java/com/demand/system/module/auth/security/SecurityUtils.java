package com.demand.system.module.auth.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        // 优先从UserPrincipal获取
        if (principal instanceof UserPrincipal userPrincipal) {
            return userPrincipal.getUserId();
        }
        // 兼容旧的UserDetails方式
        if (principal instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
            return Long.parseLong(userDetails.getUsername());
        }
        return null;
    }

    public static String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        // 优先从UserPrincipal获取
        if (principal instanceof UserPrincipal userPrincipal) {
            return userPrincipal.getUsername();
        }
        return authentication.getName();
    }

    @SuppressWarnings("unchecked")
    public static List<String> getCurrentUserRoles() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return List.of();
        }
        // 优先从UserPrincipal获取
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipal userPrincipal) {
            return userPrincipal.getRoles();
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }
}
