# 多维表格功能设计文档

## Context

需求管理系统当前以需求列表为核心，表格功能仅限于 Element Plus `el-table` + 列配置。用户需要一个类似钉钉/飞书多维表格的独立模块，支持在线编辑、实时多人协同、AI辅助能力。项目已有可复用基础设施：CustomField 数据模型（未激活）、Milvus 向量库 + LlmGateway + RAG 链路（AI能力就绪）、UserColumnConfig 列配置（已完整实现）、vxe-table（已安装未使用）。

用户需求确认：
- **定位**: 独立新模块（与需求列表完全独立，但可双向关联）
- **协作级别**: 实时多人协同（同一表格多人同时编辑）
- **AI能力**: 全选 — AI建表、AI智能填充、AI对话式查询、AI自动分类/摘要

---

## 一、多维表格核心能力梳理（参照钉钉/飞书Bitable/Airtable）

### 1.1 数据组织模型

| 层级 | 说明 | 参照 |
|------|------|------|
| **Base（多维表格）** | 顶级容器，类似飞书"多维表格"、Airtable"Base" | 钉钉多维表格、飞书 Bitable |
| **Table（数据表）** | 一个 Base 内可包含多个数据表，表间可关联 | 飞书多表关联 |
| **Field（字段）** | 每个表拥有独立字段定义，支持丰富类型 | 钉钉20+字段类型 |
| **Record（记录/行）** | 字段的值按行存储 | 通用 |
| **View（视图）** | 同一数据的不同展示方式（表格/看板/甘特/日历/画廊） | 飞书5种视图 |

### 1.2 字段类型体系（参照钉钉多维表格）

| 类别 | 字段类型 | 说明 |
|------|---------|------|
| **基础** | 文本(text)、数字(number)、日期(date)、日期范围(date_range) | 基础值存储 |
| **选择** | 单选(single_select)、多选(multi_select) | 选项预定义 |
| **人员** | 人员(user)、部门(department) | 关联组织架构 |
| **进度** | 进度条(progress)、评分(rating) | 百分比/星级 |
| **关联** | 关联(link)、关联记录汇总(rollup)、查找引用(lookup) | 跨表关联与计算 |
| **计算** | 公式(formula)、自动编号(auto_number) | 动态计算 |
| **富媒体** | 附件(attachment)、URL(url)、邮箱(email) | 文件与链接 |
| **标记** | 复选框(check)、创建时间(created_time)、修改时间(modified_time)、创建人(created_user)、修改人(modified_user) | 自动/标记 |

### 1.3 视图类型

| 视图 | 说明 | 参照 |
|------|------|------|
| **表格视图(grid)** | 类 Excel 的行列视图，支持排序/筛选/分组/冻结列 | 标准 |
| **看板视图(kanban)** | 按单选/多选字段分组展示卡片，支持拖拽排顺序 | 钉钉看板 |
| **甘特视图(gantt)** | 按日期范围展示时间线甘特图，支持拖拽调整日期 | 飞书甘特 |
| **日历视图(calendar)** | 按日期展示日程日历 | 钉钉日历 |
| **画廊视图(gallery)** | 大卡片展示，突出附件/图片字段 | Airtable |

### 1.4 协作能力

| 能力 | 说明 |
|------|------|
| 实时多人编辑 | 同一表格多人同时编辑不同单元格，看到彼此变更 |
| 评论/讨论 | 在单元格/行上添加评论 |
| 操作历史 | 查看谁在何时修改了什么 |
| 权限控制 | 表级/字段级读写权限 |

### 1.5 AI能力（参照飞书AI + 钉钉智能辅助）

| AI能力 | 说明 | 技术实现 |
|--------|------|---------|
| **自然语言建表** | "帮我创建一个项目管理表"→ AI生成完整字段结构 | LLM Chat + 字段模板库 |
| **AI智能填充** | AI字段根据上下文自动填充值 | LLM Chat + 表数据上下文 |
| **AI对话式查询** | 自然语言提问 → 返回筛选结果 | LLM + SQL生成/向量检索 |
| **AI自动分类** | 根据文本字段自动分类标签 | Embedding + Milvus聚类 |
| **AI自动摘要** | 对长文本字段自动生成摘要 | LLM Chat |
| **AI公式生成** | 自然语言描述 → 生成公式表达式 | LLM Chat + 公式验证 |

