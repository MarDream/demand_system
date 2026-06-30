# 文档中心知识库问答功能 — 系统性测试方案

> **测试目标**：覆盖"文档中心"RAG 问答全链路——从模型配置到知识库管理、文档上传索引、语义检索、流式问答、证据面板、会话管理，确保各模块协同工作、边界场景稳定。
>
> **版本**：v1.0 | **日期**：2026-06-29 | **测试专家**：端测测

---

## 一、测试范围总览

| 模块 | 核心功能 | 涉及路由/API |
|------|---------|-------------|
| 模型配置 | 接入组 CRUD、模型 CRUD、连通性测试、模型嗅探 | `/settings/llm`、`/v1/llm-providers/*` |
| 知识库管理 | 知识库 CRUD、默认知识库、文档迁移 | `/settings/knowledge`、`/v1/knowledge/bases/*` |
| 文档管理 | 上传/删除/重传/跳过索引/批量操作/分享 | `/settings/knowledge/:id`、`/v1/knowledge/bases/{kbId}/documents/*` |
| 全局语义检索 | 跨知识库搜索、搜索模式、AI 摘要 | `/settings/knowledge/search`、`/v1/knowledge/search` |
| RAG 问答工作台 | 三栏交互、流式问答、上下文、模型选择、证据面板 | `/settings/rag`、`/v1/knowledge/search/stream` |
| 公开分享 | Token 验证、文件预览/下载 | `/public/share/:token`、`/v1/public/knowledge/shares/*` |

---

## 二、前置条件（Prerequisite）

| # | 条件 | 验证方式 |
|---|------|---------|
| P1 | 后端服务正常运行（Spring Boot + Milvus + RabbitMQ + MinIO） | `GET /api/v1/knowledge/stats` 返回 200 |
| P2 | 至少配置 1 个启用的 LLM 接入组，且含 ≥1 个已通过连通性测试的 Chat 模型 | 模型配置页面显示绿色测试通过标记 |
| P3 | Embedding 服务可用（默认智谱 `embedding-3`） | 上传文档后状态从 `indexing` 变为 `indexed` |
| P4 | 至少存在 1 个知识库，且包含 ≥3 份已索引文档 | 知识库卡片显示 `docCount > 0` 且 `chunkCount > 0` |
| P5 | 用户已登录且有 `menu:rag` 权限 | 可正常访问 `/settings/rag` |

---

## 三、功能测试用例

### 3.1 模型配置模块

#### 3.1.1 接入组 CRUD

| ID | 用例名称 | 前置条件 | 操作步骤 | 预期结果 | 优先级 |
|----|---------|---------|---------|---------|-------|
| MC-001 | 创建 OpenAI 协议接入组 | 无同名接入组 | 1. 进入模型配置页<br>2. 点击"新增接入组"<br>3. 填写：名称="测试OpenAI"、协议=openai、BaseURL=`https://api.openai.com`、APIKey=sk-xxx<br>4. 保存 | 接入组创建成功，列表显示新条目，maskedApiKey 为 `sk-***xxx` | P0 |
| MC-002 | 创建 Anthropic 协议接入组 | 无同名接入组 | 1. 填写：名称="测试Claude"、协议=anthropic、BaseURL=`https://api.anthropic.com`、APIKey=sk-ant-xxx<br>2. 保存 | 接入组创建成功，协议标签显示 anthropic | P0 |
| MC-003 | 创建时 BaseURL 为空 | — | BaseURL 留空，其余必填项正常 | 前端校验拦截，提示"BaseURL 不能为空" | P1 |
| MC-004 | 创建时 APIKey 为空 | — | APIKey 留空 | 前端校验拦截，提示"API Key 不能为空" | P1 |
| MC-005 | 编辑接入组名称 | 存在接入组 | 修改名称，保存 | 名称更新成功，关联模型不受影响 | P1 |
| MC-006 | 禁用接入组 | 存在启用的接入组 | 点击 toggle 开关禁用 | 接入组状态变为"已禁用"；其下所有模型在 RAG 工作台不可选 | P0 |
| MC-007 | 启用已禁用接入组 | 存在已禁用的接入组 | 点击 toggle 开关启用 | 接入组恢复启用；模型重新可选 | P1 |
| MC-008 | 删除接入组 | 接入组下无模型 | 点击删除并确认 | 接入组移除成功 | P1 |
| MC-009 | 删除含模型的接入组 | 接入组下有模型 | 点击删除并确认 | 后端应级联删除或拒绝（需确认业务规则） | P1 |
| MC-010 | 查看 API Key | 存在接入组 | 点击"查看"按钮 | 明文 API Key 显示，3 秒后自动隐藏或需手动关闭 | P1 |

#### 3.1.2 模型 CRUD

