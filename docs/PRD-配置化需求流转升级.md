# 配置化需求流转升级 - 产品需求说明书 (PRD)

> 版本：v1.2
> 日期：2026-05-16
> 适用范围：需求管理系统 Web 端
> 关联文档：`docs/PRD-需求管理系统.md`

---

## 1. 文档概述

### 1.1 编写目的

本文档用于明确“配置化需求流转升级”专项需求的业务目标、功能范围、流程规则、数据模型、接口边界与验收标准，作为产品设计、研发实现、测试验收和上线评审的统一依据。

### 1.2 项目背景

当前系统已具备需求管理、基础工作流、组织架构、项目管理、文档中心等能力，但仍存在以下业务痛点：

- 需求类型、优先级、节点状态的维护方式不统一，扩展成本高。
- 工作流与节点处理权限表达不够贴近业务，取消、回退、草稿、项目绑定等规则尚未完整闭环。
- 组织架构与岗位、角色关系边界不清，不利于节点授权和数据归属管理。
- 项目管理与需求流转的绑定时机不明确，难以满足按流程节点控制项目归集的业务模式。
- 文档中心与需求流程的关联链路不完整，智能检索结果缺少业务上下文。

### 1.3 建设目标

- 建立全局统一的需求配置中心，支持需求类型、优先级、节点状态的维护与扩展。
- 建立全局统一工作流，支持可视化配置、节点授权、回退、取消、草稿与项目绑定规则。
- 建立固定四层组织架构模型，支撑用户归属、岗位管理和权限配置。
- 明确项目在流程中的绑定规则，支持以流程节点驱动项目归集。
- 打通文档中心与需求流程，支持关联需求信息的智能检索与在线分享。
- 形成完整、可追溯、可审计的需求流转记录体系。

### 1.4 不在本期范围

- 不支持按项目配置多套工作流。
- 不支持按需求类型切换不同工作流。
- 不支持用户兼任多个岗位。
- 不对外展示大模型完整内部思维链，仅展示可审计的检索过程摘要。
- 不将“评审管理”保留为独立一级菜单。
- 不重建独立文档管理平台，文档能力基于现有知识库与检索模块扩展。

---

## 2. 业务范围

### 2.1 业务定位

本次升级聚焦“配置驱动流程”的需求管理模式，将系统从基础需求登记平台升级为流程化、可审计、可扩展的需求流转平台。

### 2.2 适用需求类型

系统中的“需求”既包括面向日常支持的工单类事项，也包括面向研发交付的开发类事项，典型场景包括：

- 日常数据处理问题
- 接口对接需求
- 测试支持事项
- 零散开发需求
- 变更修复或专项支持工单

### 2.3 业务原则

- 配置驱动：需求类型、优先级、节点状态均由后台全局配置。
- 流程驱动：需求状态仅能通过流程动作改变，不允许直接改状态。
- 权限驱动：用户是否可见、可处理、可取消由节点授权决定。
- 审计驱动：所有关键操作必须记录日志和流转轨迹。

### 2.4 现状基础与改造原则

#### 2.4.1 现状基础

当前系统已具备以下与本次升级直接相关的基础能力：

- 需求类型、优先级的基础配置能力。
- 节点状态字典能力。
- 工作流相关能力，但当前存在两套并行实现。
- 区域、部门、岗位组织管理能力。
- 基础项目管理能力。
- 知识库文档上传、解析、向量检索与 RAG 答案生成能力。

#### 2.4.2 改造原则

- 优先复用已有模块，不重复建设同类能力。
- 新需求优先在现有模块基础上扩展，不新增平行能力链路。
- 同一业务能力仅保留一个最终事实来源。
- 涉及架构收敛的模块，必须先定义兼容策略和迁移路径，再进入功能开发。
- 目标态与实施态分离表达：PRD 同时描述最终目标与本期落地边界，避免一次性过度改造。

---

## 3. 用户角色

### 3.1 角色定义

| 角色 | 说明 |
| --- | --- |
| 超级管理员 | 拥有系统全局配置、工作流配置、组织管理、项目越权修改、审计查看能力 |
| 流程管理员 | 维护工作流、节点状态、节点权限、流程发布 |
| 需求创建人 | 创建需求、保存草稿、提交需求、按规则取消需求 |
| 节点处理人 | 在被授权节点查看并处理需求 |
| 项目管理人员 | 维护项目、导入导出项目、查看项目归集结果 |
| 普通检索用户 | 在权限范围内检索文档、查看文档和关联需求信息 |

### 3.2 岗位与角色关系

- 岗位与系统角色分离。
- 岗位是组织属性，用于标识人员在组织中的归属位置。
- 系统角色是权限属性，用于标识人员拥有哪些系统操作权限。
- 一名用户只能归属一个岗位。
- 同一用户可拥有一个或多个系统角色。

---

## 4. 关键业务规则

### 4.1 全局配置规则

- 需求类型为全局字典，支持新增、编辑、删除、启停和排序。
- 优先级为全局字典，支持新增、编辑、删除、启停和排序。
- 节点状态为全局字典，支持新增、编辑、删除、启停和排序。
- 所有配置均全局生效，不按项目隔离，不按需求类型隔离。

### 4.2 工作流规则

- 目标态为全系统仅维护一套全局标准工作流。
- 当前系统存在项目维度工作流配置与节点实例流转两套相关实现，本期需先完成工作流主链路收敛。
- 本期实施策略为：以统一工作流引擎作为新增功能唯一接入入口，历史项目维度流程能力仅作兼容保留。
- 迁移期内可保留 `projectId` 作为数据归属字段，但流程定义来源需逐步提升为全局标准配置。
- 所有新建或改造的流转能力，均应以统一工作流引擎输出可执行动作、流转记录和节点权限。
- 每个流程节点必须绑定一个节点状态。
- 节点流转关系由工作流配置定义。
- 流程回退仅允许回退到直接上一个节点。
- 到达结束节点后，流程终止，不能再回退或继续流转。

### 4.2.1 工作流收敛方案

- 当前系统的工作流能力存在“运行态状态机路线”和“节点实例引擎路线”并行的现状。
- 本次升级后，系统仅保留一套工作流执行主链路作为唯一事实来源。
- 统一工作流引擎需统一承接以下能力：
  - 草稿保存
  - 提交流转
  - 节点回退
  - 节点取消
  - 节点权限判断
  - 流转记录输出
- 历史实现若仍需保留，只能作为兼容层存在，不再承接新增功能。
- 后续如需下线旧实现，必须提供历史数据兼容与迁移方案。

### 4.3 开始节点规则

- 开始节点为需求创建节点。
- 开始节点对应需求状态为“开始节点绑定状态”（由工作流配置绑定，非硬编码）。
- 开始节点支持“保存草稿”和“提交”两个动作：
  - 保存草稿：需求保留在开始节点状态，标记为草稿，不推进流程。
  - 提交：取消草稿标记并推进流程（下一节点=1 自动推进；下一节点>1 由用户选择目标节点后推进）。
- 草稿需求可长期保留。
- 草稿需求不进入正式待办流。
- 开始节点允许直接取消需求（按取消权限配置）。

### 4.4 取消规则

- 除结束节点外，其余节点均可按权限配置是否允许取消。
- 以下用户可取消需求：
  - 需求创建人
  - 超级管理员
  - 当前节点具备取消权限的授权角色用户
- 取消时必须填写取消原因。
- 一旦取消，流程立即结束。

### 4.5 节点处理规则

- 节点处理权限采用“角色集合”表达。
- 节点可配置多个权限组合，但运行态按“角色并集”计算可处理角色集合。
- 满足可处理角色集合的用户均可查看并处理该节点（同时仍需满足项目/组织归属的数据隔离规则）。
- 角色处理方式为争抢式：谁先提交动作即记录为该节点实际处理人；后续操作需提示“已被处理”并阻止重复流转。