---

## 二、数据模型设计

### 2.1 核心实体关系

```
Base (1) ──→ (N) Table (1) ──→ (N) Field (1) ──→ (N) View (1) ──→ (N) ViewConfig
Table (1) ──→ (N) Record (1) ──→ (N) CellValue
Field (1) ──→ (N) LinkRelation (跨表关联)
Base (1) ──→ (N) BaseMember (协作权限)
Record (1) ──→ (N) Comment (行级评论)
```

### 2.2 数据库表设计

#### `bitable_bases` — 多维表格容器

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| name | VARCHAR(200) | 表格名称 |
| description | TEXT | 描述 |
| icon | VARCHAR(50) | 图标标识 |
| cover_color | VARCHAR(20) | 封面颜色 |
| project_id | BIGINT NULL | 关联项目(可选) |
| creator_id | BIGINT | 创建人 |
| is_template | TINYINT | 是否模板 |
| sort_order | INT | 排序 |
| deleted_at | BIGINT NULL | 软删除 |

#### `bitable_tables` — 数据表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| base_id | BIGINT FK | 所属多维表格 |
| name | VARCHAR(200) | 表名 |
| description | TEXT | 描述 |
| icon | VARCHAR(50) | 图标 |
| sort_order | INT | 排序 |
| deleted_at | BIGINT NULL | 软删除 |

#### `bitable_fields` — 字段定义

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| table_id | BIGINT FK | 所属数据表 |
| name | VARCHAR(200) | 字段名 |
| field_type | VARCHAR(30) | 字段类型枚举(text/number/date/single_select/multi_select/user/link/formula/...) |
| config | JSON | 字段配置(options/format/defaultValue/linkTargetTableId/formulaExpr等) |
| required | TINYINT | 是否必填 |
| ai_prompt | TEXT NULL | AI填充提示词(AI字段专用) |
| is_ai_field | TINYINT | 是否AI自动填充字段 |
| sort_order | INT | 排序 |
| deleted_at | BIGINT NULL | 软删除 |

#### `bitable_records` — 记录行

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| table_id | BIGINT FK | 所属数据表 |
| sort_order | INT | 行排序 |
| created_by | BIGINT | 创建人 |
| created_at | DATETIME | 创建时间 |
| updated_by | BIGINT | 最后修改人 |
| updated_at | DATETIME | 最后修改时间 |
| deleted_at | BIGINT NULL | 软删除 |

#### `bitable_cell_values` — 单元格值（EAV模式）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| record_id | BIGINT FK | 记录行 |
| field_id | BIGINT FK | 字段 |
| value_text | TEXT NULL | 文本值 |
| value_number | DECIMAL(20,4) NULL | 数值 |
| value_date | DATE NULL | 日期 |
| value_json | JSON NULL | 复杂值(多选数组/关联ID列表/附件列表等) |

> 唯一索引: `uk_record_field(record_id, field_id)`

#### `bitable_views` — 视图定义

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| table_id | BIGINT FK | 所属数据表 |
| name | VARCHAR(200) | 视图名称 |
| view_type | VARCHAR(20) | 视图类型(grid/kanban/gantt/calendar/gallery) |
| sort_config | JSON | 排序配置 |
| filter_config | JSON | 筛选配置 |
| group_config | JSON | 分组配置 |
| column_config | JSON | 列宽/顺序/冻结/隐藏配置 |
| color_config | JSON | 行颜色规则 |
| sort_order | INT | 排序 |

#### `bitable_base_members` — 协作权限

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| base_id | BIGINT FK | 多维表格 |
| user_id | BIGINT FK | 用户 |
| role | VARCHAR(20) | 角色(owner/admin/editor/commenter/viewer) |
| created_at | DATETIME | 加入时间 |

#### `bitable_comments` — 行级评论

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| record_id | BIGINT FK | 记录行 |
| table_id | BIGINT FK | 数据表 |
| user_id | BIGINT FK | 评论人 |
| content | TEXT | 评论内容 |
| quote_field_id | BIGINT NULL | 引用字段(单元格评论) |
| parent_id | BIGINT NULL | 回复评论ID |
| created_at | DATETIME | 评论时间 |

