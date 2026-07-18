# 多维表格未完成能力细化实施方案

> **文档状态**：待技术评审  
> **基线日期**：2026 年 7 月 16 日  
> **适用范围**：`demand_backend`（Spring Boot / MyBatis-Plus）与 `demand_frontend`（Vue 3 / Element Plus）中的多维表格（Bitable）模块。  
> **需求依据**：`C:\Download\Qwen_markdown_20260716_echgpcchy.md`《飞书多维表格 (Feishu Base) 全面功能说明书》。

---

## 1. 目标与使用方式

本方案用于将当前已经具备基础数据表、字段、记录、关联、计算字段、视图组件、协同和部分 AI 能力的多维表格，继续演进为可稳定交付的业务能力。

本文件不是对现有代码的重做清单，而是：

1. 明确需求说明中**尚未闭环、仅有 UI、仅有数据结构或未达到可验收标准**的能力；
2. 给出按优先级拆分的实施边界、数据模型、接口、前端交互和验收口径；
3. 作为后续排期、拆分研发任务、测试用例和上线评审的共同基线。

### 1.1 交付原则

- **后端强制约束优先**：筛选、权限、计算字段、自动化等不能只在前端隐藏或限制。
- **视图与数据分离**：视图配置必须是同一张表数据的独立投影，修改一个视图不得改变其他视图或底层记录。
- **增量兼容**：新增表、字段和接口不得破坏已有 `bitable_*` 数据或已保存的字段配置 JSON。
- **可观测、可回放**：异步任务、自动化、Webhook、AI 批处理必须具有执行记录、失败原因、重试和审计信息。
- **先闭环后扩展**：先实现 P0 的最小可用闭环，再逐步增加复杂图表、连接器、AI Agent 等增强能力。

### 1.2 明确的非目标

本阶段不追求 1:1 复刻飞书或 Airtable 的全部产品细节；以下能力在 P0/P1 中不作为必需交付：

- 实时多人光标、字符级协同编辑和 OT/CRDT 文档协同；
- 百万级归档表、全量 BI 数仓和跨租户数据湖；
- 所有外部 SaaS 的双向同步连接器；
- 可自主执行任意操作的开放式 AI Agent。

这些能力应在基础权限、任务队列、审计和扩展 API 稳定后再评审。

---

## 2. 当前实现基线

### 2.1 已具备且应保留的能力

当前代码已经实现或具备可复用基础，后续工作应在此基础上补齐，而非重复开发：

| 能力域 | 当前基线 | 主要实现位置 |
| --- | --- | --- |
| 基础数据模型 | Base、数据表、字段、记录、单元格值、视图、成员、评论、操作日志实体及基础 CRUD | `demand_backend/.../module/bitable/entity`、`database/init.sql` |
| 字段类型 | 已扩展 `group`、`checkbox`、`process`、`button`、`location`、`barcode`、`currency`、`bidirectional_link`、系统字段等 | `FieldType.java`、字段 DTO/VO、`BitableFieldServiceImpl.java` |
| 计算字段 | 自动编号、公式、Lookup、Rollup 可在记录返回时合成；计算异常可回传 `#ERROR` | `BitableFormulaServiceImpl.java`、`BitableRecordServiceImpl.java` |
| 关联字段 | 单向/双向关联、关联记录查询与选择、双向同步 | `BitableLinkServiceImpl.java`、`LinkFieldSelector.vue` |
| 基础视图组件 | Grid、Kanban、Gantt、Calendar、Gallery、Form 组件已经存在 | `demand_frontend/src/views/bitable/components` |
| 表单基础能力 | 表单字段显示、必填、提示、提交后清空等 | `FormView.vue` |
| 协作/AI/导入导出基础 | 已有对应服务/组件入口，可作为后续扩展基础 | `BitableCollaborationService`、`BitableAiService`、`BitableImportExportService` 等 |

### 2.2 已发现的关键缺口证据

| 领域 | 现状判断 | 证据或影响 |
| --- | --- | --- |
| 视图管理闭环 | 后端有视图 CRUD 与 `sort_config`、`filter_config`、`group_config` 存储；前端未形成完整视图列表/管理/切换闭环 | `editor.vue` 当前以 `currentViewType` 直接切换组件，`handleManageView()` 仍提示“视图管理功能开发中” |
| 视图级数据投影 | 需要确认并补齐服务端筛选、排序、分组、分页稳定性和高级字段比较 | 不能仅依赖前端对已加载数据处理，否则分页、权限和数据规模下会失真 |
| 高级权限 | 现有 Base 成员角色不足以满足行级、列级、表单隔离要求 | 需求要求后端对读取和写入均执行字段/行约束 |
| 自动化/工作流 | 需求中定义了触发器、动作、控制节点；当前尚未形成执行引擎、任务记录和失败重试闭环 | 会影响按钮字段、公开表单、Webhook 和仪表盘推送等后续功能 |
| 仪表盘、分享、开放 API | 需求中有明确能力，但当前 Bitable 核心实体中没有对应的 Dashboard、Share、API Key、Webhook 领域模型 | 需以独立领域模型建设，不能挤入 `BitableView.config` |

### 2.3 现有数据表

当前初始化脚本已包含：`bitable_bases`、`bitable_tables`、`bitable_fields`、`bitable_records`、`bitable_cell_values`、`bitable_views`、`bitable_base_members`、`bitable_comments`、`bitable_operations`。

后续新增表应沿用 `bitable_` 前缀、统一逻辑删除与审计字段规范，并通过**新的增量 migration** 发布；不得修改历史 migration 的语义来覆盖线上环境。

---

## 3. 差异总览与优先级