| ID | 用例名称 | 操作步骤 | 预期结果 | 优先级 |
|----|---------|---------|---------|-------|
| MC-011 | 添加 Chat 模型 | 在接入组下添加模型：名称="GPT-4o"、modelId="gpt-4o"、modelType="general"、temperature=0.7、maxTokens=4096 | 模型创建成功，类型标签显示"general" | P0 |
| MC-012 | 添加 Embedding 模型 | modelType="embedding" | 模型创建成功，但在 RAG 工作台模型选择器中不可选 | P0 |
| MC-013 | 添加 Rerank 模型 | modelType="rerank" | 模型创建成功，同样在问答模型选择器中不可选 | P1 |
| MC-014 | 设置默认模型 | 点击某个模型的"设为默认" | 该模型 isDefault=true，其他同类型模型自动取消默认 | P1 |
| MC-015 | 禁用模型 | toggle 关闭 | 模型在 RAG 工作台不可选 | P0 |
| MC-016 | 编辑模型参数 | 修改 temperature 从 0.7 → 0.3 | 参数更新成功 | P2 |
| MC-017 | 删除模型 | 点击删除并确认 | 模型移除成功 | P1 |

#### 3.1.3 连通性测试

| ID | 用例名称 | 操作步骤 | 预期结果 | 优先级 |
|----|---------|---------|---------|-------|
| MC-018 | 测试单个模型连通性 | 点击模型的"测试"按钮 | 返回测试结果：success=true、content 非空、durationMs 合理、token 统计有值 | P0 |
| MC-019 | 测试无效 API Key | 配置错误 APIKey 后测试 | success=false、errorMessage 包含认证错误描述 | P0 |
| MC-020 | 测试无效 BaseURL | 配置不可达 BaseURL 后测试 | success=false、errorMessage 包含连接超时或拒绝描述 | P0 |
| MC-021 | 批量测试 | 点击"批量测试"按钮 | 所有模型逐一测试，结果更新到表格 | P1 |
| MC-022 | 测试详情 Drawer | 测试完成后点击"查看详情" | Drawer 展示：响应时间、Tokens 用量、回答内容、错误信息 | P2 |

#### 3.1.4 模型嗅探

| ID | 用例名称 | 操作步骤 | 预期结果 | 优先级 |
|----|---------|---------|---------|-------|
| MC-023 | 嗅探 OpenAI 接入组的可用模型 | 点击"嗅探模型"按钮 | 返回模型列表，每条含 modelId、ownedBy、alreadyExists 标记 | P1 |
| MC-024 | 批量导入嗅探结果 | 嗅探后选择多个模型并导入 | 批量创建成功，新模型出现在列表中 | P1 |
| MC-025 | 嗅探已断连的接入组 | BaseURL 不可达时嗅探 | 返回空列表或友好错误提示 | P2 |

---

### 3.2 知识库管理模块

| ID | 用例名称 | 操作步骤 | 预期结果 | 优先级 |
|----|---------|---------|---------|-------|
| KB-001 | 创建知识库 | 填写名称+描述 → 保存 | 创建成功，卡片列表出现新知识库 | P0 |
| KB-002 | 创建时名称为空 | 名称留空 | 前端校验拦截 | P1 |
| KB-003 | 编辑知识库 | 修改名称/描述 | 更新成功 | P1 |
| KB-004 | 删除空知识库 | 知识库下无文档时删除 | 删除成功 | P1 |
| KB-005 | 删除含文档的知识库 | 知识库下有文档时删除 | 需确认提示，删除后文档和向量数据同步清除 | P0 |
| KB-006 | 设置默认知识库 | 点击"设为需求文件默认存储库" | 标记更新，全局仅一个默认 | P1 |
| KB-007 | 取消默认知识库 | 取消默认标记 | 标记移除 | P2 |
| KB-008 | 知识库状态筛选 | 切换"活跃/归档"筛选 | 列表正确过滤 | P2 |
| KB-009 | 文档迁移 | 选择目标知识库，勾选文档，执行迁移 | 迁移成功，源知识库文档减少，目标增加，向量数据重新归属 | P0 |
| KB-010 | 迁移时显示影响说明 | 点击迁移前 | 弹出迁移影响说明弹窗 | P1 |
| KB-011 | 概览统计 | 查看知识库列表顶部统计 | 总数/活跃数/文档总量/分块总量数字准确 | P2 |

---

### 3.3 文档管理模块