#### `bitable_operations` — 操作历史（审计）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| base_id | BIGINT FK | 多维表格 |
| table_id | BIGINT FK | 数据表 |
| user_id | BIGINT FK | 操作人 |
| operation_type | VARCHAR(30) | 操作类型(insert_record/update_cell/delete_record/add_field/...) |
| detail | JSON | 操作详情(变更前后值) |
| created_at | DATETIME | 操作时间 |

---

## 三、在线协作编辑方案

### 3.1 技术选型：WebSocket + 行级锁 + 操作日志

> 选择理由：纯 CRDT（如 Yjs）对后端数据一致性要求高，且需要额外基础设施；OT 算法复杂度高。本项目已有 Redis + RabbitMQ，采用**WebSocket推送 + 行级乐观锁 + 操作日志广播**方案更务实。

### 3.2 协作架构

```
前端(WebSocket Client) ──→ Spring WebSocket Handler ──→ Redis Pub/Sub ──→ 所有客户端
                                        │
                                        ↓
                                  MySQL(持久化) + 操作日志表
```

**核心流程**:
1. 用户编辑单元格 → 发送 `cell_update` 操作到 WebSocket
2. 后端验证权限 → 行级乐观锁(@Version)检查 → 写入 DB + 操作日志
3. 后端通过 Redis Pub/Sub 广播变更到所有连接该表格的 WebSocket 客户端
4. 其他客户端收到变更 → 更新本地单元格值 + 高亮闪烁提示

### 3.3 WebSocket 消息协议

```json
// 客户端 → 服务端
{ "type": "cell_update", "tableId": 1, "recordId": 10, "fieldId": 3, "value": "新值", "version": 5 }
{ "type": "record_insert", "tableId": 1, "recordId": null, "values": {...} }
{ "type": "record_delete", "tableId": 1, "recordId": 10 }

// 服务端 → 客户端
{ "type": "cell_updated", "tableId": 1, "recordId": 10, "fieldId": 3, "value": "新值", "updatedBy": "张三", "version": 6 }
{ "type": "conflict", "tableId": 1, "recordId": 10, "fieldId": 3, "message": "该行已被其他人修改，请刷新" }
{ "type": "cursor_move", "userId": 1, "userName": "张三", "tableId": 1, "recordId": 10, "fieldId": 3 }
```

### 3.4 冲突解决策略

- **行级乐观锁**: Record 表加 `@Version` 字段，cell_update 操作携带 version，版本不匹配返回冲突
- **字段级粒度**: cell_value 按字段独立更新，不同字段不冲突
- **冲突提示**: 前端收到 conflict 消息后弹出提示，用户选择覆盖或合并

### 3.5 前端表格组件选型

**选择 vxe-table**（已安装未使用）:
- 支持单元格直接编辑（点击即编辑，无需弹窗）
- 支持行列拖拽、排序、筛选、分组
- 支持树形结构、合并单元格
- 性能优异（虚拟滚动，万行数据流畅）
- Vue 3 专用版本(vxe-table@4.x)

> 替代方案：Fortune-Sheet(在线Excel)功能更强但引入成本高，暂不采用。

---

## 四、AI能力集成方案

### 4.1 可复用基础设施

| 基础设施 | 位置 | 复用方式 |
|---------|------|---------|
| LlmGateway | `module/knowledge/llm/LlmGateway.java` | 直接调用 `chatWithProvider()` / `streamChatWithProvider()` |
| LlmProvider/LlmModel | `module/llm/entity/` | 查询默认 Chat/Embedding 模型 |
| MilvusVectorStore | `module/knowledge/vectorstore/` | 向量索引与搜索 |
| EmbeddingServiceImpl | `module/knowledge/service/impl/` | 调用 `embed()` 获取向量 |
| RagAnswerServiceImpl | `module/knowledge/service/impl/` | 复用 SSE 流式输出 |
| KnowledgeDocumentConsumer | `module/knowledge/consumer/` | 复用 RabbitMQ 异步处理模式 |

### 4.2 AI能力详细设计

#### 4.2.1 AI自然语言建表

**流程**:
1. 用户输入意图描述（如"帮我创建一个招聘管理表"）
2. 调用 LlmGateway.chatWithProvider()，System Prompt 包含：
   - 可用字段类型枚举及说明
   - 输出格式约束（JSON: `{fields: [{name, fieldType, config, aiPrompt}]}`）
3. LLM 返回字段结构 JSON → 前端展示预览 → 用户确认 → 调用 `createTableWithFields` API

