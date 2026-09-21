package com.demand.system.module.bitable.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.util.UserNameResolver;
import com.demand.system.module.bitable.constant.OperationType;
import com.demand.system.module.bitable.converter.BitableConverter;
import com.demand.system.module.bitable.dto.BitableBaseCreateDTO;
import com.demand.system.module.bitable.dto.BitableBaseUpdateDTO;
import com.demand.system.module.bitable.dto.BitableBaseVO;
import com.demand.system.module.bitable.entity.BitableBase;
import com.demand.system.module.bitable.entity.BitableBaseCustomRoleMember;
import com.demand.system.module.bitable.entity.BitableBaseMember;
import com.demand.system.module.bitable.entity.BitableBaseRolePermission;
import com.demand.system.module.bitable.entity.BitableDashboard;
import com.demand.system.module.bitable.entity.BitableDashboardWidget;
import com.demand.system.module.bitable.entity.BitableTable;
import com.demand.system.module.bitable.mapper.*;
import com.demand.system.module.bitable.service.BitableBaseService;
import com.demand.system.module.bitable.util.BitableAuditHelper;
import com.demand.system.module.bitable.util.BitableJsonUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多维表格容器 Service 实现
 */
@Service
public class BitableBaseServiceImpl implements BitableBaseService {

    private final BitableBaseMapper baseMapper;
    private final BitableBaseGroupMapper baseGroupMapper;
    private final BitableTableMapper tableMapper;
    private final BitableFieldMapper fieldMapper;
    private final BitableRecordMapper recordMapper;
    private final BitableCellMapper cellMapper;
    private final BitableViewMapper viewMapper;
    private final BitableCommentMapper commentMapper;
    private final BitableDashboardMapper dashboardMapper;
    private final BitableDashboardWidgetMapper dashboardWidgetMapper;
    private final BitableBaseMemberMapper memberMapper;
    private final BitableAutomationMapper automationMapper;
    private final BitableBaseCustomRoleMemberMapper customRoleMemberMapper;
    private final BitableBaseRolePermissionMapper rolePermissionMapper;
    private final BitableConverter converter;
    private final UserNameResolver userNameResolver;
    private final BitableAuditHelper auditHelper;

    public BitableBaseServiceImpl(BitableBaseMapper baseMapper,
                                  BitableBaseGroupMapper baseGroupMapper,
                                  BitableTableMapper tableMapper,
                                  BitableFieldMapper fieldMapper,
                                  BitableRecordMapper recordMapper,
                                  BitableCellMapper cellMapper,
                                  BitableViewMapper viewMapper,
                                  BitableCommentMapper commentMapper,
                                  BitableDashboardMapper dashboardMapper,
                                  BitableDashboardWidgetMapper dashboardWidgetMapper,
                                  BitableBaseMemberMapper memberMapper,
                                  BitableAutomationMapper automationMapper,
                                  BitableBaseCustomRoleMemberMapper customRoleMemberMapper,
                                  BitableBaseRolePermissionMapper rolePermissionMapper,
                                  BitableConverter converter,
                                  UserNameResolver userNameResolver,
                                  BitableAuditHelper auditHelper) {
        this.baseMapper = baseMapper;
        this.baseGroupMapper = baseGroupMapper;
        this.tableMapper = tableMapper;
        this.fieldMapper = fieldMapper;
        this.recordMapper = recordMapper;
        this.cellMapper = cellMapper;
        this.viewMapper = viewMapper;
        this.commentMapper = commentMapper;
        this.dashboardMapper = dashboardMapper;
        this.dashboardWidgetMapper = dashboardWidgetMapper;
        this.memberMapper = memberMapper;
        this.automationMapper = automationMapper;
        this.customRoleMemberMapper = customRoleMemberMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.converter = converter;
        this.userNameResolver = userNameResolver;
        this.auditHelper = auditHelper;
    }