| 优先级 | 功能包 | 交付目标 | 前置依赖 |
| --- | --- | --- | --- |
| **P0** | 视图系统闭环 | 每个视图拥有独立配置，能创建、复制、重命名、删除、切换及可靠渲染 | 现有 View CRUD、记录查询 |
| **P0** | 服务端筛选、排序、分组 | 所有视图以统一、可分页、可索引的方式读取数据 | 视图配置规范 |
| **P0** | 高级权限 | 后端强制实施 Base/表/行/列/表单权限 | 成员角色、用户部门数据 |
| **P0** | 公式与计算字段完善 | 安全且可预测的函数库、依赖图、循环检测、缓存 | 现有公式/Lookup/Rollup |
| **P0** | 自动化 MVP | Trigger → Action 可配置、异步执行、可追踪和重试 | 操作日志、消息/HTTP 能力 |
| **P1** | 工作流编排 | 多分支、循环、延迟、子流程和数据转换 | 自动化任务引擎 |
| **P1** | 仪表盘 | 指标与基础图表、筛选器、权限跟随、嵌入视图 | 统一查询与权限引擎 |
| **P1** | 公开表单与分享视图 | 受令牌、密码、有效期、限流保护的外部访问 | 视图配置、权限、自动化 |
| **P1** | 开放 API 与 Webhook | 受 scope、限流、幂等和审计保护的集成接口 | 权限、任务队列 |
| **P2** | AI 深化与字段补全 | 问数、Agent、AI 批处理治理及完整字段行为 | 任务队列、审计、权限 |
| **P2** | 协同、稳定性与测试 | 变更回滚、通知、容量治理、自动化测试和压测 | 上述能力稳定 |

> 建议：P0 的五个功能包可并行设计，但必须先统一“字段值规范、视图配置 schema、权限判定入口、异步任务模型”四项基础约定，避免后续重复改造。

---

## 4. 横向技术约定（所有后续功能共用）

### 4.1 配置 JSON 的版本化

字段配置、视图配置、自动化节点配置、仪表盘组件配置均应包含版本号：

```json
{
  "schemaVersion": 1,
  "data": {}
}
```

要求：

- 后端使用显式 DTO 反序列化并做校验，不以裸 `Map<String, Object>` 直接执行业务；
- 每次不兼容变更新增迁移器，例如 `ViewConfigMigratorV1ToV2`；
- 读取旧配置时允许降级；保存时写入最新版本；
- 配置解析失败应返回明确的配置错误，不得导致整表记录查询失败。

### 4.2 字段值规范

建立 `BitableCellValueNormalizer` 与 `BitableFieldValueValidator`，统一处理 API、导入、表单、自动化、开放 API 的写入：

- `single_select`：单个选项 ID；`multi_select`：选项 ID 数组；
- `date`：ISO-8601 带时区时间或明确约定的日期字符串；
- `number` / `currency`：高精度数字，避免前端浮点字符串直接参与汇总；
- `checkbox`：布尔值；
- 关联字段：记录 ID 数组；
- `location`、附件、人员、群组、流程：结构化 JSON；
- 计算字段、系统字段：统一判定为只读，所有写入渠道均拒绝。

### 4.3 数据访问链路

所有读取/写入数据的入口统一经过：

```text
Controller / Job / Open API / Form Submit
  → 权限上下文构造
  → 字段值校验与规范化
  → 行/列权限校验或过滤
  → 记录服务 / 查询服务
  → 操作审计、事件发布
  → 自动化 / Webhook 异步消费
```

禁止自动化、导入、公开表单或外部 API 绕开 `BitableRecordService` 直接写 `bitable_cell_values`。

### 4.4 事件模型

新增领域事件（可先采用数据库 outbox，再接入消息队列）：

- `record.created`、`record.updated`、`record.deleted`；
- `form.submitted`、`button.clicked`；
- `automation.completed`、`automation.failed`；
- `workflow.completed`、`workflow.failed`。

事件载荷应包含：事件 ID、Base/Table/Record ID、操作者、变更字段、时间、链路追踪 ID、来源（UI/API/Form/Automation）。

---

## 5. P0-1：视图系统闭环与按视图渲染

### 5.1 目标

让 `bitable_views` 成为唯一的视图配置来源。用户可在同一数据表下管理多个独立视图，切换视图后使用该视图自己的字段显示、筛选、排序、分组与特定视图配置，且不修改底层记录或其他视图。

### 5.2 范围

**本期包含**：

- 视图列表、选择、创建、复制、重命名、删除、默认视图；
- Grid/Kanban/Gantt/Calendar/Gallery/Form 的通用配置持久化；
- 字段隐藏、排序、筛选、分组、列宽、冻结、行高等独立配置；
- 当前视图 ID 路由化（建议 query：`?viewId=xxx`）；
- 保存失败提示、乐观锁冲突提示、删除默认视图保护。

**本期不包含**：公开分享链接、仪表盘嵌入视图（见 P1）；复杂的跨表视图。

### 5.3 视图配置 Schema

保留已有 `sort_config`、`filter_config`、`group_config` 列，新增或扩展 `config` JSON 列。建议结构：

```json
{
  "schemaVersion": 1,
  "columnOrder": [101, 102],
  "hiddenFieldIds": [103],
  "frozenFieldIds": [101],
  "fieldWidths": { "101": 180 },
  "rowHeight": "medium",
  "card": { "coverFieldId": 0, "visibleFieldIds": [] },
  "calendar": { "startFieldId": 0, "endFieldId": 0, "titleFieldId": 0, "colorFieldId": 0 },
  "gantt": { "startFieldId": 0, "endFieldId": 0, "dependencyFieldId": 0, "milestoneFieldId": 0 },
  "form": { "fieldOrder": [], "hiddenFieldIds": [], "requiredFieldIds": [], "descriptions": {}, "successMessage": "", "redirectUrl": "" }
}
```

