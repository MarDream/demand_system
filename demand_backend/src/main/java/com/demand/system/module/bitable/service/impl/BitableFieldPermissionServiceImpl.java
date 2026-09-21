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
import com.demand.system.module.bitable.util.BitableJsonUtils;
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

    public static final String LEVEL_ADD_ONLY = "add_only";
    /** add_only：字段可见、新增记录时可填写，但不能修改已有记录的字段值 */
    private static final Set<String> VALID_LEVELS = Set.of(LEVEL_EDITABLE, LEVEL_ADD_ONLY, LEVEL_READONLY, LEVEL_HIDDEN);

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
            dto.setOptionConfig(row.getOptionConfig());
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

            // editable 是默认值只删不插；但带选项级配置时仍要落库（字段可编辑 ≠ 选项全部可编辑）
            boolean hasOptionConfig = change.getOptionConfig() != null && !change.getOptionConfig().isBlank();
            if (!LEVEL_EDITABLE.equals(level) || hasOptionConfig) {
                BitableFieldPermission row = new BitableFieldPermission();
                row.setBaseId(baseId);
                row.setRoleType(change.getRoleType());
                row.setSystemRoleCode("system".equals(change.getRoleType()) ? change.getSystemRoleCode() : null);
                row.setCustomRoleId("custom".equals(change.getRoleType()) ? change.getCustomRoleId() : null);
                row.setTableId(tableId);
                row.setFieldId(change.getFieldId());
                row.setPermissionLevel(level);
                row.setOptionConfig(hasOptionConfig ? change.getOptionConfig() : null);
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
        // editable 是默认值，不必下发（add_only/readonly/hidden 需要返回给调用方区分场景）
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
        assertEditable(field, resolveFieldPermissions(field.getTableId(), userId).get(fieldId), false);
    }

    @Override
    public void checkFieldsEditable(Long tableId, Collection<Long> fieldIds, boolean forCreate, Long userId) {
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
            assertEditable(field, level, forCreate);
        }
    }

    /**
     * 命中只读/隐藏/add_only 时抛错；{@code field} 仅用于报错文案。
     * add_only（可查看+可新增）：新增记录场景放行，修改已有记录拒绝。
     */
    private static void assertEditable(BitableField field, String level, boolean forCreate) {
        String name = field != null ? field.getName() : String.valueOf(field == null ? "" : field.getId());
        if (LEVEL_HIDDEN.equals(level)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "字段「" + name + "」对当前角色不可见，无法修改");
        }
        if (LEVEL_READONLY.equals(level)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "字段「" + name + "」为只读，无法修改");
        }
        if (LEVEL_ADD_ONLY.equals(level) && !forCreate) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "字段「" + name + "」仅可在新增记录时填写，不能修改已有记录");
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

    // ==================== 选项级权限 ====================

    /** 选项级配置解析结果：editableKeys=可编辑选项 label 集合；manage=选项定义管理权限 */
    private record OptionRule(Set<String> editableKeys, String manage) {
    }

    private static final String MANAGE_FULL = "full";
    private static final String MANAGE_ADD_ONLY = "add-only";

    @Override
    public void checkSelectOptionPermission(Long tableId, Long fieldId,
                                            Collection<String> newLabels,
                                            Collection<String> oldLabels,
                                            Long userId) {
        if (tableId == null || fieldId == null || userId == null
                || newLabels == null || newLabels.isEmpty()) {
            return;
        }
        BitableField field = fieldMapper.selectById(fieldId);
        if (field == null || !isSelectFieldType(field.getFieldType())) {
            return;
        }
        OptionRule rule = resolveOptionRule(tableId, fieldId, userId);
        if (rule == null) {
            return;
        }
        String fieldName = field.getName() != null ? field.getName() : String.valueOf(fieldId);
        for (String label : newLabels) {
            if (label == null || rule.editableKeys().contains(label)) {
                continue;
            }
            // 原值里已有的受限选项允许原样保留（也可查看），但不能新增或改选
            if (oldLabels != null && oldLabels.contains(label)) {
                continue;
            }
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "字段「" + fieldName + "」的选项「" + label + "」对当前角色为只读，不能选择");
        }
    }

    @Override
    public void checkFieldOptionManage(Long fieldId,
                                       Collection<String> oldLabels,
                                       Collection<String> newLabels,
                                       Long userId) {
        if (fieldId == null || userId == null
                || oldLabels == null || oldLabels.isEmpty()
                || newLabels == null) {
            return;
        }
        BitableField field = fieldMapper.selectById(fieldId);
        if (field == null || !isSelectFieldType(field.getFieldType())) {
            return;
        }
        Long tableId = field.getTableId();
        if (tableId == null) {
            return;
        }
        OptionRule rule = resolveOptionRule(tableId, fieldId, userId);
        if (rule == null || MANAGE_FULL.equals(rule.manage())) {
            return;
        }
        // add-only：已有选项不可改名/删除，只允许追加
        String fieldName = field.getName() != null ? field.getName() : String.valueOf(fieldId);
        for (String label : oldLabels) {
            if (!newLabels.contains(label)) {
                throw new BusinessException(ErrorCode.FORBIDDEN,
                        "当前角色对字段「" + fieldName + "」仅可新增选项，不能修改或删除已有选项");
            }
        }
    }

    /**
     * 解析用户在指定字段上生效的选项级规则；未配置时返回 null。
     * 用户同时命中多个角色的配置时取并集（可编辑选项取并集，manage 取最宽松）。
     */
    private OptionRule resolveOptionRule(Long tableId, Long fieldId, Long userId) {
        List<BitableFieldPermission> rows = fieldPermissionMapper.selectByTableId(tableId);
        if (rows.isEmpty()) {
            return null;
        }
        Set<String> roleKeys = resolveRoleKeys(tableId, userId);
        if (roleKeys.isEmpty()) {
            return null;
        }
        Set<String> editableKeys = new HashSet<>();
        String manage = null;
        boolean found = false;
        for (BitableFieldPermission row : rows) {
            if (!fieldId.equals(row.getFieldId())
                    || row.getOptionConfig() == null || row.getOptionConfig().isBlank()
                    || !roleKeys.contains(roleIdentifier(row))) {
                continue;
            }
            OptionRule parsed = parseOptionConfig(row.getOptionConfig());
            if (parsed == null) {
                continue;
            }
            found = true;
            editableKeys.addAll(parsed.editableKeys());
            if (MANAGE_FULL.equals(parsed.manage())) {
                manage = MANAGE_FULL;
            } else if (manage == null) {
                manage = parsed.manage();
            }
        }
        return found ? new OptionRule(editableKeys, manage != null ? manage : MANAGE_FULL) : null;
    }

    /** 解析 option_config JSON；结构不合法时返回 null（按未配置处理，不让坏数据锁死写入）。 */
    private OptionRule parseOptionConfig(String json) {
        try {
            Object parsed = BitableJsonUtils.parseJson(json);
            if (!(parsed instanceof Map<?, ?> map)) {
                return null;
            }
            Set<String> editableKeys = new HashSet<>();
            if (map.get("editableKeys") instanceof Collection<?> keys) {
                for (Object key : keys) {
                    if (key != null) {
                        editableKeys.add(String.valueOf(key));
                    }
                }
            }
            String manage = map.get("manage") != null ? String.valueOf(map.get("manage")) : MANAGE_FULL;
            if (!MANAGE_FULL.equals(manage) && !MANAGE_ADD_ONLY.equals(manage)) {
                manage = MANAGE_FULL;
            }
            return new OptionRule(editableKeys, manage);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isSelectFieldType(String fieldType) {
        return "single_select".equals(fieldType) || "multi_select".equals(fieldType);
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
        for (Long roleId : customRoleMapper.selectGlobalCustomRoleIdsByMember(userId)) {
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
            case LEVEL_EDITABLE -> 4;
            case LEVEL_ADD_ONLY -> 3;
            case LEVEL_READONLY -> 2;
            default -> 1;
        };
    }
}