    @Override
    public List<BitableBaseVO> listBases(Long userId) {
        List<BitableBase> created = baseMapper.selectByCreator(userId);
        List<BitableBase> memberOf = baseMapper.selectByMember(userId);

        // 合并去重（按 id）
        Map<Long, BitableBase> merged = new LinkedHashMap<>();
        for (BitableBase b : created) {
            merged.put(b.getId(), b);
        }
        for (BitableBase b : memberOf) {
            merged.putIfAbsent(b.getId(), b);
        }

        // 自定义角色通道：用户是某自定义角色成员、且该角色对某 Base 至少一个表/仪表盘
        // 有非 none 授权时，该 Base 对用户可见（否则高级权限里配了的表在目录树永远不出现）
        List<Long> roleIds = customRoleMemberMapper.selectList(
                        new LambdaQueryWrapper<BitableBaseCustomRoleMember>()
                                .eq(BitableBaseCustomRoleMember::getMemberType, "user")
                                .eq(BitableBaseCustomRoleMember::getMemberId, userId))
                .stream().map(BitableBaseCustomRoleMember::getRoleId).distinct().toList();
        if (!roleIds.isEmpty()) {
            List<BitableBaseRolePermission> perms = rolePermissionMapper.selectList(
                    new LambdaQueryWrapper<BitableBaseRolePermission>()
                            .eq(BitableBaseRolePermission::getRoleType, "custom")
                            .in(BitableBaseRolePermission::getCustomRoleId, roleIds)
                            .ne(BitableBaseRolePermission::getPermissionLevel, "none"));
            for (Long baseId : perms.stream().map(BitableBaseRolePermission::getBaseId).distinct().toList()) {
                if (merged.containsKey(baseId)) continue;
                BitableBase base = baseMapper.selectById(baseId);
                if (base != null && (base.getDeletedAt() == null || base.getDeletedAt() == 0)) {
                    merged.put(baseId, base);
                }
            }
        }

        List<BitableBaseVO> result = new ArrayList<>(merged.size());
        for (BitableBase base : merged.values()) {
            BitableBaseVO vo = converter.toBaseVO(base);
            vo.setCreatorName(userNameResolver.resolveUserName(base.getCreatorId(), "未知用户"));
            // tableCount: 使用 countByBaseId 填充
            vo.setTableCount(tableMapper.countByBaseId(base.getId()));
            result.add(vo);
        }
        return result;
    }

    @Override
    public BitableBaseVO getBaseById(Long id) {
        BitableBase base = baseMapper.selectDetailById(id);
        if (base == null) {
            throw new BusinessException("多维表格不存在");
        }
        BitableBaseVO vo = converter.toBaseVO(base);
        vo.setCreatorName(userNameResolver.resolveUserName(base.getCreatorId(), "未知用户"));
        // selectDetailById 已通过 COUNT 聚合填充 tableCount，但 BitableBase 实体没有该字段，
        // MyBatis 会将额外列映射到同名字段；实体无 tableCount 字段，所以这里手动 count。
        if (vo.getTableCount() == null) {
            vo.setTableCount(tableMapper.countByBaseId(id));
        }
        return vo;
    }

