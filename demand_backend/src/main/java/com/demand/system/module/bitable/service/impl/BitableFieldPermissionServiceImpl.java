package com.demand.system.module.bitable.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.module.bitable.constant.MemberRole;
import com.demand.system.module.bitable.dto.BitableFieldPermissionDTO;
import com.demand.system.module.bitable.entity.BitableBaseMember;
import com.demand.system.module.bitable.entity.BitableField;
import com.demand.system.module.bitable.entity.BitableFieldPermission;
import com.demand.system.module.bitable.mapper.BitableBaseCustomRoleMapper;
import com.demand.system.module.bitable.mapper.BitableBaseMemberMapper;
import com.demand.system.module.bitable.mapper.BitableFieldMapper;
import com.demand.system.module.bitable.mapper.BitableFieldPermissionMapper;
import com.demand.system.module.bitable.service.BitableAuthorizationService;
import com.demand.system.module.bitable.service.BitableFieldPermissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 多维表格-字段级权限 Service 实现。
 */
@Service
public class BitableFieldPermissionServiceImpl implements BitableFieldPermissionService {

    /** 可编辑：默认值，不落库。 */
    public static final String LEVEL_EDITABLE = "editable";
    /** 只读：可见但不可修改。 */
    public static final String LEVEL_READONLY = "readonly";
    /** 隐藏：既不可见也不可修改。 */
    public static final String LEVEL_HIDDEN = "hidden";

    private static final Set<String> VALID_LEVELS = Set.of(LEVEL_EDITABLE, LEVEL_READONLY, LEVEL_HIDDEN);

    private final BitableFieldPermissionMapper fieldPermissionMapper;
    private final BitableFieldMapper fieldMapper;
    private final BitableBaseMemberMapper memberMapper;
    private final BitableBaseCustomRoleMapper customRoleMapper;
    private final BitableAuthorizationService authorizationService;

    public BitableFieldPermissionServiceImpl(BitableFieldPermissionMapper fieldPermissionMapper,
                                             BitableFieldMapper fieldMapper,
                                             BitableBaseMemberMapper memberMapper,
                                             BitableBaseCustomRoleMapper customRoleMapper,
                                             BitableAuthorizationService authorizationService) {
        this.fieldPermissionMapper = fieldPermissionMapper;
        this.fieldMapper = fieldMapper;
        this.memberMapper = memberMapper;
        this.customRoleMapper = customRoleMapper;
        this.authorizationService = authorizationService;
    }

