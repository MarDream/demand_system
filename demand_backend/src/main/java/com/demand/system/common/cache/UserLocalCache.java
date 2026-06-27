package com.demand.system.common.cache;

import com.demand.system.module.user.entity.User;
import com.demand.system.module.user.mapper.UserMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户信息二级缓存（L1: Caffeine + L2: Redis）
 *
 * 查询链路: Caffeine -> Redis -> DB -> 写回 Redis + Caffeine
 * 用于缓存用户信息，减少 DB 查询
 */
@Service
public class UserLocalCache {

    private static final Logger log = LoggerFactory.getLogger(UserLocalCache.class);
    private static final String REDIS_KEY_PREFIX = "demand:user:";
    private static final Duration REDIS_TTL = Duration.ofMinutes(10);

    private final LoadingCache<Long, User> caffeineCache;
    private final RedisTemplate<String, Object> redisTemplate;
    private final UserMapper userMapper;

    public UserLocalCache(RedisTemplate<String, Object> redisTemplate, UserMapper userMapper) {
        this.redisTemplate = redisTemplate;
        this.userMapper = userMapper;
        this.caffeineCache = Caffeine.newBuilder()
                .maximumSize(5000)
                .expireAfterWrite(Duration.ofMinutes(10))
                .recordStats()
                .build(this::loadFromRedisOrDb);
    }

    /**
     * 根据 ID 获取用户（L1 -> L2 -> DB）
     */
    public User getUserById(Long id) {
        if (id == null) return null;
        try {
            return caffeineCache.get(id);
        } catch (Exception e) {
            log.error("获取用户缓存失败: userId={}", id, e);
            // 降级：直接查 DB
            return userMapper.selectById(id);
        }
    }

    /**
     * 批量获取用户（先批量查缓存，miss 的再批量查 DB 回填）
     */
    public Map<Long, User> batchGetUsers(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return Collections.emptyMap();

        Map<Long, User> result = new LinkedHashMap<>();
        List<Long> missedIds = new ArrayList<>();

        // L1: Caffeine
        for (Long id : ids) {
            if (id == null) continue;
            User cached = caffeineCache.getIfPresent(id);
            if (cached != null) {
                result.put(id, cached);
            } else {
                missedIds.add(id);
            }
        }

        if (missedIds.isEmpty()) return result;

        // L2: Redis 批量查询
        List<Long> dbMissIds = new ArrayList<>();
        for (Long id : missedIds) {
            try {
                String redisKey = REDIS_KEY_PREFIX + id;
                Object redisVal = redisTemplate.opsForValue().get(redisKey);
                if (redisVal instanceof User user) {
                    caffeineCache.put(id, user);
                    result.put(id, user);
                } else {
                    dbMissIds.add(id);
                }
            } catch (Exception e) {
                dbMissIds.add(id);
                log.warn("Redis 读取用户缓存失败: userId={}", id, e);
            }
        }

        // L3: DB 批量查询
        if (!dbMissIds.isEmpty()) {
            try {
                List<User> dbUsers = userMapper.selectBatchIds(dbMissIds);
                for (User u : dbUsers) {
                    result.put(u.getId(), u);
                    putUser(u.getId(), u);
                }
            } catch (Exception e) {
                log.error("批量查询用户 DB 失败: ids={}", dbMissIds, e);
            }
        }

        return result;
    }

    /**
     * 手动放入缓存（用户 CRUD 时调用）
     */
    public void putUser(Long id, User user) {
        if (id == null || user == null) return;
        caffeineCache.put(id, user);
        try {
            redisTemplate.opsForValue().set(REDIS_KEY_PREFIX + id, user, REDIS_TTL);
        } catch (Exception e) {
            log.warn("Redis 写入用户缓存失败: userId={}", id, e);
        }
    }

    /**
     * 使缓存失效（L1 + L2）
     */
    public void invalidate(Long id) {
        if (id == null) return;
        caffeineCache.invalidate(id);
        try {
            redisTemplate.delete(REDIS_KEY_PREFIX + id);
        } catch (Exception e) {
            log.warn("Redis 删除用户缓存失败: userId={}", id, e);
        }
    }

    /**
     * LoadingCache 加载回调：L2(Redis) -> L3(DB)
     */
    private User loadFromRedisOrDb(Long id) {
        // L2: Redis
        try {
            String redisKey = REDIS_KEY_PREFIX + id;
            Object redisVal = redisTemplate.opsForValue().get(redisKey);
            if (redisVal instanceof User user) {
                return user;
            }
        } catch (Exception e) {
            log.warn("Redis 读取用户缓存失败: userId={}", id, e);
        }

        // L3: DB
        User user = userMapper.selectById(id);
        if (user != null) {
            // 回填 Redis
            try {
                redisTemplate.opsForValue().set(REDIS_KEY_PREFIX + id, user, REDIS_TTL);
            } catch (Exception e) {
                log.warn("Redis 回填用户缓存失败: userId={}", id, e);
            }
        }
        return user;
    }

    /**
     * 获取缓存统计信息
     */
    public String getCacheStats() {
        var stats = caffeineCache.stats();
        return String.format(
            "UserCache - 命中率: %.2f%%, 命中次数: %d, 未命中次数: %d, 当前大小: %d",
            stats.hitRate() * 100,
            stats.hitCount(),
            stats.missCount(),
            caffeineCache.estimatedSize()
        );
    }
}
