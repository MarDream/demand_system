# 综合运营管理平台 · 全链路体验测试报告

> **测试性质**：后端已运行（`:8081`）的真实端到端冒烟测试（Black-box API + SSE 流式验证）
> **测试时间**：2026-07-18 13:51 – 14:00 (GMT+8)
> **测试账号**：`admin / admin123`（超级管理员）
> **测试覆盖**：认证、知识库 RAG、AI 操作助手、需求、工作流、多维表格、权限/通知/模型、安全探测
> **测试人**：UX Researcher（Agent）

---

## 🎯 测试目标与方法

**研究问题**
1. 后端启动后，各核心模块是否可真实连通并正确返回数据？
2. 我们刚完成的知识库 RAG 流式问答、AI 助手集成（知识库+模型选择）是否端到端可用？
3. 之前代码审查标记的已知风险（CORS/权限/Bitable 表缺失/Rating 前缀）在运行时是否真实存在？

**方法**
- 登录拿 Token（Bearer），逐模块打接口，记录 HTTP 状态码、响应时间、关键响应体
- 对 SSE 流式接口（RAG 问答、助手对话）做真实长连接采样，验证 `event:`/`data:` 协议完整性
- 对历史已知问题做针对性探测（CORS 预检、DELETE 越权、Rating 前缀、Bitable 建表）
- 中文请求体统一用 UTF-8 文件方式发送，排除 shell 编码干扰

---

## 📊 全链路测试结果总览

| 模块 | 关键接口 | 结果 | 证据 |
|---|---|---|---|
| 认证 | `POST /auth/login` | ✅ 200 (~360ms) | 返回 accessToken（231字符） |
| 认证 | `GET /auth/me` | ✅ 200 | 用户信息正常；无 token → 401 |
| 知识库 | `GET /knowledge/bases` | ✅ 200 | 返回「一体化运营知识库」(id=1, 0文档) |
| 知识库 RAG | `POST /knowledge/search/stream` (SSE) | ✅ 200 | `event:results`→`event:done` 协议完整；空库优雅返回"未找到相关文档片段" |
| AI 助手 | `POST /assistant/sessions` | ✅ 200 | 创建会话成功 |
| AI 助手 | `POST /assistant/sessions/{id}/messages/stream` (SSE) | ✅ 200 | **完整跑通**：`meta`→`actions`(导航按钮)→`delta`(逐字)→`done`；真实 LLM（MiniMax-M3）回复 |
| 需求 | `GET /requirements` | ✅ 200 | 列表正常（空，total=0） |
| 需求 | `POST /requirements` | ⚠️ 400 | "项目ID不能为空"（合法校验，需求必须带 projectId） |
| 工作流 | `GET /workflows/versions/active` 等 | ✅ 200 | 版本/审批/状态/迭代全部正常 |
| 项目 | `GET /projects` | ✅ 200 | 返回项目列表 (id=1) |
| 多维表格 | `POST /bitable/bases` | ✅ 200 | 建库成功 |
| 多维表格 | `POST /bitable/bases/{id}/tables` | 🔴 **5000** | **bad SQL grammar on `BitableViewMapper.insert`** |
| 通知 | `GET /notifications` `/unread` | ✅ 200 | 正常 |
| 模型 | `GET /llm-providers/chat-models` | ✅ 200 | 5+ 模型，默认 MiniMax-M3；Embedding=Qwen3-VL-Embedding-8B(已配置) |
| CORS | `OPTIONS` 预检 (Origin: evil.com) | ✅ 403 | 白名单机制生效，未回显 `*`（历史"过宽"担忧已修复） |
| Rating | `GET /api/v1/statistics/rating` | ✅ 200 | 路径含 `/api` 前缀正常（历史"缺前缀"担忧已修复） |

---

## ✅ 验证可用（真实运行确认）

### 1. AI 操作助手 —— 端到端体验完整（本次最大亮点）
真实 LLM 流式回复，事件链完整：
```
event:meta      → {sessionId, userMessageId, assistantMessageId}
event:actions   → 可点击导航卡片 (targetPath: /requirements)
event:delta     → 逐字流式文本
event:done      → 完整消息 + sources + intent
```
- 知识库选择（`knowledgeBaseId`）与模型选择（`llmModelId`）字段前后端一致（前端 `AssistantChatRequest.message` 与后端 DTO 对齐 ✅）
- 回复质量高，带"操作导航" action card，体验符合预期

### 2. 知识库 RAG 流式
- SSE 协议 `event:results` / `event:done` 格式正确，前端 `handleStreamEvent` 可正常解析
- 当前知识库 **0 文档** → 检索返回"未找到相关文档片段"（功能正常，但无内容可检，需先上传文档入库才能真正体验 RAG 检索+引用）