    @Override
    public List<BitableFieldPermissionDTO> listFieldPermissions(Long baseId, Long tableId, Long userId) {
        authorizationService.checkManagePermission(baseId, userId);

        List<BitableFieldPermission> rows = tableId != null
                ? fieldPermissionMapper.selectByTableId(tableId)
                : fieldPermissionMapper.selectByBaseId(baseId);

        List<BitableFieldPermissionDTO> result = new ArrayList<>();
        for (BitableFieldPermission row : rows) {
            if (!baseId.equals(row.getBaseId())) {
                continue;
            }
            BitableFieldPermissionDTO dto = new BitableFieldPermissionDTO();
            dto.setBaseId(row.getBaseId());
            dto.setRoleType(row.getRoleType());
            dto.setSystemRoleCode(row.getSystemRoleCode());
            dto.setCustomRoleId(row.getCustomRoleId());
            dto.setTableId(row.getTableId());
            dto.setFieldId(row.getFieldId());
            dto.setPermissionLevel(row.getPermissionLevel());
            result.add(dto);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchSaveFieldPermissions(Long baseId, List<BitableFieldPermissionDTO> changes, Long userId) {
        authorizationService.checkManagePermission(baseId, userId);
        if (changes == null || changes.isEmpty()) {
            return;
        }

        for (BitableFieldPermissionDTO change : changes) {
            if (change == null || change.getFieldId() == null || change.getTableId() == null) {
                continue;
            }
            String level = normalizeLevel(change.getPermissionLevel());
            Long tableId = change.getTableId();

            LambdaQueryWrapper<BitableFieldPermission> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(BitableFieldPermission::getBaseId, baseId)
                    .eq(BitableFieldPermission::getTableId, tableId)
                    .eq(BitableFieldPermission::getFieldId, change.getFieldId());
            if ("system".equals(change.getRoleType())) {
                wrapper.eq(BitableFieldPermission::getRoleType, "system")
                        .eq(BitableFieldPermission::getSystemRoleCode, change.getSystemRoleCode());
            } else {
                wrapper.eq(BitableFieldPermission::getRoleType, "custom")
                        .eq(BitableFieldPermission::getCustomRoleId, change.getCustomRoleId());
            }
            fieldPermissionMapper.delete(wrapper);

            // editable 是默认值，只删不插
            if (!LEVEL_EDITABLE.equals(level)) {
                BitableFieldPermission row = new BitableFieldPermission();
                row.setBaseId(baseId);
                row.setRoleType(change.getRoleType());
                row.setSystemRoleCode("system".equals(change.getRoleType()) ? change.getSystemRoleCode() : null);
                row.setCustomRoleId("custom".equals(change.getRoleType()) ? change.getCustomRoleId() : null);
                row.setTableId(tableId);
                row.setFieldId(change.getFieldId());
                row.setPermissionLevel(level);
                row.setCreatorId(userId);
                fieldPermissionMapper.insert(row);
            }
        }
    }

    @Override
    public Map<Long, String> resolveFieldPermissions(Long tableId, Long userId) {
        Map<Long, String> result = new HashMap<>();
        if (tableId == null || userId == null) {
            return result;
        }

        List<BitableFieldPermission> rows = fieldPermissionMapper.selectByTableId(tableId);
        if (rows.isEmpty()) {
            return result;
        }

        Set<String> roleKeys = resolveRoleKeys(tableId, userId);
        if (roleKeys.isEmpty()) {
            return result;
        }

        Map<Long, String> best = new HashMap<>();
        for (BitableFieldPermission row : rows) {
            if (!roleKeys.contains(roleIdentifier(row))) {
                continue;
            }
            String level = normalizeLevel(row.getPermissionLevel());
            String current = best.get(row.getFieldId());
            if (current == null || weight(level) > weight(current)) {
                best.put(row.getFieldId(), level);
            }
        }
        // editable 是默认值，不必下发
        best.forEach((fieldId, level) -> {
            if (!LEVEL_EDITABLE.equals(level)) {
                result.put(fieldId, level);
            }
        });
        return result;
    }

    @Override
    public void checkFieldEditable(Long fieldId, Long userId) {
        if (fieldId == null) {
            return;
        }
        BitableField field = fieldMapper.selectById(fieldId);
        if (field == null || field.getTableId() == null) {
            return;
        }
        assertEditable(field, resolveFieldPermissions(field.getTableId(), userId).get(fieldId));
    }

    @Override
    public void checkFieldsEditable(Long tableId, Collection<Long> fieldIds, Long userId) {
        if (tableId == null || fieldIds == null || fieldIds.isEmpty()) {
            return;
        }
        Map<Long, String> levels = resolveFieldPermissions(tableId, userId);
        if (levels.isEmpty()) {
            // 该表未配置任何字段权限，全部字段可编辑
            return;
        }
        Map<Long, BitableField> fieldById = new HashMap<>();
        for (BitableField field : fieldMapper.selectByTableId(tableId)) {
            fieldById.put(field.getId(), field);
        }
        for (Long fieldId : fieldIds) {
            if (fieldId == null) {
                continue;
            }
            String level = levels.get(fieldId);
            if (level == null) {
                continue;
            }
            BitableField field = fieldById.get(fieldId);
            assertEditable(field, level);
        }
    }

    /** 命中只读/隐藏时抛错；{@code field} 仅用于报错文案。 */
    private static void assertEditable(BitableField field, String level) {
        String name = field != null ? field.getName() : String.valueOf(field == null ? "" : field.getId());
        if (LEVEL_HIDDEN.equals(level)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "字段「" + name + "」对当前角色不可见，无法修改");
        }
        if (LEVEL_READONLY.equals(level)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "字段「" + name + "」为只读，无法修改");
        }
    }

    @Override
    public void deleteByFieldId(Long fieldId) {
        if (fieldId == null) {
            return;
        }
        fieldPermissionMapper.deleteByFieldId(fieldId);
    }

    @Override
    public void deleteByTableId(Long tableId) {
        if (tableId == null) {
            return;
        }
        fieldPermissionMapper.deleteByTableId(tableId);
    }

    /**
     * 解析用户在指定表中的全部角色标识（系统角色 + 自定义角色）。
     *
     * @return {@code Set<"system:editor">} 形式的标识集合
     */
    private Set<String> resolveRoleKeys(Long tableId, Long userId) {
        Set<String> keys = new HashSet<>();
        Long baseId = authorizationService.getBaseIdByTableId(tableId);
        if (baseId == null) {
            return keys;
        }
        BitableBaseMember member = memberMapper.selectByBaseAndUser(baseId, userId);
        if (member != null) {
            MemberRole role = MemberRole.fromCode(member.getRole());
            if (role != null) {
                keys.add("system:" + role.getCode());
            }
        }
        for (Long roleId : customRoleMapper.selectCustomRoleIdsByMember(baseId, userId)) {
            keys.add("custom:" + roleId);
        }
        return keys;
    }

    /** 与数据库生成列 {@code role_identifier} 保持同一套拼法。 */
    private static String roleIdentifier(BitableFieldPermission row) {
        if ("system".equals(row.getRoleType())) {
            return "system:" + row.getSystemRoleCode();
        }
        return "custom:" + row.getCustomRoleId();
    }

    private static String normalizeLevel(String level) {
        if (level == null || level.isBlank()) {
            return LEVEL_EDITABLE;
        }
        String normalized = level.trim().toLowerCase();
        if (!VALID_LEVELS.contains(normalized)) {
            throw new BusinessException("不支持的字段权限级别: " + level);
        }
        return normalized;
    }

    /** 权限宽松度权重，用于多角色取并集。 */
    private static int weight(String level) {
        return switch (level) {
            case LEVEL_EDITABLE -> 3;
            case LEVEL_READONLY -> 2;
            default -> 1;
        };
    }
}