- 所有字段 ID 必须属于当前表；删除字段后由字段服务异步或同步清理视图配置中的引用。
- 各视图类型只校验并读取自身需要的子配置；通用列配置对所有表格类视图有效。
- 默认视图建议在 `bitable_tables.default_view_id` 存储；删除默认视图前必须先指定另一个视图为默认。

### 5.4 后端设计

新增/调整：

| 接口 | 说明 |
| --- | --- |
| `GET /api/bitable/tables/{tableId}/views` | 获取排序后的视图列表及摘要配置 |
| `POST /api/bitable/tables/{tableId}/views` | 创建视图；首次建表自动创建一个 Grid 默认视图 |
| `POST /api/bitable/views/{viewId}/duplicate` | 复制视图，不复制记录 |
| `PATCH /api/bitable/views/{viewId}` | 局部更新名称、类型、各类配置；携带 `version` 做乐观锁 |
| `POST /api/bitable/tables/{tableId}/default-view/{viewId}` | 设置默认视图 |
| `DELETE /api/bitable/views/{viewId}` | 删除非默认视图；至少保留一个视图 |

服务职责：

- `BitableViewService`：视图生命周期和配置 schema 校验；
- `BitableViewConfigService`：配置合并、字段引用校验、迁移和清理；
- `BitableRecordQueryService`：根据视图配置和临时筛选条件形成查询计划（见第 6 节）。

### 5.5 前端设计

`editor.vue` 应由“仅切换 `currentViewType`”调整为“以 `activeViewId` 驱动”：

1. 进入表格时加载视图列表，优先使用 URL `viewId`，否则表默认视图；
2. 左侧或顶部展示视图导航：图标、名称、视图类型、新建菜单；
3. “视图管理”弹窗替换当前占位提示，支持重命名、复制、删除、设为默认；
4. 每个视图组件接收 `view` 和规范化后的 `viewConfig`，配置面板按当前视图类型渲染；
5. 拖动列宽、隐藏字段、修改排序等操作做 300–500ms 防抖保存；离开前若保存失败应给出重试入口；
6. Form 视图在编辑态提供字段拖动、字段说明、必填、成功提示和跳转 URL 配置。

### 5.6 验收标准

- 同一张表创建两个 Grid 视图，在 A 隐藏字段/设置筛选后，B 的字段及数据顺序不变；
- 刷新页面、切换设备或重新登录后，视图配置仍正确生效；
- 复制视图后，新视图拥有独立配置，修改副本不影响原视图；
- 删除默认视图被拒绝，或先完成默认视图切换；删除最后一个视图被拒绝；
- 字段被删除后，受影响视图不报错，引用项自动清理或明确提示重新配置；
- 无管理视图权限的成员只能使用已授予的视图，不能修改配置。

---

## 6. P0-2：服务端筛选、排序、分组与查询性能

### 6.1 目标

建立统一查询引擎，确保 Grid、Kanban、Calendar、Gantt、Gallery、Dashboard、开放 API 都可以在**权限过滤之后**得到可重复、可分页、稳定的记录顺序。

### 6.2 查询能力范围

首期操作符：

| 字段类别 | 首期操作符 |
| --- | --- |
| 文本/URL/邮箱/电话 | 等于、不等于、包含、不包含、为空、不为空 |
| 数字/货币/评分/进度 | 等于、大于、大于等于、小于、小于等于、区间、为空 |
| 日期 | 当天、过去 N 天、未来 N 天、区间、为空 |
| 单选/人员/群组 | 是任一项、不是任一项、为空 |
| 多选/关联 | 包含任一、包含全部、不包含、为空 |
| 复选框 | 已勾选、未勾选 |
| 公式/Lookup/Rollup | 基于已计算结果筛选；首期限制复杂公式字段的服务端筛选并给出原因 |

筛选组合支持嵌套 `AND` / `OR`，最大嵌套深度建议 5 层，单个视图最大规则数建议 50 条。

### 6.3 配置与接口

标准筛选结构：

```json
{
  "schemaVersion": 1,
  "root": {
    "logic": "AND",
    "rules": [
      { "fieldId": 101, "operator": "eq", "value": "open" },
      { "logic": "OR", "rules": [{ "fieldId": 102, "operator": "gt", "value": 10 }] }
    ]
  }
}
```

排序项：`[{"fieldId":101,"direction":"asc","nulls":"last"}]`。所有排序自动追加 `record_id ASC` 作为最终稳定排序键。

接口建议：

- `GET /api/bitable/tables/{tableId}/records?viewId=&cursor=&pageSize=`：默认应用视图筛选/排序；
- `POST /api/bitable/tables/{tableId}/records/query`：支持临时筛选、字段投影和聚合，仅供内部页面/仪表盘调用；
- 响应携带 `nextCursor`、`appliedFilterHash`、`total`（仅在成本可控时返回）。

### 6.4 实现方案

1. 将当前 EAV 单元格值查询封装为查询计划，而不是在 Controller 中拼条件。
2. 先使用 `EXISTS` 子查询或按字段值 join 实现支持字段类型；对热点字段增加复合索引，例如 `(field_id, value_text)`、`(field_id, value_number)`、`(field_id, value_datetime)`。
3. 计算字段：先计算依赖字段，再在应用层过滤；对于大数据量计算字段，必须通过物化缓存或显式限制避免全表扫描。
4. 关联字段筛选通过关联表或关系索引实现，禁止逐条记录 N+1 查询。
5. 分组首先由查询层返回分组键和记录列表；Kanban 允许返回空分组，分组上限默认 100。
6. 查询日志记录耗时、表 ID、视图 ID、筛选哈希和命中条数；慢查询阈值建议 1 秒。

### 6.5 验收标准