| ID | 用例名称 | 操作步骤 | 预期结果 | 优先级 |
|----|---------|---------|---------|-------|
| DOC-001 | 上传 PDF 文档 | 选择 PDF 文件上传 | 上传成功，状态先为 `indexing`，后变为 `indexed` | P0 |
| DOC-002 | 上传 Word 文档 | 选择 .docx 文件上传 | 同上 | P0 |
| DOC-003 | 上传 Excel 文档 | 选择 .xlsx 文件上传 | 同上 | P1 |
| DOC-004 | 上传文本文件 | 选择 .txt/.md 文件上传 | 同上 | P1 |
| DOC-005 | 上传不支持格式 | 选择 .exe 文件 | 前端/后端拒绝，提示格式不支持 | P1 |
| DOC-006 | 上传超大文件 | 上传 >100MB 文件 | 超出大小限制提示或异步处理不阻塞 | P1 |
| DOC-007 | 文档索引状态流转 | 上传后观察状态 | `stored` → `indexing` → `indexed`（或 `failed`） | P0 |
| DOC-008 | 索引失败后重传 | 对 `failed` 状态文档点击"重传" | 状态重置为 `indexing`，重新处理 | P0 |
| DOC-009 | 跳过索引 | 对 `indexing` 卡死文档点击"跳过索引" | 状态变为 `stored`，保留预览/下载能力 | P1 |
| DOC-010 | 删除单个文档 | 点击删除并确认 | 文档删除，向量数据同步清除 | P0 |
| DOC-011 | 批量删除 | 勾选多文档 → 批量删除 | 批量删除成功，返回 deleted 数量 | P1 |
| DOC-012 | 批量下载 ZIP | 勾选多文档 → 批量下载 | 下载 ZIP 文件，解压后文件完整 | P1 |
| DOC-013 | 单文档下载 | 点击下载按钮 | 文件正确下载 | P0 |
| DOC-014 | 生成分享链接 | 点击分享，默认 24h 有效期 | 返回可分享 URL + 二维码 | P0 |
| DOC-015 | 分享链接有效期 | 设置 1h 有效期 | 1h 后访问返回过期提示 | P1 |
| DOC-016 | 一次性访问链接 | 开启 oneTimeAccess | 第一次访问正常，第二次返回已使用提示 | P1 |
| DOC-017 | 需登录访问 | 开启 requireLogin | 未登录用户跳转登录页 | P1 |
| DOC-018 | 高级筛选 | 按文件名/状态/时间范围筛选 | 筛选结果正确 | P2 |
| DOC-019 | 需求引用查询 | 查看文档的需求关联 | 返回关联的需求列表 | P2 |
| DOC-020 | 处理日志时间线 | 查看文档索引处理日志 | 时间线显示解析/分块/向量化各阶段 | P2 |

---

### 3.4 全局语义检索模块

| ID | 用例名称 | 操作步骤 | 预期结果 | 优先级 |
|----|---------|---------|---------|-------|
| GS-001 | 混合模式检索 | 搜索模式=hybrid，输入问题 | 返回结果，混合语义+关键词匹配 | P0 |
| GS-002 | 语义检索模式 | 搜索模式=semantic | 结果侧重语义相似度 | P0 |
| GS-003 | 关键词检索模式 | 搜索模式=keyword | 结果侧重关键词精确匹配 | P0 |
| GS-004 | 跨知识库搜索 | 不指定 knowledgeBaseId | 所有知识库的文档均参与检索 | P0 |
| GS-005 | 指定知识库搜索 | 选择特定知识库 | 仅返回该知识库的结果 | P1 |
| GS-006 | TopK 调整 | 分别设置 TopK=5/10/20 | 返回结果数量符合限制 | P1 |
| GS-007 | AI 摘要回答 | 选择 Chat 模型后搜索 | 返回 answer 字段，内容为 LLM 生成的摘要 | P0 |
| GS-008 | 无模型时检索 | 未选 Chat 模型搜索 | 仅返回检索结果，无 AI 摘要 | P1 |
| GS-009 | 无结果搜索 | 搜索完全无关内容 | 返回空结果集，前端显示"未检索到相关内容" | P1 |
| GS-010 | 批量下载命中文档 | 搜索后勾选结果批量下载 | 下载 ZIP 包含命中文档 | P2 |

---

### 3.5 RAG 问答工作台（核心模块）

#### 3.5.1 三栏布局与导航

| ID | 用例名称 | 操作步骤 | 预期结果 | 优先级 |
|----|---------|---------|---------|-------|
| RAG-001 | 三栏布局渲染 | 页面加载 | 左侧知识库导航 + 中间对话区 + 右侧证据面板 正常显示 | P0 |
| RAG-002 | 侧边栏收起/展开 | 点击收起/展开按钮 | 侧边栏隐藏/显示，中间区域自适应宽度 | P1 |
| RAG-003 | 知识库卡片列表 | 加载页面 | 显示所有活跃知识库，含名称/状态/文档数/分块数 | P0 |
| RAG-004 | 选择知识库 | 点击某个知识库卡片 | 卡片高亮，对话区标题更新，自动创建/恢复会话 | P0 |
| RAG-005 | 无知识库时提示 | 无任何知识库时 | 显示"暂无知识库，请先创建"引导 | P1 |

#### 3.5.2 会话管理

