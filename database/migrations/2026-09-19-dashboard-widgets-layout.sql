-- 仪表盘升级为可视化大屏编辑器：组件增加布局配置（行/列宽/高度）
-- 2026-09-19

ALTER TABLE `bitable_dashboard_widgets`
  ADD COLUMN `layout_config` JSON DEFAULT NULL COMMENT '布局配置: {rowId, span, height}' AFTER `display_config`;