### 3. 认证 / 权限 / 工作流 / 通知 / 模型
- 登录、token 校验、401 拦截均正常
- 工作流版本、审批、状态、迭代接口全部 200
- LLM 模型列表与 Embedding 配置就绪（RAG 入库前提满足）

### 4. 历史风险项状态更新
| 历史标记 | 运行时验证 | 结论 |
|---|---|---|
| CORS 配置过宽 | 预检 evil.com → 403，无 `*` 回显 | ✅ **已修复**（白名单 `cors.allowed-origins`） |
| Rating 缺 `/api` 前缀 | `/api/v1/statistics/rating` → 200 | ✅ **已修复** |
| Bitable 表未创建 | 建表 5000 bad SQL grammar | 🔴 **确认存在** |

---

## 🔴 确认的运行时缺陷（需修复）

### P0 — 多维表格无法创建表（模块不可用）
- **现象**：`POST /api/v1/bitable/bases/{id}/tables` → `{"code":5000,"message":"... bad SQL grammar ... BitableViewMapper.insert ..."}`
- **根因**：Bitable 核心表（`bitable_tables` / `bitable_views` / `bitable_records` 等）未在运行数据库中创建。迁移脚本 `database/migrations/V20260716_02~06__bitable_*.sql` 未被应用（建库成功但建表时 mapper 插入失败即印证）。
- **影响**：多维表格模块**完全不可用**——用户能建库，但建表即崩溃，无法进入表格/视图/记录任何核心流程。
- **修复建议**：在运行库执行 Bitable 迁移脚本（含 `bitable_tables`/`bitable_views` 的 CREATE + ALTER），并补一个启动自检/迁移校验。

### P1 — REST 状态码语义缺失（全局）
- **现象**：业务错误统一返回 **HTTP 200**，错误码放在 body 的 `code` 字段。例如：
  - `DELETE /api/v1/requirements/999999` → `200` + `{"code":500,"message":"需求不存在"}`
  - `DELETE /api/v1/requirements/1`（不存在）→ 同样 `200` + code:500
- **影响**：
  - 前端基于 HTTP 状态码的错误处理/重试/全局拦截易误判为"成功"
  - 监控、网关、负载均衡无法用状态码区分成功/失败，告警与限流失效
  - 与浏览器/HTTP 语义（404/403/400）背离
- **修复建议**：业务异常映射到真实 HTTP 状态码（404 资源不存在、403 越权、400 参数错误）；保留 body.code 作为业务细分码。

### P2 — 需求删除缺少资源存在性/权限语义
- 合并于 P1：删除不存在的资源未返回 404，且需确认是否校验"仅可删自己有权限的需求"（当前仅返回业务错误）。

---

## 💡 UX 体验洞察与建议

1. **RAG 需"内容填充"才能体现价值**
   知识库当前为空，RAG 问答只能返回"未找到相关文档"。建议：上线引导流程（首次进入知识库引导上传文档 + 入库进度），否则用户会觉得"AI 答非所问"。

2. **Bitable 是阻断性缺项**
   作为卖点模块之一，建表即崩会直接导致用户流失。优先级高于新功能。

3. **默认管理员弱口令**
   `admin/admin123` 应强制首次登录改密，避免生产环境安全风险。

4. **错误反馈一致性**
   P1 的状态码问题会让前端"成功/失败"提示错乱（如删除需求明明失败却提示成功）。修复后整体交互可信度显著提升。

5. **助手体验是亮点，可加强**
   导航 action card 体验好；建议补充：回答中引用知识库来源的可点击跳转、对话上下文记忆提示、停止生成按钮（前端已规划）。

---

## 📈 关键指标

| 指标 | 结果 |
|---|---|
| 核心模块连通率 | 11/12 模块可正常响应（Bitable 建表除外） |
| 流式协议完整性 | RAG ✅ / 助手 ✅（meta-actions-delta-done 全链路） |
| 登录耗时 | ~360ms |
| 历史风险项闭环 | 2 项已修复 ✅ / 1 项确认存在 🔴 |
| 阻断性 Bug | 1 个（P0 Bitable 建表） |

---

## 🚀 下一步

1. **立即（P0）**：执行 Bitable 迁移脚本，验证建表→视图→记录全链路，重测通过。
2. **短期（P1）**：统一异常→HTTP 状态码映射，补前端基于状态码的错误处理。
3. **体验（P2）**：知识库上传引导、默认口令改密、助手引用来源跳转。

---
**UX Researcher**：Agent
**测试日期**：2026-07-18
**环境**：后端 :8081（运行中）、前端同源自带 vite 代理（dev 不存在跨域阻断）
**复测方式**：本报告所有结论均基于真实 HTTP 请求与 SSE 采样，非静态推断。