### 4.6 项目绑定规则

- 目标态为新建需求时项目默认非必填。
- 考虑当前系统对项目字段存在既有依赖，本期采用兼容演进方式推进项目后绑定能力。
- 是否必须绑定项目，由工作流节点配置决定。
- 当节点被配置为“项目必选”时，未选择项目则不允许提交流转。
- 已接入统一工作流引擎的新流程，可支持“创建时不选项目、节点上补选项目”。
- 未完成迁移的旧流程场景，可暂时保留创建时项目必填逻辑。
- 项目一旦绑定，普通用户后续不可修改。
- 超级管理员可在任意节点修改项目，且必须记录越权修改日志。
- 已截止项目不可在流转时被新绑定。

### 4.7 文档检索规则

- 本期文档中心能力基于现有知识库与智能检索模块扩展实现。
- 需求创建和流转中的附件默认进入文档中心/知识库归档链路。
- 文档若来自需求流程，检索结果中必须展示关联需求信息。
- LLM 检索输出仅展示“过程摘要”，不展示完整内部思考链。
- 检索结果必须展示引用来源和命中文档片段。
- 本期重点不在重建独立文档管理平台，而在于打通“需求附件归档、知识库检索、需求关联展示”。

### 4.8 流转与权限详细设计说明

本节为研发实现提供可直接落地的“状态机 + 可见性 + 并发 + 审计 + 接口”详细设计口径，作为后续升级改造的统一实现标准。

#### 4.8.1 名词与边界

- 工作流定义（Workflow Definition）：当前启用的流程模板，定义节点、节点绑定状态、节点权限与节点间连线。
- 工作流实例（Workflow Instance）：某个需求对应的一次流程执行实例，记录当前节点与历史流转轨迹。
- 流程节点（Node）：流程中的一个环节，必须绑定一个“节点状态”，并配置“可处理角色集合”。
- 节点状态（Status）：需求当前的状态展示值（如“新建 / 待评审 / 处理中 / 已完成”等），由节点绑定。
- 草稿（Draft）：开始节点中的一种状态形态，用于创建人暂存；草稿不进入正式待办流。
- 待办（My Pending）：仅展示“我可处理”的需求列表（本期采用 B 模式），并受项目/组织归属隔离。

#### 4.8.2 需求字段与状态机约束

需求（Requirement）需具备以下与流程/并发相关的核心字段（字段名以实现为准，本节仅约束语义）：

- `workflowDefinitionId`：当前启用工作流定义 ID（或引用键）。
- `workflowInstanceId`：工作流实例 ID。
- `currentNodeId`：当前所在流程节点 ID。
- `statusCode`：当前节点绑定的状态编码。
- `isDraft`：是否草稿（仅在开始节点生效）。
- `creatorId`：创建人 ID。
- `creatorDeptId`：创建人部门 ID（建议创建时快照，避免后续组织变更导致草稿可见性漂移）。
- `creatorRoleIds`：创建人角色集合（建议创建时快照，用于“同部门同角色可见”判定）。
- `version`：并发控制版本号（用于防止重复流转与并发覆盖）。
- `projectId / orgScope`：项目与组织隔离字段（以系统既有数据隔离实现为准，本期必须叠加）。

状态机约束：

- 需求状态只能通过工作流动作改变；禁止直接改状态。
- 草稿仅允许存在于开始节点；非开始节点禁止 `isDraft=1`。
- “保存草稿”不推进流程；“提交”推进流程。

#### 4.8.3 草稿可见性（开始节点）

草稿需求对用户可见当且仅当满足以下任一条件：

- 创建人本人可见：`userId == creatorId`
- 同部门且同角色可见：`userDeptId == creatorDeptId` 且 `userRoleIds ∩ creatorRoleIds ≠ ∅`
- 部门管理者角色可见：`userRoleIds ∩ deptManagerRoleIds(creatorDeptId) ≠ ∅`

其中：

- `deptManagerRoleIds(creatorDeptId)` 为部门维度配置的“管理者角色集合”（每个部门可不同，本期仅支持角色维度配置）。
- 草稿不进入正式待办流，但可作为“我的草稿”列表展示。

#### 4.8.4 待办可见性（仅展示我可处理）

本期列表模式采用 B：列表仅展示“我可处理”的需求（待办箱），并受项目/组织归属隔离。

待办列表返回条件：

- 非草稿：`isDraft=0`
- 当前节点可处理角色命中：`userRoleIds ∩ nodeHandlerRoleIds(currentNodeId) ≠ ∅`
- 数据隔离通过：必须满足项目归属与组织归属隔离规则（不得绕过 RBAC/组织隔离）。

#### 4.8.5 提交与流转（下一节点选择）

开始节点动作：

- 保存草稿：
  - 仅创建人发起（或满足草稿可见性且具备创建/编辑权限的用户发起，按权限策略实现）。
  - 持久化需求内容与流程实例（如系统以实例驱动），但不推进到下一节点。
  - `isDraft` 置为 `1`。
- 提交：
  - 取消草稿：`isDraft` 置为 `0`。
  - 计算下一节点候选集（基于当前启用工作流定义与当前节点连线）：
    - 候选集大小=1：自动推进到该节点。
    - 候选集大小>1：前端必须展示“下一环节”下拉选择（节点名称列表），由用户选择目标节点后才允许提交。

非开始节点动作（本期仅定义统一口径，具体动作类型按工作流配置扩展）：

- 提交流转必须由当前节点可处理角色命中且满足数据隔离的用户发起。
- 目标节点必须属于“当前节点的下一节点候选集”，否则视为目标非法。

#### 4.8.6 并发控制与提示策略

目标：以首个成功流转者为准，后续重复提交必须阻止，并提示“谁在什么时间已处理完成”。

实现建议：

- 采用乐观锁（`version`）控制流转一致性：流转 API 请求需携带当前 `version`。
- 更新时以 `(id, version, currentNodeId, statusCode, isDraft)` 为条件执行原子更新：
  - 若更新成功（影响行数=1）：写入流转记录，`version+1`。
  - 若更新失败（影响行数=0）：返回冲突信息，并附带最近一次成功流转记录（处理人、处理时间、从/到节点与状态）。

前端提示示例：

- “该需求已于 {handledAt} 被 {handledBy} 处理并流转至 {toNodeName}/{toStatusName}，请刷新后查看最新状态。”

#### 4.8.7 审计与流转记录

所有关键动作必须记录审计：

- 动作类型：保存草稿 / 提交 / 流转 / 回退 / 取消
- 操作人、操作时间
- fromNode/fromStatus → toNode/toStatus
- 备注/原因（取消原因、回退原因等）
- 并发冲突失败也需返回明确原因（无权限/状态已变更/目标非法）。

#### 4.8.8 接口设计（建议）

统一响应遵循系统约定：`{ code, message, data }`，分页遵循 `{ list, total, pageNum, pageSize }`。

1）保存草稿（开始节点）

- `POST /api/v1/requirements/{id}/draft`
- 入参（示例，字段以需求表单为准）：
  - `payload`：需求表单内容
  - `version`：当前版本号（新建草稿可不传或传 0）
- 结果：
  - 返回最新 `requirementId`、`version`、`statusCode`、`isDraft`

2）提交（开始节点/通用流转）

- `POST /api/v1/requirements/{id}/submit`
- 入参：
  - `version`：当前版本号
  - `nextNodeId`：可选；当候选下一节点>1 时必填
- 错误码（示例）：
  - 403：无权限
  - 409：并发冲突（返回 latestTransitionInfo）
  - 400：目标节点非法/缺少 nextNodeId/状态不允许提交

3）获取下一节点候选集

- `GET /api/v1/requirements/{id}/next-nodes`
- 返回：候选节点列表（nodeId、nodeName、bindStatusCode、bindStatusName）

4）我的草稿列表

