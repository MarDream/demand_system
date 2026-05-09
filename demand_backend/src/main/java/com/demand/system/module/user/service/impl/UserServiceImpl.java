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
import com.demand.system.module.user.entity.User;
import com.demand.system.module.user.mapper.UserMapper;
import com.demand.system.module.user.service.UserService;
import com.demand.system.module.organization.entity.Department;
import com.demand.system.module.organization.entity.Position;
import com.demand.system.module.organization.entity.Region;
import com.demand.system.module.organization.mapper.DepartmentMapper;
import com.demand.system.module.organization.mapper.PositionMapper;
import com.demand.system.module.organization.mapper.RegionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
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
                .eq(query.getRegionId() != null, User::getRegionId, query.getRegionId())
                .eq(query.getDepartmentId() != null, User::getDepartmentId, query.getDepartmentId())
                .eq(query.getPositionId() != null, User::getPositionId, query.getPositionId())
                .orderByDesc(User::getCreatedAt);

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
        if (dto.getRegionId() != null) {
            user.setRegionId(dto.getRegionId());
        }
        if (dto.getDepartmentId() != null) {
            user.setDepartmentId(dto.getDepartmentId());
        }
        if (dto.getPositionId() != null) {
            user.setPositionId(dto.getPositionId());
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
        vo.setRegionId(user.getRegionId());
        vo.setDepartmentId(user.getDepartmentId());
        vo.setPositionId(user.getPositionId());
        vo.setCreatedAt(user.getCreatedAt());
        vo.setUpdatedAt(user.getUpdatedAt());

        // Fill region info and build region path
        if (user.getRegionId() != null) {
            Region region = regionMapper.selectById(user.getRegionId());
            if (region != null) {
                vo.setRegionName(region.getName());
                vo.setRegionPath(buildRegionPath(region.getId()));
            }
        }

        // Fill department info
        if (user.getDepartmentId() != null) {
            Department dept = departmentMapper.selectById(user.getDepartmentId());
            if (dept != null) {
                vo.setDepartmentName(dept.getName());
            }
        }

        // Fill position info
        if (user.getPositionId() != null) {
            Position position = positionMapper.selectById(user.getPositionId());
            if (position != null) {
                vo.setPositionName(position.getName());
            }
        }

        return vo;
    }

    /**
     * 构建区域完整路径，如"华南区 > 深圳市 > 福田区"
     */
    private String buildRegionPath(Long regionId) {
        List<String> pathList = new ArrayList<>();
        Long currentId = regionId;

        while (currentId != null) {
            Region region = regionMapper.selectById(currentId);
            if (region == null) {
                break;
            }
            pathList.add(0, region.getName());
            currentId = region.getParentId();
        }

        return String.join(" > ", pathList);
    }
}
