package com.demand.system.module.organization.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.module.organization.dto.*;
import com.demand.system.module.organization.entity.SysOrg;
import com.demand.system.module.organization.mapper.SysOrgMapper;
import com.demand.system.module.organization.service.SysOrgService;
import com.demand.system.module.user.entity.User;
import com.demand.system.module.user.entity.UserOrganization;
import com.demand.system.module.user.mapper.UserMapper;
import com.demand.system.module.user.mapper.UserOrganizationMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SysOrgServiceImpl implements SysOrgService {

    private final SysOrgMapper sysOrgMapper;
    private final UserMapper userMapper;
    private final UserOrganizationMapper userOrganizationMapper;

    public SysOrgServiceImpl(SysOrgMapper sysOrgMapper, UserMapper userMapper, UserOrganizationMapper userOrganizationMapper) {
        this.sysOrgMapper = sysOrgMapper;
        this.userMapper = userMapper;
        this.userOrganizationMapper = userOrganizationMapper;
    }

    @Override
    public List<SysOrgVO> getTree() {
        List<SysOrg> all = sysOrgMapper.selectList(
                new LambdaQueryWrapper<SysOrg>().orderByAsc(SysOrg::getSortOrder).orderByAsc(SysOrg::getId)
        );
        List<SysOrgVO> voList = all.stream().map(this::toVO).collect(Collectors.toList());
        return buildTree(voList);
    }

    @Override
    public SysOrgVO getDetail(Long id) {
        SysOrg org = sysOrgMapper.selectById(id);
        if (org == null) throw new BusinessException(ErrorCode.NOT_FOUND, "组织不存在");
        return toVO(org);
    }

    @Override
    @Transactional
    public void create(SysOrgCreateDTO dto) {
        SysOrg org = new SysOrg();
        BeanUtils.copyProperties(dto, org);
        if (org.getSortOrder() == null) org.setSortOrder(nextSortOrder(dto.getParentId()));
        sysOrgMapper.insert(org);
        updatePathAndLevel(org);
    }

    @Override
    @Transactional
    public void update(SysOrgUpdateDTO dto) {
        SysOrg existing = sysOrgMapper.selectById(dto.getId());
        if (existing == null) throw new BusinessException(ErrorCode.NOT_FOUND, "组织不存在");

        if (dto.getName() != null) existing.setName(dto.getName());
        if (dto.getOrgType() != null) existing.setOrgType(dto.getOrgType());
        if (dto.getCode() != null) existing.setCode(dto.getCode());
        if (dto.getLeaderId() != null) existing.setLeaderId(dto.getLeaderId());
        if (dto.getDescription() != null) existing.setDescription(dto.getDescription());
        if (dto.getSortOrder() != null) existing.setSortOrder(dto.getSortOrder());

        if (dto.getParentId() != null && !dto.getParentId().equals(existing.getParentId())) {
            if (dto.getParentId().equals(existing.getId())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "不能将自己设为父节点");
            }
            SysOrg newParent = sysOrgMapper.selectById(dto.getParentId());
            if (newParent == null) throw new BusinessException(ErrorCode.NOT_FOUND, "目标父节点不存在");
            if (isDescendant(existing.getId(), dto.getParentId())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "不能将节点移动到自己的子节点下");
            }
            existing.setParentId(dto.getParentId());
        }

        sysOrgMapper.updateById(existing);
        updatePathAndLevel(existing);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        SysOrg org = sysOrgMapper.selectById(id);
        if (org == null) throw new BusinessException(ErrorCode.NOT_FOUND, "组织不存在");

        long childCount = sysOrgMapper.selectCount(
                new LambdaQueryWrapper<SysOrg>().eq(SysOrg::getParentId, id)
        );
        if (childCount > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该组织下有子节点，无法删除");
        }

        sysOrgMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void move(SysOrgMoveDTO dto) {
        SysOrg org = sysOrgMapper.selectById(dto.getId());
        if (org == null) throw new BusinessException(ErrorCode.NOT_FOUND, "节点不存在");

        String oldPath = org.getPath();

        if (dto.getTargetParentId() != null) {
            if (dto.getTargetParentId().equals(org.getId())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "不能将节点移动到自己下");
            }
            if (isDescendant(org.getId(), dto.getTargetParentId())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "不能将节点移动到自己的子节点下");
            }
            SysOrg targetParent = sysOrgMapper.selectById(dto.getTargetParentId());
            if (targetParent == null) throw new BusinessException(ErrorCode.NOT_FOUND, "目标父节点不存在");

            org.setParentId(dto.getTargetParentId());
            org.setLevel(targetParent.getLevel() + 1);
            org.setPath(targetParent.getPath() + org.getId() + "/");
        } else {
            org.setParentId(null);
            org.setLevel(0);
            org.setPath("/" + org.getId() + "/");
        }

        if (dto.getTargetSortOrder() != null) {
            org.setSortOrder(dto.getTargetSortOrder());
        }

        sysOrgMapper.updateById(org);

        // Cascade update descendants path
        if (oldPath != null && !oldPath.equals(org.getPath())) {
            sysOrgMapper.update(null, new LambdaUpdateWrapper<SysOrg>()
                    .likeRight(SysOrg::getPath, oldPath)
                    .ne(SysOrg::getId, org.getId())
                    .setSql("path = REPLACE(path, '" + oldPath + "', '" + org.getPath() + "')")
                    .setSql("level = level + (" + (org.getLevel() - (oldPath.split("/").length - 2)) + ")")
            );
        }

        // Re-index sibling sort orders
        reindexSiblingSortOrders(org.getParentId());
    }

    @Override
    public List<Long> getDescendantIds(Long orgId) {
        SysOrg org = sysOrgMapper.selectById(orgId);
        if (org == null || org.getPath() == null) return List.of(orgId);
        List<SysOrg> descendants = sysOrgMapper.selectList(
                new LambdaQueryWrapper<SysOrg>().likeRight(SysOrg::getPath, org.getPath())
        );
        List<Long> ids = descendants.stream().map(SysOrg::getId).collect(Collectors.toList());
        ids.add(orgId);
        return ids;
    }

    private boolean isDescendant(Long ancestorId, Long nodeId) {
        SysOrg node = sysOrgMapper.selectById(nodeId);
        if (node == null || node.getPath() == null) return false;
        String ancestorPath = "/" + ancestorId + "/";
        return node.getPath().contains(ancestorPath);
    }

    private void updatePathAndLevel(SysOrg org) {
        if (org.getParentId() == null) {
            org.setPath("/" + org.getId() + "/");
            org.setLevel(0);
        } else {
            SysOrg parent = sysOrgMapper.selectById(org.getParentId());
            if (parent != null) {
                org.setPath(parent.getPath() + org.getId() + "/");
                org.setLevel(parent.getLevel() + 1);
            }
        }
        sysOrgMapper.updateById(org);
    }

    private void reindexSiblingSortOrders(Long parentId) {
        List<SysOrg> siblings = sysOrgMapper.selectList(
                new LambdaQueryWrapper<SysOrg>()
                        .eq(parentId != null, SysOrg::getParentId, parentId)
                        .isNull(parentId == null, SysOrg::getParentId)
                        .orderByAsc(SysOrg::getSortOrder)
                        .orderByAsc(SysOrg::getId)
        );
        for (int i = 0; i < siblings.size(); i++) {
            SysOrg s = siblings.get(i);
            if (!Objects.equals(s.getSortOrder(), i)) {
                s.setSortOrder(i);
                sysOrgMapper.updateById(s);
            }
        }
    }

    private Integer nextSortOrder(Long parentId) {
        LambdaQueryWrapper<SysOrg> wrapper = new LambdaQueryWrapper<SysOrg>()
                .orderByDesc(SysOrg::getSortOrder).last("LIMIT 1");
        if (parentId != null) wrapper.eq(SysOrg::getParentId, parentId);
        else wrapper.isNull(SysOrg::getParentId);
        SysOrg last = sysOrgMapper.selectOne(wrapper);
        return last == null || last.getSortOrder() == null ? 0 : last.getSortOrder() + 1;
    }

    private SysOrgVO toVO(SysOrg org) {
        SysOrgVO vo = new SysOrgVO();
        BeanUtils.copyProperties(org, vo);
        // 递归统计该组织及其所有子组织的成员数量
        vo.setMemberCount(countMembersRecursively(org.getId()));
        return vo;
    }

    /**
     * 递归统计组织及其所有子组织的成员数量
     * @param orgId 组织ID
     * @return 成员总数
     */
    private int countMembersRecursively(Long orgId) {
        // 获取当前组织及其所有子组织的ID列表
        List<Long> orgIds = getDescendantIds(orgId);

        // 统计这些组织中的所有成员（去重）
        // 查询 User 表中 orgId、regionId 或 departmentId 在列表中的用户
        long memberCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>()
                        .and(wrapper -> wrapper
                                .in(User::getOrgId, orgIds)
                                .or()
                                .in(User::getRegionId, orgIds)
                                .or()
                                .in(User::getDepartmentId, orgIds)
                        )
        );

        return (int) memberCount;
    }

    private List<SysOrgVO> buildTree(List<SysOrgVO> all) {
        Map<Long, SysOrgVO> map = new LinkedHashMap<>();
        for (SysOrgVO vo : all) map.put(vo.getId(), vo);

        List<SysOrgVO> roots = new ArrayList<>();
        for (SysOrgVO vo : all) {
            if (vo.getParentId() == null) {
                roots.add(vo);
            } else {
                SysOrgVO parent = map.get(vo.getParentId());
                if (parent != null) {
                    if (parent.getChildren() == null) parent.setChildren(new ArrayList<>());
                    parent.getChildren().add(vo);
                } else {
                    roots.add(vo);
                }
            }
        }
        return roots;
    }
}
