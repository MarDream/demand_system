package com.demand.system.module.bitable.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.util.UserNameResolver;
import com.demand.system.module.bitable.converter.BitableConverter;
import com.demand.system.module.bitable.dto.BitableBaseCreateDTO;
import com.demand.system.module.bitable.dto.BitableBaseUpdateDTO;
import com.demand.system.module.bitable.dto.BitableBaseVO;
import com.demand.system.module.bitable.entity.BitableBase;
import com.demand.system.module.bitable.entity.BitableBaseMember;
import com.demand.system.module.bitable.entity.BitableTable;
import com.demand.system.module.bitable.mapper.*;
import com.demand.system.module.bitable.service.BitableBaseService;
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
    private final BitableTableMapper tableMapper;
    private final BitableFieldMapper fieldMapper;
    private final BitableRecordMapper recordMapper;
    private final BitableCellMapper cellMapper;
    private final BitableViewMapper viewMapper;
    private final BitableCommentMapper commentMapper;
    private final BitableBaseMemberMapper memberMapper;
    private final BitableConverter converter;
    private final UserNameResolver userNameResolver;

    public BitableBaseServiceImpl(BitableBaseMapper baseMapper,
                                  BitableTableMapper tableMapper,
                                  BitableFieldMapper fieldMapper,
                                  BitableRecordMapper recordMapper,
                                  BitableCellMapper cellMapper,
                                  BitableViewMapper viewMapper,
                                  BitableCommentMapper commentMapper,
                                  BitableBaseMemberMapper memberMapper,
                                  BitableConverter converter,
                                  UserNameResolver userNameResolver) {
        this.baseMapper = baseMapper;
        this.tableMapper = tableMapper;
        this.fieldMapper = fieldMapper;
        this.recordMapper = recordMapper;
        this.cellMapper = cellMapper;
        this.viewMapper = viewMapper;
        this.commentMapper = commentMapper;
        this.memberMapper = memberMapper;
        this.converter = converter;
        this.userNameResolver = userNameResolver;
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createBase(BitableBaseCreateDTO dto, Long userId) {
        BitableBase base = new BitableBase();
        BeanUtils.copyProperties(dto, base);
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

        return base.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateBase(Long id, BitableBaseUpdateDTO dto) {
        BitableBase existing = baseMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("多维表格不存在");
        }

        UpdateWrapper<BitableBase> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", id);
        if (dto.getName() != null) {
            wrapper.set("name", dto.getName());
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
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBase(Long id) {
        BitableBase existing = baseMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("多维表格不存在");
        }

        // 级联删除：逐表删字段/记录/单元格/视图/评论，再删成员，最后软删 base
        List<BitableTable> tables = tableMapper.selectByBaseId(id);
        for (BitableTable table : tables) {
            Long tableId = table.getId();
            // 软删字段
            fieldMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.demand.system.module.bitable.entity.BitableField>()
                    .eq(com.demand.system.module.bitable.entity.BitableField::getTableId, tableId));
            // 物理删记录 + 单元格
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

        // 删除成员（物理删除，无 deleted_at）
        memberMapper.deleteByBaseId(id);

        // 软删 base
        baseMapper.deleteById(id);
    }
}