- 相同筛选和数据版本下，连续翻页无重复、无遗漏、排序稳定；
- 多选、关联、日期区间、空值、数字及文本筛选均有自动化测试；
- 行权限生效后，前端、仪表盘和开放 API 返回的数据一致；
- 对 1 万条记录、常用索引字段筛选分页，P95 查询目标在技术评审后设定并纳入压测报告；
- 非法字段 ID、不可用操作符、超深配置返回 4xx 业务错误，不返回 SQL 异常。

---

## 7. P0-3：高级权限与安全模型

### 7.1 目标

在既有 Owner/Manager/Editor/Viewer 成员角色之上，实现表级、行级、列级和表单隔离，并保证 UI、批量导入、自动化、Webhook、开放 API 都无法绕过后端授权。

### 7.2 权限模型

#### 固定角色（Base 级）

| 角色 | Base 配置 | 表结构 | 记录读取 | 记录写入 |
| --- | --- | --- | --- | --- |
| Owner | 完全控制 | 完全控制 | 完全控制 | 完全控制 |
| Manager | 成员、视图、自动化、仪表盘 | 可管理 | 可管理 | 可管理 |
| Editor | 不可改 Base 配置 | 默认不可改 | 按规则可读 | 按规则新增/编辑/删除 |
| Viewer | 不可改 | 不可改 | 按规则可读 | 不可写 |

#### 高级规则

- **表级规则**：表是否对角色/用户/部门可见；
- **行级规则**：规则表达式决定 `read/create/update/delete`；例如 `{创建人} = CURRENT_USER()`；
- **列级规则**：`hidden`、`read_only`、`editable`；敏感字段读取应从 API 响应中剔除，而非仅前端遮挡；
- **表单隔离**：匿名/外部填写者仅有指定表单写入权限，不能查询任何记录或表结构。

### 7.3 数据模型建议

| 表 | 关键字段 | 说明 |
| --- | --- | --- |
| `bitable_permission_rules` | `id, base_id, table_id, subject_type, subject_id, resource_type, action, effect, condition_config, priority, enabled` | 高级权限规则；`subject_type` 支持 role/user/department/public_form/service_account |
| `bitable_permission_rule_fields` | `rule_id, field_id, permission_type` | 列级规则关联字段；避免在条件 JSON 中存无约束 ID |
| `bitable_permission_audits` | `request_id, actor_type, actor_id, resource, action, result, rule_id, reason` | 高风险拒绝/授权审计，可按采样策略写入 |

`condition_config` 使用受限 DSL/AST，而不是直接执行 JEXL 原始表达式。首期仅支持：字段比较、`CURRENT_USER()`、`CURRENT_USER_DEPARTMENT()`、逻辑 AND/OR/NOT、空值判断。

### 7.4 后端落点

- 新增 `BitableAuthorizationService`：提供 `canManageBase`、`canReadTable`、`filterReadableFields`、`buildRowPredicate`、`canWriteField`、`canWriteRecord`；
- 在 `BitableRecordService`、`BitableFieldService`、`BitableViewService`、导入导出、评论、关联查询、自动化执行器、开放 API 中接入；
- 行级读取必须下推到查询层；不能先全量读出后在内存中裁剪；
- 对更新请求：先确认记录可写，再确认每个字段可写；混合合法/非法字段的批量操作应默认原子失败，或明确返回逐项结果（接口需固定语义）；
- 对关联字段：同时校验源记录、目标记录、反向写入字段的权限，防止通过关联越权改写。

### 7.5 前端设计

- 基于接口返回的 `capabilities` 决定按钮是否显示，但不以此作为安全边界；
- 被列级隐藏的字段不渲染、不参与导出、不进入前端本地缓存；
- 规则配置页提供“选择主体 → 选择资源/动作 → 可视化条件 → 试算用户/记录”流程；
- 增加“权限试算”接口，管理员可查看某用户对某记录和字段的最终决策及命中规则；
- 表单编辑器明确标识“公开字段”和“仅内部字段”。

### 7.6 验收标准

- Viewer 直接调用记录、导出、关联搜索、批量接口均不能读取受限行/列；
- Editor 只能编辑被授权记录和字段，篡改请求体中的字段 ID 被拒绝；
- 行级规则以创建人、部门、单选状态等条件验证通过；
- 公开表单 token 无法访问记录读取接口；
- 权限规则变更后，缓存失效及时生效，并留下管理操作审计；
- Owner 不受普通规则误锁，除非产品明确支持“所有者也受限制”的特殊模式。

---

## 8. P0-4：公式、Lookup 与 Rollup 计算引擎

### 8.1 目标

将已有基础 JEXL 公式计算提升为受控、可解释、可扩展的计算引擎：拥有函数白名单、字段依赖图、循环检测、类型校验和缓存失效机制。

### 8.2 函数分期

| 分期 | 函数 |
| --- | --- |
| P0 必须 | `IF`、`IFS`、`AND`、`OR`、`NOT`、`SWITCH`、`BLANK`；`CONCATENATE`、`LEFT`、`RIGHT`、`MID`、`LEN`、`FIND`、`REPLACE`；`SUM`、`AVERAGE`、`MAX`、`MIN`、`ROUND`、`CEILING`、`FLOOR`、`MOD`；`TODAY`、`NOW`、`DATE`、`YEAR`、`MONTH`、`DAY`、`WEEKDAY`；`ARRAYJOIN`、`COUNTALL`、`COUNTA`、`RECORD_ID` |
| P1 | `REGEX_MATCH`、`REGEX_EXTRACT`、`DATEDIF`、`WORKDAY`、`NETWORKDAYS`、日期时区格式化 |
| P2 | 公式快捷模板、复杂数组函数、更多业务函数 |

### 8.3 实施方案

