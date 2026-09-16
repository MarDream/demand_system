# 专题：AI 助手 / NL2SQL / 模型配置

> 从 `MEMORY.md` 拆出（原文过长导致注入截断）。动这三块前先读本文件。

## AI 助手（SystemAssistant）

- `SystemAssistant.vue`；`AssistantServiceImpl#dispatchByIntent` 分发 `data_query`(NL2SQL) / `knowledge_qa` / `web_search` / 导航。
  SSE 事件：`meta` / `actions` / `taskUpdate` / `thinkingSteps` / `dataResult` / `reasoningDelta` / `delta` / `usage` / `done` / `error`。
- 弹框三态自管理（Teleport + Transition + `.assistant-window`），store `windowMode` = `normal` | `maximized` | `minimized`；改外层只动 `.assistant-window` / header / `.assistant-mini-bar`。
- **禁止重复入口（用户明确要求）**：停止生成 = 只留 composer 的发送/停止互斥（HUD 纯展示）；侧栏折叠 = 只留 header 左侧；快捷提问 = 空状态只在中央渲染；迷你条还原 = 热区 + 显式「还原」按钮。
- **弹框拖拽**：`windowPos` null = 用 CSS 默认定位，拖过才固化 left/top；抓手 = header（normal）/ 整条迷你条（minimized），全屏态不拖；3px 阈值区分点击与拖拽；按钮用 `closest('button,...')` + `@mousedown.stop` 排除；拖拽中加 `.is-dragging` 关 transition。
- 回归脚本（改助手 UI 后三个都跑）：`verify-assistant-window.cjs` / `check-no-duplicate-entries.cjs` / `verify-assistant-drag.cjs`。

## NL2SQL

- 链路 `SchemaCatalogService` → `Nl2SqlPromptBuilder` → `LlmGateway` → `SqlSafetyValidator`（平面 SELECT）→ `SqlScopeInjector`（软删+组织权限）→ `SqlExecutor` → 整合回答。
- **安全铁律**：模型 SQL 一律不可信；关键词匹配在 `SqlTextScanner.maskLiterals` 骨架文本上（保留反引号）；敏感列按标识符子串匹配。测试在 `module/nl2sql/`（98 项，改安全逻辑必跑）。
- **JSON 数组列判空**：判"有内容"必须 `JSON_LENGTH(列) > 0`（`IS NOT NULL` 会把 `[]` 当有内容）；判空用 `(列 IS NULL OR JSON_LENGTH(列)=0)`；schema prompt 对 json 列自动追加该口径。
- **回答禁止臆造表名**：空结果只能引用已执行 SQL 里的表/列，用"建议核对"。文档 `docs/nl2sql-assistant-design.md`。

## 模型配置

- 「模型配置 → 模型应用」= `views/settings/llm.vue` 第二个 tab，**左右布局**：左 `ApplicationGroupTree.vue`，右按选中范围渲染功能点卡片。**别再退回平铺网格**。
- 表 `llm_application_groups`（全局树，`parent_id` 自关联）+ `llm_applications.group_id`；接口 `GET/POST /api/v1/llm-application-groups[/tree]`、`PUT /{id}`、`PUT /{id}/move`、`DELETE /{id}`、`PUT /api/v1/llm-applications/{code}/group`。虚拟节点不落库，删分组 = 子级与功能点上移。
- **选中态单一数据源**：`ApplicationTreeSelection` 由 llm.vue 持有，树只渲染高亮 + `emit('select')`，**树内部不要再存 selectedGroupId**。分组过滤要**含子孙**（`collectApplicationGroupIds`）。
- 折叠 `localStorage['llm.applicationGroupTree.collapsed']`；「全部应用」默认折叠、分组默认展开。回归 `verify-application-group-tree.cjs`（17 项）。

## LLM 网关

- LLM 走 `LlmGateway`；模型解析走 `LlmModelResolver` + `LlmApplicationCode`（配在 `llm_applications`）。新功能点 = 常量 + 迁移 `INSERT IGNORE` + 未配置时回退。
- **模型解析铁律**：未绑定时 `resolveFirst` 返回该类型 `is_default=1` 的全局默认模型，**不是** `assistant.chat` 绑的。
- `llm_models.model_type` 只有 general/embedding/rerank/vision（**无 chat**）；同名模型跨接入组重复，切模型先选接入组。
