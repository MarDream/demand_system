package com.demand.system.module.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.module.user.entity.Department;
import com.demand.system.module.user.entity.Position;
import com.demand.system.module.user.entity.Region;
import com.demand.system.module.user.entity.UserOrganization;
import com.demand.system.module.user.mapper.DepartmentMapper;
import com.demand.system.module.user.mapper.PositionMapper;
import com.demand.system.module.user.mapper.RegionMapper;
import com.demand.system.module.user.mapper.UserOrganizationMapper;
import com.demand.system.module.user.service.OrganizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrganizationServiceImpl implements OrganizationService {

    private final RegionMapper regionMapper;
    private final DepartmentMapper departmentMapper;
    private final PositionMapper positionMapper;
    private final UserOrganizationMapper userOrganizationMapper;

    @Override
    public List<Region> getRegionTree() {
        List<Region> all = regionMapper.selectList(
                new LambdaQueryWrapper<Region>().orderByAsc(Region::getSortOrder));
        return buildRegionTree(all, null);
    }

    @Override
    public List<Department> getDepartmentTree() {
        List<Department> all = departmentMapper.selectList(
                new LambdaQueryWrapper<Department>().orderByAsc(Department::getSortOrder));
        return buildDepartmentTree(all, null);
    }

    @Override
    public List<Position> listPositions() {
        return positionMapper.selectList(
                new LambdaQueryWrapper<Position>().orderByAsc(Position::getLevel));
    }

    @Override
    public void createRegion(Region region) {
        regionMapper.insert(region);
    }

    @Override
    public void updateRegion(Region region) {
        regionMapper.updateById(region);
    }

    @Override
    public void createDepartment(Department dept) {
        departmentMapper.insert(dept);
    }

    @Override
    public void updateDepartment(Department dept) {
        departmentMapper.updateById(dept);
    }

    @Override
    public void createPosition(Position position) {
        positionMapper.insert(position);
    }

    @Override
    public void updatePosition(Position position) {
        positionMapper.updateById(position);
    }

    @Override
    public void updateOrg(Long userId, Long regionId, Long departmentId, Long positionId, String systemRole) {
        LambdaQueryWrapper<UserOrganization> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserOrganization::getUserId, userId);
        List<UserOrganization> orgs = userOrganizationMapper.selectList(wrapper);
        if (orgs.isEmpty()) {
            throw new BusinessException("用户组织关系不存在");
        }
        UserOrganization org = orgs.get(0);
        org.setRegionId(regionId);
        org.setDepartmentId(departmentId);
        org.setPositionId(positionId);
        org.setSystemRole(systemRole);
        userOrganizationMapper.updateById(org);
    }

    @Override
    public void deleteRegion(Long id) {
        // Check if any department references this region
        Long deptCount = departmentMapper.selectCount(
                new LambdaQueryWrapper<Department>().eq(Department::getRegionId, id));
        if (deptCount > 0) {
            throw new BusinessException("该区域下存在关联部门，无法删除");
        }
        // Check if any user references this region
        Long userCount = userOrganizationMapper.selectCount(
                new LambdaQueryWrapper<UserOrganization>().eq(UserOrganization::getRegionId, id));
        if (userCount > 0) {
            throw new BusinessException("该区域下存在关联用户，无法删除");
        }
        regionMapper.deleteById(id);
    }

    @Override
    public void deleteDepartment(Long id) {
        // Check for child departments
        Long childCount = departmentMapper.selectCount(
                new LambdaQueryWrapper<Department>().eq(Department::getParentId, id));
        if (childCount > 0) {
            throw new BusinessException("该部门下存在子部门，无法删除");
        }
        // Check if any user references this department
        Long userCount = userOrganizationMapper.selectCount(
                new LambdaQueryWrapper<UserOrganization>().eq(UserOrganization::getDepartmentId, id));
        if (userCount > 0) {
            throw new BusinessException("该部门下存在关联用户，无法删除");
        }
        departmentMapper.deleteById(id);
    }

    @Override
    public void deletePosition(Long id) {
        // Check if any user references this position
        Long userCount = userOrganizationMapper.selectCount(
                new LambdaQueryWrapper<UserOrganization>().eq(UserOrganization::getPositionId, id));
        if (userCount > 0) {
            throw new BusinessException("该岗位下存在关联用户，无法删除");
        }
        positionMapper.deleteById(id);
    }

    private List<Region> buildRegionTree(List<Region> all, Long parentId) {
        return all.stream()
                .filter(r -> (parentId == null && r.getParentId() == null)
                        || (parentId != null && parentId.equals(r.getParentId())))
                .peek(r -> r.setChildren(buildRegionTree(all, r.getId())))
                .collect(Collectors.toList());
    }

    private List<Department> buildDepartmentTree(List<Department> all, Long parentId) {
        return all.stream()
                .filter(d -> (parentId == null && d.getParentId() == null)
                        || (parentId != null && parentId.equals(d.getParentId())))
                .peek(d -> d.setChildren(buildDepartmentTree(all, d.getId())))
                .collect(Collectors.toList());
    }
}
