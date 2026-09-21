-- ============================================================
-- 多维表格：仪表盘支持拖拽排序（与数据表共用同层级序列）
-- 日期：2026-09-19
--
-- 背景：列表页左侧目录树里，数据表与仪表盘是同层级的兄弟节点，但仪表盘
--   「长按不变抓手、也拖不动」——根因是 bitable_dashboards 没有 sort_order 列，
--   而目录树的叶子顺序又是由「Base 顺序」间接决定的（见 index.vue 的 baseTables），
--   于是仪表盘只能被动地排在数据表后面，无法参与排序。
--
-- 本脚本做三件事：
--   1. 给 bitable_dashboards 补 sort_order 列（幂等）
--   2. 补 (base_id, sort_order) 组合索引（幂等）
--   3. **首次执行时**按当前视觉顺序回填 sort_order：
--      每个分组层级内「数据表在前、仪表盘在后」，两者共用 0..n-1 的同一序列。
--      回填口径（与旧的前端渲染顺序完全一致）：
--        数据表  ORDER BY base.created_at DESC, table.sort_order, table.created_at, table.id
--        仪表盘  接在数据表之后，ORDER BY base.created_at DESC, dashboard.created_at, dashboard.id
--
-- 执行方式（未接入 Flyway，需手工执行）：
--   docker exec -i mysql mysql -uroot -padmin123 --default-character-set=utf8mb4 demand_system < database/migrations/2026-09-19-dashboard-sort-order.sql
--
-- 说明：
--   - 排序回写走后端接口 PUT /v1/bitable/leaves/sort（按传入顺序以下标回写 sort_order）。
--   - 第 3 步带 @need_backfill 守卫：只有「本次新建了列」或「仪表盘还全是默认 0」时才回填，
--     避免重复执行把用户手工拖好的顺序重置。
-- ============================================================

-- ---------- 1. 补列（幂等） ----------
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bitable_dashboards' AND COLUMN_NAME = 'sort_order'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `bitable_dashboards` ADD COLUMN `sort_order` INT NOT NULL DEFAULT 0 COMMENT ''同层级排序（与数据表共用同一序列）'' AFTER `name`',
  'SELECT ''bitable_dashboards.sort_order 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 2. 补索引（幂等） ----------
SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bitable_dashboards' AND INDEX_NAME = 'idx_bitable_dashboard_base_sort'
);
SET @ddl := IF(@idx_exists = 0,
  'ALTER TABLE `bitable_dashboards` ADD INDEX `idx_bitable_dashboard_base_sort` (`base_id`, `sort_order`)',
  'SELECT ''idx_bitable_dashboard_base_sort 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 3. 回填 sort_order（仅首次） ----------
-- 守卫：仪表盘还存在 sort_order = 0 之外的取值 → 说明已经排过，不再回填
SET @need_backfill := (
  SELECT COUNT(*) FROM `bitable_dashboards` WHERE deleted_at = 0 AND sort_order <> 0
) = 0;

DROP TEMPORARY TABLE IF EXISTS `tmp_leaf_order`;
CREATE TEMPORARY TABLE `tmp_leaf_order` (
  `kind` VARCHAR(10) NOT NULL,
  `id` BIGINT UNSIGNED NOT NULL,
  `rn` INT NOT NULL,
  PRIMARY KEY (`kind`, `id`)
);

-- 数据表：同层级内按 Base 顺序 + 表顺序编号
INSERT INTO `tmp_leaf_order` (`kind`, `id`, `rn`)
SELECT 'table', t.id,
       ROW_NUMBER() OVER (
         PARTITION BY COALESCE(b.group_id, -1)
         ORDER BY b.created_at DESC, t.sort_order, t.created_at, t.id
       ) - 1
FROM `bitable_tables` t
JOIN `bitable_bases` b ON b.id = t.base_id
WHERE t.deleted_at = 0 AND b.deleted_at = 0;

-- 仪表盘：接在同一层级数据表之后继续编号
INSERT INTO `tmp_leaf_order` (`kind`, `id`, `rn`)
SELECT 'dashboard', d.id,
       (SELECT COUNT(*)
        FROM `bitable_tables` t2
        JOIN `bitable_bases` b2 ON b2.id = t2.base_id
        WHERE t2.deleted_at = 0 AND b2.deleted_at = 0
          AND COALESCE(b2.group_id, -1) = COALESCE(b.group_id, -1))
       + ROW_NUMBER() OVER (
           PARTITION BY COALESCE(b.group_id, -1)
           ORDER BY b.created_at DESC, d.created_at, d.id
         ) - 1
FROM `bitable_dashboards` d
JOIN `bitable_bases` b ON b.id = d.base_id
WHERE d.deleted_at = 0 AND b.deleted_at = 0;

SET @sql := IF(@need_backfill,
  'UPDATE `bitable_tables` t JOIN `tmp_leaf_order` o ON o.kind = ''table'' AND o.id = t.id SET t.sort_order = o.rn',
  'SELECT ''数据表 sort_order 已排过，跳过回填'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(@need_backfill,
  'UPDATE `bitable_dashboards` d JOIN `tmp_leaf_order` o ON o.kind = ''dashboard'' AND o.id = d.id SET d.sort_order = o.rn',
  'SELECT ''仪表盘 sort_order 已排过，跳过回填'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

DROP TEMPORARY TABLE IF EXISTS `tmp_leaf_order`;
