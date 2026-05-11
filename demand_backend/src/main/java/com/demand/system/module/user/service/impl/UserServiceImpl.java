package com.demand.system.module.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.PageResult;
import com.demand.system.module.auth.service.EmailService;
import com.demand.system.module.user.dto.UserCreateDTO;
import com.demand.system.module.user.dto.UserQueryDTO;
import com.demand.system.module.user.dto.UserUpdateDTO;
import com.demand.system.module.user.dto.UserVO;
import com.demand.system.module.user.entity.User;
import com.demand.system.module.user.mapper.UserMapper;
import com.demand.system.module.user.service.UserService;
import com.demand.system.module.organization.dto.SysOrgVO;
import com.demand.system.module.organization.entity.Position;
import com.demand.system.module.organization.mapper.PositionMapper;
import com.demand.system.module.organization.service.SysOrgService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PositionMapper positionMapper;
    private final SysOrgService sysOrgService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

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
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, dto.getUsername());
        if (userMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("用户名已存在");
        }

        if (dto.getEmail() == null || dto.getEmail().isBlank()) {
            throw new BusinessException("邮箱不能为空");
        }
        if (dto.getPhone() == null || dto.getPhone().length() < 3) {
            throw new BusinessException("手机号不能为空且至少包含3位");
        }

        User user = new User();
        BeanUtil.copyProperties(dto, user);
        String initialPassword = buildInitialPassword(dto.getUsername(), dto.getPhone(), dto.getPassword());
        user.setPassword(passwordEncoder.encode(initialPassword));
        user.setStatus(User.STATUS_ACTIVE);
        userMapper.insert(user);

        emailService.sendInitialPasswordEmail(user.getEmail(), user.getUsername(), initialPassword);
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

    @Override
    public boolean resetInitialPassword(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new BusinessException("用户未配置邮箱，无法发送初始密码");
        }
        if (user.getPhone() == null || user.getPhone().length() < 3) {
            throw new BusinessException("用户手机号不足3位，无法生成初始密码");
        }

        String initialPassword = buildInitialPassword(user.getUsername(), user.getPhone(), null);
        user.setPassword(passwordEncoder.encode(initialPassword));
        userMapper.updateById(user);
        return emailService.sendInitialPasswordEmail(user.getEmail(), user.getUsername(), initialPassword);
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
            SysOrgVO region = sysOrgService.getDetail(user.getRegionId());
            if (region != null) {
                vo.setRegionName(region.getName());
                vo.setRegionPath(region.getPath() != null ? buildOrgPath(region.getPath()) : region.getName());
            }
        }

        // Fill department info
        if (user.getDepartmentId() != null) {
            SysOrgVO dept = sysOrgService.getDetail(user.getDepartmentId());
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
     * 根据物化路径构建显示路径，如 "/1/5/8/" -> "东莞市 > 开普云科技 > 研发中心"
     */
    private String buildOrgPath(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String[] ids = path.split("/");
        List<String> names = new ArrayList<>();
        for (String idStr : ids) {
            if (idStr.isBlank()) {
                continue;
            }
            SysOrgVO org = sysOrgService.getDetail(Long.parseLong(idStr));
            if (org != null) {
                names.add(org.getName());
            }
        }
        return String.join(" > ", names);
    }

    private String buildInitialPassword(String username, String phone, String fallbackPassword) {
        if (fallbackPassword != null && !fallbackPassword.isBlank()) {
            return fallbackPassword;
        }
        if (phone == null || phone.length() < 3) {
            throw new BusinessException("手机号不足3位，无法生成初始密码");
        }
        return username + phone.substring(phone.length() - 3);
    }
}