| ID | 用例名称 | 操作步骤 | 预期结果 | 优先级 |
|----|---------|---------|---------|-------|
| RAG-006 | 新建会话 | 点击"新建"按钮 | 创建新会话，标题"新对话"，消息列表为空 | P0 |
| RAG-007 | 切换会话 | 点击不同会话 | 对话区切换到对应会话的消息历史 | P0 |
| RAG-008 | 删除会话 | 点击删除按钮并确认 | 会话移除，自动切换到其他会话 | P1 |
| RAG-009 | 清空对话 | 点击"清空对话"并确认 | 当前会话消息清空，标题重置为"新对话" | P1 |
| RAG-010 | 会话标题自动更新 | 首次提问后 | 标题从"新对话"变为提问内容前 18 字 | P1 |
| RAG-011 | 会话持久化 | 刷新页面后 | 会话列表从 localStorage 恢复 | P0 |
| RAG-012 | 切换知识库时会话隔离 | 从知识库A切到B | 显示B的会话，A的会话保留 | P0 |

#### 3.5.3 模型选择

| ID | 用例名称 | 操作步骤 | 预期结果 | 优先级 |
|----|---------|---------|---------|-------|
| RAG-013 | 模型选择器展示 | 点击模型选择按钮 | 按 Provider 分组的二级菜单，左侧接入组列表，右侧模型列表 | P0 |
| RAG-014 | 选择模型 | 点击某个模型 | 按钮文字更新为模型名，后续提问使用该模型 | P0 |
| RAG-015 | 默认模型预选 | 页面加载 | 自动选中 isDefault=true 的模型 | P1 |
| RAG-016 | 无可用模型提示 | 所有模型禁用或无 Chat 类型 | 显示"未配置模型"，composer-tip 提示"当前未加载到可用问答模型" | P0 |
| RAG-017 | 选中模型被禁用 | 外部禁用了当前选中模型 | 重新加载后自动切换到下一个可用模型 | P1 |
| RAG-018 | Provider 切换 | 点击不同接入组 | 右侧模型列表更新 | P1 |

#### 3.5.4 检索参数

| ID | 用例名称 | 操作步骤 | 预期结果 | 优先级 |
|----|---------|---------|---------|-------|
| RAG-019 | 搜索模式切换 | 切换 hybrid/semantic/keyword | 下次提问使用新模式 | P0 |
| RAG-020 | TopK 切换 | 切换 Top 5/10/20 | 下次提问使用新 TopK 值 | P1 |
| RAG-021 | 上下文轮数设置 | 选择"上下文 2 轮" | 后续提问携带最近 2 轮历史 QA | P0 |
| RAG-022 | 单轮检索模式 | 选择"单轮" | 提问不携带历史上下文 | P0 |
| RAG-023 | 上下文轮数显示 | 观察会话卡片 | 显示"上下文 X 轮"或"单轮检索" | P2 |

#### 3.5.5 提问与流式回答（核心流程）

| ID | 用例名称 | 操作步骤 | 预期结果 | 优先级 |
|----|---------|---------|---------|-------|
| RAG-024 | 正常提问（流式） | 输入问题 → Enter/点击发送 | 1. 用户消息显示在右侧<br>2. 显示"检索中"loading<br>3. 流式回答逐字渲染<br>4. 完成后显示证据数量和模型信息 | P0 |
| RAG-025 | 正常提问（非流式降级） | 流式接口失败 | 自动降级为非流式请求，提示"流式输出不可用，已切换为普通回答" | P0 |
| RAG-026 | 全链路失败降级 | 非流式也失败 | 显示红色错误气泡，含思考步骤和排查建议 | P0 |
| RAG-027 | 空问题提交 | 不输入内容直接发送 | 提示"请输入检索问题" | P1 |
| RAG-028 | 未选知识库提交 | 未选择知识库时发送 | 提示"请先选择知识库"，输入框禁用 | P0 |
| RAG-029 | 提问时禁用输入 | 提问进行中 | 输入框和发送按钮禁用，防止重复提交 | P0 |
| RAG-030 | Enter 发送 | 按 Enter | 发送问题 | P1 |
| RAG-031 | Shift+Enter 换行 | 按 Shift+Enter | 输入框换行，不发送 | P1 |
| RAG-032 | 无模型时提问 | 不选模型发送 | 仅返回检索证据摘要，无 LLM 生成回答 | P0 |
| RAG-033 | 有模型时提问 | 选择了 Chat 模型 | 返回 LLM 生成的结构化回答 + 检索证据 | P0 |
| RAG-034 | 多轮上下文问答 | 连续追问 3 次 | 第 2、3 次提问携带历史上下文，回答体现上下文连贯性 | P0 |
| RAG-035 | 复制提问内容 | 点击用户消息的复制按钮 | 内容复制到剪贴板，提示"已复制" | P2 |
| RAG-036 | 长问题输入 | 输入 500+ 字问题 | 输入框自动扩高，提交正常 | P2 |