**System Prompt 模板**:
```
你是一个多维表格字段设计专家。用户将描述他们想要创建的数据表用途。
请根据描述设计合适的字段列表，每个字段包含：
- name: 字段中文名
- fieldType: 从以下类型选择(text/number/date/single_select/multi_select/user/link/formula/url/email/check/progress/rating/attachment/auto_number)
- config: 字段配置（如 single_select 需提供 options 数组，link 需提供 targetTableId）
- aiPrompt: 如果该字段适合AI自动填充，提供提示词；否则为 null

输出JSON格式：{ "tableName": "...", "description": "...", "fields": [...] }
```

#### 4.2.2 AI智能填充字段

**流程**:
1. 用户在 AI 字段上触发"AI填充"按钮
2. 后端读取当前行的已有字段值作为上下文
3. 调用 LlmGateway.chatWithProvider()，System Prompt 包含：
   - 字段定义（名称+类型+AI提示词）
   - 当前行的已有值
4. LLM 返回填充值 → 写入 cell_value → 广播变更

**批量填充**: 支持对所有空行的 AI 字段批量填充（RabbitMQ 异步处理，逐行调用 LLM）

#### 4.2.3 AI对话式查询

**流程**:
1. 用户在多维表格内打开 AI 查询面板
2. 输入自然语言问题（如"哪些任务延期了？"）
3. 后端两条路径并行：
   - **SQL生成路径**: LLM 将自然语言 → SQL → 执行查询 → 返回结果集
   - **向量检索路径**: Embedding + Milvus 搜索字段值向量 → 返回相似行
4. 结果合并 → LLM 生成回答 + 返回匹配的 recordId 列表
5. 前端高亮匹配行 + 展示 AI 回答

**安全约束**: SQL 生成仅允许 SELECT，禁止 INSERT/UPDATE/DELETE；执行前验证表名/字段名属于当前 Base

#### 4.2.4 AI自动分类/摘要

**分类**:
1. 用户选择文本列 → 触发"AI分类"
2. 后端读取该列所有值 → Embedding 向量化 → Milvus 聚类
3. LLM 分析聚类结果 → 生成分类标签名（如"技术类"/"运营类"/"设计类"）
4. 创建 single_select 字段 → 批量写入分类值

**摘要**:
1. 用户选择长文本列 → 触发"AI摘要"
2. LLM 逐行（或批量）生成摘要 → 写入新的 AI 摘要字段

#### 4.2.5 AI公式生成

**流程**:
1. 用户描述计算意图（如"计算每个任务的天数=截止日期-开始日期"）
2. LLM 生成公式表达式 → 前端预览 → 用户确认
3. 公式引擎执行计算 → 写入 formula 字段值

**公式引擎**: 后端使用简单表达式解析器（如 JEXL/Aviator），支持基础运算、日期差、条件逻辑。暂不实现完整的 Airtable 公式体系。

---

## 五、后端架构设计

### 5.1 新增模块: `module/bitable`

