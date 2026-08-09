# 工单正文全量检索与图片理解方案

> 适用范围：在现有“知识库问答 + 互联网搜索 + 工单附件索引”的基础上，将需求/工单正文中的文本、富文本表格、内嵌图片、图片 OCR 文本和图片语义描述纳入 AI 助手检索范围。
>
> 现状基线：代码位于 `demand_backend` / `demand_frontend`，当前知识检索使用 MySQL 关键词检索 + Milvus 向量检索；工单附件已通过 `KnowledgeDocumentService.syncRequirementAttachmentsWithContext(...)` 同步为知识文档，但工单 `requirements.description` 尚未进入知识索引，正文中的 `<img src="/api/v1/files/{fileId}/preview">` 也尚未形成 OCR/视觉语义索引。

## 1. 目标与非目标

### 1.1 目标

1. AI 助手能够检索所有用户有权限看到的工单正文内容：标题、需求描述、富文本段落、列表、表格、代码片段、编号、错误信息等。
2. 能够检索正文内嵌图片中的文字（OCR），例如截图里的报错、配置项、接口地址、表格文字。
3. 能够按图片语义检索，例如“包含登录流程截图的工单”“展示支付失败页面的需求”。
4. 问答结果可以回溯到工单、正文片段、图片位置，并支持从来源跳转到工单详情。
5. 工单新增、编辑、删除、恢复后，索引最终一致；索引失败不阻塞工单主流程。
6. 知识库检索、工单正文检索、互联网查询可以统一编排，并对来源类型和可信度进行区分。

### 1.2 非目标

- 不建议把所有工单正文默认同步到“公共知识库”，避免用户权限、项目权限和知识库权限混淆。
- 不建议把原始工单图片直接发送给互联网搜索服务；工单内容可能包含敏感信息，图片处理应优先走企业已配置的多模态模型或本地 OCR。
- 第一阶段不把评论、审批评价、流程流转记录混入“正文检索”；这些可以作为后续独立来源类型接入。

## 2. 当前代码梳理与缺口

### 2.1 已有能力

- AI 助手入口和 SSE 问答链路：`AssistantController`、`AssistantServiceImpl`。
- 知识库问答入口：`KnowledgeSearchServiceImpl`，支持 `keyword`、`vector`、`hybrid` / `rag`。
- 向量存储：`MilvusVectorStore`，当前向量实体包含知识库、文档、分片、文本、章节、页码、文件名和文件类型。
- 文件处理：`KnowledgeDocumentServiceImpl` 已支持 PDF、DOC/DOCX、Excel、文本等文档解析和分块；工单附件通过 `knowledge_documents` 与 `knowledge_chunks` 接入。
- 工单正文：存储在 `requirements.description`，前端创建/编辑页使用 Isle/Tiptap 富文本编辑器；正文图片通过 `/api/v1/files/{fileId}/preview` 引用，保存时会将 URL 规范化。
- 搜索结果已经有 `RequirementReference`，但当前主要是“文档命中后反查关联工单”，并不是直接检索工单正文。

### 2.2 主要缺口

1. `requirements.description` 没有进入 `knowledge_chunks` / Milvus，问“哪张工单提到某个报错”只能依赖标题或附件命中。
2. 富文本 HTML 直接做简单去标签会丢失结构；表格、图片顺序、图片所在段落无法准确回溯。
3. 正文图片只被当成文件引用，没有 OCR 文本、图片摘要、图片 embedding。
4. 当前 Milvus schema 没有工单正文来源类型、工单 ID、项目 ID、组织 ID等完整过滤字段。
5. `KnowledgeSearchController` 和知识检索服务虽然要求登录，但检索结果必须额外按工单数据权限过滤，不能仅依赖“文档已入向量库”。
6. 工单更新流程当前重点同步附件；描述字段变更需要单独触发正文索引任务。删除/恢复也要同步索引状态。

## 3. 推荐总体架构

采用“统一内容索引 + 多路召回 + 权限过滤 + 证据化回答”的架构：

