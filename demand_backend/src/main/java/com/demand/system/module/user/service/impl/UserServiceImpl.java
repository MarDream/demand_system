package com.demand.system.module.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.PageResult;
import com.demand.system.module.user.dto.UserCreateDTO;
import com.demand.system.module.user.dto.UserQueryDTO;
import com.demand.system.module.user.dto.UserUpdateDTO;
import com.demand.system.module.user.dto.UserVO;
import com.demand.system.module.user.entity.Department;
import com.demand.system.module.user.entity.Position;
import com.demand.system.module.user.entity.Region;
import com.demand.system.module.user.entity.User;
import com.demand.system.module.user.entity.UserOrganization;
import com.demand.system.module.user.mapper.DepartmentMapper;
import com.demand.system.module.user.mapper.PositionMapper;
import com.demand.system.module.user.mapper.RegionMapper;
import com.demand.system.module.user.mapper.UserMapper;
import com.demand.system.module.user.mapper.UserOrganizationMapper;
import com.demand.system.module.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final UserOrganizationMapper userOrganizationMapper;
    private final RegionMapper regionMapper;
    private final DepartmentMapper departmentMapper;
    private final PositionMapper positionMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public PageResult<UserVO> list(UserQueryDTO query) {
        Page<User> page = new Page<>(query.getPageNum(), query.getPageSize());

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(query.getUsername() != null, User::getUsername, query.getUsername())
                .like(query.getRealName() != null, User::getRealName, query.getRealName())
                .eq(query.getStatus() != null, User::getStatus, query.getStatus())
                .orderByDesc(User::getCreatedAt);

        // If filtering by org, collect matching userIds first
        Set<Long> userIds = null;
        if (query.getRegionId() != null || query.getDepartmentId() != null) {
            LambdaQueryWrapper<UserOrganization> orgWrapper = new LambdaQueryWrapper<>();
            orgWrapper.eq(query.getRegionId() != null, UserOrganization::getRegionId, query.getRegionId())
                    .eq(query.getDepartmentId() != null, UserOrganization::getDepartmentId, query.getDepartmentId());
            List<UserOrganization> orgs = userOrganizationMapper.selectList(orgWrapper);
            userIds = orgs.stream().map(UserOrganization::getUserId).collect(Collectors.toSet());
            if (userIds.isEmpty()) {
                return new PageResult<>(Collections.emptyList(), 0, query.getPageNum(), query.getPageSize());
            }
            wrapper.in(User::getId, userIds);
        }

        Page<User> userPage = userMapper.selectPage(page, wrapper);

        List<UserVO> voList = userPage.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return new PageResult<>(voList, userPage.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    public UserVO getById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return toVO(user);
    }

    @Override
    public void create(UserCreateDTO dto) {
        // Check username unique
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, dto.getUsername());
        if (userMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("用户名已存在");
        }

        User user = new User();
        BeanUtil.copyProperties(dto, user);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setStatus(User.STATUS_ACTIVE);
        userMapper.insert(user);

        // Create default org record
        UserOrganization org = new UserOrganization();
        org.setUserId(user.getId());
        org.setSystemRole("member");
        org.setEffectiveDate(java.time.LocalDate.now());
        userOrganizationMapper.insert(org);
    }

    @Override
    public void update(UserUpdateDTO dto) {
        User user = userMapper.selectById(dto.getId());
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        if (dto.getRealName() != null) {
            user.setRealName(dto.getRealName());
        }
        if (dto.getEmail() != null) {
            user.setEmail(dto.getEmail());
        }
        if (dto.getPhone() != null) {
            user.setPhone(dto.getPhone());
        }
        if (dto.getAvatar() != null) {
            user.setAvatar(dto.getAvatar());
        }
        if (dto.getStatus() != null) {
            user.setStatus(dto.getStatus());
        }
        userMapper.updateById(user);
    }

    @Override
    public void delete(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        userMapper.deleteById(id);
    }

    private UserVO toVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setAvatar(user.getAvatar());
        vo.setStatus(user.getStatus());
        vo.setCreatedAt(user.getCreatedAt());
        vo.setUpdatedAt(user.getUpdatedAt());

        // Fill org info
        LambdaQueryWrapper<UserOrganization> orgWrapper = new LambdaQueryWrapper<>();
        orgWrapper.eq(UserOrganization::getUserId, user.getId());
        List<UserOrganization> orgs = userOrganizationMapper.selectList(orgWrapper);
        if (!orgs.isEmpty()) {
            UserOrganization org = orgs.get(0);
            vo.setSystemRole(org.getSystemRole());
            if (org.getRegionId() != null) {
                Region region = regionMapper.selectById(org.getRegionId());
                if (region != null) {
                    vo.setRegionName(region.getName());
                }
            }
            if (org.getDepartmentId() != null) {
                Department dept = departmentMapper.selectById(org.getDepartmentId());
                if (dept != null) {
                    vo.setDepartmentName(dept.getName());
                }
            }
            if (org.getPositionId() != null) {
                Position position = positionMapper.selectById(org.getPositionId());
                if (position != null) {
                    vo.setPositionName(position.getName());
                }
            }
        }
        return vo;
    }
}
