package com.demand.system.module.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.common.constant.RedisConstants;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.utils.JwtUtils;
import com.demand.system.module.auth.dto.LoginRequest;
import com.demand.system.module.auth.dto.RefreshTokenRequest;
import com.demand.system.module.auth.dto.TokenResponse;
import com.demand.system.module.auth.dto.UserInfoResponse;
import com.demand.system.module.auth.entity.SysUser;
import com.demand.system.module.user.entity.UserOrganization;
import com.demand.system.module.auth.mapper.SysUserMapper;
import com.demand.system.module.user.mapper.UserOrganizationMapper;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper sysUserMapper;
    private final UserOrganizationMapper userOrganizationMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final PasswordEncoder passwordEncoder;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    @Override
    public TokenResponse login(LoginRequest request) {
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, request.getUsername())
        );

        if (user == null) {
            throw new BusinessException("用户名或密码错误");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }

        if (!"active".equals(user.getStatus())) {
            throw new BusinessException("账户已被禁用，请联系管理员");
        }

        List<String> roles = getUserRoles(user.getId());

        String accessToken = JwtUtils.generateToken(
                user.getId(),
                user.getUsername(),
                roles,
                accessTokenExpiration,
                jwtSecret
        );

        String refreshToken = UUID.randomUUID().toString();
        String redisKey = RedisConstants.REFRESH_TOKEN_PREFIX + refreshToken;
        stringRedisTemplate.opsForValue().set(
                redisKey,
                user.getId().toString(),
                refreshTokenExpiration,
                TimeUnit.MILLISECONDS
        );

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(accessTokenExpiration / 1000)
                .tokenType("Bearer")
                .build();
    }

    @Override
    public void logout(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        String username = SecurityUtils.getCurrentUsername();
        if (username != null) {
            String redisKey = RedisConstants.USER_PREFIX + username;
            stringRedisTemplate.delete(redisKey);
        }
        // Also try to delete by token if it's a refresh token
        if (token != null && !token.isEmpty()) {
            String refreshKey = RedisConstants.REFRESH_TOKEN_PREFIX + token;
            stringRedisTemplate.delete(refreshKey);
        }
    }

    @Override
    public TokenResponse refreshToken(RefreshTokenRequest request) {
        String redisKey = RedisConstants.REFRESH_TOKEN_PREFIX + request.getRefreshToken();
        String userIdStr = stringRedisTemplate.opsForValue().get(redisKey);

        if (userIdStr == null) {
            throw new BusinessException("刷新令牌已失效，请重新登录");
        }

        Long userId = Long.parseLong(userIdStr);
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || !"active".equals(user.getStatus())) {
            stringRedisTemplate.delete(redisKey);
            throw new BusinessException("账户已被禁用，请联系管理员");
        }

        List<String> roles = getUserRoles(userId);

        String newAccessToken = JwtUtils.generateToken(
                userId,
                user.getUsername(),
                roles,
                accessTokenExpiration,
                jwtSecret
        );

        return TokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(request.getRefreshToken())
                .expiresIn(accessTokenExpiration / 1000)
                .tokenType("Bearer")
                .build();
    }

    @Override
    public UserInfoResponse getCurrentUser() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException("未获取到用户信息");
        }

        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        UserOrganization org = userOrganizationMapper.selectOne(
                new LambdaQueryWrapper<UserOrganization>()
                        .eq(UserOrganization::getUserId, userId)
                        .last("LIMIT 1")
        );

        List<String> roles = getUserRoles(userId);

        UserInfoResponse.UserInfoResponseBuilder builder = UserInfoResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .email(user.getEmail())
                .avatar(user.getAvatar())
                .roles(roles);

        if (org != null) {
            builder.regionId(org.getRegionId())
                    .departmentId(org.getDepartmentId())
                    .positionId(org.getPositionId());
        }

        return builder.build();
    }

    private List<String> getUserRoles(Long userId) {
        UserOrganization org = userOrganizationMapper.selectOne(
                new LambdaQueryWrapper<UserOrganization>()
                        .eq(UserOrganization::getUserId, userId)
                        .last("LIMIT 1")
        );
        if (org != null && org.getSystemRole() != null && !org.getSystemRole().isEmpty()) {
            return List.of(org.getSystemRole());
        }
        return List.of("USER");
    }
}
