package com.demand.system.common.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class RateLimitService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RateLimitService.class);

    private final StringRedisTemplate stringRedisTemplate;

    public RateLimitService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public boolean isAllowed(String key, int maxRequests, int windowSeconds) {
        try {
            String redisKey = "rate_limit:" + key;
            Long count = stringRedisTemplate.opsForValue().increment(redisKey);
            if (count != null && count == 1) {
                stringRedisTemplate.expire(redisKey, windowSeconds, TimeUnit.SECONDS);
            }
            return count == null || count <= maxRequests;
        } catch (Exception e) {
            log.warn("Rate limit check failed, allowing request", e);
            return true;
        }
    }
}