#### 3.5.6 证据面板（右侧 Insight）

| ID | 用例名称 | 操作步骤 | 预期结果 | 优先级 |
|----|---------|---------|---------|-------|
| RAG-037 | 关键问题总结 | 提问后观察右侧面板 | 显示 processSummary，描述检索范围和结果数量 | P0 |
| RAG-038 | 模型思考摘要 | 观察思考步骤 | 显示 5 个步骤：问题聚焦→上下文处理→证据召回→证据整合→输出总结 | P0 |
| RAG-039 | 关键点提取 | 观察关键点列表 | 显示 ≤4 条核心要点，内容与问题相关 | P1 |
| RAG-040 | 涉及文件列表 | 观察证据文件区 | 每个文件显示：文件名、片段数、章节、页码、相关度百分比 | P0 |
| RAG-041 | 证据文件预览 | 点击某个证据文件 | 弹出 FilePreviewDialog，支持在线预览 | P0 |
| RAG-042 | 点击不同回答切换证据 | 点击历史回答气泡 | 右侧面板切换到该回答的证据 | P1 |
| RAG-043 | 无证据时提示 | 检索无结果 | 显示"当前回答未返回可预览文件" | P1 |
| RAG-044 | 相关度百分比 | 观察证据文件 | 显示 `score * 100` 的整数百分比 | P2 |

#### 3.5.7 工作区状态持久化

| ID | 用例名称 | 操作步骤 | 预期结果 | 优先级 |
|----|---------|---------|---------|-------|
| RAG-045 | 刷新后恢复知识库选择 | 刷新页面 | 自动恢复到上次选择的知识库 | P1 |
| RAG-046 | 刷新后恢复会话 | 刷新页面 | 会话列表和消息历史从 localStorage 恢复 | P0 |
| RAG-047 | 刷新后恢复模型选择 | 刷新页面 | 自动恢复到上次选择的模型 | P1 |
| RAG-048 | 知识库被外部删除后恢复 | 上次选中的知识库已被删除 | 自动切换到第一个可用知识库 | P1 |

---

### 3.6 公开分享模块

| ID | 用例名称 | 操作步骤 | 预期结果 | 优先级 |
|----|---------|---------|---------|-------|
| SH-001 | 访问有效分享链接 | 打开分享 URL | 跳转到文档预览页，可预览/下载 | P0 |
| SH-002 | 访问过期链接 | 超过有效期后访问 | 提示链接已过期 | P1 |
| SH-003 | 一次性链接二次访问 | 第二次访问 | 提示链接已使用 | P1 |
| SH-004 | 需登录链接未登录访问 | 未登录时访问 | 跳转登录页 | P1 |
| SH-005 | Office 文件在线预览 | 分享 Word/Excel/PPT | kkFileView 加载预览 | P1 |
| SH-006 | 图片文件预览 | 分享图片文件 | 内置图片预览器 | P2 |
| SH-007 | 二维码显示 | 生成分享链接 | 同时显示二维码 | P2 |

---

## 四、API 测试用例

### 4.1 知识库检索 API

#### `POST /api/v1/knowledge/search`

| ID | 场景 | 请求体 | 预期状态码 | 预期响应 |
|----|------|-------|-----------|---------|
| API-001 | 正常混合检索 | `{ "query": "需求管理流程", "mode": "hybrid", "topK": 10 }` | 200 | results 数组非空，total > 0 |
| API-002 | 语义检索 | `{ "query": "需求管理流程", "mode": "semantic" }` | 200 | results 按 score 降序 |
| API-003 | 关键词检索 | `{ "query": "需求管理流程", "mode": "keyword" }` | 200 | results 包含关键词匹配 |
| API-004 | 指定知识库 | `{ "query": "xxx", "knowledgeBaseId": 1 }` | 200 | results 均属于该知识库 |
| API-005 | 指定模型 | `{ "query": "xxx", "llmModelId": 1 }` | 200 | answer 字段非空（LLM 生成） |
| API-006 | 空 query | `{ "query": "" }` | 400 | 校验错误"检索内容不能为空" |
| API-007 | query 为 null | `{ }` | 400 | 校验错误 |
| API-008 | 不存在的知识库 ID | `{ "query": "xxx", "knowledgeBaseId": 99999 }` | 200/404 | 空结果或 404 |
| API-009 | 不存在的模型 ID | `{ "query": "xxx", "llmModelId": 99999 }` | 200/500 | 降级为无模型回答或返回错误 |
| API-010 | 无效 mode | `{ "query": "xxx", "mode": "invalid" }` | 200/400 | 降级为默认模式或校验拒绝 |
| API-011 | TopK=1 | `{ "query": "xxx", "topK": 1 }` | 200 | results 最多 1 条 |
| API-012 | TopK=100 | `{ "query": "xxx", "topK": 100 }` | 200 | 结果不超过实际匹配数 |
| API-013 | 未认证 | 不带 Authorization | 401 | 未授权 |
| API-014 | 超长 query | query 为 10000 字 | 200/413 | 正常处理或提示过长 |