1. **语法解析**：采用受控 parser 或将显示语法编译为安全 AST；禁止向 JEXL 暴露反射、类加载、任意方法调用和系统对象。
2. **字段引用**：统一使用字段 ID token（如 `FIELD(101)`），展示层再映射为字段名，避免重命名字段破坏公式。
3. **依赖图**：新增 `bitable_formula_dependencies(formula_field_id, dependency_field_id, dependency_kind)`；字段创建/更新时解析并更新依赖。
4. **循环检测**：在保存公式前做有向图 DFS/Tarjan 检测；发现直接或间接循环返回完整链路，例如 `A → B → C → A`。
5. **类型系统**：公式编译结果记录预期类型；`number/date/array/text/boolean/null/error` 的隐式转换规则必须文档化。
6. **缓存策略**：以 `(record_id, formula_field_id, dependency_version)` 缓存；单元格更新、关联变更和时间函数跨日时失效。`NOW()` 不允许造成每次查询全量重算，可按分钟桶缓存。
7. **Lookup/Rollup**：关联值为空时有一致的空值表现；汇总函数基于原始数值，货币字段禁止混币种直接求和，除非设置汇率/目标币种。
8. **错误呈现**：记录级错误返回 `#ERROR` 和可供管理员查看的错误代码（如 `FORMULA_TYPE_ERROR`），不得泄露堆栈。

### 8.4 API 与前端

- `POST /api/bitable/formulas/validate`：不落库校验公式、返回依赖字段、结果类型、错误位置；
- `GET /api/bitable/fields/{fieldId}/formula-dependencies`：供管理员定位影响范围；
- `GET /api/bitable/fields/{fieldId}/formula-preview?recordId=`：预览特定记录结果；
- 公式编辑器提供函数面板、字段引用插入、错误定位、依赖提示、循环错误提示；
- 字段重命名后展示名称自动更新，但底层 token 不变。

### 8.5 验收标准

- 所列 P0 函数均有单元测试和典型边界用例；
- 字段 A→B→A 和更长循环在保存时被拒绝；
- 非法表达式、类型不匹配、除零等错误只影响当前计算字段/记录，不影响整表查询；
- 重命名被引用字段后，公式仍正确；
- 更新依赖字段、关联记录后，Lookup/Rollup/公式结果在约定时间内刷新；
- 安全测试证明公式不能访问 JVM 类、文件、网络或任意 Bean。

---

## 9. P0-5：自动化 MVP（Trigger → Action）

### 9.1 目标

提供可配置、可启停、可重试、可审计的线性自动化。MVP 优先覆盖记录变更、表单提交、定时三类触发器及更新/创建/消息/HTTP 四类动作。

### 9.2 MVP 范围

| 类型 | P0 支持 | P1/P2 延后 |
| --- | --- | --- |
| 触发器 | `record_changed`、`form_submitted`、`scheduled` | `button_clicked`、`webhook_received`、`message_received` |
| 动作 | `update_record`、`create_record`、`send_message`、`send_email`、`http_request` | `delete_record`、日历/任务、`ai_generate` |
| 执行方式 | 异步队列、按自动化串行、可重试 | 并行分支、复杂编排（工作流） |

### 9.3 数据模型建议

| 表 | 关键字段 | 说明 |
| --- | --- | --- |
| `bitable_automations` | `id, base_id, table_id, name, status, trigger_type, trigger_config, version, created_by` | 自动化定义 |
| `bitable_automation_actions` | `id, automation_id, sequence_no, action_type, action_config, retry_policy` | 有序动作链 |
| `bitable_automation_runs` | `id, automation_id, event_id, status, context_json, started_at, finished_at, attempt` | 单次运行；`automation_id + event_id` 唯一保证幂等 |
| `bitable_automation_action_runs` | `run_id, action_id, status, input_json, output_json, error_code, error_message, retry_count` | 节点执行明细 |
| `bitable_outbox_events` | `event_id, event_type, aggregate_type, aggregate_id, payload, status, available_at` | 可靠投递基础设施 |

### 9.4 触发和执行约束

- 记录更新事件必须包含变化前后值摘要和变更字段 ID；触发器可按字段和条件筛选。
- 自动化因自身写入再次触发时，携带 `traceId` 与 `originAutomationId`；默认禁止同一自动化在同一链路内递归触发。
- `http_request` 必须有域名白名单、超时（如 10 秒）、响应体大小限制、敏感 Header 脱敏、SSRF 防护；禁止访问本地/内网地址。
- 重试仅针对可重试错误（网络、5xx、限流）；使用指数退避并设置最大次数。非幂等外部请求需要用户配置 idempotency key。
- 自动化运行应以“运行时服务账号”执行，并接受权限约束；其权限来源应可配置并可审计。

### 9.5 接口与前端

- 自动化列表：创建、复制、启停、最近执行状态；
- 自动化编辑器：触发器配置 → 条件 → 动作列表 → 测试运行；
- `POST /api/bitable/automations/{id}/test`：传入测试记录上下文，仅允许管理员；
- `GET /api/bitable/automations/{id}/runs`、`GET /runs/{runId}`：查看执行历史及脱敏日志；
- 执行失败提供“重试本次运行”与“复制错误详情”入口。

### 9.6 验收标准

- 新建/更新符合条件的记录可稳定触发自动化；不符合条件时不运行；
- 表单提交可触发创建关联记录或消息通知；
- 定时任务在时区明确、重启恢复后不重复或漏执行；
- 相同 outbox 事件重复投递时，只产生一次业务效果；
- HTTP 失败能按策略重试，最终失败有可查询日志和告警；
- 自动化不能通过服务账号绕过明确的列级/行级限制；
- 自动化循环受到阻断，并能在日志中定位调用链。

---

## 10. P1-1：工作流编排

### 10.1 目标与边界

