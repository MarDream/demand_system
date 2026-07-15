-- 需求审批评分表补充 rating_dimensions 列（Map<String,Integer>，JSON 类型）
-- 对应 RequirementApprovalEvaluation.ratingDimensions 字段（JacksonTypeHandler 序列化）
-- 修复 /api/v1/statistics/rating 系列接口报 BadSqlGrammarException 的问题
-- 幂等：仅在列不存在时添加

SET @db_name = 'demand_system';
SET @tbl_name = 'requirement_approval_evaluations';
SET @col_name = 'rating_dimensions';

SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db_name
      AND TABLE_NAME = @tbl_name
      AND COLUMN_NAME = @col_name
);

SET @alter_sql = IF(
    @col_exists = 0,
    'ALTER TABLE requirement_approval_evaluations ADD COLUMN rating_dimensions JSON NULL COMMENT ''评分维度明细（各维度打分）''',
    'SELECT 1 AS skipped'
);

PREPARE stmt FROM @alter_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