#### `POST /api/v1/knowledge/search/stream`

| ID | 场景 | 预期 SSE 事件流 |
|----|------|----------------|
| API-015 | 正常流式检索 | `event: results` → `event: delta`(多次) → `event: done` |
| API-016 | 流式 + 模型 | results 事件含检索结果，delta 事件逐步输出 LLM 回答 |
| API-017 | 流式无模型 | results 事件后直接 done，无 delta 事件 |
| API-018 | 流式错误 | `event: error`，data 含错误消息 |
| API-019 | 流式中途断连 | 客户端断开后服务端资源释放，无内存泄漏 |

### 4.2 模型配置 API

#### `POST /api/v1/llm-providers/{id}/models/{modelId}/test`

| ID | 场景 | 预期结果 |
|----|------|---------|
| API-020 | 测试有效模型 | `{ success: true, content: "...", durationMs: 1200, totalTokens: 150 }` |
| API-021 | 测试无效 APIKey | `{ success: false, errorMessage: "Unauthorized..." }` |
| API-022 | 测试不可达 BaseURL | `{ success: false, errorMessage: "Connection refused..." }` |
| API-023 | 超时处理 | 60s 超时后返回失败结果 |

#### `POST /api/v1/llm-providers/{id}/sniff-models`

| ID | 场景 | 预期结果 |
|----|------|---------|
| API-024 | 正常嗅探 | 返回 SniffedModel 数组 |
| API-025 | 不可达服务 | 返回空数组或 500 错误 |
| API-026 | 已存在的模型标记 alreadyExists | alreadyExists=true 的模型在导入时跳过或更新 |

### 4.3 知识库管理 API

| ID | 端点 | 场景 | 预期结果 |
|----|------|------|---------|
| API-027 | `POST /v1/knowledge/bases` | 正常创建 | 201，返回含 id 的知识库对象 |
| API-028 | `POST /v1/knowledge/bases` | 名称重复 | 409 或创建成功（看业务规则） |
| API-029 | `GET /v1/knowledge/bases/all` | 获取全部 | 200，返回数组 |
| API-030 | `DELETE /v1/knowledge/bases/{id}` | 删除含文档的知识库 | 需确认级联删除策略 |
| API-031 | `POST /v1/knowledge/bases/{id}/migrate` | 正常迁移 | 返回 migratedDocuments 和 migratedChunks |
| API-032 | `PATCH /v1/knowledge/bases/{id}/set-default` | 设为默认 | 成功，其他默认取消 |

### 4.4 文档管理 API

| ID | 端点 | 场景 | 预期结果 |
|----|------|------|---------|
| API-033 | `POST /v1/knowledge/bases/{kbId}/documents/upload` | 上传 PDF | 201，返回文档对象 |
| API-034 | 同上 | 上传空文件 | 400 |
| API-035 | 同上 | 上传超大文件 | 413 或异步接受 |
| API-036 | `DELETE /v1/knowledge/bases/{kbId}/documents/{docId}` | 正常删除 | 200 |
| API-037 | `POST /v1/knowledge/bases/{kbId}/documents/retry` | 批量重传 | `{ retried: N }` |
| API-038 | `POST /v1/knowledge/bases/{kbId}/documents/{docId}/skip-indexing` | 跳过索引 | 200 |
| API-039 | `POST /v1/knowledge/bases/{kbId}/documents/batch-delete` | 批量删除 | `{ deleted: N }` |
| API-040 | `POST /v1/knowledge/bases/{kbId}/documents/batch-download` | 批量下载 | 返回 ZIP blob |
| API-041 | `POST .../share?expireHours=1&oneTimeAccess=true` | 生成一次性链接 | 返回 token/URL |

---

## 五、边界与异常测试