- `GET /api/v1/requirements/my-drafts`
- 条件：草稿可见性规则命中（创建人/同部门同角色/部门管理者角色），并叠加数据隔离（项目/组织）。

5）我的待办列表（仅展示我可处理）

- `GET /api/v1/requirements/my-pending`
- 条件：非草稿 + 节点可处理角色命中 + 数据隔离。

6）流转记录

- `GET /api/v1/requirements/{id}/transitions`
- 返回：流转记录列表（用于详情页展示审计轨迹与并发提示来源）。

#### 4.8.9 前端交互设计（建议）

- 需求创建/编辑（开始节点）：
  - 操作按钮：保存草稿 / 提交
  - 提交时：
    - 候选下一节点=1：直接提交并自动推进
    - 候选下一节点>1：在提交区域展示“下一环节”下拉（节点名称），选择后才允许提交
  - 草稿可见性：创建人同部门同角色、部门管理者角色可看到草稿，但默认仅创建人可编辑（如需支持协作编辑需另行定义编辑权限口径）

- 待办列表：
  - 默认进入“我的待办（我可处理）”
  - 支持切换到“我的草稿”
  - 列表仅展示符合可见性条件的数据，避免无意义的信息堆叠

#### 4.8.10 数据库设计（建议）

本节以当前 `database/init.sql` 为基线，定义为满足“草稿可见性 / 待办过滤 / 并发控制 / 审计轨迹”的最小新增与约束。

（1）需求表 requirements

现状已包含（或通过增量语句补齐）与流程有关字段：

- `workflow_instance_id`：工作流实例 ID
- `node_status`：当前节点状态编码（与工作流节点绑定的状态）
- `is_draft`：草稿标记（0/1）
- `version`：乐观锁版本号
- `department_id`：所属部门 ID（本期定义为“创建时部门快照”，用于草稿可见性判定）

本期建议新增字段（用于“同部门同角色可见”稳定判定）：

- `creator_role_codes` JSON DEFAULT NULL COMMENT '创建人角色码快照（取 SecurityUtils.getCurrentUserRoles），用于草稿可见性(同部门同角色)'

索引建议：

- `idx_is_draft_department`：(`is_draft`, `department_id`)
- `idx_is_draft_creator`：(`is_draft`, `creator_id`)
- `idx_workflow_instance_id`：(`workflow_instance_id`)
- 保留现有 `idx_status` 与 `node_status` 的一致性（建议逐步以 `node_status` 作为流程展示/过滤字段，`status` 作为兼容展示字段）

（2）部门管理者角色配置

当前 `init.sql` 未定义部门实体表，为支持“部门维度配置管理者角色集合（每个部门可不同）”，本期建议新增独立配置表：

- 表：`department_manager_roles`
- 字段：
  - `id` BIGINT UNSIGNED AUTO_INCREMENT
  - `department_id` BIGINT UNSIGNED NOT NULL
  - `manager_role_codes` JSON NOT NULL COMMENT '部门管理者角色码列表（部门维度配置）'
  - `updated_by` BIGINT UNSIGNED DEFAULT NULL
  - `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
- 约束：
  - `uk_department_id`：(`department_id`) 唯一
- 索引：
  - `idx_updated_at`：(`updated_at`)

（3）工作流实例与流转记录

现状已有：

- `workflow_instances`：需求对应的实例（唯一绑定 requirement_id）
- `workflow_instance_transitions`：流转记录（含 from/to 节点、操作人、动作、进入/离开时间、停留时长）

本期约束补充：

- 任意“提交/流转/回退/取消”必须写入 `workflow_instance_transitions`（失败则返回明确原因；如需要留存失败尝试，可另外引入失败日志表，但本期不强制）。

#### 4.8.11 接口定义（详细）

接口基线：

- Base Path：`/api/v1/`
- 认证：`Authorization: Bearer <token>`
- 统一响应：`{ code, message, data }`
- 分页响应：`{ list, total, pageNum, pageSize }`

（1）保存草稿（开始节点）

- `POST /api/v1/requirements/{id}/draft`
- 请求体（示例）：
  - `version`：number（可选；新建草稿默认 0）
  - `payload`：需求表单字段（以现有需求创建/编辑 DTO 为准）
- 返回 `data`（示例）：
  - `id`：number
  - `version`：number
  - `isDraft`：boolean
  - `nodeStatus`：string
  - `workflowInstanceId`：number | null

规则：

- 保存草稿不会推进流程，不产生“待办”。
- 保存草稿时必须将 `isDraft=1`，并更新 `department_id`（创建人部门快照）与 `creator_role_codes`（创建人角色快照）。

（2）获取下一环节候选节点

- `GET /api/v1/requirements/{id}/next-nodes`
- 返回 `data`：
  - `list`：候选节点数组
    - `nodeId`：string
    - `nodeName`：string
    - `bindStatusCode`：string
    - `bindStatusName`：string

规则：

- 候选集大小=1 时前端可直接提交无需选择；>1 时前端必须提供下拉选择 `nextNodeId`。

（3）提交（开始节点/通用流转）

- `POST /api/v1/requirements/{id}/submit`
- 请求体：
  - `version`：number（必填）
  - `nextNodeId`：string（当候选集>1 时必填）
  - `comment`：string（可选，记录到流转轨迹）
- 返回 `data`：
  - `id`：number
  - `version`：number（更新后的版本）
  - `isDraft`：boolean（提交后必须为 false）
  - `nodeStatus`：string（更新后的节点状态）
  - `currentNodeId`：string（更新后的节点ID）
  - `workflowInstanceId`：number

错误码与错误体建议：

- 400：目标节点非法 / 缺少 nextNodeId / 状态不允许提交
- 403：无权限（角色不匹配/数据隔离不通过）
- 409：并发冲突（返回最新处理信息）
  - `data.latestTransition`：
    - `operatorId`：number
    - `operatorName`：string
    - `completedAt`：string（ISO 或 yyyy-MM-dd HH:mm:ss）
    - `toNodeName`：string
    - `toStatusName`：string

（4）我的草稿列表

- `GET /api/v1/requirements/my-drafts`
- 返回分页：`{ list, total, pageNum, pageSize }`
- 查询条件：
  - 草稿可见性规则命中（创建人/同部门同角色/部门管理者角色）
  - 叠加项目/组织数据隔离

（5）我的待办列表（仅展示我可处理）

- `GET /api/v1/requirements/my-pending`
- 返回分页：`{ list, total, pageNum, pageSize }`
- 查询条件：
  - 非草稿 `isDraft=0`
  - 节点可处理角色集合命中
  - 叠加项目/组织数据隔离

（6）流转记录

- `GET /api/v1/requirements/{id}/transitions`
- 返回 `data`：
  - `list`：流转记录数组（按时间倒序或正序统一约定）
    - `action`：submit/cancel/rollback/approve/reject 等
    - `fromNodeName` / `toNodeName`
    - `operatorName`
    - `startedAt` / `completedAt`
    - `comment`

#### 4.8.12 页面与交互（原型级描述）

（1）需求创建页（开始节点）

- 表单区：标题、描述、类型、优先级、附件、项目（可选/按节点配置必填）。
- 操作区：
  - 按钮：保存草稿 / 提交
  - 当 `nextNodes.length > 1`：
    - 提交按钮旁展示“下一环节”下拉（节点名称列表）
    - 未选择时提交按钮置灰或提交报错提示

（2）我的待办（仅展示我可处理）

- 默认入口为“我的待办”
- 列表字段建议：
  - 需求编号、标题、类型、优先级、当前状态（node_status）、创建人、更新时间、项目
- 操作：
  - 点击行进入详情
  - 详情页根据“当前节点可执行动作”渲染按钮（提交/回退/取消等）

（3）我的草稿

- 默认展示满足草稿可见性规则的数据
- 区分权限：
  - 创建人：可编辑、可提交
  - 同部门同角色/部门管理者：默认仅可查看（如需协作编辑需另行定义“可编辑草稿”规则）

#### 4.8.13 权限矩阵（关键动作）

| 动作 | 创建人 | 同部门同角色 | 部门管理者角色 | 当前节点处理角色 | 超级管理员 |
| --- | --- | --- | --- | --- | --- |
| 查看草稿 | 是 | 是 | 是 | 否 | 是 |
| 编辑草稿 | 是 | 否（默认） | 否（默认） | 否 | 是（可选，需审计） |
| 保存草稿 | 是 | 否 | 否 | 否 | 是（可选，需审计） |
| 提交草稿 | 是 | 否（默认） | 否（默认） | 否 | 是（可选，需审计） |
| 查看待办 | 否（除非可处理） | 否（除非可处理） | 否（除非可处理） | 是 | 是 |
| 提交流转 | 否（除非可处理） | 否（除非可处理） | 否（除非可处理） | 是 | 是 |

备注：

- “部门管理者角色”配置来源：`department_manager_roles.manager_role_ids`（部门维度配置，角色并集判断）。
- “部门管理者角色”配置来源：`department_manager_roles.manager_role_codes`（部门维度配置，角色并集判断，角色码来源同 SecurityUtils.getCurrentUserRoles）。
- 项目/组织数据隔离始终叠加，不因角色命中而绕过。

#### 4.8.14 兼容与迁移（建议）

- 当前 `requirements` 已存在 `status` 字段与新增 `node_status` 字段：
  - 目标态：列表与详情展示优先使用 `node_status`（与工作流节点绑定）。
  - 兼容策略：保留 `status` 作为历史字段或与 `node_status` 同步（以现有代码现状为准，迁移期内允许双写）。
- `project_id` 当前为 NOT NULL：
  - 若要支持“创建时不选项目”，建议使用 `project_id=0` 作为“未绑定项目”的兼容值，并在节点配置要求项目必选时校验 `project_id != 0`。

#### 4.8.15 验收用例（关键路径）

- 草稿保存：
  - 创建人保存草稿后再次进入可继续编辑，`isDraft=1` 不推进流程
- 草稿可见：
  - 同部门且与创建人有相同角色的用户可见草稿
  - 部门管理者角色用户可见草稿
  - 非同部门/无相同角色/非部门管理者不可见草稿
- 提交推进：
  - 候选下一节点=1 自动推进，状态与节点正确变化，写入流转记录
  - 候选下一节点>1 必须选择下一环节才能提交，推进后写入流转记录
- 待办过滤：
  - 待办仅返回我可处理的需求（角色命中 + 数据隔离）
- 并发冲突：
  - 两个处理人同时提交流转：首个成功，后者返回 409 并提示处理人和处理时间

#### 4.8.16 DTO 与返回结构（字段级）

本节给出接口入参/出参的字段级定义与示例，作为前后端联调与测试用例编写依据。字段命名以系统现有风格为准（下述以 camelCase 举例，后端可按项目既有 DTO 命名规范落地）。

（1）统一响应 Envelope

- `code`：number，0 表示成功，其它为业务错误码
- `message`：string，错误提示或成功提示
- `data`：T，业务数据

示例：

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

（2）分页响应 Page<T>

- `list`：T[]
- `total`：number
- `pageNum`：number
- `pageSize`：number

示例：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "list": [],
    "total": 0,
    "pageNum": 1,
    "pageSize": 20
  }
}
```

