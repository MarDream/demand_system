package com.demand.system.module.bitable.service.impl;

import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.module.bitable.constant.MemberRole;
import com.demand.system.module.bitable.entity.BitableBaseCustomRoleMember;
import com.demand.system.module.bitable.entity.BitableBaseMember;
import com.demand.system.module.bitable.entity.BitableBaseRolePermission;
import com.demand.system.module.bitable.entity.BitableComment;
import com.demand.system.module.bitable.entity.BitableDashboard;
import com.demand.system.module.bitable.entity.BitableField;
import com.demand.system.module.bitable.entity.BitableRecord;
import com.demand.system.module.bitable.entity.BitableTable;
import com.demand.system.module.bitable.entity.BitableView;
import com.demand.system.module.bitable.entity.BitableWebhookSubscription;
import com.demand.system.module.bitable.mapper.BitableBaseCustomRoleMemberMapper;
import com.demand.system.module.bitable.mapper.BitableBaseMemberMapper;
import com.demand.system.module.bitable.mapper.BitableBaseRolePermissionMapper;
import com.demand.system.module.bitable.mapper.BitableCommentMapper;
import com.demand.system.module.bitable.mapper.BitableDashboardMapper;
import com.demand.system.module.bitable.mapper.BitableFieldMapper;
import com.demand.system.module.bitable.mapper.BitableRecordMapper;
import com.demand.system.module.bitable.mapper.BitableTableMapper;
import com.demand.system.module.bitable.mapper.BitableViewMapper;
import com.demand.system.module.bitable.mapper.BitableWebhookSubscriptionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.module.bitable.service.BitableAuthorizationService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 多维表格权限校验 Service 实现
 * <p>
 * 核心逻辑：
 * 1. getMemberRole：查 bitable_base_members 表，缓存到 Redis（key: bitable:role:{baseId}:{userId}，TTL 5分钟）
 * 2. checkPermission：获取角色后与 requiredRole 比较，MemberRole 枚举的 getLevel() 用于层级比较
 * 3. 反查 baseId：通过关联查询获取（table→base, field→table→base 等）
 * 4. 如果用户不是成员，默认拒绝（null role = 权限不足）
 */
@Service
public class BitableAuthorizationServiceImpl implements BitableAuthorizationService {

    private static final String ROLE_CACHE_PREFIX = "bitable:role:";
    private static final long ROLE_CACHE_TTL_MINUTES = 5;

    private final BitableBaseMemberMapper memberMapper;
    private final BitableTableMapper tableMapper;
    private final BitableFieldMapper fieldMapper;
    private final BitableRecordMapper recordMapper;
    private final BitableViewMapper viewMapper;
    private final BitableCommentMapper commentMapper;
    private final BitableDashboardMapper dashboardMapper;
    private final BitableWebhookSubscriptionMapper webhookMapper;
    private final BitableBaseCustomRoleMemberMapper customRoleMemberMapper;
    private final BitableBaseRolePermissionMapper rolePermissionMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    public BitableAuthorizationServiceImpl(BitableBaseMemberMapper memberMapper,
                                           BitableTableMapper tableMapper,
                                           BitableFieldMapper fieldMapper,
                                           BitableRecordMapper recordMapper,
                                           BitableViewMapper viewMapper,
                                           BitableCommentMapper commentMapper,
                                           BitableDashboardMapper dashboardMapper,
                                           BitableWebhookSubscriptionMapper webhookMapper,
                                           BitableBaseCustomRoleMemberMapper customRoleMemberMapper,
                                           BitableBaseRolePermissionMapper rolePermissionMapper,
                                           RedisTemplate<String, Object> redisTemplate) {
        this.memberMapper = memberMapper;
        this.tableMapper = tableMapper;
        this.fieldMapper = fieldMapper;
        this.recordMapper = recordMapper;
        this.viewMapper = viewMapper;
        this.commentMapper = commentMapper;
        this.dashboardMapper = dashboardMapper;
        this.webhookMapper = webhookMapper;
        this.customRoleMemberMapper = customRoleMemberMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public MemberRole getMemberRole(Long baseId, Long userId) {
        if (baseId == null || userId == null) {
            return null;
        }

        // 先查 Redis 缓存
        String cacheKey = ROLE_CACHE_PREFIX + baseId + ":" + userId;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            if (cached instanceof MemberRole role) {
                return role;
            }
            // 缓存中可能是字符串 "NONE" 标记（非成员）
            if ("NONE".equals(cached.toString())) {
                return null;
            }
        }

        // 查数据库
        BitableBaseMember member = memberMapper.selectByBaseAndUser(baseId, userId);
        MemberRole role = null;
        if (member != null) {
            role = MemberRole.fromCode(member.getRole());
        }

        // 自定义角色提升：用户是某自定义角色成员、且该角色在本 Base 至少一个表/仪表盘
        // 上有非 none 授权时，按最高授权级别提升（edit→EDITOR，view→VIEWER）。
        // 自定义角色的成员关系不在 bitable_base_members 里，不查这条通道会形同虚设。
        MemberRole boost = resolveCustomRoleBoost(baseId, userId);
        if (boost != null && (role == null || boost.isAtLeast(role))) {
            role = boost;
        }

        // 写入缓存（非成员也缓存，避免反复查库）
        if (role != null) {
            redisTemplate.opsForValue().set(cacheKey, role, ROLE_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } else {
            // 用 "NONE" 标记非成员，TTL 缩短为 1 分钟
            redisTemplate.opsForValue().set(cacheKey, "NONE", 1, TimeUnit.MINUTES);
        }

        return role;
    }

    /** 自定义角色对该 Base 的最高授权级别（无成员关系或全为 none 时返回 null） */
    private MemberRole resolveCustomRoleBoost(Long baseId, Long userId) {
        List<BitableBaseCustomRoleMember> memberships = customRoleMemberMapper.selectList(
                new LambdaQueryWrapper<BitableBaseCustomRoleMember>()
                        .eq(BitableBaseCustomRoleMember::getMemberType, "user")
                        .eq(BitableBaseCustomRoleMember::getMemberId, userId));
        if (memberships.isEmpty()) {
            return null;
        }
        List<Long> roleIds = memberships.stream().map(BitableBaseCustomRoleMember::getRoleId).distinct().toList();
        List<BitableBaseRolePermission> perms = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<BitableBaseRolePermission>()
                        .eq(BitableBaseRolePermission::getBaseId, baseId)
                        .eq(BitableBaseRolePermission::getRoleType, "custom")
                        .in(BitableBaseRolePermission::getCustomRoleId, roleIds)
                        .ne(BitableBaseRolePermission::getPermissionLevel, "none"));
        if (perms.isEmpty()) {
            return null;
        }
        boolean editable = perms.stream().anyMatch(p -> "edit".equals(p.getPermissionLevel()));
        return editable ? MemberRole.EDITOR : MemberRole.VIEWER;
    }