| ID | 场景 | 操作 | 预期行为 | 优先级 |
|----|------|-----|---------|-------|
| EDG-001 | Milvus 服务宕机 | 停止 Milvus 后提问 | 检索失败，显示友好错误，不暴露堆栈 | P0 |
| EDG-002 | Embedding 服务不可用 | 配置错误的 Embedding 模型 | 文档索引失败，状态变为 `failed`，错误信息可追溯 | P0 |
| EDG-003 | LLM 服务超时 | 模型响应 >60s | 超时错误提示，不阻塞 UI | P0 |
| EDG-004 | LLM 返回空回答 | 模型返回空 content | 前端降级显示检索证据摘要 | P0 |
| EDG-005 | RabbitMQ 宕机 | 停止 RabbitMQ | 上传成功但索引不触发，文档卡在 `stored` 状态 | P0 |
| EDG-006 | MinIO 不可用 | 停止 MinIO | 上传失败，错误提示 | P0 |
| EDG-007 | 并发提问 | 快速连续发送 5 个问题 | 请求排队处理，无竞态条件，每个回答对应正确问题 | P1 |
| EDG-008 | 网络断开后重连 | 提问中断网 | SSE 连接断开，显示错误；网络恢复后可重新提问 | P1 |
| EDG-009 | Token 过期 | 操作中 Token 过期 | 401 拦截，跳转登录页 | P0 |
| EDG-010 | 空知识库提问 | 知识库无文档时提问 | 返回"未检索到相关文档片段" | P1 |
| EDG-011 | 特殊字符问题 | 输入 `<script>alert(1)</script>` | 不执行脚本，内容纯文本显示 | P0 |
| EDG-012 | 超长回答 | LLM 生成 5000+ 字回答 | 内容正常显示，可滚动 | P2 |
| EDG-013 | 大量会话数据 | localStorage 存 100+ 会话 | 性能无明显下降 | P2 |
| EDG-014 | 文档索引超时卡死 | 文档索引超过阈值 | `KnowledgeDocumentTimeoutChecker` 自动标记超时 | P1 |

---

## 六、集成测试（端到端流程）

| ID | 端到端流程 | 步骤 | 预期结果 |
|----|----------|-----|---------| 
| E2E-001 | **完整 RAG 问答流程** | 1. 配置 LLM 接入组+模型并测试通过<br>2. 创建知识库<br>3. 上传 3 份文档并等待索引完成<br>4. 进入 RAG 工作台选择知识库和模型<br>5. 提问并验证流式回答<br>6. 检查证据面板 | 全流程无阻断，回答内容与上传文档相关 |
| E2E-002 | **模型切换问答对比** | 1. 用模型 A 提问<br>2. 切换模型 B 再问同样问题<br>3. 对比两次回答 | 两次回答均正常，证据相同但回答风格/质量可能不同 |
| E2E-003 | **文档迁移后检索** | 1. 知识库 A 有文档<br>2. 迁移到知识库 B<br>3. 在知识库 B 中检索原内容 | 迁移后内容可正常检索到 |
| E2E-004 | **多轮上下文追问** | 1. 提问"需求审批流程是什么"<br>2. 追问"它的超时机制呢" | 第二次回答理解"它"指代"需求审批流程" |
| E2E-005 | **分享→预览→下载** | 1. 生成分享链接<br>2. 无痕窗口打开链接<br>3. 预览文件<br>4. 下载文件 | 全流程正常 |
| E2E-006 | **模型禁用→工作台适配** | 1. 在模型配置页禁用当前使用的模型<br>2. 返回 RAG 工作台<br>3. 重新提问 | 模型自动切换到可用模型或提示选择 |

---

## 七、性能测试要点

| 指标 | 目标 | 测试方法 |
|------|-----|---------|
| 页面首次加载（FCP） | < 2s | Lighthouse / Chrome DevTools |
| 流式首 token 延迟 | < 3s | 从发送到首次 onDelta 回调 |
| 非流式检索完整响应 | < 8s | API 响应时间 |
| 100 文档知识库检索 | < 5s | API 压测 |
| 1000 文档知识库检索 | < 10s | API 压测 |
| 会话恢复（50 条消息） | < 500ms | localStorage 读取 + 渲染 |
| 并发 10 用户检索 | 无 500 错误 | k6 / Artillery |

---

## 八、可访问性测试（A11y）

| ID | 检查项 | 标准 | 优先级 |
|----|-------|------|-------|
| A11y-001 | 键盘可操作：Tab 遍历所有交互元素 | WCAG 2.1 AA | P1 |
| A11y-002 | Enter 发送、Shift+Enter 换行可用 | 功能性 | P1 |
| A11y-003 | 颜色对比度 ≥ 4.5:1（文本/背景） | WCAG 2.1 AA | P1 |
| A11y-004 | 证据文件相关度百分比有文字说明 | 非纯视觉传达 | P2 |
| A11y-005 | loading 状态有 aria-live 区域 | 屏幕阅读器可感知 | P2 |
| A11y-006 | 模型选择器可键盘导航 | 可访问性 | P2 |

---

## 九、视觉回归测试要点

| 页面/组件 | 关键视觉检查点 | 浏览器覆盖 |
|-----------|-------------|-----------|
| RAG 工作台-空状态 | 三栏空态布局、提示文案 | Chrome/Safari/Firefox |
| RAG 工作台-对话中 | 消息气泡、流式打字效果、loading 动画 | Chrome |
| 证据面板 | 关键点列表、文件卡片、相关度标签 | Chrome |
| 知识库管理 | 卡片列表、统计数字、迁移弹窗 | Chrome |
| 模型配置 | 接入组列表、模型表格、测试结果 Drawer | Chrome |
| 移动端适配 | 侧边栏折叠、对话区全宽 | Chrome Mobile |