（3）保存草稿

- `POST /api/v1/requirements/{id}/draft`

Request Body：`RequirementDraftSaveRequest`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| version | number | 否 | 乐观锁版本，新建草稿可不传或传 0 |
| payload | object | 是 | 需求表单字段（标题、描述、类型、优先级、附件、项目等） |

Response Data：`RequirementDraftSaveResponse`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | number | 是 | 需求ID |
| version | number | 是 | 更新后的版本号 |
| isDraft | boolean | 是 | 是否草稿，必须为 true |
| nodeStatus | string | 是 | 当前节点状态编码（开始节点绑定的状态） |
| workflowInstanceId | number \| null | 是 | 工作流实例ID（按实现，允许草稿即创建实例或延后创建） |

示例：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 10001,
    "version": 3,
    "isDraft": true,
    "nodeStatus": "DRAFT",
    "workflowInstanceId": 90001
  }
}
```

（4）获取下一环节候选节点

- `GET /api/v1/requirements/{id}/next-nodes`

Response Data：`NextNodeListResponse`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| list | NextNodeOption[] | 是 | 候选节点列表 |

`NextNodeOption`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| nodeId | string | 是 | 节点ID |
| nodeName | string | 是 | 节点名称（用于下拉展示） |
| bindStatusCode | string | 是 | 目标节点绑定状态编码 |
| bindStatusName | string | 是 | 目标节点绑定状态名称 |

示例：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "list": [
      {
        "nodeId": "N_ANALYSIS",
        "nodeName": "需求分析",
        "bindStatusCode": "PENDING_ANALYSIS",
        "bindStatusName": "待分析"
      },
      {
        "nodeId": "N_CANCEL",
        "nodeName": "取消",
        "bindStatusCode": "CANCELLED",
        "bindStatusName": "已取消"
      }
    ]
  }
}
```

（5）提交（开始节点/通用流转）

- `POST /api/v1/requirements/{id}/submit`

Request Body：`RequirementSubmitRequest`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| version | number | 是 | 乐观锁版本号 |
| nextNodeId | string | 条件必填 | 当下一节点候选集 > 1 时必填 |
| comment | string | 否 | 操作意见，记录到流转记录 |

Response Data：`RequirementSubmitResponse`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | number | 是 | 需求ID |
| version | number | 是 | 更新后的版本号 |
| isDraft | boolean | 是 | 必须为 false |
| nodeStatus | string | 是 | 更新后的节点状态 |
| currentNodeId | string | 是 | 更新后的当前节点ID |
| workflowInstanceId | number | 是 | 工作流实例ID |

