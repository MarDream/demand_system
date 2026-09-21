package com.demand.system.module.bitable.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.module.bitable.constant.MemberRole;
import com.demand.system.module.bitable.dto.*;
import com.demand.system.module.bitable.entity.*;
import com.demand.system.module.bitable.mapper.*;
import com.demand.system.module.bitable.service.BitableAuthorizationService;
import com.demand.system.module.bitable.service.BitableBaseRoleService;
import com.demand.system.module.organization.entity.SysOrg;
import com.demand.system.module.organization.mapper.SysOrgMapper;
import com.demand.system.module.user.entity.User;
import com.demand.system.module.user.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class BitableBaseRoleServiceImpl implements BitableBaseRoleService {

    private final BitableBaseCustomRoleMapper customRoleMapper;
    private final BitableBaseCustomRoleMemberMapper roleMemberMapper;
    private final BitableBaseRolePermissionMapper rolePermissionMapper;
    private final BitableBaseMemberMapper baseMemberMapper;
    private final BitableTableMapper tableMapper;
    private final BitableAuthorizationService authorizationService;
    private final UserMapper userMapper;
    private final SysOrgMapper sysOrgMapper;

    public BitableBaseRoleServiceImpl(
            BitableBaseCustomRoleMapper customRoleMapper,
            BitableBaseCustomRoleMemberMapper roleMemberMapper,
            BitableBaseRolePermissionMapper rolePermissionMapper,
            BitableBaseMemberMapper baseMemberMapper,
            BitableTableMapper tableMapper,
            BitableAuthorizationService authorizationService,
            UserMapper userMapper,
            SysOrgMapper sysOrgMapper) {
        this.customRoleMapper = customRoleMapper;
        this.roleMemberMapper = roleMemberMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.baseMemberMapper = baseMemberMapper;
        this.tableMapper = tableMapper;
        this.authorizationService = authorizationService;
        this.userMapper = userMapper;
        this.sysOrgMapper = sysOrgMapper;
    }

    @Override
    public List<BitableBaseRoleVO> listRolesWithPermissions(Long baseId, String permissionType) {
        if (permissionType == null) {
            permissionType = "data";
        }

        List<BitableBaseRoleVO> result = new ArrayList<>();

        // 1. 系统角色
        for (MemberRole role : MemberRole.values()) {
            BitableBaseRoleVO vo = new BitableBaseRoleVO();
            vo.setRoleType("system");
            vo.setSystemRoleCode(role.getCode());
            vo.setName(role.getLabel());
            vo.setSortOrder(100 - role.getLevel()); // OWNER在前

            // 系统角色成员从 bitable_base_members 读取
            List<BitableBaseMember> members = baseMemberMapper.selectByBaseId(baseId);
            List<BitableBaseRoleVO.MemberVO> memberVOs = members.stream()
                    .filter(m -> role.getCode().equals(m.getRole()))
                    .map(m -> {
                        BitableBaseRoleVO.MemberVO mv = new BitableBaseRoleVO.MemberVO();
                        mv.setMemberType("user");
                        mv.setMemberId(m.getUserId());
                        return mv;
                    })
                    .collect(Collectors.toList());
            vo.setMembers(memberVOs);

            // 读取该角色的权限配置
            List<BitableBaseRolePermission> perms = rolePermissionMapper.selectByBaseAndType(baseId, permissionType);
            List<BitableBaseRoleVO.PermissionVO> permVOs = perms.stream()
                    .filter(p -> "system".equals(p.getRoleType()) && role.getCode().equals(p.getSystemRoleCode()))
                    .map(p -> {
                        BitableBaseRoleVO.PermissionVO pv = new BitableBaseRoleVO.PermissionVO();
                        pv.setTableId(p.getTableId());
                        pv.setPermissionType(p.getPermissionType());
                        pv.setPermissionLevel(p.getPermissionLevel());
                        return pv;
                    })
                    .collect(Collectors.toList());
            vo.setPermissions(permVOs);

            result.add(vo);
        }

        // 2. 自定义角色（全局有效：跨 Base 聚合展示，任何 Base 的对象都可配置它们）
        List<BitableBaseCustomRole> customRoles = customRoleMapper.selectGlobalAll();
        for (BitableBaseCustomRole role : customRoles) {
            BitableBaseRoleVO vo = new BitableBaseRoleVO();
            vo.setRoleType("custom");
            vo.setCustomRoleId(role.getId());
            vo.setName(role.getName());
            vo.setSortOrder(role.getSortOrder());
            vo.setCreatedAt(role.getCreatedAt());

            // 自定义角色成员
            List<BitableBaseCustomRoleMember> members = roleMemberMapper.selectByRoleId(role.getId());
            List<BitableBaseRoleVO.MemberVO> memberVOs = members.stream()
                    .map(m -> {
                        BitableBaseRoleVO.MemberVO mv = new BitableBaseRoleVO.MemberVO();
                        mv.setMemberType(m.getMemberType());
                        mv.setMemberId(m.getMemberId());
                        return mv;
                    })
                    .collect(Collectors.toList());
            vo.setMembers(memberVOs);

            // 权限配置
            List<BitableBaseRolePermission> perms = rolePermissionMapper.selectByBaseAndType(baseId, permissionType);
            List<BitableBaseRoleVO.PermissionVO> permVOs = perms.stream()
                    .filter(p -> "custom".equals(p.getRoleType()) && role.getId().equals(p.getCustomRoleId()))
                    .map(p -> {
                        BitableBaseRoleVO.PermissionVO pv = new BitableBaseRoleVO.PermissionVO();
                        pv.setTableId(p.getTableId());
                        pv.setPermissionType(p.getPermissionType());
                        pv.setPermissionLevel(p.getPermissionLevel());
                        return pv;
                    })
                    .collect(Collectors.toList());
            vo.setPermissions(permVOs);

            result.add(vo);
        }

        // 批量补全成员显示名与头像（前端据此渲染首字缩略图 / 头像）
        fillMemberProfiles(result);

        // 按 sortOrder 排序
        result.sort(Comparator.comparingInt(BitableBaseRoleVO::getSortOrder));
        return result;
    }

    /**
     * 批量补全角色成员的显示名与头像。
     * <p>
     * 用户类成员取 {@code users.real_name} / {@code users.avatar}（real_name 为空时回退 username），
     * 部门类成员取 {@code sys_org.name}。一次性批量查询，避免按成员逐个查库。
     * 成员已被删除或不存在时保留 null，由前端做占位展示。
     *
     * @param roles 已组装好的角色列表，方法内就地填充
     */
    private void fillMemberProfiles(List<BitableBaseRoleVO> roles) {
        Set<Long> userIds = new HashSet<>();
        Set<Long> deptIds = new HashSet<>();
        for (BitableBaseRoleVO role : roles) {
            if (role.getMembers() == null) {
                continue;
            }
            for (BitableBaseRoleVO.MemberVO member : role.getMembers()) {
                if (member.getMemberId() == null) {
                    continue;
                }
                if ("dept".equalsIgnoreCase(member.getMemberType())) {
                    deptIds.add(member.getMemberId());
                } else {
                    userIds.add(member.getMemberId());
                }
            }
        }

        Map<Long, User> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (User user : userMapper.selectBatchIds(userIds)) {
                userMap.put(user.getId(), user);
            }
        }
        Map<Long, String> deptNameMap = new HashMap<>();
        if (!deptIds.isEmpty()) {
            for (SysOrg org : sysOrgMapper.selectBatchIds(deptIds)) {
                deptNameMap.put(org.getId(), org.getName());
            }
        }

        for (BitableBaseRoleVO role : roles) {
            if (role.getMembers() == null) {
                continue;
            }
            for (BitableBaseRoleVO.MemberVO member : role.getMembers()) {
                if (member.getMemberId() == null) {
                    continue;
                }
                if ("dept".equalsIgnoreCase(member.getMemberType())) {
                    member.setMemberName(deptNameMap.get(member.getMemberId()));
                } else {
                    User user = userMap.get(member.getMemberId());
                    if (user != null) {
                        member.setMemberName(
                                user.getRealName() != null && !user.getRealName().isBlank()
                                        ? user.getRealName()
                                        : user.getUsername());
                        member.setMemberAvatar(user.getAvatar());
                    }
                }
            }
        }
    }

    @Override
    @Transactional
    public BitableBaseCustomRoleCreateDTO createCustomRole(BitableBaseCustomRoleCreateDTO dto, Long userId) {
        authorizationService.checkManagePermission(dto.getBaseId(), userId);

        BitableBaseCustomRole role = new BitableBaseCustomRole();
        role.setBaseId(dto.getBaseId());
        role.setName(dto.getName().trim());
        role.setSortOrder((customRoleMapper.selectMaxSortOrder(dto.getBaseId()) == null ? 0 : customRoleMapper.selectMaxSortOrder(dto.getBaseId())) + 1);
        role.setCreatorId(userId);

        customRoleMapper.insert(role);
        // 回填新生成的角色ID，前端乐观插入用
        dto.setCustomRoleId(role.getId());
        return dto;
    }

    @Override
    @Transactional
    public void updateCustomRole(Long roleId, BitableBaseCustomRoleUpdateDTO dto, Long userId) {
        // selectById 已由 @TableLogic 自动追加 deleted_at=0，能查出来的行必然未删除。
        // 不要再判 role.getDeletedAt() != null：正常行的 deleted_at 是 0（非 null），
        // 那样写会让所有自定义角色的改名/删除/成员增删永远抛「角色不存在」。
        BitableBaseCustomRole role = customRoleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        authorizationService.checkManagePermission(role.getBaseId(), userId);
        role.setName(dto.getName().trim());
        customRoleMapper.updateById(role);
    }

    @Override
    @Transactional
    public void deleteCustomRole(Long roleId, Long userId) {
        BitableBaseCustomRole role = customRoleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        authorizationService.checkManagePermission(role.getBaseId(), userId);

        // 软删除角色
        customRoleMapper.deleteById(roleId);

        // 清理成员
        LambdaQueryWrapper<BitableBaseCustomRoleMember> memberWrapper = new LambdaQueryWrapper<>();
        memberWrapper.eq(BitableBaseCustomRoleMember::getRoleId, roleId);
        roleMemberMapper.delete(memberWrapper);

        // 清理权限
        LambdaQueryWrapper<BitableBaseRolePermission> permWrapper = new LambdaQueryWrapper<>();
        permWrapper.eq(BitableBaseRolePermission::getCustomRoleId, roleId);
        rolePermissionMapper.delete(permWrapper);
    }

    @Override
    @Transactional
    public void addRoleMember(BitableBaseCustomRoleMemberDTO dto, Long userId) {
        BitableBaseCustomRole role = customRoleMapper.selectById(dto.getRoleId());
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        authorizationService.checkManagePermission(role.getBaseId(), userId);

        BitableBaseCustomRoleMember member = new BitableBaseCustomRoleMember();
        member.setRoleId(dto.getRoleId());
        member.setMemberType(dto.getMemberType());
        member.setMemberId(dto.getMemberId());
        member.setCreatorId(userId);

        roleMemberMapper.insert(member);
    }

    @Override
    @Transactional
    public void removeRoleMember(Long roleId, String memberType, Long memberId, Long userId) {
        BitableBaseCustomRole role = customRoleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        authorizationService.checkManagePermission(role.getBaseId(), userId);

        LambdaQueryWrapper<BitableBaseCustomRoleMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BitableBaseCustomRoleMember::getRoleId, roleId)
                .eq(BitableBaseCustomRoleMember::getMemberType, memberType)
                .eq(BitableBaseCustomRoleMember::getMemberId, memberId);
        roleMemberMapper.delete(wrapper);
    }

    /** dashboard/dashboard_data 的默认级别是 full，「无权限/不可见」必须显式落行才生效 */
    private static boolean needsExplicitRow(String permissionType, String level) {
        return !("none".equals(level)) || "dashboard".equals(permissionType) || "dashboard_data".equals(permissionType);
    }

    @Override
    @Transactional
    public void setRolePermission(BitableBaseRolePermissionDTO dto, Long userId) {
        authorizationService.checkManagePermission(dto.getBaseId(), userId);

        // 先删除旧权限
        LambdaQueryWrapper<BitableBaseRolePermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BitableBaseRolePermission::getBaseId, dto.getBaseId())
                .eq(BitableBaseRolePermission::getTableId, dto.getTableId())
                .eq(BitableBaseRolePermission::getPermissionType, dto.getPermissionType());

        if ("system".equals(dto.getRoleType())) {
            wrapper.eq(BitableBaseRolePermission::getRoleType, "system")
                    .eq(BitableBaseRolePermission::getSystemRoleCode, dto.getSystemRoleCode());
        } else {
            wrapper.eq(BitableBaseRolePermission::getRoleType, "custom")
                    .eq(BitableBaseRolePermission::getCustomRoleId, dto.getCustomRoleId());
        }
        rolePermissionMapper.delete(wrapper);

        // 插入新权限（如果不是 none）
        if (needsExplicitRow(dto.getPermissionType(), dto.getPermissionLevel())) {
            BitableBaseRolePermission perm = new BitableBaseRolePermission();
            perm.setBaseId(dto.getBaseId());
            perm.setRoleType(dto.getRoleType());
            perm.setSystemRoleCode(dto.getSystemRoleCode());
            perm.setCustomRoleId(dto.getCustomRoleId());
            perm.setTableId(dto.getTableId());
            perm.setPermissionType(dto.getPermissionType());
            perm.setPermissionLevel(dto.getPermissionLevel());
            perm.setCreatorId(userId);
            rolePermissionMapper.insert(perm);
        }
    }

    @Override
    @Transactional
    public void batchSetRolePermissions(Long baseId, String roleType, String systemRoleCode, Long customRoleId,
                                         String permissionType, String permissionLevel, Long userId) {
        authorizationService.checkManagePermission(baseId, userId);

        // 获取 base 下所有表
        LambdaQueryWrapper<BitableTable> tableWrapper = new LambdaQueryWrapper<>();
        tableWrapper.eq(BitableTable::getBaseId, baseId).eq(BitableTable::getDeletedAt, 0);
        List<BitableTable> tables = tableMapper.selectList(tableWrapper);

        // 先清理该角色所有权限
        LambdaQueryWrapper<BitableBaseRolePermission> delWrapper = new LambdaQueryWrapper<>();
        delWrapper.eq(BitableBaseRolePermission::getBaseId, baseId)
                .eq(BitableBaseRolePermission::getPermissionType, permissionType);
        if ("system".equals(roleType)) {
            delWrapper.eq(BitableBaseRolePermission::getRoleType, "system")
                    .eq(BitableBaseRolePermission::getSystemRoleCode, systemRoleCode);
        } else {
            delWrapper.eq(BitableBaseRolePermission::getRoleType, "custom")
                    .eq(BitableBaseRolePermission::getCustomRoleId, customRoleId);
        }
        rolePermissionMapper.delete(delWrapper);

        // 批量插入（如果不是 none）
        if (needsExplicitRow(permissionType, permissionLevel)) {
            for (BitableTable table : tables) {
                BitableBaseRolePermission perm = new BitableBaseRolePermission();
                perm.setBaseId(baseId);
                perm.setRoleType(roleType);
                perm.setSystemRoleCode(systemRoleCode);
                perm.setCustomRoleId(customRoleId);
                perm.setTableId(table.getId());
                perm.setPermissionType(permissionType);
                perm.setPermissionLevel(permissionLevel);
                perm.setCreatorId(userId);
                rolePermissionMapper.insert(perm);
            }
        }
    }

    @Override
    @Transactional
    public void batchSaveRolePermissions(Long baseId, List<BitableBaseRolePermissionDTO> changes, Long userId) {
        authorizationService.checkManagePermission(baseId, userId);

        for (BitableBaseRolePermissionDTO dto : changes) {
            // 先删除旧权限
            LambdaQueryWrapper<BitableBaseRolePermission> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(BitableBaseRolePermission::getBaseId, dto.getBaseId())
                    .eq(BitableBaseRolePermission::getTableId, dto.getTableId())
                    .eq(BitableBaseRolePermission::getPermissionType, dto.getPermissionType());

            if ("system".equals(dto.getRoleType())) {
                wrapper.eq(BitableBaseRolePermission::getRoleType, "system")
                        .eq(BitableBaseRolePermission::getSystemRoleCode, dto.getSystemRoleCode());
            } else {
                wrapper.eq(BitableBaseRolePermission::getRoleType, "custom")
                        .eq(BitableBaseRolePermission::getCustomRoleId, dto.getCustomRoleId());
            }
            rolePermissionMapper.delete(wrapper);

            // 插入新权限（如果不是 none）
            if (needsExplicitRow(dto.getPermissionType(), dto.getPermissionLevel())) {
                BitableBaseRolePermission perm = new BitableBaseRolePermission();
                perm.setBaseId(dto.getBaseId());
                perm.setRoleType(dto.getRoleType());
                perm.setSystemRoleCode(dto.getSystemRoleCode());
                perm.setCustomRoleId(dto.getCustomRoleId());
                perm.setTableId(dto.getTableId());
                perm.setPermissionType(dto.getPermissionType());
                perm.setPermissionLevel(dto.getPermissionLevel());
                perm.setCreatorId(userId);
                rolePermissionMapper.insert(perm);
            }
        }
    }

    // ==================== 视图 / 仪表盘权限解析与校验 ====================

    /** 权限宽松度权重（越大越宽松） */
    private static int permissionWeight(String level) {
        return switch (level) {
            case "full" -> 4;
            case "edit" -> 3;
            case "view" -> 2;
            default -> 1; // none 及未知值
        };
    }

    /** 解析用户在 Base 内的角色标识（system:<code> + custom:<id>），owner 恒包含以放行 */
    private Set<String> resolveRoleIdentifiers(Long baseId, Long userId) {
        Set<String> keys = new HashSet<>();
        BitableBaseMember member = baseMemberMapper.selectByBaseAndUser(baseId, userId);
        if (member != null) {
            MemberRole role = MemberRole.fromCode(member.getRole());
            if (role != null) {
                keys.add("system:" + role.getCode());
            }
        }
        for (Long roleId : customRoleMapper.selectGlobalCustomRoleIdsByMember(userId)) {
            keys.add("custom:" + roleId);
        }
        return keys;
    }

    @Override
    public String resolveObjectPermission(Long baseId, Long tableId, String permissionType, Long userId) {
        if (baseId == null || tableId == null || userId == null) {
            return null;
        }
        Set<String> roleKeys = resolveRoleIdentifiers(baseId, userId);
        if (roleKeys.isEmpty()) {
            return null;
        }
        String best = null;
        for (BitableBaseRolePermission perm : rolePermissionMapper.selectByBaseAndType(baseId, permissionType)) {
            if (!Objects.equals(perm.getTableId(), tableId) || !roleKeys.contains(perm.getRoleType() + ":" + (perm.getRoleType().equals("system") ? perm.getSystemRoleCode() : perm.getCustomRoleId()))) {
                continue;
            }
            String level = perm.getPermissionLevel();
            if (best == null || permissionWeight(level) > permissionWeight(best)) {
                best = level;
            }
        }
        return best;
    }

    @Override
    public void checkViewManagePermission(Long baseId, Long tableId, Long userId) {
        String level = resolveObjectPermission(baseId, tableId, "view", userId);
        if ("view".equals(level)) {
            throw new BusinessException("当前角色的视图权限为「可查看」，不能新增、修改或删除视图");
        }
    }

    private static final Set<String> DASHBOARD_LEVELS = Set.of("full", "view", "none");

    @Override
    public String resolveDashboardPermission(Long baseId, Long dashboardId, Long userId) {
        String level = resolveObjectPermission(baseId, dashboardId, "dashboard", userId);
        return level != null && DASHBOARD_LEVELS.contains(level) ? level : "full";
    }

    @Override
    public String resolveDashboardDataPermission(Long baseId, Long dashboardId, Long userId) {
        String level = resolveObjectPermission(baseId, dashboardId, "dashboard_data", userId);
        return level != null && DASHBOARD_LEVELS.contains(level) ? level : "full";
    }
}