    /**
     * 同名检测：同一分组（含未分组）下同类型的多维表格名必须唯一
     */
    private void checkBaseNameAvailable(Long groupId, String name, Long excludeBaseId) {
        if (baseMapper.countSameNameInGroup(groupId, name, excludeBaseId) > 0) {
            throw new BusinessException("同级已存在同名多维表格「" + name + "」");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createBase(BitableBaseCreateDTO dto, Long userId) {
        Long groupId = dto.getGroupId();
        if (groupId != null && baseGroupMapper.selectById(groupId) == null) {
            throw new BusinessException("目标分组不存在");
        }
        String name = dto.getName() == null ? null : dto.getName().trim();
        if (name != null) {
            checkBaseNameAvailable(groupId, name, null);
        }

        BitableBase base = new BitableBase();
        BeanUtils.copyProperties(dto, base);
        base.setName(name);
        base.setGroupId(groupId);
        base.setCreatorId(userId);
        base.setIsTemplate(0);
        base.setSortOrder(0);
        baseMapper.insert(base);

        // 自动将创建者加入 base_members 表，role=owner
        BitableBaseMember member = new BitableBaseMember();
        member.setBaseId(base.getId());
        member.setUserId(userId);
        member.setRole("owner");
        memberMapper.insert(member);

        // 审计
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("name", dto.getName());
        auditHelper.record(base.getId(), null, userId, OperationType.CREATE_BASE,
                BitableJsonUtils.toJsonString(detail));

        return base.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateBase(Long id, BitableBaseUpdateDTO dto, Long userId) {
        BitableBase existing = baseMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("多维表格不存在");
        }
        if (dto.getName() != null && !dto.getName().equals(existing.getName())) {
            checkBaseNameAvailable(existing.getGroupId(), dto.getName().trim(), id);
        }

        UpdateWrapper<BitableBase> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", id);
        if (dto.getName() != null) {
            wrapper.set("name", dto.getName().trim());
        }
        if (dto.getDescription() != null) {
            wrapper.set("description", dto.getDescription());
        }
        if (dto.getIcon() != null) {
            wrapper.set("icon", dto.getIcon());
        }
        if (dto.getCoverColor() != null) {
            wrapper.set("cover_color", dto.getCoverColor());
        }
        baseMapper.update(null, wrapper);

        // 审计：记录变更的字段
        Map<String, Object> detail = new LinkedHashMap<>();
        if (dto.getName() != null) {
            detail.put("before", existing.getName());
            detail.put("after", dto.getName());
        }
        auditHelper.record(id, null, userId, OperationType.UPDATE_BASE, BitableJsonUtils.toJsonString(detail));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBase(Long id) {
        BitableBase existing = baseMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("多维表格不存在");
        }

        // 级联删除：逐表删字段/单元格/记录/视图/评论，再删成员与自动化，最后软删 base
        List<BitableTable> tables = tableMapper.selectByBaseId(id);
        for (BitableTable table : tables) {
            Long tableId = table.getId();
            // 软删字段
            fieldMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.demand.system.module.bitable.entity.BitableField>()
                    .eq(com.demand.system.module.bitable.entity.BitableField::getTableId, tableId));
            // 物理删单元格（需在记录删除前按表清理）+ 记录
            cellMapper.deleteByTableId(tableId);
            recordMapper.deleteByTableId(tableId);
            // 软删视图
            viewMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.demand.system.module.bitable.entity.BitableView>()
                    .eq(com.demand.system.module.bitable.entity.BitableView::getTableId, tableId));
            // 软删评论
            commentMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.demand.system.module.bitable.entity.BitableComment>()
                    .eq(com.demand.system.module.bitable.entity.BitableComment::getTableId, tableId));
            // 软删数据表
            tableMapper.deleteById(tableId);
        }

        // 级联删除仪表盘：原先只删了数据表，仪表盘会变成挂在已删 Base 上的孤儿行
        // （bitable_dashboards 有 @TableLogic，软删；组件表无 deleted_at，物理删）
        List<BitableDashboard> dashboards = dashboardMapper.selectList(
                new LambdaQueryWrapper<BitableDashboard>().eq(BitableDashboard::getBaseId, id));
        for (BitableDashboard dashboard : dashboards) {
            dashboardWidgetMapper.delete(new LambdaQueryWrapper<BitableDashboardWidget>()
                    .eq(BitableDashboardWidget::getDashboardId, dashboard.getId()));
            dashboardMapper.deleteById(dashboard.getId());
        }

        // 删除成员（物理删除，无 deleted_at）
        memberMapper.deleteByBaseId(id);

        // 停用该 Base 下的自动化规则，防止残留规则对已删数据继续触发
        automationMapper.disableByBaseId(id);

        // 软删 base
        baseMapper.deleteById(id);
    }
}