---

## 十、测试执行优先级与建议

### 第一轮：冒烟测试（P0 用例，~30 条）

确保核心链路跑通：
1. **模型配置**：MC-001, MC-011, MC-018
2. **知识库**：KB-001, KB-005
3. **文档**：DOC-001, DOC-007, DOC-010
4. **RAG 问答**：RAG-024, RAG-025, RAG-026, RAG-028, RAG-032, RAG-033
5. **API**：API-001, API-006, API-013, API-015, API-020
6. **异常**：EDG-001, EDG-003, EDG-009, EDG-011
7. **E2E**：E2E-001

### 第二轮：完整功能测试（P0+P1，~80 条）

覆盖所有正常流程和主要边界场景。

### 第三轮：边界/异常/性能（P1+P2，~40 条）

深入边界值、并发、性能基线。

### 自动化建议

| 层级 | 工具 | 覆盖范围 |
|------|------|---------|
| API 自动化 | Playwright API / Vitest | 全部 API 用例（API-001 ~ API-041） |
| E2E 自动化 | Playwright | E2E-001 ~ E2E-006 + 核心 RAG 交互 |
| 视觉回归 | Playwright screenshots | 6 个关键页面/组件 |
| 性能基线 | k6 | 检索 API 压测 |

---

## 附录 A：关键 API 速查

| 方法 | 路径 | 说明 |
|------|-----|------|
| POST | `/api/v1/knowledge/search` | 普通语义检索 |
| POST | `/api/v1/knowledge/search/stream` | SSE 流式检索 |
| GET | `/api/v1/knowledge/stats` | 知识库模块状态 |
| GET | `/api/v1/knowledge/bases` | 知识库分页列表 |
| GET | `/api/v1/knowledge/bases/all` | 知识库全部列表 |
| POST | `/api/v1/knowledge/bases` | 创建知识库 |
| PUT | `/api/v1/knowledge/bases/{id}` | 更新知识库 |
| DELETE | `/api/v1/knowledge/bases/{id}` | 删除知识库 |
| POST | `/api/v1/knowledge/bases/{id}/migrate` | 文档迁移 |
| PATCH | `/api/v1/knowledge/bases/{id}/set-default` | 设为默认 |
| POST | `/api/v1/knowledge/bases/{kbId}/documents/upload` | 上传文档 |
| GET | `/api/v1/knowledge/bases/{kbId}/documents` | 文档列表 |
| DELETE | `/api/v1/knowledge/bases/{kbId}/documents/{docId}` | 删除文档 |
| POST | `/api/v1/knowledge/bases/{kbId}/documents/retry` | 批量重传 |
| POST | `/api/v1/knowledge/bases/{kbId}/documents/batch-delete` | 批量删除 |
| POST | `/api/v1/knowledge/bases/{kbId}/documents/batch-download` | 批量下载 |
| POST | `/api/v1/knowledge/bases/{kbId}/documents/{docId}/share` | 生成分享链接 |
| GET | `/api/v1/llm-providers` | 接入组列表 |
| POST | `/api/v1/llm-providers` | 创建接入组 |
| PUT | `/api/v1/llm-providers/{id}` | 更新接入组 |
| DELETE | `/api/v1/llm-providers/{id}` | 删除接入组 |
| PATCH | `/api/v1/llm-providers/{id}/toggle` | 启停接入组 |
| POST | `/api/v1/llm-providers/{id}/models` | 添加模型 |
| PATCH | `/api/v1/llm-providers/{pid}/models/{mid}/toggle` | 启停模型 |
| POST | `/api/v1/llm-providers/{pid}/models/{mid}/test` | 测试模型 |
| POST | `/api/v1/llm-providers/{id}/sniff-models` | 嗅探模型 |

## 附录 B：数据流图

```
用户提问
   │
   ▼
[RAG 工作台前端]
   │ streamSearchKnowledge({ query, mode, knowledgeBaseId, topK, llmModelId })
   ▼
POST /api/v1/knowledge/search/stream (SSE)
   │
   ▼
[KnowledgeSearchController]
   │
   ▼
[KnowledgeSearchService]
   ├─→ [EmbeddingService] → LlmGateway.chat(embedding) → 向量化 query
   ├─→ [MilvusVectorStore.search()] → 向量检索 + 关键词检索
   ├─→ [LlmGateway.rerank()] → Reranker 重排序
   ├─→ SSE event: results → 前端渲染证据列表
   ├─→ [RagAnswerService.streamAnswer()] → LlmGateway.streamChat()
   │    ├─→ SSE event: delta (逐字输出)
   │    └─→ SSE event: done (完整回答)
   └─→ 异常 → SSE event: error → 前端降级处理
```
