package com.demand.system.common.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final StringRedisTemplate stringRedisTemplate;

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
