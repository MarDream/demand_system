# 优先级 Critical 显示问题修复概览

## 复盘与定位
- 截图中的 `Critical` 出现在需求管理列表"优先级"列，不是新建/编辑表单产生的。
- 新建需求 `create.vue` 与 `RequirementForm.vue` 中的优先级下拉都已绑定 `requirementConfigApi.listPriorities()`，与"需求配置-优先级"联动，本身没问题。
- 真实原因：`requirements.priority` 列历史脏数据是英文（`critical/high/medium/low` 等），与 `priorities` 表中的 `P0/P1/P2/P3` 不一致。`init.sql` 中 `priorities` 已有 P0/P1/P2/P3 四条，配置项没问题。

## 修复策略
- 数据层：新增迁移脚本，把历史英文值归一化为配置表标准 code（Critical→P0、High→P1、Medium→P2、Low→P3、Urgent→P0、Middle→P2）。
- 测试数据：把 `scripts/generate_test_data.sql` 中所有英文值改为标准 code，避免脏数据再次产生。
- 前端：`priorityLabel()` 改为严格依赖"需求配置-优先级"映射，移除英文 fallback；未命中的脏值原样展示，便于发现和清理。

## 关键变更
- `database/migration/V20260627_01__normalize_requirement_priority_codes.sql`：新增历史数据归一化迁移脚本（含前后校验段）。
- `scripts/generate_test_data.sql`：`Critical/High/Medium/Low/Urgent` 全部改为 `P0/P1/P2/P3`。
- `demand_frontend/src/views/requirements/index.vue`：`priorityLabel()` 只走配置表映射，移除 P0-P3 与英文 fallback 兜底。

## 后续事项
- 需在数据库环境执行新增迁移脚本；脚本已包含迁移前影响行数、迁移后残留检查、迁移后分布三段校验，便于人工复核。
- 前端构建已重新触发；如之前 `src/views/system/workflow-config/editor.vue` 的 `ratingConfig` 类型错误仍在，与本次改动无关。
