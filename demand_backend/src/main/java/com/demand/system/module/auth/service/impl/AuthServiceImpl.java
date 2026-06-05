package com.demand.system.module.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.common.constant.RedisConstants;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.utils.JwtUtils;
import com.demand.system.module.auth.dto.*;
import com.demand.system.module.auth.entity.SysUser;
import com.demand.system.module.user.entity.UserOrganization;
import com.demand.system.module.auth.mapper.SysUserMapper;
import com.demand.system.module.user.mapper.UserOrganizationMapper;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.auth.service.AuthService;
import com.demand.system.module.auth.service.VerificationCodeService;
import com.demand.system.module.rbac.support.RbacPermissionResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper sysUserMapper;
    private final UserOrganizationMapper userOrganizationMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final PasswordEncoder passwordEncoder;
    private final VerificationCodeService verificationCodeService;
    private final RbacPermissionResolver rbacPermissionResolver;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    public AuthServiceImpl(SysUserMapper sysUserMapper, UserOrganizationMapper userOrganizationMapper, StringRedisTemplate stringRedisTemplate, PasswordEncoder passwordEncoder, VerificationCodeService verificationCodeService, RbacPermissionResolver rbacPermissionResolver) {
        this.sysUserMapper = sysUserMapper;
        this.userOrganizationMapper = userOrganizationMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.passwordEncoder = passwordEncoder;
        this.verificationCodeService = verificationCodeService;
        this.rbacPermissionResolver = rbacPermissionResolver;
    }

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

        List<String> roles = rbacPermissionResolver.resolveRoles(user.getId());

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

        List<String> roles = rbacPermissionResolver.resolveRoles(userId);

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

        List<String> roles = rbacPermissionResolver.resolveRoles(userId);
        List<String> roleNames = rbacPermissionResolver.resolveRoleDisplayNames(userId);
        List<String> permissions = rbacPermissionResolver.resolvePermissions(userId, roles);

        UserInfoResponse.UserInfoResponseBuilder builder = UserInfoResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .avatar(user.getAvatar())
                .roles(roles)
                .roleNames(roleNames)
                .permissions(permissions)
                .isSuperAdmin(rbacPermissionResolver.isSuperAdmin(roles));

        if (org != null) {
            builder.regionId(org.getRegionId())
                    .departmentId(org.getDepartmentId());
        }

        return builder.build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TokenResponse register(RegisterRequest request) {
        if (!verificationCodeService.verifyCode(request.getEmail(), request.getVerificationCode(), "register")) {
            throw new BusinessException("验证码错误或已过期");
        }

        SysUser existingUser = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, request.getUsername())
        );
        if (existingUser != null) {
            throw new BusinessException("用户名已存在");
        }

        SysUser existingEmail = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getEmail, request.getEmail())
        );
        if (existingEmail != null) {
            throw new BusinessException("邮箱已被注册");
        }

        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRealName(request.getRealName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setStatus("inactive");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setDeletedAt(0);

        sysUserMapper.insert(user);

        verificationCodeService.markCodeAsUsed(request.getEmail(), request.getVerificationCode(), "register");

        List<String> roles = List.of("USER");

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
    public void sendVerificationCode(SendVerificationCodeRequest request) {
        if (!"register".equals(request.getType()) && !"reset_password".equals(request.getType())) {
            throw new BusinessException("验证码类型不正确");
        }

        if ("register".equals(request.getType())) {
            SysUser existingUser = sysUserMapper.selectOne(
                    new LambdaQueryWrapper<SysUser>()
                            .eq(SysUser::getEmail, request.getEmail())
            );
            if (existingUser != null) {
                throw new BusinessException("该邮箱已被注册");
            }
        }

        if ("reset_password".equals(request.getType())) {
            SysUser user = sysUserMapper.selectOne(
                    new LambdaQueryWrapper<SysUser>()
                            .eq(SysUser::getEmail, request.getEmail())
            );
            if (user == null) {
                throw new BusinessException("该邮箱未注册");
            }
        }

        verificationCodeService.generateAndSendCode(request.getEmail(), request.getType());
    }

    @Override
    public void requestPasswordReset(ResetPasswordRequest request) {
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getEmail, request.getEmail())
        );
        if (user == null) {
            throw new BusinessException("该邮箱未注册");
        }

        verificationCodeService.generateAndSendCode(request.getEmail(), "reset_password");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmPasswordReset(ConfirmResetPasswordRequest request) {
        if (!verificationCodeService.verifyCode(request.getEmail(), request.getVerificationCode(), "reset_password")) {
            throw new BusinessException("验证码错误或已过期");
        }

        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getEmail, request.getEmail())
        );
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.updateById(user);

        verificationCodeService.markCodeAsUsed(request.getEmail(), request.getVerificationCode(), "reset_password");
    }
}
