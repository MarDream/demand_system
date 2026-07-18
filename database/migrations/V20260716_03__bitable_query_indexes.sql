-- 为 bitable_cell_values 添加复合索引以加速字段值筛选查询
-- EAV 模型下按 field_id + value 列筛选是高频操作，复合索引可显著提升性能

-- 数字字段值筛选索引
CREATE INDEX idx_field_value_number ON bitable_cell_values (field_id, value_number);

-- 日期字段值筛选索引
CREATE INDEX idx_field_value_date ON bitable_cell_values (field_id, value_date);

-- 文本字段值筛选索引（前缀索引，TEXT 列无法全索引）
CREATE INDEX idx_field_value_text ON bitable_cell_values (field_id, value_text(50));