```
demand_backend/src/main/java/com/demand/system/module/bitable/
├── controller/
│   ├── BitableBaseController.java       # Base CRUD + 成员管理
│   ├── BitableTableController.java      # Table CRUD + 字段管理
│   ├── BitableRecordController.java     # Record CRUD + 批量操作
│   ├── BitableViewController.java       # View CRUD + 配置
│   ├── BitableCommentController.java    # 评论
│   ├── BitableAiController.java         # AI能力入口
│   └── BitableWebSocketController.java  # WebSocket 协作
├── service/
│   ├── BitableBaseService.java
│   ├── BitableTableService.java
│   ├── BitableFieldService.java         # 字段CRUD + 类型配置验证
│   ├── BitableRecordService.java        # 行CRUD + 版本冲突检测
│   ├── BitableViewService.java          # 视图CRUD + 筛选/排序/分组逻辑
│   ├── BitableCellService.java          # 单元格值读写 + 公式计算
│   ├── BitableCommentService.java
│   ├── BitableCollaborationService.java # WebSocket + Redis Pub/Sub
│   ├── BitableAiService.java            # AI能力编排(LLM/Milvus/SQL)
│   └── impl/
│       └── ...
├── mapper/
│   ├── BitableBaseMapper.java
│   ├── BitableTableMapper.java
│   ├── BitableFieldMapper.java
│   ├── BitableRecordMapper.java
│   ├── BitableCellMapper.java
│   ├── BitableViewMapper.java
│   ├── BitableBaseMemberMapper.java
│   ├── BitableCommentMapper.java
│   ├── BitableOperationMapper.java
│   └── ...XML映射文件
├── entity/
│   ├── BitableBase.java
│   ├── BitableTable.java
│   ├── BitableField.java
│   ├── BitableRecord.java
│   ├── BitableCellValue.java
│   ├── BitableView.java
│   ├── BitableBaseMember.java
│   ├── BitableComment.java
│   ├── BitableOperation.java
├── dto/
│   ├── BitableBaseCreateDTO.java
│   ├── BitableTableCreateDTO.java
│   ├── BitableFieldCreateDTO.java       # 含 fieldType + config
│   ├── BitableRecordCreateDTO.java
│   ├── BitableViewCreateDTO.java
│   ├── CellUpdateDTO.java               # 含 version(乐观锁)
│   ├── AiBuildTableRequest.java         # 自然语言建表
│   ├── AiQueryRequest.java              # 对话式查询
│   ├── AiFillRequest.java               # 智能填充
│   ├── AiClassifyRequest.java           # 自动分类
│   └── ...
├── converter/
│   └── BitableConverter.java            # MapStruct
├── config/
│   └── BitableWebSocketConfig.java      # WebSocket 配置
├── constant/
│   ├── FieldType.java                   # 字段类型枚举
│   ├── ViewType.java                    # 视图类型枚举
│   ├── MemberRole.java                  # 成员角色枚举
│   └── OperationType.java              # 操作类型枚举
└── websocket/
    ├── BitableWebSocketHandler.java     # WebSocket 消息处理
    └── BitableRedisSubscriber.java      # Redis Pub/Sub 转发
```

### 5.2 Mapper XML 文件

所有 SQL 必须在 XML 中定义（遵循项目 mybatis-xml-only.md 规则）：

```
demand_backend/src/main/resources/mapper/
├── BitableBaseMapper.xml
├── BitableTableMapper.xml
├── BitableFieldMapper.xml
├── BitableRecordMapper.xml
├── BitableCellMapper.xml
├── BitableViewMapper.xml
├── BitableBaseMemberMapper.xml
├── BitableCommentMapper.xml
├── BitableOperationMapper.xml
```

### 5.3 依赖复用

| 依赖 | 来源模块 | 复用方式 |
|------|---------|---------|
| LlmGateway | `module/knowledge/llm/` | 直接注入调用 chat/streamChat |
| EmbeddingServiceImpl | `module/knowledge/service/` | 直接注入调用 embed |
| MilvusVectorStore | `module/knowledge/vectorstore/` | 直接注入调用 search/insert |
| LlmProviderServiceImpl | `module/llm/service/` | 查询默认模型 |
| SecurityUtils | `module/auth/security/` | 获取当前用户ID |
| RBAC (SysPermission) | `module/rbac/` | 权限校验 |
| SysOrgService | `module/organization/` | 人员/部门选择器数据 |
| RabbitMQ | Spring AMQP | AI 异步任务队列 |

---

## 六、前端架构设计

### 6.1 新增页面与路由

```typescript
// router/routes.ts 新增
{
  path: 'bitable',
  name: 'BitableList',
  component: () => import('@/views/bitable/index.vue'),
  meta: { title: '多维表格', icon: 'Grid' },
},
{
  path: 'bitable/:baseId',
  name: 'BitableEditor',
  component: () => import('@/views/bitable/editor.vue'),
  meta: { title: '表格编辑', hidden: true, activeMenu: '/bitable' },
}
```

### 6.2 前端文件结构

