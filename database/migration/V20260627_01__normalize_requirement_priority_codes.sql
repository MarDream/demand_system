-- =====================================================
-- V20260627_01: 统一需求优先级 code
-- 目标: 将历史英文优先级值归一化为 priorities 配置表中的标准 code
-- 标准: P0=紧急, P1=高, P2=中, P3=低
-- 依赖: priorities 表须已存在 P0/P1/P2/P3 四条配置
-- =====================================================

-- 1) 迁移前影响行数与分布
SELECT
  LOWER(TRIM(`priority`)) AS old_value,
  COUNT(*) AS affected_rows
FROM `requirements`
WHERE `deleted_at` = 0
  AND LOWER(TRIM(`priority`)) IN ('critical', 'urgent', 'high', 'medium', 'middle', 'low')
GROUP BY LOWER(TRIM(`priority`))
ORDER BY affected_rows DESC;

-- 2) 归一化
UPDATE `requirements`
SET `priority` = CASE LOWER(TRIM(`priority`))
  WHEN 'critical' THEN 'P0'
  WHEN 'urgent'   THEN 'P0'
  WHEN 'high'     THEN 'P1'
  WHEN 'medium'   THEN 'P2'
  WHEN 'middle'   THEN 'P2'
  WHEN 'low'      THEN 'P3'
  ELSE `priority`
END
WHERE `deleted_at` = 0
  AND LOWER(TRIM(`priority`)) IN ('critical', 'urgent', 'high', 'medium', 'middle', 'low');

-- 3) 迁移后校验：必须 0 行残留
SELECT COUNT(*) AS remaining_english_values
FROM `requirements`
WHERE `deleted_at` = 0
  AND LOWER(TRIM(`priority`)) IN ('critical', 'urgent', 'high', 'medium', 'middle', 'low');

-- 4) 迁移后分布
SELECT
  `priority`,
  COUNT(*) AS cnt
FROM `requirements`
WHERE `deleted_at` = 0
GROUP BY `priority`
ORDER BY cnt DESC;
