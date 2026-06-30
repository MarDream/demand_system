# 文档中心/知识库问答代码落地复核报告

> 复核时间：2026-06-28 17:14  
> 复核目标：检查前次方案提到的关键能力是否已在代码中落地，并重新评估还需要完善的功能建设。  
> 复核范围：`demand_backend` + `demand_frontend` + `database`，以只读审查为主，未修改业务代码。

---

## 一、结论摘要

本次复核发现：**文档中心到知识库问答的主链路已经基本落地，而且前次评估中两个 P0 问题已有明显进展：关键词检索和流式输出均已实现。**

当前系统已具备：知识库管理、文档上传/解析/切片/向量化、Milvus 检索、关键词检索、Hybrid + Reranker、RAG 答案生成、SSE 流式输出、LLM 接入组管理、文档预览/下载/分享、需求附件同步等能力。

但仍存在若干需要完善的建设点，主要集中在：**RAG 多轮对话语义、全局检索页参数未透传、监控统计伪实现、任务重试/DLQ、真实关键词检索质量、文档结构化解析和服务端会话持久化**。

综合复核评分从前次 3.6/5.0 调整为：**4.1 / 5.0 — 主功能已落地，进入体验与可靠性增强阶段。**

---

## 二、前次重点问题落地状态复核

| 前次问题 | 当前状态 | 复核结论 |
|---|---:|---|
| 关键词检索名不副实 | ✅ 已落地 | 后端已实现 `keywordSearch()`，基于 `KnowledgeChunk.content/sectionTitle` 和 `KnowledgeDocument.fileName` 做 SQL LIKE 匹配和打分，不再直接复用语义检索。 |
| RAG 问答无流式输出 | ✅ 已落地 | 后端新增 `/api/v1/knowledge/search/stream`，返回 `SseEmitter`；前端 `streamSearchKnowledge()` 使用 `fetch + ReadableStream` 解析 SSE 事件。 |
| 上下文拼接简单 | ⚠️ 部分落地 | 前端支持最近 N 轮上下文拼入 query，但后端仍没有结构化 conversation/messages 入参，LLM 侧无法区分历史消息与当前问题。 |
| 会话仅 localStorage | ⚠️ 未完善 | 前端会话仍主要保存在本地，尚无服务端会话表与审计能力。 |
| RAG 监控统计不足 | ❌ 未落地 | `/api/v1/knowledge/stats` 仍只返回固定 `module/status`，不是真实健康度指标。 |

---

## 三、代码落地证据

### 3.1 后端能力

| 能力 | 关键代码 | 状态 |
|---|---|---:|
| 知识库 CRUD / 迁移 / 默认知识库 | `demand_backend/src/main/java/com/demand/system/module/knowledge/controller/KnowledgeBaseController.java` | ✅ |
| 文档上传 / 预览 / 下载 / 分享 / 删除 / 重试 | `KnowledgeDocumentController.java`、`KnowledgeDocumentServiceImpl.java` | ✅ |
| RabbitMQ 异步文档处理 | `KnowledgeDocumentConsumer.java`、`RabbitMQConfig.java` | ✅ |
| 文档解析与切片 | `KnowledgeDocumentServiceImpl.java` | ✅ |
| Embedding 调用 | `EmbeddingServiceImpl.java` → `LlmGateway.embed()` | ✅ |
| Milvus 向量库 | `MilvusVectorStore.java` | ✅ |
| Hybrid + Reranker | `KnowledgeSearchServiceImpl.hybridSearch()` | ✅ |
| 关键词检索 | `KnowledgeSearchServiceImpl.keywordSearch()` | ✅ |
| RAG 同步答案 | `RagAnswerServiceImpl.generateAnswer()` | ✅ |
| RAG 流式答案 | `KnowledgeSearchController.streamSearch()`、`KnowledgeSearchServiceImpl.streamSearch()`、`RagAnswerServiceImpl.streamAnswer()` | ✅ |
| LLM 接入组管理 | `module/llm/controller/LlmProviderController.java` | ✅ |
| 数据库表结构 | `database/init.sql` 中 `knowledge_*`、`llm_*` 表 | ✅ |