工作流用于替代“多动作线性自动化”无法表达的分支、循环、延迟和复用逻辑。它复用自动化的事件、任务、日志、权限和服务账号基础设施，但拥有独立的定义和运行时状态。

首期节点：`condition`、`switch`、`loop`、`delay`、`sub_workflow`、`data_transform`；AI 节点放入 P2。

### 10.2 数据模型与运行时

- `bitable_workflows`：工作流定义，包含草稿/已发布版本；
- `bitable_workflow_versions`：不可变流程图 JSON、输入输出 schema；
- `bitable_workflow_runs`：运行实例、当前状态、变量上下文、恢复点；
- `bitable_workflow_node_runs`：节点状态、输入输出、错误、开始/结束时间；
- `bitable_workflow_timers`：延迟节点唤醒任务。

流程定义采用图结构 JSON，但发布前必须校验：唯一开始节点、连线合法、无无条件死循环、子流程版本存在、循环上限、变量引用合法。

### 10.3 执行规则

- 每个节点有明确状态：`PENDING/RUNNING/SUCCEEDED/FAILED/SKIPPED/WAITING/CANCELED`；
- 循环必须有最大次数/最大处理记录数；
- 延迟节点持久化，下次 Worker 拉取时继续执行；
- 失败策略可选“停止、重试、走失败分支”；
- 已发布版本不可修改；编辑后发布新版本，运行实例绑定具体版本。

### 10.4 验收标准

- 条件、switch、循环、延迟、子流程、数据转换各至少一个端到端用例；
- 暂停/恢复、Worker 重启、重复消息投递后工作流状态一致；
- 发布后修改草稿不影响历史运行；
- 超过循环上限或执行超时被安全终止且可审计。

---

## 11. P1-2：仪表盘与数据可视化

### 11.1 MVP 目标

创建独立仪表盘，在权限过滤后的数据上提供 KPI、柱状图、折线图、饼/环图、排行榜和嵌入视图；支持全局筛选器和组件级筛选。

### 11.2 数据模型

| 表 | 关键字段 | 说明 |
| --- | --- | --- |
| `bitable_dashboards` | `id, base_id, name, layout_config, owner_id, status` | 仪表盘元数据 |
| `bitable_dashboard_widgets` | `id, dashboard_id, type, position_config, data_source_config, display_config, sort_no` | 单个组件 |
| `bitable_dashboard_filters` | `id, dashboard_id, field_mapping_config, display_config` | 全局切片器 |
| `bitable_dashboard_shares` | `dashboard_id, token, permission, expire_at, password_hash` | 后续分享能力可复用 |

`data_source_config` 应只允许选择经授权的表、字段、聚合和筛选，禁止用户写任意 SQL。

### 11.3 后端与前端

- 新增聚合查询服务：`count/sum/avg/min/max`、按字段 group by、Top N；
- 添加 `GET /dashboards/{id}/data`，根据当前用户权限和全局筛选器返回各 Widget 数据；
- 前端使用统一 Widget Registry，组件只能消费标准化数据，不直接拼后端查询；
- 支持拖拽布局、保存草稿、预览、错误占位；
- 首期默认限制：每个仪表盘 20 个 Widget，每个图 3,000 数据点；超限做采样或提示收窄筛选。

### 11.4 后续增强

交叉过滤、数据透视、漏斗、词云、散点/雷达、定时截图推送放在后续迭代。跨过滤必须复用查询与权限引擎，不在浏览器保留全量未授权数据。

### 11.5 验收标准

- 6 类 MVP 组件可保存配置、刷新后正确恢复；
- 同一仪表盘由不同权限用户查看时，指标和图表数据遵循行/列权限；
- 全局筛选器可联动所有已映射字段的组件；
- 无数据、无权限、配置失效、查询超时都有可理解的 UI 状态；
- 图表请求有缓存和并发限制，不因刷新造成数据库雪崩。

---

## 12. P1-3：公开表单、分享视图与外部访问

### 12.1 公开表单

在现有 Form View 基础上增加“发布”概念：

- `bitable_form_publishes`：`view_id, token, status, password_hash, expire_at, submit_limit, rate_limit_config, success_config`；
- 公开路由仅通过 token 获取**脱敏后的表单 schema**，不能返回表 ID、记录列表、隐藏字段或内部字段配置；
- 提交使用 `POST /public/bitable/forms/{token}/submit`，支持验证码/限流、文件上传白名单、幂等提交键；
- 记录写入统一进入 `BitableRecordService`，操作者为受限 `public_form` 服务主体；
- 提交成功可显示自定义提示、重定向 URL，并发布 `form.submitted` 事件。

### 12.2 分享视图

- `bitable_view_shares`：`view_id, token, permission(read/comment), password_hash, expire_at, allow_download, enabled`；
- 分享视图是只读投影，严格套用“可分享字段清单”和脱敏规则；
- 不允许将内部隐藏字段、评论中敏感信息、成员信息、操作日志直接暴露；
- 访问日志记录 token、时间、IP 哈希、结果，不保存不必要的原始个人数据。

### 12.3 验收标准

- 表单填写者只能获得表单需要的字段 schema，不能枚举记录；
- 密码、有效期、停用、提交限额、IP/用户维度限流均生效；
- 附件上传超过类型/大小限制被拒绝；
- 分享视图修改、撤销后立即失效，访问不泄露底层接口错误；
- 公开提交可被自动化消费，但不能触发无限递归。

---

## 13. P1-4：开放 API、Webhook 与连接器基础

### 13.1 开放 API

实现面向系统集成而非浏览器页面复用的 `/open-apis/bitable/v1` 接口：

