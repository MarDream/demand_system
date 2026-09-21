-- ============================================================
-- 用户外观设置持久化（2026-09-18）
--   users 新增 appearance_config 列：个人设置-外观（主题模式/主题色/圆角档位）
--   以 JSON 字符串存储，如 {"mode":"dark","primary":"#2563EB","radius":"soft"}
--   NULL = 未自定义（前端使用默认值/localStorage 缓存）
-- 执行：docker exec -i mysql mysql --default-character-set=utf8mb4 -uroot -padmin123 demand_system < <本文件>
-- ============================================================

ALTER TABLE `users`
  ADD COLUMN `appearance_config` VARCHAR(191) DEFAULT NULL COMMENT '外观设置JSON({mode,primary,radius})' AFTER `avatar`;