### 3.2 前端能力

| 能力 | 关键代码 | 状态 |
|---|---|---:|
| 知识库列表 | `demand_frontend/src/views/knowledge/index.vue` | ✅ |
| 知识库详情 / 文档管理 | `demand_frontend/src/views/knowledge/detail.vue` | ✅ |
| 文档上传弹窗 | `src/components/document/DocumentUploadDialog.vue` | ✅ |
| 文档预览 | `src/components/document/FilePreviewDialog.vue` | ✅ |
| 文档分享页 | `src/views/public/KnowledgeSharePage.vue` | ✅ |
| 全局检索页 | `src/views/knowledge/search.vue` | ✅ |
| RAG 问答工作台 | `src/views/rag/index.vue` | ✅ |
| 流式检索客户端 | `src/api/modules/knowledge.ts` 中 `streamSearchKnowledge()` | ✅ |
| 模型选择 | `src/views/rag/index.vue` + `llmProviderApi.list()` | ✅ |
| 本地会话与上下文轮数 | `src/views/rag/index.vue` | ✅ |

---

## 四、仍需完善的问题

### P0：全局检索页 TopK 未传给后端

**现象**：`src/views/knowledge/search.vue` 页面有 TopK 选择器，但 `handleSearch()` 调用：

```ts
store.search(query.value, mode.value, selectedKbId.value === '' ? undefined : selectedKbId.value)
```

未传 `topK.value`。这会导致用户选择 Top 10/20/50 时后端仍使用默认配置。

**建议**：改为：

```ts
store.search(
  query.value,
  mode.value,
  selectedKbId.value === '' ? undefined : selectedKbId.value,
  topK.value
)
```

### P0：RAG 模式入口不够显性

后端支持 `mode = rag` 或传 `llmModelId` 触发答案生成；RAG 工作台通过模型选择触发流式回答。但全局检索页只暴露 `hybrid / semantic / keyword`，用户不容易理解“检索”和“问答”的边界。

**建议**：
- 全局检索页保持“查资料”定位；
- RAG 工作台明确标注“知识库问答”；
- 或新增“生成回答”开关，显式传 `llmModelId` / `mode=rag`。

### P1：多轮对话仍是“查询改写式”，不是后端结构化会话

当前前端 `buildRequestQuery()` 将历史问答拼成一个长 query。这样可以增强检索，但 LLM 无法获得结构化 messages，也无法区分：系统指令、历史问题、历史回答、当前问题、检索证据。

**建议建设**：
1. 新增后端 DTO：`conversationId`、`messages`、`contextTurns`；
2. RAG 生成阶段使用 messages 数组；
3. 历史上下文用于“query rewrite”，LLM 回答阶段仍明确传入当前问题 + 证据 + 历史消息。

### P1：服务端会话持久化缺失

当前 RAG 会话主要存在浏览器 localStorage。问题包括：换设备丢失、无法审计、无法统计热门问题、无法做团队知识沉淀。

**建议建设**：
- 新增 `knowledge_chat_sessions`、`knowledge_chat_messages` 表；
- 支持会话列表、消息历史、收藏、删除；
- 增加用户维度与知识库维度权限控制；
- localStorage 只做临时草稿/缓存。

### P1：知识库健康度与质量评估未落地

`/api/v1/knowledge/stats` 当前只返回固定：

```json
{ "module": "knowledge-rag", "status": "active" }
```

这不足以支撑生产运维。

**建议建设真实指标**：
- 文档总数 / 已索引 / 失败 / stored；
- chunk 总量；
- Milvus 向量数量；
- 最近 24h 上传/失败数；
- 平均检索耗时；
- Rerank/LLM 成功率；
- 索引失败 Top 错误。

### P1：RabbitMQ 缺少重试与死信队列策略

当前异步处理已落地，但未看到明确 DLQ、最大重试次数、指数退避、失败告警。文档处理是知识库建设的核心链路，失败不可只靠人工发现。

**建议建设**：
- `knowledge.document.process.retry.queue`；
- `knowledge.document.process.dlq`；
- 最大重试 3 次；
- 失败原因落库；
- 后台提供“重试失败文档”批量入口（已有重试接口，可配合 DLQ 完善）。

