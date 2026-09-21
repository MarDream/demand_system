-- 工作流版本「编辑时间」可靠性修复
--
-- 背景：版本管理列表的「编辑时间」列取 workflow_versions.updated_at，但该列既没有 DB 级自动更新，
--       又有若干业务路径只写别的列（编辑版本信息 / 编辑工作流定义 / JSON 版发布启用 / 审核驳回 /
--       删除待审核记录回退 draft），编辑后时间不刷新，列表里看起来恒等于创建时间。
--
-- 处置：
--   1) 给 updated_at 加 ON UPDATE CURRENT_TIMESTAMP —— 任何未显式指定 updated_at 的 UPDATE 都会自动刷新，
--      兜住以后新增的写入路径（与本库 user_column_configs.updated_at 的写法一致）。
--      ⚠️ 显式赋值优先于 ON UPDATE：业务代码里从库里查出来的实体必须先 setUpdatedAt(now) 再 updateById，
--      否则会把旧值原样写回、时间依旧不动。相关代码修复已同步提交。
--   2) 存量 NULL 不回填：这些版本创建后从未变更过，「最后一次编辑时间」就等于创建时间，
--      由查询侧 COALESCE(updated_at, created_at) / 前端兜底展示。

ALTER TABLE `workflow_versions`
  MODIFY COLUMN `updated_at` DATETIME NULL DEFAULT NULL
    ON UPDATE CURRENT_TIMESTAMP
    COMMENT '编辑时间(最近一次保存/启停/复制等变更时间，DB 自动刷新)';