```text
工单新增/编辑/删除/恢复
          |
          | after-commit event / outbox
          v
RequirementContentIndexJob
          |
          +--> HTML 结构化解析 --> 正文纯文本 / 表格文本 / 图片占位符
          |
          +--> 图片资产解析 --> OCR 文本
          |                    +--> 图片语义描述
          |                    +--> 可选图片向量
          v
统一索引记录（内容块 + metadata + ACL）
          |
          +--> MySQL 关键词索引
          +--> Milvus 文本向量索引
          +--> 可选 Milvus 图片向量索引
          v
AssistantQueryOrchestrator
          |
          +--> 工单正文召回
          +--> 知识库召回
          +--> 互联网查询（按开关/意图）
          +--> 去重、权限校验、重排
          v
RAG / 多模态 LLM 回答
          |
          +--> 工单来源
          +--> 正文片段
          +--> 图片 OCR / 图片理解来源
          +--> 外部网页来源
```

### 3.1 为什么推荐“独立正文索引”，而不是直接改成知识库文档

建议在统一检索层增加 `requirement_body` 来源类型，但不要把正文复制成用户可见的知识库文档：

- 工单正文的可见性由工单项目、组织、创建人、当前流程和角色决定；知识库文档权限模型不同。
- 工单正文更新频率通常高于知识库文档，独立索引更容易做版本化和增量重建。
- 工单正文属于业务主数据，索引只能是派生数据，删除、恢复、权限变化时可独立失效。
- 用户可以在回答中点击回到工单详情，而不需要先进入知识库文档页。

## 4. 数据模型设计

### 4.1 方案 A：复用现有 `knowledge_documents` / `knowledge_chunks`（推荐 MVP）

增加/约定以下来源类型：

| `source_type` | 含义 |
|---|---|
| `knowledge_base` | 用户上传的知识库文档 |
| `requirement` | 工单附件 |
| `requirement_body` | 工单标题和正文的结构化索引文档 |
| `requirement_body_image` | 工单正文内嵌图片的 OCR/视觉语义索引文档 |

正文索引文档建议：

- `knowledge_documents.requirement_id = requirements.id`
- `knowledge_documents.source_type = 'requirement_body'`
- `knowledge_documents.source_id = requirements.id`
- `file_name = '工单正文：{requirementNo} {title}'`
- `minio_key = NULL`
- `file_type = 'html'`
- `project_id = requirements.project_id`
- `chunk_count`、`status`、`error_message` 表示异步索引状态

正文图片索引文档建议：

- `source_type = 'requirement_body_image'`
- `source_id = fileId`
- `requirement_id = requirements.id`
- `file_name = '工单图片：{requirementNo}#{position}'`
- `minio_key` 取 `file_records.storage_name`

如果现有表对 `minio_key`、`uploader_id` 有非空约束，应先用迁移将正文索引字段改为可空，或填入统一的逻辑值并在处理器中按 `source_type` 分支读取数据库正文。

### 4.2 建议新增索引元数据表

为了支持幂等、增量和可观测性，建议新增 `requirement_search_indexes`：

