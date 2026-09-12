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

    /**
     * 批量解析用户显示名称（realName 优先，其次 username，均空的跳过）。
     * 走 UserLocalCache 二级缓存，未命中的 id 不会出现在结果中。
     *
     * @param userIds 用户 ID 集合
     * @return userId -> 显示名称 映射
     */
    public java.util.Map<Long, String> resolveUserNames(java.util.Collection<Long> userIds) {
        java.util.Map<Long, String> nameMap = new java.util.HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return nameMap;
        }
        for (User user : userLocalCache.batchGetUsers(userIds).values()) {
            if (user == null || user.getId() == null) {
                continue;
            }
            String name = org.springframework.util.StringUtils.hasText(user.getRealName())
                    ? user.getRealName().trim()
                    : (org.springframework.util.StringUtils.hasText(user.getUsername())
                            ? user.getUsername().trim() : null);
            if (name != null) {
                nameMap.put(user.getId(), name);
            }
        }
        return nameMap;
    }
}
