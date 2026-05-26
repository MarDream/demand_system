package com.demand.system.module.auth.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipal userPrincipal) {
            return userPrincipal.getUserId();
        }
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
        if (principal instanceof UserPrincipal userPrincipal) {
            return userPrincipal.getUsername();
        }
        return authentication.getName();
    }

    public static List<String> getCurrentUserRoles() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return List.of();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipal userPrincipal) {
            return userPrincipal.getRoles();
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }

    public static List<String> getCurrentUserPermissions() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            return List.of();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipal userPrincipal && userPrincipal.getPermissions() != null) {
            return userPrincipal.getPermissions();
        }
        return List.of();
    }

    public static boolean hasAnyRole(String... roles) {
        List<String> currentRoles = getCurrentUserRoles();
        if (currentRoles.isEmpty() || roles == null || roles.length == 0) {
            return false;
        }
        Set<String> expected = Set.of(roles);
        return currentRoles.stream().anyMatch(expected::contains);
    }

    public static boolean hasAnyPermission(String... permissions) {
        List<String> currentPermissions = getCurrentUserPermissions();
        if (currentPermissions.isEmpty() || permissions == null || permissions.length == 0) {
            return false;
        }
        Set<String> expected = Set.of(permissions);
        return currentPermissions.stream().anyMatch(expected::contains);
    }

    public static boolean isSuperAdmin() {
        return hasAnyRole("super_admin", "SUPER_ADMIN");
    }
}