```sql
CREATE TABLE requirement_search_indexes (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  requirement_id BIGINT UNSIGNED NOT NULL,
  project_id BIGINT UNSIGNED DEFAULT NULL,
  org_id BIGINT UNSIGNED DEFAULT NULL,
  creator_id BIGINT UNSIGNED DEFAULT NULL,
  content_hash CHAR(64) DEFAULT NULL,
  source_version INT DEFAULT 0,
  status VARCHAR(32) NOT NULL DEFAULT 'pending',
  text_chunk_count INT NOT NULL DEFAULT 0,
  image_count INT NOT NULL DEFAULT 0,
  ocr_completed_count INT NOT NULL DEFAULT 0,
  vision_completed_count INT NOT NULL DEFAULT 0,
  last_error VARCHAR(1000) DEFAULT NULL,
  indexed_at DATETIME DEFAULT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_requirement_id (requirement_id),
  KEY idx_status_updated (status, updated_at),
  KEY idx_project_org (project_id, org_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

建议新增 `requirement_search_assets` 保存正文图片处理结果：

```sql
CREATE TABLE requirement_search_assets (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  requirement_id BIGINT UNSIGNED NOT NULL,
  file_id BIGINT UNSIGNED DEFAULT NULL,
  position_no INT NOT NULL,
  source_url VARCHAR(1000) DEFAULT NULL,
  content_hash CHAR(64) DEFAULT NULL,
  ocr_text MEDIUMTEXT,
  vision_caption TEXT,
  status VARCHAR(32) NOT NULL DEFAULT 'pending',
  error_message VARCHAR(1000) DEFAULT NULL,
  processed_at DATETIME DEFAULT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_requirement_position (requirement_id, position_no),
  KEY idx_file_id (file_id),
  KEY idx_status_updated (status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

> 如果希望最小改动，可以不新增 `requirement_search_assets`，将 OCR/视觉结果直接作为 `knowledge_chunks.content`，但不利于图片变化检测、失败重试和原始图片位置回溯；正式方案建议保留资产表。

### 4.3 向量 metadata 扩展

现有 `MilvusVectorStore` 至少增加：

- `source_type`
- `source_id`
- `requirement_id`
- `project_id`
- `org_id`
- `content_type`：`title` / `body_text` / `table` / `image_ocr` / `image_caption`
- `asset_id` / `file_id`
- `block_index`
- `content_hash`

其中 `text` 仍保存用于回答的上下文，`section_title` 保存“工单正文 / 图片 OCR / 图片理解”等可读标题。

**权限过滤建议：**

- MVP：向量召回 `topK * 5`，取回后通过统一 `RequirementVisibilityService` 批量校验可见工单，再截取最终 topK。
- 稳定版：把 `project_id`、`org_id`、`requirement_id` 等字段写入 Milvus，并在检索时使用当前用户的项目/组织可见范围构造 filter；同时保留 DB 二次校验，防止权限规则变更后出现脏数据。
- 不能仅根据 `knowledge_base_id` 过滤，因为正文索引不是普通知识库文档。

## 5. 正文解析与图片理解

### 5.1 HTML 正文解析

新增 `RequirementContentParser`，不要只使用 `replaceAll("<[^>]+>", "")`。建议输出带位置的 block：

```json
{
  "blocks": [
    {"index": 0, "type": "heading", "text": "问题现象"},
    {"index": 1, "type": "paragraph", "text": "用户点击提交后返回 500"},
    {"index": 2, "type": "table", "text": "字段 | 期望值 | 实际值\\n..."},
    {"index": 3, "type": "image", "fileId": 123, "alt": "错误截图", "position": 3}
  ]
}
```

处理规则：

1. 保留标题、段落、列表、引用、代码块和表格的语义顺序。
2. HTML entity 解码，规范化空白，但不能删除编号、接口路径、错误码、版本号等关键 token。
3. 图片节点替换成稳定占位符，例如 `[正文图片#3：错误截图]`，保证正文和图片语义能够在同一上下文中结合。
4. 通过 `fileId` 解析本地文件；对历史数据中的外链图片记录为 `external_image`，默认不主动抓取，避免 SSRF 与数据泄露。
5. 对 base64 图片进行限流、大小限制和 hash 处理，不直接将 base64 写入索引文本。

### 5.2 文本索引内容

正文索引至少生成两类文本：

- `body_text`：标题 + 正文文本 + 表格文本 + 图片占位符。
- `image_ocr` / `image_caption`：一张图片一条或多条独立索引内容。

示例：

```text
[工单] DEM-1024 登录失败排查
[标题] 登录失败排查
[正文]
问题现象：用户提交登录后返回错误。
[正文图片#1：错误截图]
环境：生产环境，版本 2.3.1。
```

```text
[工单图片] DEM-1024#1
[OCR]
HTTP 502 Bad Gateway ... traceId=abc123
[图片理解]
浏览器页面显示登录接口请求失败，错误提示为 502，页面右上角有生产环境标识。
```

### 5.3 图片 OCR 和视觉语义

建议采用可插拔 `ImageUnderstandingService`：

1. **OCR**：优先企业内网/本地 OCR；若暂无本地 OCR，调用已配置的多模态 LLM 的 vision 能力。
2. **图片描述**：调用多模态模型生成 1～3 句中性描述，要求只描述可见内容，不猜测业务事实。
3. **图片向量**：第一阶段不强制引入独立 CLIP/多模态 embedding；先对 OCR + caption 文本做文本 embedding，已经可以覆盖“图中报错/页面类型/字段名称”等高价值场景。
4. **真正的视觉相似检索**：第二阶段再增加 `image_embedding`，需要确认所选 Milvus embedding 模型维度与现有文本 embedding 不同，建议单独 collection，不要混用现有 `dense_vector`。
5. OCR/视觉调用必须脱敏：对 token、手机号、邮箱、身份证号、密钥、Cookie 等敏感信息做掩码；保留原始图片只用于回看，不将脱敏前文本传给互联网搜索。
6. 模型不可用时，正文文本索引仍需成功；图片状态可为 `ocr_failed` / `vision_failed`，支持后台重试。

## 6. 索引触发与一致性

### 6.1 事件触发

在 `RequirementServiceImpl` 的 create/update/draft update/delete/restore 流程中，提交事务后发布：

```text
RequirementChangedEvent(
  requirementId,
  changeType: CREATED | UPDATED | DELETED | RESTORED,
  changedFields: TITLE | DESCRIPTION | PROJECT | ORG | ATTACHMENTS | ALL,
  version,
  operatorId
)
```

不建议在事务中同步调用 OCR、LLM 或 embedding。

推荐顺序：

1. 主事务写入 `requirements`。
2. 同事务写 outbox 记录或使用事务事件监听器。
3. after-commit worker 拉取事件，按 `requirement_id + version` 幂等处理。
4. 新版本开始索引前，将旧版本向量标记为不可检索或删除；新版本成功后切换 `active_version`。
5. 删除/软删除时立即将正文索引置为 `disabled`，异步删除 Milvus 向量；恢复时重新入队。

### 6.2 幂等与增量

- HTML 正文规范化后计算 `content_hash`；正文未变化则不重建正文向量。
- 单张图片按 `file_id + content_hash` 去重；图片位置变化不必重新 OCR，但需要更新 block 位置元数据。
- 先写状态 `processing`，成功后写 `ready`，失败写 `failed` 并记录错误。
- 任务重试采用指数退避，最多 3～5 次；失败任务进入管理页面可手动重试。
- 建议限制并发：图片视觉任务和 embedding 任务分开队列，避免同时占满 LLM 限额。

## 7. 统一检索编排

新增 `AssistantQueryOrchestrator` 或在现有 `KnowledgeSearchService` 之上增加 `SearchScope`：

```json
{
  "query": "哪些工单提到 502 登录失败？",
  "scopes": ["REQUIREMENT_BODY", "KNOWLEDGE_BASE", "WEB"],
  "knowledgeBaseId": null,
  "requirementProjectIds": null,
  "topK": 8,
  "includeImageUnderstanding": true
}
```

### 7.1 意图与默认范围

| 场景 | 默认检索范围 |
|---|---|
| “哪个工单/需求提到……” | 工单标题 + 正文 + 正文图片 OCR/描述 |
| “系统规定/操作手册怎么做” | 知识库 |
| “现在最新的……” | 互联网，可叠加本地知识库，但要标记时间 |
| “这个工单里截图说明了什么” | 当前工单正文 + 对应图片，优先多模态直读 |
| 未明确指定 | 工单正文 + 知识库；是否联网沿用前端 webSearch 开关 |

### 7.2 召回、过滤和重排

1. Query 预处理：提取工单编号、错误码、版本号、接口路径、产品名等精确 token；同时生成语义 query。
2. 并行召回：
   - MySQL/全文检索：编号、错误码、精确字段和短关键词。
   - Milvus：正文向量、OCR/caption 文本向量。
   - 知识库：沿用现有 hybrid 检索。
   - Web：仅在用户开启联网或意图识别为最新信息时调用。
3. 权限过滤：先按当前用户数据范围过滤工单，再进入回答上下文；结果层再次校验。
4. Reranker：对“标题/工单编号/正文命中/OCR命中/caption命中/时间新鲜度”加权。建议初始权重：
   - 工单编号精确命中：1.00
   - 错误码/接口路径精确命中：0.95
   - 正文语义命中：0.75
   - OCR 命中：0.72
   - 图片 caption 命中：0.60
   - 仅附件文件名命中：0.40
5. 去重：同一工单最多保留 2～3 个片段；正文、图片 OCR、知识库附件可以合并为一个工单来源卡片。
6. 上下文组装：每个工单优先保留标题、命中片段、图片 OCR/描述和工单链接；避免把整张正文无条件塞入 LLM。

### 7.3 查询结果来源结构

扩展 `AssistantSource` / `KnowledgeSearchResponse.SearchResultItem`：

```json
{
  "type": "REQUIREMENT_BODY_IMAGE",
  "requirementId": 1024,
  "requirementNo": "DEM-1024",
  "title": "登录失败排查",
  "contentType": "image_ocr",
  "assetId": 88,
  "position": 1,
  "snippet": "HTTP 502 Bad Gateway ...",
  "score": 0.91,
  "route": "/requirements/1024"
}
```

回答中区分：

- `REQUIREMENT_BODY`：工单正文。
- `REQUIREMENT_BODY_IMAGE_OCR`：正文图片 OCR。
- `REQUIREMENT_BODY_IMAGE_CAPTION`：正文图片理解。
- `REQUIREMENT_ATTACHMENT`：工单附件。
- `KNOWLEDGE`：知识库。
- `WEB`：互联网。

## 8. 权限设计（必须先于上线）

### 8.1 统一可见性服务

新增 `RequirementVisibilityService`，不要在 AI 模块复制一套权限 SQL。它应复用需求列表/详情当前使用的组织、项目、角色和流程数据权限规则，提供：

```java
boolean canView(Long requirementId, Long userId);
Set<Long> filterVisibleRequirementIds(Collection<Long> ids, CurrentUserContext user);
RequirementSearchAcl buildAcl(CurrentUserContext user);
```

至少覆盖：

- 超级管理员。
- 组织/角色数据范围。
- 项目成员或项目可见范围。
- 创建人、负责人、抄送人、当前流程参与者。
- 草稿只允许创建人和现有业务规则允许的用户可见。
- 需求删除后不可被检索；恢复后重新可检索。

### 8.2 防止越权的两道闸

1. **召回前过滤**：用 Milvus metadata filter 或候选 ID 过滤减少无权限候选。
2. **回答前过滤**：重新从 MySQL 加载需求并调用 `RequirementVisibilityService`；无权限结果不进入 prompt、不出现在 `sources`，不能只在前端隐藏。

联网搜索不得把未脱敏的工单正文、OCR 或图片发送给外部搜索服务。联网只接收脱敏后的摘要，默认仍建议不发送工单上下文。

## 9. 后端改造清单

### 9.1 新增模块/类

- `RequirementContentParser`：HTML -> 结构化 block。
- `RequirementImageExtractor`：提取 `<img>` 的 fileId、外链和位置。
- `ImageUnderstandingService`：OCR、caption、重试和脱敏。
- `RequirementBodyIndexService`：索引生命周期、hash、版本和删除。
- `RequirementIndexJob` / `RequirementIndexWorker`：异步队列消费。
- `RequirementVisibilityService`：统一权限批量过滤。
- `AssistantQueryOrchestrator`：工单正文、知识库、联网多路编排。

### 9.2 现有类改造点

- `RequirementServiceImpl`：create/update/draft update/delete/restore 发布索引事件。
- `KnowledgeDocumentServiceImpl`：支持 `requirement_body` 虚拟文档或迁移到统一正文索引服务；保留附件同步逻辑。
- `KnowledgeSearchServiceImpl`：新增 `REQUIREMENT_BODY` 检索分支和权限过滤；统一结果映射。
- `MilvusVectorStore`：扩展 metadata；必要时新建 `requirement_text_collection`。
- `AssistantServiceImpl`：将 `request.getKnowledgeBaseId()` 分支从“只查知识库”升级为 query orchestrator；Web 模式中本地轻量检索也包含工单正文。
- `KnowledgeSearchResponse` / `AssistantSource`：增加来源类型、工单 ID、图片位置、contentType 和跳转 route。

### 9.3 API 建议

管理/运维接口：

- `POST /api/v1/requirements/{id}/search-index/rebuild`
- `POST /api/v1/requirements/search-index/rebuild-batch`
- `GET /api/v1/requirements/search-index/{id}`
- `POST /api/v1/requirements/search-index/retry-failed`

助手请求可向后兼容：

```json
{
  "message": "哪些工单提到 502 登录失败？",
  "knowledgeBaseId": -1,
  "webSearch": false,
  "searchScopes": ["REQUIREMENT_BODY", "KNOWLEDGE_BASE"],
  "includeImageUnderstanding": true,
  "llmModelId": 12,
  "pageContext": {}
}
```

`searchScopes` 缺省时按既有行为兼容；升级后建议通用问答默认包含 `REQUIREMENT_BODY`，知识库专用页可只查知识库。

## 10. 前端改造清单

1. AI 助手增加“检索范围”展示：工单正文、知识库、互联网；提供可选开关。
2. 来源卡片区分来源类型：工单正文、图片 OCR、图片理解、附件、知识库、网页。
3. 点击工单来源跳转 `/requirements/{id}`；点击图片来源打开工单详情并定位到正文图片位置（可增加 `?focusImage={fileId}`）。
4. 助手任务列表增加：
   - 工单正文检索
   - 图片文字识别
   - 图片语义理解
   - 权限过滤
   - 综合排序
5. 设置页增加索引健康度：待处理、成功、失败、图片 OCR 完成率、最近更新时间。
6. 对图片理解不可用的来源给出“图片已找到，但暂未完成图片理解”的明确提示，不能让模型假装看过图片。

## 11. 分阶段实施

### Phase 0：验证与基线（1～2 天）

- 统计工单正文 HTML 的实际格式、图片引用形式、历史数据规模和图片大小分布。
- 抽样 100～300 个工单，确认是否存在 base64、外链图片、失效 fileId、表格和代码块。
- 盘点已有 LLM Provider 是否支持 vision、是否有本地 OCR；确定脱敏策略和最大图片尺寸。
- 建立 20～50 条真实查询的离线评测集。

### Phase 1：正文文本索引（3～5 天）

- 新增正文解析器、正文索引表/来源类型、事件和异步任务。
- 将标题、正文、表格、代码块纳入 MySQL + Milvus。
- 接入权限过滤和来源回溯。
- 完成全量回填和增量更新。

### Phase 2：图片 OCR + caption（3～7 天）

- 提取内嵌图片并关联 `fileId`。
- 增加 OCR 和视觉描述任务、脱敏、失败重试。
- 将 OCR/caption 文本 embedding 后纳入统一召回。
- 前端展示图片来源和索引状态。

### Phase 3：统一问答编排与体验优化（3～5 天）

- AI 助手默认支持工单正文范围。
- 工单正文 + 知识库 + 互联网并行检索、去重、重排。
- 增加 query 改写、精确编号/错误码召回、来源引用和工单定位。

### Phase 4：视觉向量与高级能力（按需）

- 独立图片向量 collection。
- 支持“找相似截图”“按页面视觉类型检索”。
- 引入图片区域 OCR、表格识别、图表理解，但仍需保留人工可核验来源。

## 12. 验收指标

### 功能

- 标题、正文纯文本、表格、代码块能够被检索。
- 图片中的错误码、接口名、版本号能够被 OCR 检索。
- “截图表达什么”类问题能够引用对应图片来源；模型未完成视觉处理时不得生成虚假描述。
- 返回结果可跳转到工单详情，显示命中片段和图片位置。

### 权限与安全

- 无权限用户不能通过助手、知识库搜索、SSE、来源详情接口获取工单内容。
- 删除工单不再出现在检索结果；恢复后按规则重新出现。
- 外链图片默认不抓取；图片访问有大小、类型、超时和 SSRF 防护。
- 发送给外部 LLM/互联网服务前完成脱敏并有审计日志。

### 性能与可靠性

- 普通文本检索 P95 < 1.5 秒（不含 LLM 生成）。
- 图片理解异步化，不阻塞工单保存。
- 索引任务可重试、可观测，单工单重复消费不产生重复向量。
- 全量回填支持暂停、续跑和失败重试。
- 关键命中查询在离线评测集上 Recall@10、MRR 和权限准确率达到团队设定阈值；建议初始目标：正文文本 Recall@10 ≥ 90%，图片 OCR Recall@10 ≥ 80%，权限误放率 = 0。

## 13. 最终建议

优先采用以下最小可行路径：

1. 不改动知识库和互联网查询的产品入口，增加 `REQUIREMENT_BODY` 检索范围。
2. 复用现有 `knowledge_documents` / `knowledge_chunks` / Milvus 能力，用 `requirement_body` 表示正文索引，但增加独立 `requirement_search_indexes` 和 `requirement_search_assets` 管理版本与图片处理。
3. 第一阶段先实现正文文本 + 表格 + 图片 OCR；图片 caption 作为同一异步任务中的可选增强。
4. 检索前后都调用统一工单可见性服务；不把权限判断放在前端或只依赖知识库权限。
5. AI 助手统一走 Query Orchestrator，并在来源中明确区分工单正文、图片 OCR、图片理解、知识库和互联网。
6. 只有在正文文本/OCR 的效果无法满足“相似截图”类需求时，才引入独立图片向量 collection。

这样可以最大化复用现有 RAG 设施，同时满足“所有工单正文 + 正文图片理解”的检索目标，并把权限、索引一致性和后续扩展能力一次性纳入设计。

## 7.4 工单正文引用展示与跳转规则

对于“工单正文”命中的结果，引用对象必须展示为工单，而不是展示为知识库文档或虚拟索引文档。

### 后端返回结构

建议扩展 `AssistantSource`：

```java
private String code;             // requirement_body / requirement_body_image_ocr / knowledge_document
private String title;            // 前端展示标题
private String path;             // /requirements/{requirementId}
private String reason;           // 命中说明
private Long requirementId;      // 工单 ID
private String requirementNo;    // 工单编号
private String requirementTitle; // 工单名称
private String contentType;      // body / table / image_ocr / image_caption
private Integer hitCount;        // 当前工单命中的正文片段数量
private Double maxScore;         // 当前工单最高相关度
private Long documentId;         // 仅附件/知识库文档来源使用
private Long knowledgeBaseId;    // 仅知识库来源使用
```

正文命中示例：

```json
{
  "code": "requirement_body",
  "title": "DEM-1024 登录失败排查",
  "path": "/requirements/1024",
  "requirementId": 1024,
  "requirementNo": "DEM-1024",
  "requirementTitle": "登录失败排查",
  "contentType": "body",
  "hitCount": 3,
  "maxScore": 0.91,
  "reason": "正文命中 3 处，相关度 91%"
}
```

正文图片命中示例：

```json
{
  "code": "requirement_body_image_ocr",
  "title": "DEM-1024 登录失败排查",
  "path": "/requirements/1024",
  "requirementId": 1024,
  "requirementNo": "DEM-1024",
  "requirementTitle": "登录失败排查",
  "contentType": "image_ocr",
  "hitCount": 1,
  "maxScore": 0.88,
  "reason": "正文图片 OCR 命中 1 处，相关度 88%"
}
```

### 引用聚合规则

- 同一工单命中多个正文片段时，引用列表只展示一条工单引用，避免重复展示。
- 同一工单同时命中正文文本、图片 OCR 和图片描述时，仍只展示一条工单引用；`reason` 中可说明命中类型，例如“正文 + 图片 OCR”。
- 同一工单的附件命中仍可以单独展示为附件/知识库文档来源，避免把“工单正文”和“工单附件”混淆。
- 引用列表按工单最高相关度降序排列。
- 引用标题优先使用“工单编号 + 工单名称”，工单编号缺失时才退化为工单名称。

### 前端点击行为

当前系统的工单详情路由为：

```text
name: RequirementDetail
path: /requirements/:id
```

因此：

- `requirement_body`、`requirement_body_image_ocr`、`requirement_body_image_caption` 均设置 `path = /requirements/{requirementId}`；
- 前端引用卡片显示：`DEM-1024 登录失败排查`；
- 卡片可点击、可键盘 Enter/Space 操作；
- 点击后调用 `router.push({ name: 'RequirementDetail', params: { id: source.requirementId } })`；
- 不走知识库文档预览逻辑；`documentId` 和 `knowledgeBaseId` 对正文来源保持为空；
- 进入工单详情后，可选增加 `focus` 查询参数定位到命中正文区域或正文图片，例如：

```text
/requirements/1024?focus=image&fileId=123
```

### 前端类型与判断

`demand_frontend/src/types/assistant.ts` 中增加：

```ts
export interface AssistantSource {
  code?: string
  title?: string
  path?: string
  reason?: string
  documentId?: number | null
  knowledgeBaseId?: number | null
  requirementId?: number | null
  requirementNo?: string | null
  requirementTitle?: string | null
  contentType?: 'body' | 'table' | 'image_ocr' | 'image_caption' | 'attachment' | string
  hitCount?: number | null
  maxScore?: number | null
}
```

`SystemAssistant.vue` 中的点击判断建议调整为：

```ts
function isRequirementSource(source: AssistantSource) {
  return source.code === 'requirement_body'
    || source.code === 'requirement_body_image_ocr'
    || source.code === 'requirement_body_image_caption'
}

function isSourceClickable(source: AssistantSource) {
  if (isRequirementSource(source) && source.requirementId) return true
  if (source.code === 'knowledge_document' && source.documentId && source.knowledgeBaseId) return true
  return !!source.path
}

async function handleSourceNavigate(source: AssistantSource) {
  if (isRequirementSource(source) && source.requirementId) {
    await router.push({
      name: 'RequirementDetail',
      params: { id: source.requirementId },
    })
    return
  }

  if (source.code === 'knowledge_document' && source.documentId && source.knowledgeBaseId) {
    openSourcePreview(source)
    return
  }

  if (source.path) {
    await handleNavigate(source.path)
  }
}
```

### 需要避免的实现方式

不要将正文来源统一映射为当前已有的 `knowledge_document`，否则前端会尝试使用 `documentId + knowledgeBaseId` 打开知识库文档预览，导致正文引用无法正确跳转，且用户会误以为命中的是附件。

后端的 `mapCitationsToSources(...)` 也需要根据 `sourceType` 分支：

```text
sourceType = requirement_body
    -> code = requirement_body
    -> title = requirementNo + " " + requirementTitle
    -> path = /requirements/{requirementId}
    -> requirementId / requirementNo / requirementTitle 有值
    -> documentId / knowledgeBaseId 为空
    -> contentType = body / image_ocr / image_caption / body_image

sourceType = knowledge_base 或 requirement attachment
    -> code = knowledge_document
    -> 保持现有知识库文档预览逻辑
```

## 14. 当前实现的运行配置与上线步骤

### 14.1 图片 OCR / 视觉理解配置

图片理解不再从 `application.yml` 或其他配置文件读取 Provider、Base URL、API Key 和模型名称，统一在管理端 **模型配置 → 模型应用** 中配置：

1. 在“接入组与模型”中新增或维护支持图片输入的模型，并将模型类型设置为 `vision`。
2. 在“模型应用”中找到“工单正文图片理解”（应用编码：`knowledge.image-understanding`）。
3. 选择对应的 `vision` 模型并启用该应用。
4. 未选择模型、应用未启用或 Provider/模型不可用时，图片理解自动降级为空结果，只保留正文文本、表格文本和图片 `alt` 索引。

协议、Base URL、API Key 和实际模型名称均复用所选模型所属接入组和模型记录。模型应返回 `{"ocrText":"...","caption":"..."}`；实现兼容 Markdown JSON 代码围栏，以及 JSON 前后包含少量说明文字的情况。

原始图片只发送到模型配置中所选的企业内部 Provider，不会发送到互联网搜索服务。

### 14.2 安全与降级规则

- 只下载正文中 `/api/v1/files/{id}`、`/preview`、`/preview-url` 形式的内部图片引用；外链图片不会由后端抓取，避免 SSRF。
- 文件记录的 Content-Type 必须以 `image/` 开头。
- 单张图片最大 10 MB；超限、文件不存在、下载失败、模型超时或返回格式异常时跳过该图片。
- 图片处理失败不会阻塞工单保存，也不会影响正文文本进入索引。模型应用未配置、未启用或未选择可用模型时，向量化流程会跳过正文图片 OCR/视觉理解并记录提示日志，不会将工单标记为失败。
- OCR 文本和图片描述分别写入独立分块，引用列表仍按工单聚合，不暴露为附件来源。

### 14.3 历史数据回填

应用升级并完成数据库迁移后，由管理员调用：

```http
POST /api/v1/knowledge/requirement-bodies/backfill
```

接口返回提交数量：

```json
{
  "code": 200,
  "data": {
    "submitted": 128
  }
}
```

回填会为历史工单创建或更新 `source_type=requirement_body` 的虚拟文档，并重新提交正文、OCR 和图片理解分块。建议在低峰期执行，并关注文档索引状态、消息队列积压和视觉模型调用量。

### 14.4 验收检查

1. 新建包含富文本、表格和内部图片的工单，等待索引完成。
2. 分别使用正文关键字、图片内错误码、图片语义描述进行 AI 助手提问。
3. 引用列表应显示工单编号和工单名称，并标识“工单正文”“图片 OCR”或“图片理解”。
4. 全局 AI 助手的来源标签、知识库问答页的引用来源面板都应显示工单编号和工单名称。
5. 点击正文角标、引用卡片或“查看工单”应进入 `/requirements/{requirementId}`，而不是打开知识库附件预览。
6. 编辑正文后旧片段不应继续命中；删除工单后正文引用应消失；恢复后应重新进入索引。