示例：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 10001,
    "version": 4,
    "isDraft": false,
    "nodeStatus": "PENDING_ANALYSIS",
    "currentNodeId": "N_ANALYSIS",
    "workflowInstanceId": 90001
  }
}
```

并发冲突（409）返回建议：`RequirementSubmitConflictResponse`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| latestTransition | object | 是 | 最近一次成功流转信息，用于提示 |

`latestTransition`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| operatorId | number | 是 | 处理人ID |
| operatorName | string | 是 | 处理人名称 |
| completedAt | string | 是 | 处理完成时间 |
| toNodeName | string | 是 | 目标节点名称 |
| toStatusName | string | 是 | 目标状态名称 |

示例：

```json
{
  "code": 409,
  "message": "状态已变更",
  "data": {
    "latestTransition": {
      "operatorId": 20001,
      "operatorName": "张三",
      "completedAt": "2026-05-16 15:12:33",
      "toNodeName": "需求分析",
      "toStatusName": "待分析"
    }
  }
}
```

（6）我的草稿列表

- `GET /api/v1/requirements/my-drafts`

Query（示例）：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| pageNum | number | 否 | 默认 1 |
| pageSize | number | 否 | 默认 20 |
| keyword | string | 否 | 标题/编号关键字 |
| projectId | number | 否 | 项目过滤（叠加隔离） |

List Item：`RequirementDraftListItem`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | number | 是 | 需求ID |
| requirementNo | string | 否 | 需求编号 |
| title | string | 是 | 标题 |
| priority | string | 是 | 优先级 |
| type | string | 是 | 类型 |
| nodeStatus | string | 是 | 当前节点状态 |
| creatorId | number | 是 | 创建人 |
| creatorName | string | 否 | 创建人名称 |
| departmentId | number | 否 | 创建人部门快照 |
| updatedAt | string | 是 | 更新时间 |

（7）我的待办列表

- `GET /api/v1/requirements/my-pending`

Query（示例）：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| pageNum | number | 否 | 默认 1 |
| pageSize | number | 否 | 默认 20 |
| keyword | string | 否 | 标题/编号关键字 |
| nodeStatus | string | 否 | 状态过滤 |
| projectId | number | 否 | 项目过滤（叠加隔离） |

List Item：`RequirementPendingListItem`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | number | 是 | 需求ID |
| requirementNo | string | 否 | 需求编号 |
| title | string | 是 | 标题 |
| priority | string | 是 | 优先级 |
| type | string | 是 | 类型 |
| nodeStatus | string | 是 | 当前节点状态 |
| currentNodeId | string | 是 | 当前节点ID |
| creatorName | string | 否 | 创建人 |
| projectName | string | 否 | 项目名称 |
| updatedAt | string | 是 | 更新时间 |

（8）流转记录

- `GET /api/v1/requirements/{id}/transitions`

Response Data：`TransitionRecordListResponse`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| list | TransitionRecord[] | 是 | 流转记录 |

`TransitionRecord`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| action | string | 是 | submit/cancel/rollback/... |
| fromNodeName | string | 否 | 源节点名称 |
| toNodeName | string | 是 | 目标节点名称 |
| operatorName | string | 是 | 操作人 |
| startedAt | string | 是 | 进入该节点时间 |
| completedAt | string | 否 | 离开该节点时间 |
| comment | string | 否 | 意见/备注 |

#### 4.8.17 时序与异常流程（文字时序图）

（1）保存草稿

1. 用户在开始节点编辑需求 → 点击“保存草稿”
2. 前端调用（新建草稿）`POST /api/v1/requirements/drafts` 或（更新草稿）`PUT /api/v1/requirements/{id}/draft`
3. 后端：
   - 校验：用户是否具备保存草稿权限（至少创建人；其它可见者默认只读）
   - 写入：`requirements.is_draft=1`、更新表单字段、更新 `department_id`（创建人部门快照）与 `creator_role_codes`（创建人角色快照）
   - 返回：最新 `version` 与草稿状态
4. 前端提示“已保存草稿”

（2）提交流转（下一节点=1）

1. 用户点击“提交”
2. 前端请求 `GET /requirements/{id}/next-nodes`
3. 若返回候选节点为 1：
   - 前端直接调用 `POST /requirements/{id}/submit`（不传 `nextNodeId` 或由后端默认）
4. 后端：
   - 校验：草稿状态可提交、用户权限、数据隔离
   - 并发：按 `(id, version, currentNodeId, nodeStatus, isDraft)` 原子更新
   - 写入：`workflow_instance_transitions` 记录
   - 返回：新 `version`、新 `nodeStatus/currentNodeId`
5. 前端刷新详情/列表，显示新状态

（3）提交流转（下一节点>1）

1. 用户点击“提交”
2. 前端请求 `GET /requirements/{id}/next-nodes`
3. 若候选节点>1：
   - 前端展示“下一环节”下拉（节点名称）
   - 用户选择 `nextNodeId` 后点击确认提交
4. 后端：
   - 校验：`nextNodeId` 必须在候选集中
   - 其余同（2）

（4）并发冲突（409）

1. A、B 两个处理人几乎同时提交
2. A 先成功更新并写入流转记录
3. B 提交时原子更新影响行数=0
4. 后端返回 409，并携带最近一次成功流转信息（operatorName/completedAt/toNodeName/toStatusName）
5. 前端弹窗提示：
   - “该需求已于 {completedAt} 被 {operatorName} 处理并流转至 {toNodeName}/{toStatusName}，请刷新后查看最新状态。”

#### 4.8.18 研发直接照抄 - 数据库增量脚本（DDL）

以下 DDL 可直接追加到 `database/init.sql` 末尾（本仓库约定仅维护此文件，不新增迁移脚本文件）。

（1）requirements：补齐草稿可见性角色快照字段

```sql
ALTER TABLE `requirements`
  ADD COLUMN IF NOT EXISTS `creator_role_codes` JSON DEFAULT NULL COMMENT '创建人角色码快照（SecurityUtils.getCurrentUserRoles）';
```

（2）department_manager_roles：部门维度管理者角色集合（角色码）

```sql
CREATE TABLE IF NOT EXISTS `department_manager_roles` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `department_id` BIGINT UNSIGNED NOT NULL COMMENT '部门ID（SysOrg 部门节点ID）',
  `manager_role_codes` JSON NOT NULL COMMENT '部门管理者角色码列表（部门维度配置）',
  `updated_by` BIGINT UNSIGNED DEFAULT NULL,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_department_id` (`department_id`),
  INDEX `idx_updated_at` (`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门管理者角色配置表';
