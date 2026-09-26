package com.demand.system.module.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.demand.system.common.constant.RedisConstants;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.util.JwtUtils;
import com.demand.system.module.auth.dto.*;
import com.demand.system.module.auth.entity.SysUser;
import com.demand.system.module.organization.dto.SysOrgVO;
import com.demand.system.module.organization.service.SysOrgService;
import com.demand.system.module.user.entity.User;
import com.demand.system.module.user.entity.UserOrganization;
import com.demand.system.module.auth.mapper.SysUserMapper;
import com.demand.system.module.user.mapper.UserMapper;
import com.demand.system.module.user.mapper.UserOrganizationMapper;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.auth.service.AuthService;
import com.demand.system.module.auth.service.VerificationCodeService;
import com.demand.system.module.rbac.support.RbacPermissionResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
    private final UserMapper userMapper;
    private final SysOrgService sysOrgService;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    public AuthServiceImpl(SysUserMapper sysUserMapper, UserOrganizationMapper userOrganizationMapper, StringRedisTemplate stringRedisTemplate, PasswordEncoder passwordEncoder, VerificationCodeService verificationCodeService, RbacPermissionResolver rbacPermissionResolver, UserMapper userMapper, SysOrgService sysOrgService) {
        this.sysUserMapper = sysUserMapper;
        this.userOrganizationMapper = userOrganizationMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.passwordEncoder = passwordEncoder;
        this.verificationCodeService = verificationCodeService;
        this.rbacPermissionResolver = rbacPermissionResolver;
        this.userMapper = userMapper;
        this.sysOrgService = sysOrgService;
    }

    /** 判断用户是否无组织（既不属于任何区域，也不属于任何部门） */
    private boolean isOrphan(Long userId) {
        if (userId == null) {
            return true;
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            return true;
        }
        return user.getOrgId() == null && user.getRegionId() == null && user.getDepartmentId() == null;
    }

    @Override
    public TokenResponse login(LoginRequest request) {
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, request.getUsername())
        );

        if (user == null) {
            throw new BadCredentialsException("用户名或密码错误");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("用户名或密码错误");
        }

        if (!"active".equals(user.getStatus())) {
            throw new DisabledException("账户已被禁用，请联系管理员");
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
                .needOrgBind(isOrphan(user.getId()))
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

        // 同步读取 users 表上的组织字段，确保前后端一致
        User userEntity = userMapper.selectById(userId);

        // 角色切换：allRoles 是用户完整角色（供下拉选择），roles 是激活角色过滤后的生效角色
        List<String> allRoles = rbacPermissionResolver.resolveRoles(userId);
        Map<String, String> roleNameMap = rbacPermissionResolver.resolveRoleNameMap(userId);
        List<String> allRoleNames = allRoles.stream()
                .map(code -> roleNameMap.getOrDefault(code, code))
                .toList();

        String requestedActiveRole = rbacPermissionResolver.currentActiveRole();
        String activeRole = rbacPermissionResolver.isActiveRoleApplied(userId, requestedActiveRole)
                ? requestedActiveRole.trim() : null;
        List<String> roles = activeRole == null ? allRoles : List.of(activeRole);
        List<String> roleNames = roles.stream()
                .map(code -> roleNameMap.getOrDefault(code, code))
                .toList();
        List<String> permissions = rbacPermissionResolver.resolvePermissions(userId, roles);

        boolean orphan = userEntity == null
                || (userEntity.getOrgId() == null && userEntity.getRegionId() == null && userEntity.getDepartmentId() == null);

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
                .isSuperAdmin(rbacPermissionResolver.isSuperAdmin(roles))
                .allRoles(allRoles)
                .allRoleNames(allRoleNames)
                .activeRole(activeRole)
                .orgId(userEntity == null ? null : userEntity.getOrgId())
                .needOrgBind(orphan)
                .jobNumber(userEntity == null ? null : userEntity.getJobNumber())
                .appearanceConfig(user.getAppearanceConfig());

        if (userEntity != null && userEntity.getOrgId() != null) {
            try {
                SysOrgVO orgVO = sysOrgService.getDetail(userEntity.getOrgId());
                if (orgVO != null) {
                    builder.orgName(orgVO.getName());
                }
            } catch (Exception ignored) {
                // 组织详情读取失败不影响用户信息返回
            }
        }

        if (org != null) {
            builder.regionId(org.getRegionId())
                    .departmentId(org.getDepartmentId());
        }

        return builder.build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserInfoResponse updateProfile(UpdateProfileRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException("未获取到用户信息");
        }

        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        String email = request.getEmail() == null ? null : request.getEmail().trim();
        String phone = request.getPhone() == null ? null : request.getPhone().trim();

        // 邮箱/手机号不允许与其他用户冲突（排除本人）
        if (email != null && sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getEmail, email).ne(SysUser::getId, userId)) > 0) {
            throw new BusinessException("该邮箱已被其他账号使用");
        }
        if (phone != null && !phone.isBlank() && sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getPhone, phone).ne(SysUser::getId, userId)) > 0) {
            throw new BusinessException("该手机号已被其他账号使用");
        }

        user.setEmail(email);
        user.setPhone(phone);
        // 头像：显式传入时才更新（null = 未提供保持不变；空串 = 清除头像回退首字占位）
        if (request.getAvatar() != null) {
            String avatar = request.getAvatar().trim();
            if (avatar.isEmpty()) {
                user.setAvatar(null);
            } else if (avatar.startsWith("preset:") || avatar.startsWith("/")
                    || avatar.startsWith("http://") || avatar.startsWith("https://")) {
                user.setAvatar(avatar.length() > 512 ? avatar.substring(0, 512) : avatar);
            } else {
                throw new BusinessException("头像地址不合法");
            }
        }
        sysUserMapper.updateById(user);

        return getCurrentUser();
    }

    @Override
    public void saveAppearanceConfig(UpdateAppearanceRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException("未获取到用户信息");
        }

        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        // 入参已通过 Bean Validation 校验，此处规范 primary 为大写十六进制后序列化。
        // sidebar 为后增字段：历史客户端不传时沿用库里已保存的值（避免旧端保存把用户侧边栏风格抹成默认）
        String sidebar = (request.getSidebar() == null || request.getSidebar().isBlank())
                ? extractStoredSidebar(user.getAppearanceConfig())
                : request.getSidebar();
        String config = String.format(
                "{\"mode\":\"%s\",\"primary\":\"%s\",\"radius\":\"%s\",\"sidebar\":\"%s\"}",
                request.getMode(), request.getPrimary().toUpperCase(), request.getRadius(), sidebar);
        user.setAppearanceConfig(config);
        sysUserMapper.updateById(user);
    }

    /** 从已存储的外观 JSON 中取 sidebar 字段；缺失/解析失败回退 classic */
    private String extractStoredSidebar(String appearanceConfig) {
        if (appearanceConfig != null && appearanceConfig.contains("\"sidebar\"")) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("\"sidebar\"\\s*:\\s*\"([a-z-]+)\"").matcher(appearanceConfig);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return "classic";
    }

    @Override
    public void changePassword(ChangePasswordRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException("未获取到用户信息");
        }

        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException("旧密码不正确");
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BusinessException("新密码不能与旧密码相同");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindOrg(Long orgId) {
        if (orgId == null) {
            throw new BusinessException("组织ID不能为空");
        }
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException("未获取到用户信息");
        }

        SysOrgVO org = sysOrgService.getDetail(orgId);
        if (org == null) {
            throw new BusinessException("所选组织不存在");
        }

        // 1) 更新 users 表：根据组织类型回填 orgId / regionId / departmentId
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setOrgId(orgId);
        String orgType = org.getOrgType();
        if ("region".equals(orgType) || "company".equals(orgType) || "bureau".equals(orgType)) {
            user.setRegionId(orgId);
            user.setDepartmentId(null);
        } else if ("department".equals(orgType) || "group".equals(orgType)) {
            user.setDepartmentId(orgId);
            // 沿路径向上找最近的 region/company/bureau
            Long regionId = null;
            if (org.getPath() != null) {
                String[] ids = org.getPath().split("/");
                for (String idStr : ids) {
                    if (idStr.isBlank()) continue;
                    SysOrgVO ancestor = sysOrgService.getDetail(Long.parseLong(idStr));
                    if (ancestor != null && ("region".equals(ancestor.getOrgType())
                            || "company".equals(ancestor.getOrgType())
                            || "bureau".equals(ancestor.getOrgType()))) {
                        regionId = ancestor.getId();
                        break;
                    }
                }
            }
            user.setRegionId(regionId);
        }
        userMapper.updateById(user);

        // 2) 同步更新 user_organizations 表，确保登录/审批等查询能命中
        UserOrganization existing = userOrganizationMapper.selectOne(
                new LambdaQueryWrapper<UserOrganization>()
                        .eq(UserOrganization::getUserId, userId)
                        .last("LIMIT 1")
        );
        if (existing == null) {
            UserOrganization relation = new UserOrganization();
            relation.setUserId(userId);
            relation.setOrgId(orgId);
            relation.setRegionId(user.getRegionId());
            relation.setDepartmentId(user.getDepartmentId());
            relation.setSystemRole("USER");
            relation.setEffectiveDate(LocalDate.now());
            userOrganizationMapper.insert(relation);
        } else {
            existing.setOrgId(orgId);
            existing.setRegionId(user.getRegionId());
            existing.setDepartmentId(user.getDepartmentId());
            if (existing.getSystemRole() == null || existing.getSystemRole().isBlank()) {
                existing.setSystemRole("USER");
            }
            if (existing.getEffectiveDate() == null) {
                existing.setEffectiveDate(LocalDate.now());
            }
            userOrganizationMapper.updateById(existing);
        }
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
        // 注册流程已通过邮箱验证码完成邮箱验证，直接激活账号。
        // 此前写死 "inactive" 且无任何激活流程，导致新注册用户永远无法登录（Account disabled）。
        user.setStatus("active");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

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
