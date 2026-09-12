package com.demand.system.module.bitable.listener;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.demand.system.module.bitable.entity.BitableAutomation;
import com.demand.system.module.bitable.mapper.BitableAutomationMapper;
import com.demand.system.module.bitable.service.BitableAutomationService;
import com.demand.system.module.bitable.util.BitableJsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * 多维表格-定时自动化触发器调度器。
 * <p>
 * 每分钟扫描 trigger_type=scheduled 且启用的自动化规则，按 trigger_config 判定是否到期：
 * <ul>
 *   <li>{"scheduleType":"interval","intervalMinutes":30} —— 间隔触发</li>
 *   <li>{"scheduleType":"daily","time":"09:30"} —— 每天固定时刻</li>
 *   <li>{"scheduleType":"cron","cron":"0 0 9 * * MON-FRI"} —— Spring cron 表达式</li>
 * </ul>
 * 通过 CAS 更新 last_fired_at 抢占触发权，多实例部署时同一周期只有一台触发。
 */
@Component
public class BitableScheduledTriggerRunner {

    private static final Logger log = LoggerFactory.getLogger(BitableScheduledTriggerRunner.class);

    private final BitableAutomationMapper automationMapper;
    private final BitableAutomationService automationService;

    public BitableScheduledTriggerRunner(BitableAutomationMapper automationMapper,
                                         BitableAutomationService automationService) {
        this.automationMapper = automationMapper;
        this.automationService = automationService;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void scanScheduledAutomations() {
        List<BitableAutomation> automations = automationMapper.selectList(
                new LambdaQueryWrapper<BitableAutomation>()
                        .eq(BitableAutomation::getTriggerType, "scheduled")
                        .eq(BitableAutomation::getStatus, "enabled"));
        if (automations.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        for (BitableAutomation automation : automations) {
            try {
                if (!isDue(automation, now)) {
                    continue;
                }
                if (tryClaimFire(automation, now)) {
                    boolean enqueued = automationService.fireScheduledAutomation(automation.getId());
                    if (enqueued) {
                        log.info("定时自动化已触发: automationId={}, name={}", automation.getId(), automation.getName());
                    }
                }
            } catch (Exception e) {
                log.error("处理定时自动化失败: automationId={}", automation.getId(), e);
            }
        }
    }

    /**
     * 判定规则是否到期。首次创建（last_fired_at 为空）视为立即到期
     */
    private boolean isDue(BitableAutomation automation, LocalDateTime now) {
        LocalDateTime lastFiredAt = automation.getLastFiredAt();
        Map<String, Object> config = parseConfig(automation.getTriggerConfig());
        String scheduleType = String.valueOf(config.getOrDefault("scheduleType", ""));

        // 兼容未显式声明类型：有 cron 用 cron，有 intervalMinutes 用间隔，有 time 用每日
        if (scheduleType.isEmpty()) {
            if (config.containsKey("cron")) scheduleType = "cron";
            else if (config.containsKey("intervalMinutes")) scheduleType = "interval";
            else if (config.containsKey("time")) scheduleType = "daily";
            else return false;
        }

        return switch (scheduleType) {
            case "interval" -> {
                Long minutes = toLong(config.get("intervalMinutes"));
                if (minutes == null || minutes <= 0) {
                    yield false;
                }
                yield lastFiredAt == null
                        || lastFiredAt.plusMinutes(minutes).isBefore(now)
                        || lastFiredAt.plusMinutes(minutes).isEqual(now);
            }
            case "daily" -> {
                LocalTime fireTime = parseTime(config.get("time"));
                if (fireTime == null) {
                    yield false;
                }
                yield now.toLocalTime().compareTo(fireTime) >= 0
                        && (lastFiredAt == null || lastFiredAt.toLocalDate().isBefore(now.toLocalDate()));
            }
            case "cron" -> {
                String cron = String.valueOf(config.get("cron"));
                if (cron.isEmpty() || !CronExpression.isValidExpression(cron)) {
                    yield false;
                }
                CronExpression expression = CronExpression.parse(cron);
                LocalDateTime base = lastFiredAt != null ? lastFiredAt : now.minusMinutes(1);
                LocalDateTime next = expression.next(base);
                yield next != null && !next.isAfter(now);
            }
            default -> false;
        };
    }

    /**
     * CAS 抢占触发权：仅当 last_fired_at 仍为旧值时更新为 now，防止多实例/并发重复触发
     */
    private boolean tryClaimFire(BitableAutomation automation, LocalDateTime now) {
        LambdaUpdateWrapper<BitableAutomation> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(BitableAutomation::getId, automation.getId());
        if (automation.getLastFiredAt() != null) {
            wrapper.eq(BitableAutomation::getLastFiredAt, automation.getLastFiredAt());
        } else {
            wrapper.isNull(BitableAutomation::getLastFiredAt);
        }
        wrapper.set(BitableAutomation::getLastFiredAt, now);
        return automationMapper.update(null, wrapper) > 0;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return Map.of();
        }
        Object parsed = BitableJsonUtils.parseJson(configJson);
        return parsed instanceof Map ? (Map<String, Object>) parsed : Map.of();
    }

    private LocalTime parseTime(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return LocalTime.parse(String.valueOf(raw), DateTimeFormatter.ofPattern("HH:mm"));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private Long toLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return value != null ? Long.parseLong(String.valueOf(value)) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