```

#### 4.8.19 研发直接照抄 - 后端实现清单（接口/权限/并发）

本节以“最少改动、直接可跑”为原则，给出后端接口与关键业务逻辑的直接实现口径。

（1）接口清单（最终以代码为准）

- 创建草稿：`POST /api/v1/requirements/drafts`
- 更新草稿：`PUT /api/v1/requirements/{id}/draft`
- 查询草稿：`GET /api/v1/requirements/my-drafts`
- 查询待办（仅我可处理）：`GET /api/v1/requirements/my-pending`
- 查询开始节点下一环节：`GET /api/v1/requirements/{id}/next-nodes`
- 提交：`POST /api/v1/requirements/{id}/submit`

（2）草稿创建/保存规则（直接照抄）

- 创建草稿默认写入：
  - `is_draft=1`
  - `node_status='DRAFT'`
  - `workflow_instance_id=NULL`
  - `department_id=创建人 departmentId 快照`
  - `org_id=创建人 orgId 快照（无 orgId 时退化为 departmentId/regionId）`
  - `creator_role_codes=SecurityUtils.getCurrentUserRoles()`

（3）草稿可见性判定（直接照抄）

给定草稿 requirement r 与当前用户 u：

- `u.id == r.creator_id`
- 或 `u.department_id == r.department_id` 且 `intersect(u.roleCodes, r.creator_role_codes)`
- 或 `intersect(u.roleCodes, department_manager_roles(r.department_id).manager_role_codes)`

其中：

- `u.roleCodes` 取自 `SecurityUtils.getCurrentUserRoles()`（既包含 legacy system_role 也包含 RBAC roles.code）
- `intersect(a,b)`：任一相等即命中

（4）待办判定（仅展示我可处理）

- 基础条件：`is_draft=0` 且 `workflow_instance_id IS NOT NULL` 且 `deleted_at=0`
- 数据隔离：`org_id` 必须在当前用户所属组织及其子组织范围内（使用 `SysOrgService.getDescendantIds`）
- 可处理判定：按工作流引擎当前节点权限判定（SPECIFIED_USER / SPECIFIED_ROLE）
  - SPECIFIED_USER：operatorId 在 node.assigneeUserIds 中
  - SPECIFIED_ROLE：将 node.assigneeRoleId 映射为 roles.code，要求当前用户角色码包含该 code

（5）提交并发控制（直接照抄）

- 提交时必须携带 `version`
- 后端使用 `WHERE id=? AND version=?` 原子更新（影响行数=0 视为并发冲突）
- 并发冲突提示从 `workflow_instance_transitions` 取最近一次完成的流转记录，拼接到 message：
  - “该需求已于 {completedAt} 被 {operatorName} 处理并流转至 {toNodeName}/{toStatusName}，请刷新后查看最新状态。”

#### 4.8.20 研发直接照抄 - 前端实现清单（组件/交互/文案）

（1）需求列表（src/views/requirements/index.vue）

- 默认页签：我的待办
- 页签切换：
  - 我的待办 → 调用 `GET /v1/requirements/my-pending`
  - 我的草稿 → 调用 `GET /v1/requirements/my-drafts`
- 列表字段：
  - 标题、需求编号、优先级、类型、nodeStatus、创建人、更新时间
- 交互：
  - 点击行进入详情
  - 草稿行标签展示“草稿”

（2）需求创建（src/views/requirements/create.vue）

- 操作栏：
  - 保存草稿：允许部分字段为空（但 projectId 必填）
  - 提交：需先校验标题与优先级必填；当 nextNodes>1 必须选择下一环节
- 提交流程：
  1. 若未生成 requirementId → 先创建草稿
  2. 请求 `GET /v1/requirements/{id}/next-nodes`
  3. nextNodes=1 → 直接提交；>1 → 下拉选择 nodeName 对应的 nodeId，再提交
  4. 成功后跳转详情页
- 文案（统一）：
  - 草稿保存成功：“已保存草稿”
  - 提交成功：“已提交”
  - 并发冲突提示：直接使用后端返回 message 展示弹窗

#### 4.8.21 错误码与提示文案全集（研发/测试照抄）

| 场景 | code | message（建议） |
| --- | --- | --- |
| 未登录 | 401 | 未登录或登录已过期 |
| 无权限 | 403 | 您没有权限操作此节点 |
| 需求不存在 | 404 | 需求不存在 |
| 工作流未启用 | 400 | 项目未启用工作流，无法提交 |
| 下一环节缺失 | 400 | 请先选择下一环节 |
| 目标节点非法 | 400 | 目标节点不在可选范围内 |
| 并发冲突 | 409 | 该需求已于 {completedAt} 被 {operatorName} 处理并流转至 {toNodeName}/{toStatusName}，请刷新后查看最新状态。 |



## 5.1 需求配置

### 5.1.1 需求类型管理

#### 功能说明

用于维护需求内容的全局分类标签，支持对后续需求进行统一归类。

#### 功能点

- 查看需求类型列表
- 新增需求类型
- 编辑需求类型
- 删除需求类型
- 启用/停用需求类型
- 调整显示顺序

#### 字段定义

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| 类型名称 | 文本 | 是 | 如工单、开发需求、接口对接、测试支持 |
| 类型编码 | 文本 | 是 | 用于系统内部识别，需唯一 |
| 展示颜色 | 颜色值 | 否 | 用于页面标签展示 |
| 排序号 | 数值 | 是 | 控制前端展示顺序 |
| 状态 | 枚举 | 是 | 启用、停用 |
| 说明 | 文本 | 否 | 类型用途描述 |

#### 规则说明

- 类型编码全局唯一。
- 停用后的类型不可用于新建需求，但保留历史数据展示。
- 删除前需校验是否已有需求引用。

### 5.1.2 优先级管理

#### 功能说明

用于维护需求紧急程度的全局字典，并支持拖拽排序。

#### 功能点

- 查看优先级列表
- 新增优先级
- 编辑优先级
- 删除优先级
- 启用/停用优先级
- 拖拽排序

#### 字段定义

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| 优先级名称 | 文本 | 是 | 如紧急、高、中、低 |
| 优先级编码 | 文本 | 是 | 用于系统内部识别，需唯一 |
| 展示颜色 | 颜色值 | 否 | 用于标签和筛选展示 |
| 排序号 | 数值 | 是 | 控制新建需求时下拉展示顺序 |
| 状态 | 枚举 | 是 | 启用、停用 |

#### 交互要求

- 排序采用拖拽方式完成。
- 页面显示手抓样式图标即可拖动，不显示传统拖拽条图标。

### 5.1.3 节点状态管理

#### 功能说明

用于维护工作流节点可绑定的状态字典。

#### 功能点

- 查看节点状态列表
- 新增节点状态
- 编辑节点状态
- 删除节点状态
- 启用/停用节点状态
- 调整排序

#### 字段定义

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| 状态名称 | 文本 | 是 | 如待分析、待评审、开发中、测试中、已验收 |
| 状态编码 | 文本 | 是 | 用于系统内部识别，需唯一 |
| 展示颜色 | 颜色值 | 否 | 页面标签展示 |
| 是否终态 | 布尔 | 是 | 标识是否为终止状态 |
| 排序号 | 数值 | 是 | 控制展示顺序 |
| 状态 | 枚举 | 是 | 启用、停用 |

#### 规则说明

- 节点状态仅作为流程节点的状态承载，不单独维护前后流转关系。
- 删除前需校验是否已被工作流节点引用。

---

## 5.2 工作流配置

### 5.2.1 功能目标

通过可视化配置逐步收敛现有工作流能力，形成一套统一工作流执行主链路，覆盖需求从新建、分析、评审、排期、开发、测试、上线、验收到取消和结束的全过程。

### 5.2.2 页面能力

- 可视化画布
- 拖拽节点
- 连线配置
- 节点属性配置
- 保存草稿
- 发布流程
- 流程校验

### 5.2.3 节点类型

| 节点类型 | 说明 |
| --- | --- |
| 开始节点 | 需求创建入口，支持草稿和提交 |
| 业务节点 | 绑定具体节点状态和处理权限 |
| 取消节点 | 流程取消终止节点 |
| 结束节点 | 流程正常结束节点 |

### 5.2.4 节点配置项

| 配置项 | 必填 | 说明 |
| --- | --- | --- |
| 节点名称 | 是 | 用于页面展示 |
| 绑定状态 | 是 | 从全局节点状态字典选择 |
| 处理方式 | 是 | 角色、指定人员、角色+指定人员 |
| 授权角色 | 否 | 处理方式涉及角色时必填 |
| 指定人员 | 否 | 处理方式涉及人员时必填 |
| 是否允许取消 | 是 | 标识该节点是否开放取消动作 |
| 是否项目必选 | 是 | 标识该节点流转前是否必须选择项目 |
| 下一节点 | 是 | 定义正向流转关系 |

### 5.2.5 流程校验规则

- 必须存在且仅存在一个开始节点。
- 至少存在一个结束节点。
- 任意业务节点都必须可从开始节点到达。
- 任意非终态节点都应具备可离开路径。
- 不允许孤立节点。
- 不允许非法自循环。
- 回退不通过自由连线表达，而由系统按“仅上一节点可回退”内置控制。

### 5.2.6 发布规则

- 画布保存为配置草稿。
- 发布前必须通过流程完整性校验。
- 发布成功后成为当前唯一生效流程。
- 对于仍依赖历史项目维度流程的场景，需保留兼容查询能力，但不再作为新流程发布入口。

---

## 5.3 组织架构管理

### 5.3.1 组织模型

业务展示层采用固定四层模型：

```text
区域
  └── 公司
        └── 部门
              └── 岗位