```
demand_frontend/src/
├── views/bitable/
│   ├── index.vue                    # 多维表格列表页(卡片展示所有Base)
│   ├── editor.vue                   # 编辑器主页(核心交互)
│   ├── components/
│       ├── BaseCard.vue             # Base 卡片
│       ├── BaseCreateDialog.vue     # 创建/编辑Base弹窗
│       ├── TableSidebar.vue         # 左侧数据表列表
│       ├── Toolbar.vue              # 顶部工具栏(视图切换/筛选/排序/分组/AI)
│       ├── GridView.vue             # 表格视图(vxe-table核心)
│       ├── KanbanView.vue           # 看板视图
│       ├── GanttView.vue            # 甘特视图(Phase2)
│       ├── CalendarView.vue         # 日历视图(Phase2)
│       ├── GalleryView.vue          # 画廊视图(Phase2)
│       ├── FieldCreateDialog.vue    # 新增字段弹窗(含AI建表)
│       ├── FieldConfigPanel.vue     # 字段配置侧滑面板
│       ├── AiChatPanel.vue          # AI对话查询面板
│       ├── AiFillDialog.vue         # AI智能填充弹窗
│       ├── AiClassifyDialog.vue     # AI分类/摘要弹窗
│       ├── CommentPanel.vue         # 评论面板
│       ├── MemberManager.vue        # 协作成员管理
│       ├── OperationHistory.vue     # 操作历史
│       ├── CollaborationCursor.vue  # 实时协作光标指示
│       └── ViewCreateDialog.vue     # 新建视图弹窗
├── api/modules/
│   ├── bitable.ts                   # Base/Table/Field/Record/View CRUD
│   ├── bitableAi.ts                 # AI能力API
│   ├── bitableCollaboration.ts      # WebSocket连接管理
├── composables/
│   ├── useBitableWebSocket.ts       # WebSocket连接/消息收发/断线重连
│   ├── useBitableEditor.ts          # 编辑器核心状态(当前表/字段/记录/视图)
│   ├── useBitableFilter.ts          # 筛选逻辑
│   ├── useBitableSort.ts            # 排序逻辑
│   ├── useBitableGroup.ts           # 分组逻辑
│   ├── useBitableView.ts            # 视图切换与配置
│   ├── useBitableAi.ts              # AI能力交互
├── stores/
│   ├── bitable.ts                   # Pinia全局状态(Base列表/当前Base)
├── types/
│   ├── bitable.d.ts                 # 所有类型定义
```

### 6.3 WebSocket前端实现

```typescript
// useBitableWebSocket.ts 核心逻辑
export function useBitableWebSocket(baseId: number) {
  const ws = ref<WebSocket | null>(null)
  const cursors = ref<Map<number, CursorInfo>>(new Map()) // 其他用户光标位置
  
  function connect() {
    ws.value = new WebSocket(`ws://localhost:8081/ws/bitable/${baseId}`)
    ws.value.onmessage = (event) => handleMessage(JSON.parse(event.data))
    ws.value.onclose = () => reconnect() // 断线自动重连
  }
  
  function sendCellUpdate(tableId, recordId, fieldId, value, version) {
    ws.value?.send(JSON.stringify({ type: 'cell_update', ... }))
  }
  
  function handleMessage(msg) {
    if (msg.type === 'cell_updated') updateLocalCell(msg)
    if (msg.type === 'conflict') showConflictDialog(msg)
    if (msg.type === 'cursor_move') updateCursor(msg)
    if (msg.type === 'record_inserted') addLocalRecord(msg)
  }
}
```

### 6.4 vxe-table 核心配置

```typescript
// GridView.vue 核心逻辑
const gridOptions = {
  columns: fields.map(f => ({
    field: f.id.toString(),
    title: f.name,
    width: f.width || 150,
    minWidth: 80,
    // 按字段类型配置编辑器
    editRender: getEditRender(f.fieldType),
    // 单元格渲染
    cellRender: getCellRender(f.fieldType),
    // 排序/筛选配置
    sortable: true,
    filters: getFilters(f),
  })),
  data: records,
  editConfig: {
    trigger: 'click',  // 点击即编辑
    mode: 'cell',      // 单元格编辑模式
    showStatus: true,   // 显示编辑状态
  },
  scrollY: { enabled: true, gt: 100 }, // 虚拟滚动
}
```

---

## 七、API设计

### 7.1 Base API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/bitable/bases` | 创建多维表格 |
| GET | `/api/v1/bitable/bases` | 列表(含成员角色) |
| GET | `/api/v1/bitable/bases/{id}` | 详情(含tables摘要) |
| PUT | `/api/v1/bitable/bases/{id}` | 更新 |
| DELETE | `/api/v1/bitable/bases/{id}` | 删除 |
| POST | `/api/v1/bitable/bases/{id}/members` | 添加成员 |
| PUT | `/api/v1/bitable/bases/{id}/members/{userId}` | 更新角色 |
| DELETE | `/api/v1/bitable/bases/{id}/members/{userId}` | 移除成员 |

