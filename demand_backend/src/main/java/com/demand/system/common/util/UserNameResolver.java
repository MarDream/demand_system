package com.demand.system.common.util;

import com.demand.system.common.cache.UserLocalCache;
import com.demand.system.module.user.entity.User;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 用户名称解析工具，通过二级缓存（L1: Caffeine + L2: Redis）查询用户信息
 */
@Component
public class UserNameResolver {

    private final UserLocalCache userLocalCache;

    public UserNameResolver(UserLocalCache userLocalCache) {
        this.userLocalCache = userLocalCache;
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
        User user = userLocalCache.getUserById(userId);
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
