package com.demand.system.common.cache;

import com.demand.system.module.organization.dto.SysOrgVO;
import com.demand.system.module.organization.service.SysOrgService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;

/**
 * 组织信息二级缓存（L1: Caffeine + L2: Redis）
 *
 * 查询链路: Caffeine -> Redis -> DB -> 写回 Redis + Caffeine
 * 用于消除需求列表查询中 sysOrgService.getDetail() 的 N+1 残留
 */
@Component
public class OrgLocalCache {

    private static final Logger log = LoggerFactory.getLogger(OrgLocalCache.class);
    private static final String REDIS_KEY_PREFIX = "demand:org:";

    private final Cache<Long, SysOrgVO> caffeineCache;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SysOrgService sysOrgService;

    public OrgLocalCache(RedisTemplate<String, Object> redisTemplate, SysOrgService sysOrgService) {
        this.redisTemplate = redisTemplate;
        this.sysOrgService = sysOrgService;
        this.caffeineCache = Caffeine.newBuilder()
                .maximumSize(2000)
                .expireAfterWrite(Duration.ofMinutes(10))
                .recordStats()
                .build();
    }

    /**
     * 根据 ID 获取组织（L1 -> L2 -> DB）
     */
    public SysOrgVO getOrgById(Long id) {
        if (id == null) return null;

        // L1: Caffeine
        SysOrgVO cached = caffeineCache.getIfPresent(id);
        if (cached != null) return cached;

        // L2: Redis
        try {
            String redisKey = REDIS_KEY_PREFIX + id;
            Object redisVal = redisTemplate.opsForValue().get(redisKey);
            if (redisVal instanceof SysOrgVO org) {
                caffeineCache.put(id, org);
                return org;
            }
        } catch (Exception e) {
            log.warn("Redis 读取组织缓存失败: orgId={}", id, e);
        }

        // L3: DB
        try {
            SysOrgVO org = sysOrgService.getDetail(id);
            if (org != null) {
                putOrg(id, org);
            }
            return org;
        } catch (Exception e) {
            log.error("DB 查询组织失败: orgId={}", id, e);
            return null;
        }
    }

    /**
     * 批量获取组织
     */
    public Map<Long, SysOrgVO> batchGetOrgs(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return Collections.emptyMap();

        Map<Long, SysOrgVO> result = new HashMap<>();
        List<Long> missedIds = new ArrayList<>();

        for (Long id : ids) {
            if (id == null) continue;
            SysOrgVO cached = caffeineCache.getIfPresent(id);
            if (cached != null) {
                result.put(id, cached);
            } else {
                missedIds.add(id);
            }
        }

        for (Long id : missedIds) {
            // 逐个走完整查询链路（getOrgById 内部会回填缓存）
            SysOrgVO org = getOrgById(id);
            if (org != null) {
                result.put(id, org);
            }
        }

        return result;
    }

    /**
     * 写入缓存（L1 + L2）
     */
    public void putOrg(Long id, SysOrgVO org) {
        caffeineCache.put(id, org);
        try {
            redisTemplate.opsForValue().set(REDIS_KEY_PREFIX + id, org, Duration.ofMinutes(10));
        } catch (Exception e) {
            log.warn("Redis 写入组织缓存失败: orgId={}", id, e);
        }
    }

    /**
     * 使缓存失效
     */
    public void invalidate(Long id) {
        caffeineCache.invalidate(id);
        try {
            redisTemplate.delete(REDIS_KEY_PREFIX + id);
        } catch (Exception e) {
            log.warn("Redis 删除组织缓存失败: orgId={}", id, e);
        }
    }

    public void invalidateAll() {
        caffeineCache.invalidateAll();
    }

    public String getCacheStats() {
        var stats = caffeineCache.stats();
        return String.format("OrgCache - 命中率: %.2f%%, 大小: %d",
                stats.hitRate() * 100, caffeineCache.estimatedSize());
    }
}
