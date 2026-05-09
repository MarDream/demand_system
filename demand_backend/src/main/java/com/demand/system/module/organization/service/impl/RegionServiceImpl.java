package com.demand.system.module.organization.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.module.organization.dto.RegionCreateDTO;
import com.demand.system.module.organization.dto.RegionSortDTO;
import com.demand.system.module.organization.dto.RegionUpdateDTO;
import com.demand.system.module.organization.dto.RegionVO;
import com.demand.system.module.organization.entity.Department;
import com.demand.system.module.organization.entity.Region;
import com.demand.system.module.organization.mapper.DepartmentMapper;
import com.demand.system.module.organization.mapper.RegionMapper;
import com.demand.system.module.organization.service.RegionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RegionServiceImpl implements RegionService {

    private final RegionMapper regionMapper;
    private final DepartmentMapper departmentMapper;

    @Override
    public List<RegionVO> getTree() {
        List<Region> allRegions = regionMapper.selectList(
            new LambdaQueryWrapper<Region>()
                .orderByAsc(Region::getSortOrder)
                .orderByAsc(Region::getId)
        );

        List<RegionVO> voList = allRegions.stream()
            .map(this::convertToVO)
            .collect(Collectors.toList());

        return buildTree(voList, null);
    }

    @Override
    public RegionVO getById(Long id) {
        Region region = regionMapper.selectById(id);
        if (region == null) {
            throw new BusinessException("区域不存在");
        }
        return convertToVO(region);
    }

    @Override
    public void create(RegionCreateDTO dto) {
        // 检查父区域是否存在
        if (dto.getParentId() != null) {
            Region parent = regionMapper.selectById(dto.getParentId());
            if (parent == null) {
                throw new BusinessException("父区域不存在");
            }
        }

        Region region = new Region();
        BeanUtils.copyProperties(dto, region);
        regionMapper.insert(region);
    }

    @Override
    public void update(RegionUpdateDTO dto) {
        Region region = regionMapper.selectById(dto.getId());
        if (region == null) {
            throw new BusinessException("区域不存在");
        }

        // 检查父区域是否存在
        if (dto.getParentId() != null) {
            if (dto.getParentId().equals(dto.getId())) {
                throw new BusinessException("不能将自己设置为父区域");
            }
            Region parent = regionMapper.selectById(dto.getParentId());
            if (parent == null) {
                throw new BusinessException("父区域不存在");
            }
        }

        BeanUtils.copyProperties(dto, region);
        regionMapper.updateById(region);
    }

    @Override
    public void delete(Long id) {
        Region region = regionMapper.selectById(id);
        if (region == null) {
            throw new BusinessException("区域不存在");
        }

        // 检查是否有子区域
        Long childCount = regionMapper.selectCount(
            new LambdaQueryWrapper<Region>()
                .eq(Region::getParentId, id)
        );
        if (childCount > 0) {
            throw new BusinessException("该区域下存在子区域，无法删除");
        }

        // 检查是否有部门
        Long deptCount = departmentMapper.selectCount(
            new LambdaQueryWrapper<Department>()
                .eq(Department::getRegionId, id)
        );
        if (deptCount > 0) {
            throw new BusinessException("该区域下存在部门，无法删除");
        }

        regionMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSort(RegionSortDTO dto) {
        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            return;
        }

        for (RegionSortDTO.SortItem item : dto.getItems()) {
            Region region = regionMapper.selectById(item.getId());
            if (region != null) {
                region.setSortOrder(item.getSortOrder());
                regionMapper.updateById(region);
            }
        }
    }

    private RegionVO convertToVO(Region region) {
        RegionVO vo = new RegionVO();
        BeanUtils.copyProperties(region, vo);
        return vo;
    }

    private List<RegionVO> buildTree(List<RegionVO> allRegions, Long parentId) {
        return allRegions.stream()
            .filter(region -> {
                if (parentId == null) {
                    return region.getParentId() == null;
                }
                return parentId.equals(region.getParentId());
            })
            .peek(region -> {
                List<RegionVO> children = buildTree(allRegions, region.getId());
                region.setChildren(children.isEmpty() ? null : children);
            })
            .collect(Collectors.toList());
    }
}