### 7.2 Table API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/bitable/bases/{baseId}/tables` | 创建数据表 |
| GET | `/api/v1/bitable/bases/{baseId}/tables` | 列表 |
| PUT | `/api/v1/bitable/tables/{id}` | 更新 |
| DELETE | `/api/v1/bitable/tables/{id}` | 删除 |

### 7.3 Field API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/bitable/tables/{tableId}/fields` | 新增字段 |
| GET | `/api/v1/bitable/tables/{tableId}/fields` | 获取所有字段 |
| PUT | `/api/v1/bitable/fields/{id}` | 更新字段配置 |
| DELETE | `/api/v1/bitable/fields/{id}` | 删除字段 |
| PUT | `/api/v1/bitable/fields/sort` | 字段排序 |

### 7.4 Record API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/bitable/tables/{tableId}/records` | 新增行 |
| GET | `/api/v1/bitable/tables/{tableId}/records` | 查询行(含筛选/排序/分页) |
| PUT | `/api/v1/bitable/records/{id}` | 更新行(整行) |
| DELETE | `/api/v1/bitable/records/{id}` | 删除行 |
| PUT | `/api/v1/bitable/records/{id}/cells/{fieldId}` | 更新单个单元格 |
| POST | `/api/v1/bitable/tables/{tableId}/records/batch` | 批量新增 |

### 7.5 View API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/bitable/tables/{tableId}/views` | 新增视图 |
| GET | `/api/v1/bitable/tables/{tableId}/views` | 视图列表 |
| PUT | `/api/v1/bitable/views/{id}` | 更新视图配置 |
| DELETE | `/api/v1/bitable/views/{id}` | 删除视图 |

### 7.6 AI API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/bitable/ai/build-table` | AI自然语言建表(返回字段结构预览) |
| POST | `/api/v1/bitable/ai/build-table/confirm` | 确认AI建表结果(写入DB) |
| POST | `/api/v1/bitable/ai/fill` | AI智能填充(单个字段) |
| POST | `/api/v1/bitable/ai/fill-batch` | AI批量填充(异步,RabbitMQ) |
| POST | `/api/v1/bitable/ai/query` | AI对话式查询 |
| POST | `/api/v1/bitable/ai/classify` | AI自动分类 |
| POST | `/api/v1/bitable/ai/summarize` | AI自动摘要 |
| POST | `/api/v1/bitable/ai/formula` | AI公式生成 |

### 7.7 Comment API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/bitable/records/{recordId}/comments` | 添加评论 |
| GET | `/api/v1/bitable/records/{recordId}/comments` | 评论列表 |
| DELETE | `/api/v1/bitable/comments/{id}` | 删除评论 |

### 7.8 WebSocket

| 路径 | 说明 |
|------|------|
| `ws://localhost:8081/ws/bitable/{baseId}` | 实时协作WebSocket连接 |

---

## 八、分阶段实施计划

### Phase 1: 核心数据模型 + 表格视图（MVP）

**目标**: 能创建 Base/Table/Field，在表格视图下编辑 Record

**后端**:
- 创建所有数据库表（9张表）
- 实现 bitable 模块全部实体 + Mapper + XML
- BitableBaseService/TableService/FieldService/RecordService/CellService/ViewService
- 全部 Controller + CRUD API
- 字段类型枚举 + config JSON Schema 定义（text/number/date/single_select/multi_select/user/check/auto_number/created_time/modified_time/url/email）

**前端**:
- bitable/index.vue — Base 列表页
- bitable/editor.vue — 编辑器主页
- TableSidebar/Toolbar/GridView — 表格视图核心
- FieldCreateDialog/FieldConfigPanel — 字段管理
- ViewCreateDialog — 视图管理
- vxe-table 集成 + 单元格编辑 + 排序/筛选/分组

**预估**: 后端 3-4 天, 前端 3-4 天

### Phase 2: 实时协作 + 看板视图

**目标**: 多人实时编辑 + 看板视图

**后端**:
- WebSocket Handler + Redis Pub/Sub
- BitableCollaborationService
- 行级乐观锁(@Version)
- 操作日志记录
- BitableBaseMemberService — 成员权限