### P2：关键词检索已实现，但质量仍偏基础

当前关键词检索基于 SQL LIKE + 简单打分。已解决“没有实现”的问题，但对大规模知识库、中文分词、编号精确匹配、字段权重仍有限。

**建议演进路线**：
1. 短期：保留 LIKE，增加编号/文件名精确匹配优先；
2. 中期：MySQL FULLTEXT + ngram；
3. 长期：Elasticsearch / OpenSearch BM25，或 Milvus sparse vector，实现真正 Hybrid = Dense + Sparse + Rerank。

### P2：文档结构化解析不足

Milvus schema 有 `section_title`、`page_num`，但当前解析对 PDF/DOC/Excel 的章节、页码、表格结构保留有限。RAG 引用可读性会受影响。

**建议建设**：
- PDF 按页提取，写入 `pageNum`；
- DOCX/Markdown 按标题层级切片；
- Excel 保留 sheetName / rowRange；
- 引用展示“文件名 + 页码/章节/sheet”。

### P2：LLM/Embedding/Rerank 调用可观测性不足

建议记录每次外部模型调用：模型ID、耗时、token用量（如接口返回）、错误码、重试次数、调用场景。这样才能定位“慢在检索、Rerank还是生成”。

---

## 五、建议建设路线

### 第一阶段：修正已落地功能的小缺口（优先做）

| 优先级 | 事项 | 预期收益 |
|---|---|---|
| P0 | 修复全局检索页 TopK 未透传 | 用户选择立即生效，避免体验不一致 |
| P0 | 明确 RAG/检索入口边界 | 降低用户误解 |
| P1 | `/knowledge/stats` 返回真实统计 | 支撑运维和验收 |
| P1 | 文档处理失败原因规范化展示 | 提升可维护性 |

### 第二阶段：增强知识库问答体验

| 优先级 | 事项 | 预期收益 |
|---|---|---|
| P1 | 后端结构化多轮 messages | 提升连续追问质量 |
| P1 | 服务端会话持久化 | 跨设备、审计、统计 |
| P2 | 引用定位增强（页码/章节/sheet） | 提升答案可信度 |
| P2 | 回答引用格式标准化 | 便于用户核查来源 |

### 第三阶段：生产可靠性与可观测性

| 优先级 | 事项 | 预期收益 |
|---|---|---|
| P1 | RabbitMQ 重试 + DLQ | 防止索引任务静默失败 |
| P1 | LLM 调用超时/重试/熔断 | 防止外部模型拖垮请求 |
| P2 | RAG 调用日志与指标 | 定位慢查询和低质量回答 |
| P2 | 检索质量评测集 | 用数据衡量召回率/准确性 |

---

## 六、复核后的评分

| 维度 | 前次评分 | 本次评分 | 变化原因 |
|---|---:|---:|---|
| 功能完整性 | 4.0 | 4.4 | 关键词检索、SSE 流式输出已落地 |
| 架构合理性 | 4.0 | 4.2 | 主链路完整，仍需 DLQ/监控/服务拆分 |
| 用户体验 | 3.0 | 3.8 | RAG 流式显著改善，但会话持久化和引用定位仍弱 |
| 可维护性 | 3.0 | 3.5 | 功能变完整，但配置/巨型 Service/统计伪实现仍需治理 |
| 生产就绪度 | 4.0 | 4.4 | 异步处理、超时检测、降级策略齐备；缺少可观测性和 DLQ |

**综合评分：4.1 / 5.0**

---

## 七、最终审查意见

文档中心知识库问答功能不是“方案停留在纸面”，而是**主功能已经基本落地**。当前系统已经具备企业内部知识库问答的基础生产能力，尤其是：文档入库、向量化、Hybrid 检索、Reranker、SSE 流式回答、模型接入组管理，这些关键能力都能在代码中找到明确实现。

下一步不建议继续大拆大建，而应转入“体验与可靠性补强”：先修复 TopK 透传、完善真实 stats、补齐服务端会话与 RabbitMQ DLQ，再逐步提升检索质量和文档结构化解析。这样投入产出比最高，也最符合当前系统状态。