- 记录 CRUD、字段元数据、过滤/排序/游标分页；
- 认证：API Key（首期）或 OAuth 2.0（后续）；
- 授权：API Key 绑定 Base、表、scope（`records:read`、`records:write`、`fields:read` 等）和过期时间；
- 写入：支持 `Idempotency-Key`；批量写入限定条数与请求体大小；
- 限流：按租户/API Key/接口类型实施令牌桶，命中返回 HTTP 429 及重试提示；
- 审计：记录调用方、scope、资源、结果、耗时、requestId，日志脱敏。

建议表：`bitable_api_credentials`、`bitable_api_scopes`、`bitable_api_request_logs`。

### 13.2 Webhook

- `bitable_webhook_subscriptions`：事件类型、目标 URL、签名密钥引用、启用状态、失败策略；
- 事件至少覆盖：`bitable.record.created`、`bitable.record.updated`、`bitable.record.deleted`；
- 通过 outbox 异步投递，payload 以 HMAC 签名并携带 timestamp/nonce；
- 失败指数退避；达到阈值进入死信状态并通知管理员；
- 每次投递记录请求状态、响应状态、耗时和脱敏错误摘要。

### 13.3 连接器

飞书审批、任务、日历、云文档等连接器不在 P1 首批全面交付。先定义标准 Connector SPI：认证、拉取、推送、字段映射、冲突策略、增量游标、回调事件；优先选择一个业务最需要的连接器做试点。

### 13.4 验收标准

- API Key 无 scope、过期、撤销、越权表访问均被拒绝；
- 游标分页稳定，错误码、限流和幂等行为有 OpenAPI 文档；
- Webhook 签名可验证，重复事件可去重，失败重试与死信可观察；
- API/Webhook 不泄露列级隐藏字段或无权限行。

---

## 14. P2：AI 深化、字段行为、协同与稳定性

### 14.1 AI 能力治理

现有 AI 建表、填充、分类、总结能力继续增强为受控异步任务：

- `bitable_ai_jobs`、`bitable_ai_job_items`、`bitable_ai_usage_logs`：记录模型、Prompt 模板版本、输入字段、输出字段、Token/费用、状态和失败原因；
- 批处理具有并发、速率、预算、重试和取消控制；
- Prompt 不应包含调用者无权读取的字段；输出写入仍经过字段值校验与权限校验；
- 增加 AI 问数：先生成受限查询计划，经过字段/行权限过滤和用户确认，再执行；禁止任意 SQL；
- AI 工作流节点、Base Agent 必须采用工具白名单、scope、人工确认和完整审计后再开放。

### 14.2 字段能力补齐

| 字段 | 待完善行为 |
| --- | --- |
| 附件 | 文件类型/数量/大小限制、病毒扫描回调、预览、下载授权、生命周期清理 |
| 人员/群组 | 用户和组织选择器、离职/失效成员处理、默认值、外部联系人策略 |
| 流程 | 节点定义、允许转移、角色限制、状态变更审计；与工作流集成 |
| 按钮 | 显式绑定自动化/工作流，二次确认、权限校验、点击审计、节流 |
| 位置 | 地址、经纬度、地图服务提供方、精度及隐私策略 |
| 条码 | 格式校验、移动端扫码输入、去重策略 |
| 货币 | 币种、精度、格式化、汇率来源和跨币种汇总限制 |
| 电话/邮箱 | 格式、地区码、规范化和脱敏展示 |

### 14.3 协同、审计与恢复

- 将 `bitable_operations` 扩展为可查询的字段/记录 diff、操作者、来源、关联 traceId；
- 支持记录版本和有限期恢复：恢复操作作为新版本写入，避免物理覆盖历史；
- 评论支持 `@` 提醒、未读状态和通知中心；
- 并发编辑采用记录版本号/ETag；冲突返回服务端版本、客户端变更与可选合并策略。

### 14.4 容量、性能与质量

- 明确产品限制：单表记录、字段、附件、批量写入、公式嵌套（最多 64 层）、图表数据点、自动化月配额；
- 超限时返回可操作错误；归档、导入/导出、AI 批处理和仪表盘计算走异步任务；
- 制定数据索引、慢查询、队列积压、失败率、Webhook 投递率、AI 成本的监控指标；
- 补齐后端单元/集成测试、前端组件测试、E2E、权限矩阵测试、性能压测和安全测试。

---

## 15. 数据库迁移与接口实施清单

### 15.1 建议 migration 拆分

| Migration | 内容 | 依赖 |
| --- | --- | --- |
| `V202607xx_01__bitable_view_config_v2.sql` | 视图版本、默认视图、配置扩展、索引 | 无 |
| `V202607xx_02__bitable_permission_rules.sql` | 高级权限规则、字段映射、审计 | 成员/用户组织 |
| `V202607xx_03__bitable_formula_dependencies.sql` | 公式依赖图、可选缓存表 | 字段表 |
| `V202607xx_04__bitable_automation.sql` | 自动化、运行、动作、outbox | 操作日志 |
| `V202607xx_05__bitable_workflow.sql` | 工作流定义、版本、实例、定时器 | 自动化任务基础 |
| `V202607xx_06__bitable_dashboards.sql` | 仪表盘、组件、筛选器 | 查询/权限服务 |
| `V202607xx_07__bitable_share_openapi.sql` | 表单/视图分享、API credential、Webhook | 权限、outbox |

每个 migration 必须提供：升级路径、默认值、必要索引、回滚/补偿说明、灰度启用开关。线上升级不得依赖清空现有表或手工修改历史 migration。

### 15.2 后端模块建议

```text
module/bitable/
  authorization/   # 权限上下文、规则编译与查询约束
  query/           # 视图/仪表盘/API 共用查询计划
  formula/         # parser、函数库、依赖图、缓存
  automation/      # 定义、outbox、worker、动作执行器
  workflow/        # 图校验、运行时、延迟恢复
  dashboard/       # Widget registry、聚合查询
  sharing/         # 公开表单、分享视图、令牌防护
  integration/     # Open API、Webhook、Connector SPI
```

