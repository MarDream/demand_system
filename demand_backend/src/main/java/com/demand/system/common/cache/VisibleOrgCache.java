package com.demand.system.common.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 可见组织ID缓存（L1: Caffeine + L2: Redis）
 *
 * 缓存每个用户的可见组织ID列表，避免 resolveVisibleOrgIds 每次请求重复查询
 */
@Component
public class VisibleOrgCache {

    private static final Logger log = LoggerFactory.getLogger(VisibleOrgCache.class);
    private static final String REDIS_KEY_PREFIX = "demand:visibleOrg:";

    private final Cache<Long, List<Long>> caffeineCache;
    private final RedisTemplate<String, Object> redisTemplate;

    public VisibleOrgCache(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.caffeineCache = Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(Duration.ofMinutes(5))
                .recordStats()
                .build();
    }

    /**
     * 获取用户的可见组织ID列表（L1 -> L2）
     * 注意：DB 回源由调用方（RequirementServiceImpl）负责，因为逻辑复杂
     */
    @SuppressWarnings("unchecked")
    public List<Long> getVisibleOrgIds(Long userId) {
        if (userId == null) return null;

        // L1: Caffeine
        List<Long> cached = caffeineCache.getIfPresent(userId);
        if (cached != null) return cached;

        // L2: Redis
        try {
            String redisKey = REDIS_KEY_PREFIX + userId;
            Object redisVal = redisTemplate.opsForValue().get(redisKey);
            if (redisVal instanceof List<?> list) {
                List<Long> orgIds = (List<Long>) list;
                caffeineCache.put(userId, orgIds);
                return orgIds;
            }
        } catch (Exception e) {
            log.warn("Redis 读取 visibleOrg 缓存失败: userId={}", userId, e);
        }

        return null; // miss, 由调用方从 DB 加载
    }

    /**
     * 写入缓存（L1 + L2）
     */
    public void putVisibleOrgIds(Long userId, List<Long> orgIds) {
        if (userId == null || orgIds == null) return;
        caffeineCache.put(userId, orgIds);
        try {
            redisTemplate.opsForValue().set(REDIS_KEY_PREFIX + userId, orgIds, Duration.ofMinutes(5));
        } catch (Exception e) {
            log.warn("Redis 写入 visibleOrg 缓存失败: userId={}", userId, e);
        }
    }

    /**
     * 使缓存失效
     */
    public void invalidate(Long userId) {
        caffeineCache.invalidate(userId);
        try {
            redisTemplate.delete(REDIS_KEY_PREFIX + userId);
        } catch (Exception e) {
            log.warn("Redis 删除 visibleOrg 缓存失败: userId={}", userId, e);
        }
    }

    public void invalidateAll() {
        caffeineCache.invalidateAll();
    }

    public String getCacheStats() {
        var stats = caffeineCache.stats();
        return String.format("VisibleOrgCache - 命中率: %.2f%%, 大小: %d",
                stats.hitRate() * 100, caffeineCache.estimatedSize());
    }
}