**前端**:
- useBitableWebSocket — 连接管理 + 消息处理
- CollaborationCursor — 光标指示
- ConflictDialog — 冲突提示
- KanbanView — 看板视图(拖拽排顺序)
- CommentPanel — 评论
- MemberManager — 成员管理

**预估**: 后端 2-3 天, 前端 2-3 天

### Phase 3: AI能力集成

**目标**: 4 项核心 AI 能力上线

**后端**:
- BitableAiService — 编排所有 AI 能力
- AI建表: LLM Chat + 字段模板库
- AI智能填充: LLM Chat + 表数据上下文 + RabbitMQ 异步批量
- AI对话式查询: LLM SQL生成 + Milvus向量检索
- AI自动分类/摘要: Embedding + Milvus + LLM
- AiController — 所有 AI 入口 API

**前端**:
- AiChatPanel — AI对话查询面板(复用SSE流式输出模式)
- AiFillDialog/AiClassifyDialog — AI操作弹窗
- FieldCreateDialog 增加"AI建表"入口
- 字段编辑增加"AI填充"按钮
- useBitableAi composable

**预估**: 后端 3-4 天, 前端 2-3 天

### Phase 4: 高级视图 + 关联字段 + 公式

**目标**: 甘特/日历/画廊视图 + 跨表关联 + 公式计算

**后端**:
- Link字段: BitableLinkRelationService — 跨表关联读写
- Rollup/Lookup字段: 基于关联的聚合计算
- Formula字段: JEXL表达式引擎
- 甘特/日历/画廊视图数据接口

**前端**:
- GanttView — 甘特视图(拖拽调整日期)
- CalendarView — 日历视图
- GalleryView — 画廊视图(卡片展示)
- Link字段选择器(跨表选择关联行)
- 公式编辑器

**预估**: 后端 2-3 天, 前端 3-4 天

### Phase 5: 模板市场 + 导入导出 + 优化

**目标**: 模板、Excel导入导出、性能优化

- Base模板库(预设项目管理/招聘/CRM等)
- Excel/CSV导入导出(复用已有Apache POI)
- 大数据量虚拟滚动优化
- 搜索索引(Elasticsearch集成)
- 移动端适配

**预估**: 3-4 天

---

## 九、风险与应对

| 风险 | 级别 | 应对 |
|------|------|------|
| WebSocket 连接稳定性 | HIGH | 断线重连 + 操作日志回补 |
| LLM 响应延迟 | MEDIUM | AI 操作异步化(RabbitMQ) + SSE流式输出 |
| EAV 模型查询性能 | MEDIUM | 高频查询字段添加 MySQL 索引 + Redis 缓存 |
| 公式引擎复杂度 | MEDIUM | Phase4 才实现，初期用简单 JEXL |
| vxe-table 功能覆盖度 | LOW | 已安装 v4，需验证字段类型编辑器兼容性 |
| SQL 注入风险(AI查询) | HIGH | LLM 生成的 SQL 仅允许 SELECT + 白名单验证表名/字段名 |

---

## 十、验证方案

### 10.1 Phase 1 验证

1. 创建 Base → 创建 Table → 添加 5 种基础字段(text/number/date/single_select/check/auto_number)
2. 在表格视图下新增行 + 编辑单元格 + 删除行
3. 创建第二个视图(不同筛选/排序) → 切换视图数据一致
4. 启动前端5170 + 后端8081 → 在浏览器实际操作全流程

### 10.2 Phase 2 验证

1. 两个浏览器窗口同时打开同一 Base → A 编辑单元格 → B 看到实时变更
2. A 和 B 同时编辑同一行 → 冲突提示弹出 → 选择覆盖/刷新
3. 打开看板视图 → 拖拽卡片切换分组 → 表格视图同步更新
4. 添加评论 → 另一用户可见

### 10.3 Phase 3 验证

1. AI建表: 输入"创建一个招聘管理表" → 生成字段预览 → 确认创建
2. AI填充: 选择某行 → 触发AI填充 → 字段值自动生成
3. AI查询: 输入"哪些任务延期了" → 返回匹配行 + AI回答
4. AI分类: 选择文本列 → 触发分类 → 自动生成分类标签字段

### 10.4 编译验证

- 后端: `mvn compile` 通过
- 前端: `npm run build` 通过
- 无 TypeScript/Java 编译错误