保持现有 `service` 的稳定入口：新模块应被 `BitableRecordService`、`BitableViewService` 等服务调用，不应将 Controller 直接耦合至 Mapper。

### 15.3 关键 API 规范

所有新接口统一：

- 返回 `requestId`，便于定位审计/任务日志；
- 版本更新接口使用 `version` 或 `If-Match`，冲突返回明确错误码；
- 列表接口优先使用 cursor，不依赖深页 offset；
- 密钥、密码、Token、Webhook 签名只写入一次或返回脱敏值；
- 业务错误采用固定错误码，例如 `BITABLE_PERMISSION_DENIED`、`BITABLE_VIEW_CONFIG_INVALID`、`BITABLE_FORMULA_CYCLE`、`BITABLE_AUTOMATION_LOOP_DETECTED`。

---

## 16. 迭代拆分、依赖与验收门禁

### 16.1 推荐里程碑

| 里程碑 | 主要交付 | 完成门槛 |
| --- | --- | --- |
| **M0：基础约定** | 配置版本化、字段值规范、事件/outbox 设计、错误码、测试基线 | 设计评审通过；核心 DTO/schema 与 migration 方案确认 |
| **M1：视图可用** | 视图管理、按视图渲染、服务端筛选排序分组、表单编辑配置 | P0-1/P0-2 验收通过；无权限用户不能修改视图 |
| **M2：数据可信** | 高级权限、公式依赖/函数库/缓存 | 权限矩阵、公式安全与回归测试通过 |
| **M3：自动化闭环** | Trigger → Action、outbox、Worker、日志与重试 | 幂等、失败重试、循环保护的端到端测试通过 |
| **M4：分析与流程** | 工作流 MVP、仪表盘 MVP | 工作流恢复和仪表盘权限跟随测试通过 |
| **M5：开放与增强** | 公开表单/分享、Open API/Webhook、AI 治理、连接器试点 | 安全评审、限流/审计/压测达标 |

### 16.2 研发任务拆分原则

- 每个功能包拆分为：数据库 migration、后端领域服务/API、前端交互、自动化测试、监控/文档五类子任务；
- 不以“页面展示完成”作为完成标准；至少包含一次真实权限校验、异常路径和重启/重试验证；
- 先为 P0 编写 API contract 与测试用例，再实现 UI，避免前后端对 JSON schema 理解不同；
- 工作流、仪表盘、开放 API 在依赖的权限/查询/事件能力未稳定前仅可做原型，不应承诺生产交付。

### 16.3 必须通过的质量门禁

| 维度 | 门禁 |
| --- | --- |
| 编译 | 后端 `mvn -q -DskipTests compile`、前端 `npm run build` 成功 |
| 后端测试 | 字段值规范、权限矩阵、筛选排序、公式、自动化幂等与重试有单测/集成测试 |
| 前端测试 | 视图配置、表单必填、权限态、自动化编辑器核心交互有组件/E2E 覆盖 |
| 安全 | 越权读取/写入、公式沙箱、SSRF、Token 泄露、Webhook 签名、限流测试通过 |
| 性能 | 以约定数据规模完成查询、批处理、图表和队列压测，并记录基线 |
| 可观测性 | 每个异步运行可按 `requestId/traceId` 查询状态、错误和重试历史 |
| 文档 | API、权限、配置 schema、迁移、运维告警和用户使用说明同步更新 |

---

## 17. 风险与决策点

| 风险 | 影响 | 控制措施 / 需决策事项 |
| --- | --- | --- |
| EAV 单元格模型下的复杂筛选性能 | 大表查询和仪表盘可能全表扫描 | 先确定字段值索引方案；必要时引入物化列/搜索索引 |
| 权限规则与公式/关联耦合 | 可能经 Lookup、关联或导出泄露数据 | 明确“计算结果是否继承源字段权限”；默认采用最严格可见性 |
| 自动化递归与外部副作用 | 重复通知、重复写数据、外部系统污染 | outbox、幂等键、traceId、最大深度和人工停用开关 |
| 公式引擎安全 | 任意表达式执行可能导致 RCE 或资源耗尽 | AST 白名单、超时、嵌套/长度限制、禁止反射/Bean 访问 |
| 公开分享泄露 | Token 传播后造成数据暴露 | 短 token 生命周期、密码、撤销、最小字段投影、限流、访问审计 |
| 需求持续扩张 | P0 被复杂图表、连接器、Agent 拖延 | 以本方案的里程碑验收为边界；新增需求进入独立评审 |

### 17.1 需要产品/技术负责人确认的事项

1. 行级权限的可用条件字段范围：是否首期支持部门、动态用户组、关联字段？
2. 自动化服务账号的默认权限：继承创建人、固定系统账号，还是由管理员指定？
3. 公开表单是否允许匿名附件上传，文件存储与病毒扫描由哪个基础服务承担？
4. 货币字段是否需要汇率换算；如需要，汇率来源和结算时点是什么？
5. 仪表盘数据刷新要求：实时、分钟级缓存还是手动刷新？
6. 开放 API 是否需在首版支持 OAuth，还是 API Key + scope 即可满足集成场景？

---

## 18. 结论

后续开发应优先完成“**视图闭环 + 服务端查询 + 后端权限 + 安全计算 + 自动化 MVP**”。这五项能力决定了现有字段、表单、关联、多视图组件、AI 与协同基础是否能在真实业务中可靠使用。

在 M3 之前，工作流、仪表盘、公开分享和开放 API 可以完成数据模型和原型验证，但不应绕过统一权限、查询和事件体系直接落地。每一阶段均以本文件的验收标准、质量门禁和风险控制项作为完成依据。
