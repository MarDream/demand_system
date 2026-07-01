package com.demand.system.common.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

/**
 * 基于Redis+Lua的分布式ID生成器
 * 支持高并发场景下的全局唯一编号生成
 */
@Service
public class DistributedIdGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    // Redis key前缀
    private static final String REDIS_KEY_PREFIX = "demand:id:sequence:";

    // 序列号最小宽度
    private static final int MIN_SEQUENCE_WIDTH = 3;

    // Redis key过期时间（秒）- 保留7天
    private static final long KEY_EXPIRE_SECONDS = 7 * 24 * 60 * 60;

    private final StringRedisTemplate stringRedisTemplate;

    // Lua脚本：原子性地获取并递增序列号
    private static final String LUA_SCRIPT =
        "local key = KEYS[1]\n" +
        "local ttl = ARGV[1]\n" +
        "local current = redis.call('INCR', key)\n" +
        "if current == 1 then\n" +
        "    redis.call('EXPIRE', key, ttl)\n" +
        "end\n" +
        "return current";

    public DistributedIdGenerator(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 生成需求编号
     * 格式: BR + YYYYMMDDHHmmss + 序列号
     * 示例: BR20260620221013001
     *
     * @return 全局唯一的需求编号
     */
    public String generateRequirementNo() {
        return generateRequirementNo(LocalDateTime.now());
    }

    /**
     * 生成需求编号（指定时间）
     *
     * @param dateTime 时间基准
     * @return 全局唯一的需求编号
     */
    public String generateRequirementNo(LocalDateTime dateTime) {
        String prefix = "BR";
        String dateStr = dateTime.format(DATE_FORMATTER);
        String timestampStr = dateTime.format(TIMESTAMP_FORMATTER);

        // Redis key: demand:id:sequence:BR:20260620
        String redisKey = REDIS_KEY_PREFIX + prefix + ":" + dateStr;

        // 使用Lua脚本原子性地获取序列号
        Long sequence = executeIncrScript(redisKey, KEY_EXPIRE_SECONDS);

        if (sequence == null) {
            throw new RuntimeException("Failed to generate requirement number from Redis");
        }

        // 计算序列号宽度（至少3位，根据实际数值动态调整）
        int sequenceWidth = Math.max(MIN_SEQUENCE_WIDTH, String.valueOf(sequence).length());
        String sequenceText = String.format("%0" + sequenceWidth + "d", sequence);

        return prefix + timestampStr + sequenceText;
    }

    /**
     * 执行Lua脚本获取递增序列号
     *
     * @param key Redis key
     * @param ttl 过期时间（秒）
     * @return 序列号
     */
    private Long executeIncrScript(String key, long ttl) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(LUA_SCRIPT);
        script.setResultType(Long.class);

        List<String> keys = Collections.singletonList(key);
        return stringRedisTemplate.execute(script, keys, String.valueOf(ttl));
    }

    /**
     * 获取指定日期的当前序列号（不递增）
     * 用于查询当前已分配到第几个编号
     *
     * @param dateTime 日期
     * @return 当前序列号，如果不存在返回0
     */
    public long getCurrentSequence(LocalDateTime dateTime) {
        String prefix = "BR";
        String dateStr = dateTime.format(DATE_FORMATTER);
        String redisKey = REDIS_KEY_PREFIX + prefix + ":" + dateStr;

        String value = stringRedisTemplate.opsForValue().get(redisKey);
        if (value == null) {
            return 0L;
        }

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * 重置指定日期的序列号（谨慎使用）
     *
     * @param dateTime 日期
     */
    public void resetSequence(LocalDateTime dateTime) {
        String prefix = "BR";
        String dateStr = dateTime.format(DATE_FORMATTER);
        String redisKey = REDIS_KEY_PREFIX + prefix + ":" + dateStr;
        stringRedisTemplate.delete(redisKey);
    }
}