    @Override
    public void checkPermission(Long baseId, Long userId, MemberRole requiredRole) {
        MemberRole currentRole = getMemberRole(baseId, userId);
        if (currentRole == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权限访问该多维表格");
        }
        if (!currentRole.isAtLeast(requiredRole)) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "权限不足，需要 " + requiredRole.getLabel() + " 及以上角色，当前角色为 " + currentRole.getLabel());
        }
    }

    @Override
    public void checkManagePermission(Long baseId, Long userId) {
        checkPermission(baseId, userId, MemberRole.ADMIN);
    }

    @Override
    public void checkWritePermission(Long baseId, Long userId) {
        checkPermission(baseId, userId, MemberRole.EDITOR);
    }

    @Override
    public void checkReadPermission(Long baseId, Long userId) {
        checkPermission(baseId, userId, MemberRole.VIEWER);
    }

    @Override
    public void checkOwnerPermission(Long baseId, Long userId) {
        checkPermission(baseId, userId, MemberRole.OWNER);
    }

    @Override
    public Long getBaseIdByTableId(Long tableId) {
        if (tableId == null) {
            return null;
        }
        BitableTable table = tableMapper.selectById(tableId);
        return table != null ? table.getBaseId() : null;
    }

    @Override
    public Long getBaseIdByFieldId(Long fieldId) {
        if (fieldId == null) {
            return null;
        }
        BitableField field = fieldMapper.selectById(fieldId);
        if (field == null) {
            return null;
        }
        return getBaseIdByTableId(field.getTableId());
    }

    @Override
    public Long getBaseIdByRecordId(Long recordId) {
        if (recordId == null) {
            return null;
        }
        BitableRecord record = recordMapper.selectById(recordId);
        if (record == null) {
            return null;
        }
        return getBaseIdByTableId(record.getTableId());
    }

    @Override
    public Long getBaseIdByViewId(Long viewId) {
        if (viewId == null) {
            return null;
        }
        BitableView view = viewMapper.selectById(viewId);
        if (view == null) {
            return null;
        }
        return getBaseIdByTableId(view.getTableId());
    }

    @Override
    public Long getBaseIdByCommentId(Long commentId) {
        if (commentId == null) {
            return null;
        }
        BitableComment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            return null;
        }
        // BitableComment 有 tableId 字段，直接通过 tableId 反查
        return getBaseIdByTableId(comment.getTableId());
    }

    @Override
    public Long getBaseIdByDashboardId(Long dashboardId) {
        if (dashboardId == null) {
            return null;
        }
        BitableDashboard dashboard = dashboardMapper.selectById(dashboardId);
        return dashboard != null ? dashboard.getBaseId() : null;
    }

    @Override
    public Long getBaseIdByWebhookId(Long webhookId) {
        if (webhookId == null) {
            return null;
        }
        BitableWebhookSubscription webhook = webhookMapper.selectById(webhookId);
        return webhook != null ? webhook.getBaseId() : null;
    }

    @Override
    public void clearRoleCache(Long baseId) {
        // SCAN 增量遍历替代 KEYS（KEYS 会阻塞 Redis，Base 成员多/键多时影响线上）
        var pattern = ROLE_CACHE_PREFIX + baseId + ":*";
        java.util.Set<String> keys = new java.util.HashSet<>();
        try (var cursor = redisTemplate.scan(
                org.springframework.data.redis.core.ScanOptions.scanOptions().match(pattern).count(500).build())) {
            while (cursor.hasNext()) {
                keys.add(String.valueOf(cursor.next()));
            }
        } catch (Exception e) {
            // SCAN 不可用时退化为精确清库：删除成员列表中每个用户的缓存键
            var members = memberMapper.selectByBaseId(baseId);
            for (var member : members) {
                keys.add(ROLE_CACHE_PREFIX + baseId + ":" + member.getUserId());
            }
        }
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Override
    public void clearRoleCache(Long baseId, Long userId) {
        String cacheKey = ROLE_CACHE_PREFIX + baseId + ":" + userId;
        redisTemplate.delete(cacheKey);
    }
}
