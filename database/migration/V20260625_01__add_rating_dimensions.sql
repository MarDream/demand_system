-- Migration: V20260625_01__add_rating_dimensions.sql
-- 描述: 为评分记录表增加多维评分支持,配合 ADR-002 工作流节点评分功能设计

USE demand_system;

-- 1. 为需求评分表添加多维评分字段
ALTER TABLE requirement_approval_evaluations
ADD COLUMN rating_dimensions JSON DEFAULT NULL COMMENT '多维评分详情(如: {"quality": 4, "response_speed": 5})'
AFTER rating;

-- 2. 为会签记录表添加多维评分字段（保持一致性）
ALTER TABLE workflow_countersign_records
ADD COLUMN rating_dimensions JSON DEFAULT NULL COMMENT '多维评分详情'
AFTER rating;

-- 3. 添加索引优化评分统计查询
ALTER TABLE requirement_approval_evaluations
ADD INDEX idx_rating_stats (requirement_id, created_at, rating);

-- 4. 添加节点平均分查询索引
ALTER TABLE requirement_approval_evaluations
ADD INDEX idx_node_rating (node_id, created_at, rating);