```

数据实现层采用兼容方案：

- 本期暂不强制新增独立 `Company` 实体。
- 继续沿用现有组织节点模型，在部门树中通过类型区分“公司”“部门”“组”。
- 前端展示层将类型为“公司”的组织节点映射为第二层“公司”。
- 用户归属仍以区域、部门、岗位为主，不在本期强制改造为用户主表新增独立 `companyId` 字段。

### 5.3.2 功能点

- 区域管理：新增、编辑、删除、排序
- 公司管理：新增、编辑、删除、排序
- 部门管理：新增、编辑、删除、排序
- 岗位管理：新增、编辑、删除、排序
- 树形折叠展示

### 5.3.3 展示要求

- 页面默认全部折叠。
- 支持逐级展开和收起。
- 支持按当前层级展示名称、编码、状态、负责人等关键信息。

### 5.3.4 岗位管理规则

- 岗位必须归属于一个部门。
- 岗位创建时必须配置所属区域、所属部门和菜单权限。
- 岗位不等于系统角色。
- 岗位用于组织归属，系统角色用于权限控制。

---

## 5.4 用户管理

### 5.4.1 功能点

- 用户列表查询
- 新建用户
- 编辑用户
- 启用/停用用户
- 查看用户详情
- 发送初始密码邮件

### 5.4.2 字段定义

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| 用户名 | 文本 | 是 | 仅允许字母、数字、下划线 |
| 真实姓名 | 文本 | 是 | 用户姓名 |
| 手机号 | 文本 | 是 | 用于默认密码规则 |
| 邮箱 | 文本 | 是 | 用于发送初始密码 |
| 所属区域 | 关联 | 是 | 从组织架构选择 |
| 所属公司 | 关联 | 是 | 从组织架构选择 |
| 所属部门 | 关联 | 是 | 从组织架构选择 |
| 岗位 | 关联 | 是 | 一个用户仅一个岗位 |
| 系统角色 | 多选 | 是 | 与岗位独立 |
| 状态 | 枚举 | 是 | 启用、停用 |

### 5.4.3 密码规则

- 默认密码规则为：`用户名 + 手机号后3位`
- 本期用户创建不再要求前端手工输入初始密码。
- 用户创建成功后，系统自动向用户邮箱发送初始密码。
- 邮件发送失败时，不回滚用户创建，但系统需提示管理员并支持重发。
- 用户名格式校验需前后端双端执行。

---

## 5.5 项目管理

### 5.5.1 业务定位

项目用于对需求进行归集和统计。目标态不要求所有需求在创建时立即绑定项目，而是在流程特定节点按规则绑定。

本期实施按兼容方案推进：

- 已接入统一工作流引擎的新流程场景，支持在指定节点强制绑定项目。
- 尚未完成迁移的旧流程场景，可保留创建时项目必填或前置绑定逻辑。

### 5.5.2 功能点

- 新建项目
- 编辑项目
- 查看项目列表
- 筛选项目
- 导出项目
- 批量导入项目
- 下载导入模板

### 5.5.3 字段定义

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| 项目名称 | 文本 | 是 | 项目标识名称 |
| 归属公司 | 关联 | 是 | 选择组织中的公司 |
| 归属团队 | 关联/文本 | 是 | 作为统计归集维度 |
| 负责人 | 用户 | 是 | 从用户列表选择，可跳转详情 |
| 开始时间 | 日期 | 是 | 项目起始时间 |
| 截止时间 | 日期 | 是 | 项目结束时间 |
| 状态 | 枚举 | 是 | 进行中、截止 |
| 备注 | 文本 | 否 | 项目说明 |

### 5.5.4 项目状态规则

- 当系统日期达到截止时间后，项目状态自动更新为“截止”。
- 截止项目不可在需求流转中被新选择。

### 5.5.5 列表筛选

- 按名称模糊筛选
- 按状态筛选
- 按创建时间筛选
- 按截止时间筛选

### 5.5.6 导入导出要求

- 支持 Excel 导出当前筛选结果。
- 支持项目批量导入。
- 系统需提供标准导入模板。
- 导入时需校验负责人、归属组织、时间合法性和名称重复。

---

## 5.6 需求管理与流转执行

### 5.6.1 新建需求

#### 核心字段

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| 需求标题 | 是 | 需求简述 |
| 需求内容 | 是 | 需求详细说明 |
| 需求类型 | 是 | 来自全局需求类型字典 |
| 优先级 | 是 | 来自全局优先级字典 |
| 附件 | 否 | 上传后自动归档到文档中心 |

#### 交互要求

- 目标态下，新建页默认不强制选择项目。
- 兼容期内，是否强制选择项目由流程接入状态决定：已接入新流程的需求按节点规则处理，旧流程场景可暂保留现有项目必填行为。
- 用户可保存草稿。
- 用户可直接提交进入正式流程。

### 5.6.2 需求详情

需求详情页需展示：

- 基础信息
- 当前状态
- 当前节点
- 已绑定项目
- 附件与文档
- 流转记录
- 当前用户可执行动作

### 5.6.3 流转执行

- 当前用户可执行动作由后端根据节点权限实时返回。
- 普通编辑接口不得直接修改需求状态。
- 流转必须通过专门的流程接口执行。
- 执行流转时需同时校验节点权限、项目必填规则、项目状态规则和取消原因规则。

---

## 5.7 评审能力收口

### 5.7.1 业务定位

评审不是独立菜单，而是流程中的中间节点状态。

### 5.7.2 处理方式

- 原“评审管理”菜单下线或并入待办/流程视图。
- 评审意见、评审结果和处理记录沉淀在需求流转记录中。
- 若后续需要评审统计，则通过报表页面或待办视图实现。

---

## 5.8 文档中心

### 5.8.1 功能点

- 文档上传
- 在线预览
- 预览链接生成
- 二维码分享
- 文档检索
- 关联需求展示

### 5.8.2 文档归档规则

- 需求创建时上传的附件自动进入文档中心。
- 流转节点上传的附件自动进入文档中心。
- 文档需记录来源类型和关联需求 ID。
- 本期优先复用现有知识库文档上传、解析、检索链路，不重建独立文档底层存储链路。

### 5.8.3 检索展示要求

检索结果需包含以下信息：

- 检索问题
- 检索过程摘要
- 命中文档列表
- 命中片段与引用来源
- 关联需求信息
- 最终回答结果

其中“检索过程摘要”至少包括：

- 检索问题解析
- 召回范围说明
- 命中依据摘要
- 引用片段
- 关联业务信息
- 最终答案

### 5.8.4 合规要求

- 不向终端用户展示模型完整内部思维链。
- 系统仅展示可审计的过程摘要和引用依据。
- 不展示包含敏感中间 Prompt 或不可审计内部推理文本的内容。

---

## 6. 页面清单

| 一级模块 | 二级页面 | 说明 |
| --- | --- | --- |
| 需求配置 | 需求类型管理 | 全局类型字典维护 |
| 需求配置 | 优先级管理 | 全局优先级字典与排序 |
| 需求配置 | 节点状态管理 | 全局节点状态字典维护 |
| 工作流配置 | 工作流设计页 | 可视化流程设计与发布 |
| 组织架构管理 | 组织树管理页 | 区域、公司、部门、岗位树维护 |
| 用户管理 | 用户列表页 | 用户查询和维护 |
| 用户管理 | 用户详情页 | 用户组织归属和角色展示 |
| 项目管理 | 项目列表页 | 项目查询、导入导出 |
| 需求管理 | 新建需求页 | 需求创建与草稿保存 |
| 需求管理 | 需求详情页 | 详情展示和流转执行 |
| 文档中心 | 文档列表页 | 文档预览、检索与分享 |
| 文档中心 | 文档详情页 | 预览、引用和关联需求查看 |

---

## 7. 业务流程

### 7.1 需求主流程

```text
新建 -> 草稿保存 / 提交
提交 -> 下一个业务节点
业务节点 -> 下一个业务节点
业务节点 -> 上一个节点（回退）
业务节点 -> 取消
最后业务节点 -> 结束
```

### 7.2 取消流程

```text
当前节点用户点击取消
-> 系统校验是否具备取消权限
-> 用户填写取消原因
-> 系统记录取消日志
-> 需求状态变更为取消
-> 流程结束
```

### 7.3 项目绑定流程

```text
需求流转到配置为“项目必选”的节点
-> 系统校验项目是否已选择
-> 若未选择则禁止提交并提示
-> 用户选择有效项目
-> 流转成功
-> 项目字段对普通用户锁定
```

### 7.4 文档归档与检索流程

```text
需求创建/流转上传附件
-> 附件进入文档中心
-> 记录与需求的关联关系
-> 用户发起文档检索
-> LLM 生成检索过程摘要和结果
-> 返回文档引用与关联需求信息
```

---

## 8. 数据模型建议

### 8.1 需求类型表

| 字段 | 说明 |
| --- | --- |
| id | 主键 |
| code | 类型编码 |
| name | 类型名称 |
| color | 颜色 |
| sort_order | 排序号 |
| enabled | 启用状态 |
| description | 描述 |

### 8.2 优先级表

| 字段 | 说明 |
| --- | --- |
| id | 主键 |
| code | 优先级编码 |
| name | 优先级名称 |
| color | 颜色 |
| sort_order | 排序号 |
| enabled | 启用状态 |

### 8.3 节点状态表

| 字段 | 说明 |
| --- | --- |
| id | 主键 |
| code | 状态编码 |
| name | 状态名称 |
| color | 颜色 |
| is_final | 是否终态 |
| sort_order | 排序号 |
| enabled | 启用状态 |

### 8.4 工作流节点表

| 字段 | 说明 |
| --- | --- |
| id | 主键 |
| node_name | 节点名称 |
| status_code | 绑定状态编码 |
| handler_mode | 处理方式 |
| role_ids | 授权角色 |
| user_ids | 指定人员 |
| allow_cancel | 是否允许取消 |
| project_required | 是否项目必选 |
| sort_order | 节点顺序 |

### 8.5 流转记录表

| 字段 | 说明 |
| --- | --- |
| id | 主键 |
| requirement_id | 需求 ID |
| from_node_id | 来源节点 |
| to_node_id | 目标节点 |
| action_type | 动作类型 |
| operator_id | 操作人 |
| enter_time | 进入时间 |
| start_handle_time | 开始处理时间 |
| finish_time | 完成时间 |
| duration_seconds | 停留时长秒数 |
| comment | 处理意见 |
| cancel_reason | 取消原因 |

### 8.6 用户组织关系表

| 字段 | 说明 |
| --- | --- |
| user_id | 用户 ID |
| region_id | 区域 ID |
| company_id | 公司 ID |
| department_id | 部门 ID |
| position_id | 岗位 ID |

### 8.7 文档关联表

| 字段 | 说明 |
| --- | --- |
| document_id | 文档 ID |
| source_type | 来源类型 |
| source_id | 来源业务 ID |
| requirement_id | 关联需求 ID |

---

## 9. 接口需求概要

### 9.1 需求配置接口

| Method | Path | 说明 |
| --- | --- | --- |
| GET | `/api/v1/requirement-config/types` | 获取需求类型列表 |
| POST | `/api/v1/requirement-config/types` | 创建需求类型 |
| PUT | `/api/v1/requirement-config/types/{id}` | 更新需求类型 |
| DELETE | `/api/v1/requirement-config/types/{id}` | 删除需求类型 |
| POST | `/api/v1/requirement-config/types/sort` | 需求类型排序 |
| GET | `/api/v1/requirement-config/priorities` | 获取优先级列表 |
| POST | `/api/v1/requirement-config/priorities` | 创建优先级 |
| PUT | `/api/v1/requirement-config/priorities/{id}` | 更新优先级 |
| DELETE | `/api/v1/requirement-config/priorities/{id}` | 删除优先级 |
| POST | `/api/v1/requirement-config/priorities/sort` | 优先级排序 |

### 9.2 工作流接口

| Method | Path | 说明 |
| --- | --- | --- |
| GET | `/api/v1/workflow/config` | 获取当前工作流配置 |
| POST | `/api/v1/workflow/config` | 保存工作流草稿 |
| POST | `/api/v1/workflow/publish` | 发布工作流 |
| GET | `/api/v1/requirements/{id}/available-actions` | 获取当前用户可执行动作 |
| POST | `/api/v1/requirements/{id}/transition` | 执行流转 |

### 9.3 组织与用户接口

| Method | Path | 说明 |
| --- | --- | --- |
| GET | `/api/v1/regions/tree` | 获取区域树 |
| GET | `/api/v1/companies/tree` | 获取公司树 |
| GET | `/api/v1/departments/tree` | 获取部门树 |
| GET | `/api/v1/positions/tree` | 获取岗位树 |
| POST | `/api/v1/users` | 创建用户 |
| PUT | `/api/v1/users/{id}` | 更新用户 |
| POST | `/api/v1/users/{id}/send-init-password` | 发送初始密码邮件 |

### 9.4 项目接口

| Method | Path | 说明 |
| --- | --- | --- |
| GET | `/api/v1/projects` | 获取项目列表 |
| POST | `/api/v1/projects` | 创建项目 |
| PUT | `/api/v1/projects/{id}` | 更新项目 |
| POST | `/api/v1/projects/import` | 批量导入项目 |
| GET | `/api/v1/projects/export` | 导出项目 |
| GET | `/api/v1/projects/template` | 下载导入模板 |

### 9.5 文档接口

| Method | Path | 说明 |
| --- | --- | --- |
| GET | `/api/v1/documents` | 获取文档列表 |
| POST | `/api/v1/documents/upload` | 上传文档 |
| GET | `/api/v1/documents/{id}/preview` | 在线预览 |
| POST | `/api/v1/documents/{id}/share` | 生成分享链接 |
| GET | `/api/v1/documents/search` | 文档智能检索 |

---

## 10. 权限要求

### 10.1 配置权限

- 仅超级管理员和流程管理员可维护需求类型、优先级、节点状态和工作流。

### 10.2 执行权限

- 用户仅可看到自己有权限处理的节点任务。
- 指定人员节点仅指定人员可见。
- 角色节点仅授权角色可见。

### 10.3 越权权限

- 超级管理员可修改已绑定项目。
- 所有越权修改必须记录审计日志。

---

## 11. 非功能要求

### 11.1 性能要求

- 需求列表、项目列表、文档列表在常规分页场景下响应时间小于 1 秒。
- 文档检索结果返回时间应尽量控制在 3 秒以内。

### 11.2 安全要求

- 所有接口基于登录鉴权和 RBAC 权限控制。
- 文档分享链接应支持有效期和权限控制。
- 流转、取消、项目修改、配置变更必须保留审计日志。

### 11.3 可用性要求

- 草稿数据需持久化保存。
- 流转失败时需给出明确错误提示。
- 邮件发送失败需支持重试。

---

## 12. 验收标准

### 12.1 需求配置

- 管理员可维护需求类型、优先级、节点状态，并能立即在相关页面生效。
- 优先级支持拖拽排序，页面不显示传统拖拽条图标。

### 12.2 工作流

- 系统仅存在一套生效工作流。
- 开始节点支持保存草稿和提交。
- 节点回退仅能回到上一节点。
- 除结束节点外，其余节点可按权限取消。

### 12.3 组织与用户

- 组织树可按区域、公司、部门、岗位四层维护并默认折叠展示。
- 创建用户时用户名校验规则生效，默认密码按规则生成并发送邮件。

### 12.4 项目

- 项目支持筛选、导入、导出和模板下载。
- 截止项目无法在需求流转中被新绑定。

### 12.5 需求流转

- 未绑定项目的需求在“项目必选”节点无法提交。
- 绑定角色节点由授权角色争抢处理，首个处理人被记录。
- 指定人员节点仅指定人可处理。
- 取消需求时必须填写取消原因。

### 12.6 文档中心

- 需求附件自动归档到文档中心。
- 检索命中文档后可查看关联需求信息。
- 检索结果包含过程摘要、引用片段和最终回答。

---

## 13. 风险与待确认项

- 工作流统一执行主链路的最终保留方案，需要在研发实施前确认。
- 全局工作流替代项目维度工作流的迁移节奏，需要结合现有项目数据制定计划。
- 公司是否在后续阶段抽离为独立实体，需要根据组织分析与权限需求评估。
- 归属团队字段最终采用组织节点、独立实体还是自由文本。
- 草稿需求是否仅创建人可见，还是管理员也可查看。
- 分享链接是否允许匿名访问，还是要求登录后访问。

---

## 14. 实施建议

### 14.1 第一阶段

- 需求配置中心
- 统一工作流主链路收敛
- 全局默认工作流配置
- 流转执行与审计记录
- 组织四层树
- 用户管理规则完善

### 14.2 第二阶段

- 项目导入导出
- 节点项目必选规则
- 文档中心需求关联
- 分享链接与二维码

### 14.3 第三阶段

- LLM 检索过程摘要增强
- 统计报表与项目归集分析
- 更多字段级节点控制能力
- 历史项目工作流迁移与旧实现下线评估
