package com.demand.system.module.organization.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.PageResult;
import com.demand.system.module.organization.dto.*;
import com.demand.system.module.organization.entity.Department;
import com.demand.system.module.organization.entity.Region;
import com.demand.system.module.organization.mapper.DepartmentMapper;
import com.demand.system.module.organization.mapper.RegionMapper;
import com.demand.system.module.organization.service.DepartmentService;
import com.demand.system.module.user.entity.User;
import com.demand.system.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentMapper departmentMapper;
    private final RegionMapper regionMapper;
    private final UserMapper userMapper;

    @Override
    public List<DepartmentVO> getTree() {
        List<Department> all = departmentMapper.selectList(
            new LambdaQueryWrapper<Department>()
                .orderByAsc(Department::getSortOrder)
                .orderByAsc(Department::getId)
        );
        List<DepartmentVO> voList = all.stream()
            .map(this::convertToVO)
            .collect(Collectors.toList());
        return buildTree(voList, null);
    }

    private List<DepartmentVO> buildTree(List<DepartmentVO> all, Long parentId) {
        return all.stream()
            .filter(vo -> {
                if (parentId == null) {
                    return vo.getParentId() == null;
                }
                return parentId.equals(vo.getParentId());
            })
            .peek(vo -> {
                List<DepartmentVO> children = buildTree(all, vo.getId());
                vo.setChildren(children.isEmpty() ? null : children);
            })
            .collect(Collectors.toList());
    }

    @Override
    public PageResult<DepartmentVO> list(DepartmentQueryDTO query) {
        Page<Department> page = new Page<>(query.getPageNum(), query.getPageSize());

        LambdaQueryWrapper<Department> wrapper = new LambdaQueryWrapper<>();
        if (query.getRegionId() != null) {
            wrapper.eq(Department::getRegionId, query.getRegionId());
        }
        if (StringUtils.hasText(query.getName())) {
            wrapper.like(Department::getName, query.getName());
        }
        wrapper.orderByAsc(Department::getSortOrder)
               .orderByAsc(Department::getId);

        Page<Department> result = departmentMapper.selectPage(page, wrapper);

        List<DepartmentVO> voList = result.getRecords().stream()
            .map(this::convertToVO)
            .collect(Collectors.toList());

        return new PageResult<>(voList, result.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    public DepartmentVO getById(Long id) {
        Department department = departmentMapper.selectById(id);
        if (department == null) {
            throw new BusinessException("部门不存在");
        }
        return convertToVO(department);
    }

    @Override
    public void create(DepartmentCreateDTO dto) {
        // 检查区域是否存在
        if (dto.getRegionId() != null) {
            Region region = regionMapper.selectById(dto.getRegionId());
            if (region == null) {
                throw new BusinessException("区域不存在");
            }
        }

        // 检查负责人是否存在
        if (dto.getLeaderId() != null) {
            User leader = userMapper.selectById(dto.getLeaderId());
            if (leader == null) {
                throw new BusinessException("负责人不存在");
            }
        }

        Department department = new Department();
        BeanUtils.copyProperties(dto, department);
        departmentMapper.insert(department);
    }

    @Override
    public void update(DepartmentUpdateDTO dto) {
        Department department = departmentMapper.selectById(dto.getId());
        if (department == null) {
            throw new BusinessException("部门不存在");
        }

        // 检查区域是否存在
        if (dto.getRegionId() != null) {
            Region region = regionMapper.selectById(dto.getRegionId());
            if (region == null) {
                throw new BusinessException("区域不存在");
            }
        }

        // 检查负责人是否存在
        if (dto.getLeaderId() != null) {
            User leader = userMapper.selectById(dto.getLeaderId());
            if (leader == null) {
                throw new BusinessException("负责人不存在");
            }
        }

        BeanUtils.copyProperties(dto, department);
        departmentMapper.updateById(department);
    }

    @Override
    public void delete(Long id) {
        Department department = departmentMapper.selectById(id);
        if (department == null) {
            throw new BusinessException("部门不存在");
        }

        // 检查是否有用户
        Long userCount = userMapper.selectCount(
            new LambdaQueryWrapper<User>()
                .eq(User::getDepartmentId, id)
        );
        if (userCount > 0) {
            throw new BusinessException("该部门下存在用户，无法删除");
        }

        departmentMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSort(DepartmentSortDTO dto) {
        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            return;
        }

        for (DepartmentSortDTO.SortItem item : dto.getItems()) {
            Department department = departmentMapper.selectById(item.getId());
            if (department != null) {
                department.setSortOrder(item.getSortOrder());
                departmentMapper.updateById(department);
            }
        }
    }

    private DepartmentVO convertToVO(Department department) {
        DepartmentVO vo = new DepartmentVO();
        BeanUtils.copyProperties(department, vo);

        // 填充区域名称
        if (department.getRegionId() != null) {
            Region region = regionMapper.selectById(department.getRegionId());
            if (region != null) {
                vo.setRegionName(region.getName());
            }
        }

        // 填充负责人名称
        if (department.getLeaderId() != null) {
            User leader = userMapper.selectById(department.getLeaderId());
            if (leader != null) {
                vo.setLeaderName(leader.getRealName());
            }
        }

        return vo;
    }
}
