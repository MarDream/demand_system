package com.demand.system.module.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.PageResult;
import com.demand.system.module.auth.service.EmailService;
import com.demand.system.module.user.dto.UserCreateDTO;
import com.demand.system.module.user.dto.UserQueryDTO;
import com.demand.system.module.user.dto.UserUpdateDTO;
import com.demand.system.module.user.dto.UserVO;
import com.demand.system.module.user.entity.User;
import com.demand.system.module.user.mapper.UserMapper;
import com.demand.system.module.user.service.UserService;
import com.demand.system.module.organization.dto.SysOrgVO;
import com.demand.system.module.organization.entity.Position;
import com.demand.system.module.organization.mapper.PositionMapper;
import com.demand.system.module.organization.service.SysOrgService;
import com.demand.system.module.rbac.entity.Role;
import com.demand.system.module.rbac.entity.UserRole;
import com.demand.system.module.rbac.mapper.RoleMapper;
import com.demand.system.module.rbac.mapper.UserRoleMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PositionMapper positionMapper;
    private final SysOrgService sysOrgService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;

    public UserServiceImpl(UserMapper userMapper, PositionMapper positionMapper, SysOrgService sysOrgService, PasswordEncoder passwordEncoder, EmailService emailService, UserRoleMapper userRoleMapper, RoleMapper roleMapper) {
        this.userMapper = userMapper;
        this.positionMapper = positionMapper;
        this.sysOrgService = sysOrgService;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
    }

    @Override
    public PageResult<UserVO> list(UserQueryDTO query) {
        Page<User> page = new Page<>(query.getPageNum(), query.getPageSize());

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(query.getUsername() != null, User::getUsername, query.getUsername())
                .like(query.getRealName() != null, User::getRealName, query.getRealName())
                .eq(query.getStatus() != null, User::getStatus, query.getStatus())
                .eq(query.getPositionId() != null, User::getPositionId, query.getPositionId())
                .orderByDesc(User::getCreatedAt);

        if (query.getOrgId() != null) {
            List<Long> orgIds = sysOrgService.getDescendantIds(query.getOrgId());
            orgIds.add(query.getOrgId());
            wrapper.in(User::getOrgId, orgIds);
        } else {
            // Fallback to old fields for backward compatibility
            if (query.getRegionId() != null) {
                List<Long> orgIds = sysOrgService.getDescendantIds(query.getRegionId());
                orgIds.add(query.getRegionId());
                wrapper.and(w -> w.in(User::getOrgId, orgIds).or().in(User::getRegionId, orgIds));
            }
            if (query.getDepartmentId() != null) {
                List<Long> orgIds = sysOrgService.getDescendantIds(query.getDepartmentId());
                orgIds.add(query.getDepartmentId());
                wrapper.and(w -> w.in(User::getOrgId, orgIds).or().in(User::getDepartmentId, orgIds));
            }
        }

        Page<User> userPage = userMapper.selectPage(page, wrapper);

        List<UserVO> voList = userPage.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return new PageResult<>(voList, userPage.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    public UserVO getById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return toVO(user);
    }

    @Override
    public void create(UserCreateDTO dto) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, dto.getUsername());
        if (userMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("用户名已存在");
        }

        if (dto.getEmail() == null || dto.getEmail().isBlank()) {
            throw new BusinessException("邮箱不能为空");
        }
        if (dto.getPhone() == null || dto.getPhone().length() < 3) {
            throw new BusinessException("手机号不能为空且至少包含3位");
        }

        User user = new User();
        BeanUtil.copyProperties(dto, user);
        // Derive regionId/departmentId from orgId
        if (dto.getOrgId() != null) {
            deriveOrgFields(user, dto.getOrgId());
        }
        String initialPassword = buildInitialPassword(dto.getUsername(), dto.getPhone(), dto.getPassword());
        user.setPassword(passwordEncoder.encode(initialPassword));
        user.setStatus(User.STATUS_ACTIVE);
        user.setJobNumber(generateJobNumber());
        userMapper.insert(user);

        emailService.sendInitialPasswordEmail(user.getEmail(), user.getUsername(), initialPassword);
    }

    @Override
    public void update(UserUpdateDTO dto) {
        User user = userMapper.selectById(dto.getId());
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        if (dto.getRealName() != null) {
            user.setRealName(dto.getRealName());
        }
        if (dto.getEmail() != null) {
            user.setEmail(dto.getEmail());
        }
        if (dto.getPhone() != null) {
            user.setPhone(dto.getPhone());
        }
        if (dto.getAvatar() != null) {
            user.setAvatar(dto.getAvatar());
        }
        if (dto.getStatus() != null) {
            user.setStatus(dto.getStatus());
        }
        if (dto.getRegionId() != null) {
            user.setRegionId(dto.getRegionId());
        }
        if (dto.getDepartmentId() != null) {
            user.setDepartmentId(dto.getDepartmentId());
        }
        if (dto.getOrgId() != null) {
            user.setOrgId(dto.getOrgId());
            deriveOrgFields(user, dto.getOrgId());
        }
        if (dto.getPositionId() != null) {
            user.setPositionId(dto.getPositionId());
        }
        userMapper.updateById(user);
    }

    @Override
    public void delete(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        userMapper.deleteById(id);
    }

    @Override
    public boolean resetInitialPassword(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new BusinessException("用户未配置邮箱，无法发送初始密码");
        }
        if (user.getPhone() == null || user.getPhone().length() < 3) {
            throw new BusinessException("用户手机号不足3位，无法生成初始密码");
        }

        String initialPassword = buildInitialPassword(user.getUsername(), user.getPhone(), null);
        user.setPassword(passwordEncoder.encode(initialPassword));
        userMapper.updateById(user);
        return emailService.sendInitialPasswordEmail(user.getEmail(), user.getUsername(), initialPassword);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long userId, List<Long> roleIds) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        List<Long> normalizedRoleIds = roleIds == null
                ? List.of()
                : roleIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (!normalizedRoleIds.isEmpty()) {
            List<Role> roles = roleMapper.selectBatchIds(normalizedRoleIds);
            if (roles.size() != normalizedRoleIds.size()) {
                throw new BusinessException("存在无效的角色");
            }
        }

        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, userId));

        if (normalizedRoleIds.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        for (Long roleId : normalizedRoleIds) {
            UserRole relation = new UserRole();
            relation.setUserId(userId);
            relation.setRoleId(roleId);
            relation.setCreatedAt(now);
            userRoleMapper.insert(relation);
        }
    }

    @Override
    public List<Long> getUserRoleIds(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, userId)
                        .orderByAsc(UserRole::getId))
                .stream()
                .map(UserRole::getRoleId)
                .filter(Objects::nonNull)
                .toList();
    }

    private UserVO toVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setAvatar(user.getAvatar());
        vo.setJobNumber(user.getJobNumber());
        vo.setStatus(user.getStatus());
        vo.setRegionId(user.getRegionId());
        vo.setDepartmentId(user.getDepartmentId());
        vo.setOrgId(user.getOrgId());
        vo.setPositionId(user.getPositionId());
        vo.setCreatedAt(user.getCreatedAt());
        vo.setUpdatedAt(user.getUpdatedAt());

        // Prefer orgId for display, fallback to regionId/departmentId
        Long displayOrgId = user.getOrgId() != null ? user.getOrgId() : user.getDepartmentId();
        if (displayOrgId == null) {
            displayOrgId = user.getRegionId();
        }

        if (displayOrgId != null) {
            SysOrgVO org = sysOrgService.getDetail(displayOrgId);
            if (org != null && org.getPath() != null) {
                String chainPath = buildOrgPath(org.getPath());
                vo.setRegionPath(chainPath);
            }
        }

        // Fill region name from regionId
        if (user.getRegionId() != null) {
            SysOrgVO region = sysOrgService.getDetail(user.getRegionId());
            if (region != null) {
                vo.setRegionName(region.getName());
            }
        }

        // Fill department name from departmentId
        if (user.getDepartmentId() != null) {
            SysOrgVO dept = sysOrgService.getDetail(user.getDepartmentId());
            if (dept != null) {
                vo.setDepartmentName(dept.getName());
            }
        }

        // Fill position info
        if (user.getPositionId() != null) {
            Position position = positionMapper.selectById(user.getPositionId());
            if (position != null) {
                vo.setPositionName(position.getName());
            }
        }

        return vo;
    }

    /**
     * Derive regionId and departmentId from the org the user belongs to.
     * region/company/bureau -> regionId, department/group -> departmentId
     * Walks up the tree to fill both fields.
     */
    private void deriveOrgFields(User user, Long orgId) {
        user.setOrgId(orgId);
        SysOrgVO org = sysOrgService.getDetail(orgId);
        if (org == null) return;

        String orgType = org.getOrgType();
        if ("region".equals(orgType) || "company".equals(orgType) || "bureau".equals(orgType)) {
            user.setRegionId(orgId);
        } else if ("department".equals(orgType) || "group".equals(orgType)) {
            user.setDepartmentId(orgId);
            // Walk up to find regionId
            if (org.getPath() != null) {
                String[] ids = org.getPath().split("/");
                for (String idStr : ids) {
                    if (idStr.isBlank()) continue;
                    SysOrgVO ancestor = sysOrgService.getDetail(Long.parseLong(idStr));
                    if (ancestor != null && ("region".equals(ancestor.getOrgType())
                            || "company".equals(ancestor.getOrgType())
                            || "bureau".equals(ancestor.getOrgType()))) {
                        user.setRegionId(ancestor.getId());
                        break;
                    }
                }
            }
        }
    }

    /**
     * 根据物化路径构建显示路径，如 "/1/5/8/" -> "东莞市 > 开普云科技 > 研发中心"
     */
    private String buildOrgPath(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String[] ids = path.split("/");
        List<String> names = new ArrayList<>();
        for (String idStr : ids) {
            if (idStr.isBlank()) {
                continue;
            }
            SysOrgVO org = sysOrgService.getDetail(Long.parseLong(idStr));
            if (org != null) {
                names.add(org.getName());
            }
        }
        return String.join(" > ", names);
    }

    private String buildInitialPassword(String username, String phone, String fallbackPassword) {
        if (fallbackPassword != null && !fallbackPassword.isBlank()) {
            return fallbackPassword;
        }
        if (phone == null || phone.length() < 3) {
            throw new BusinessException("手机号不足3位，无法生成初始密码");
        }
        return username + phone.substring(phone.length() - 3);
    }

    /**
     * 自动生成工号：A001~A999, B001~B999, ... Z001~Z999, AA001~AA999 ...
     */
    private String generateJobNumber() {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNotNull(User::getJobNumber)
                .orderByDesc(User::getJobNumber)
                .last("LIMIT 1");
        User maxUser = userMapper.selectOne(wrapper);
        if (maxUser == null || maxUser.getJobNumber() == null) {
            return "A001";
        }
        return incrementJobNumber(maxUser.getJobNumber());
    }

    private String incrementJobNumber(String current) {
        // Split letter prefix and number suffix: "A001" -> prefix="A", num=1
        int splitIdx = 0;
        while (splitIdx < current.length() && !Character.isDigit(current.charAt(splitIdx))) {
            splitIdx++;
        }
        if (splitIdx == 0 || splitIdx == current.length()) {
            return "A001";
        }
        String prefix = current.substring(0, splitIdx);
        int num;
        try {
            num = Integer.parseInt(current.substring(splitIdx));
        } catch (NumberFormatException e) {
            return "A001";
        }

        if (num < 999) {
            return prefix + String.format("%03d", num + 1);
        }
        // num == 999, advance letter: A -> B, ... Z -> AA
        return incrementLetterPrefix(prefix) + "001";
    }

    private String incrementLetterPrefix(String prefix) {
        char[] chars = prefix.toCharArray();
        int i = chars.length - 1;
        while (i >= 0) {
            if (chars[i] < 'Z') {
                chars[i]++;
                return new String(chars);
            }
            chars[i] = 'A';
            i--;
        }
        // All Z's, add one more A: Z -> AA, ZZ -> AAA
        return "A" + new String(chars);
    }
}
