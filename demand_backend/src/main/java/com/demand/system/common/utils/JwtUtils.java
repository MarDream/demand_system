package com.demand.system.common.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

public final class JwtUtils {

    private JwtUtils() {
    }

    public static String generateToken(Long userId, String username, List<String> roles,
                                        long expirationMs, String secret) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("roles", roles)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key)
                .compact();
    }

    public static Claims parseToken(String token, String secret) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public static boolean isTokenValid(String token, String secret) {
        try {
            Claims claims = parseToken(token, secret);
            return claims.getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    public static Long getUserId(String token, String secret) {
        Claims claims = parseToken(token, secret);
        return claims.get("userId", Long.class);
    }

    public static String getUsername(String token, String secret) {
        Claims claims = parseToken(token, secret);
        return claims.getSubject();
    }

    @SuppressWarnings("unchecked")
    public static List<String> getRoles(String token, String secret) {
        Claims claims = parseToken(token, secret);
        return claims.get("roles", List.class);
    }
}
