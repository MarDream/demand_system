-- ============================================================
-- 多维表格数据表行高设置（2026-09-17）
--   bitable_tables 新增 row_height 列：每个数据表可单独设置网格行距（px）
--   NULL = 未设置，前端使用默认行高 80px（当前 40px 的 2 倍）
-- 执行：docker exec -i mysql mysql --default-character-set=utf8mb4 -uroot -padmin123 demand_system < <本文件>
-- ============================================================

ALTER TABLE `bitable_tables`
  ADD COLUMN `row_height` INT UNSIGNED DEFAULT NULL COMMENT '网格行高(px)，NULL=使用前端默认(80)' AFTER `sort_order`;
