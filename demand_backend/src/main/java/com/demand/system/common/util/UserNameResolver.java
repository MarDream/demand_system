package com.demand.system.common.util;

import com.demand.system.module.user.entity.User;
import com.demand.system.module.user.mapper.UserMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 用户名称解析工具，提供带缓存的 getUserNameById 查询
 */
@Component
public class UserNameResolver {

    private final UserMapper userMapper;

    public UserNameResolver(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /**
     * 根据用户 ID 获取用户显示名称，查不到返回 fallback
     *
     * @param userId   用户 ID
     * @param fallback 查不到时的默认值
     * @return 用户显示名称
     */
    public String resolveUserName(Long userId, String fallback) {
        if (userId == null) {
            return fallback;
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            return fallback;
        }
        if (StringUtils.hasText(user.getRealName())) {
            return user.getRealName().trim();
        }
        if (StringUtils.hasText(user.getUsername())) {
            return user.getUsername().trim();
        }
        return fallback;
    }

    /**
     * 根据用户 ID 获取用户显示名称，查不到返回 null
     *
     * @param userId 用户 ID
     * @return 用户显示名称，或 null
     */
    public String resolveUserName(Long userId) {
        return resolveUserName(userId, null);
    }
}
