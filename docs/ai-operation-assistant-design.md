# 系统操作问答助手功能规划设计

## 1. 设计目标

在现有需求管理平台中增加一个全局“操作问答助手”，解决用户“不知道去哪里、下一步怎么做、某个功能如何操作”的问题。助手以悬浮图标作为入口，支持多轮对话，并结合系统已配置的 Chat LLM、系统操作目录和现有知识库/RAG 能力，输出：

1. **功能导航**：告诉用户应进入哪个菜单、点击哪个入口，并提供“立即前往”。
2. **操作指引**：按当前页面和用户角色给出步骤、前置条件、权限说明。
3. **系统问答**：回答需求、迭代、评审、工作流、多维表格、知识库、统计等模块的使用问题。
4. **上下文追问**：当用户意图不完整时，先追问项目、需求、角色或目标，再给出准确指引。
5. **受控操作建议**：第一阶段只导航和打开页面/弹窗，不直接修改业务数据；需要写操作时必须明确展示并二次确认。

> 定位：不是一个开放式聊天机器人，而是“懂当前系统、懂用户权限、能把用户带到正确操作位置”的系统操作助手。

## 2. 当前项目可复用能力

当前项目已经具备较完整的基础设施，不建议重新建设一套 RAG 或 LLM 链路：

| 现有能力 | 复用方式 |
|---|---|
| Vue 3 + TypeScript + Element Plus | 全局悬浮入口、抽屉式对话面板、操作卡片 |
| `DefaultLayout.vue` | 挂载全局助手，保证登录后所有业务页面可用 |
| 路由和动态菜单 | 生成导航目标，并结合用户权限过滤 |
| `usePermission`、路由守卫 | 导航前二次校验权限，避免模型越权引导 |
| `knowledge` 模块 | 检索系统操作手册、FAQ、业务制度和历史文档 |
| `IntentRecognizer` | 复用意图识别思路，新增“系统操作导航”类意图 |
| `RagAnswerService`、`LlmGateway` | 复用已配置的 Provider/Model 和 SSE 流式输出 |
| `llm_providers`、`llm_models` | 使用系统已配置的 Chat 模型，不在前端暴露 API Key |
| 当前业务模块 | 建立需求、迭代、评审、工作流、多维表格、知识库、统计等操作目录 |

当前主要业务入口包括：

- `/dashboard`：仪表盘
- `/requirements`：需求管理
- `/iterations`：迭代管理
- `/reviews`：评审管理
- `/bitable`：多维表格
- `/statistics`：统计报表
- `/settings/rag`：RAG 文档中心
- `/settings/knowledge`：知识库管理
- `/settings`、`/settings/llm`：系统配置和模型配置
- `/notifications`：通知中心
- `/system/workflow-config`、`/system/workflow-migration`：工作流配置与迁移

## 3. 产品形态

### 3.1 全局悬浮入口

- 位置：登录后页面右下角，避开表格分页、对话框和浏览器边缘。
- 形态：圆形 AI 图标，悬停显示“操作助手”。
- 状态：
  - 默认：静态图标；
  - 有未完成追问：显示小红点；
  - 生成中：显示呼吸动画；
  - 当前页面有推荐动作：首次进入页面时可显示一次轻提示。
- 可拖动：允许用户调整垂直位置，位置保存到浏览器本地。
- 快捷键：建议支持 `Ctrl/Cmd + K` 打开或聚焦助手输入框。

### 3.2 对话面板

建议使用右侧 `el-drawer`，宽度 420~520px，避免遮挡主业务内容；桌面端可升级为可调整宽度的浮层，移动端使用全屏抽屉。

面板结构：

1. 顶部：助手名称、当前页面标签、新建会话、会话历史、设置。
2. 首屏快捷问题：
   - “我现在能做什么？”
   - “如何新建一个需求？”
   - “如何把需求加入迭代？”
   - “如何发起评审？”
   - “这个页面的字段怎么填？”
3. 消息区：用户消息、助手回答、步骤列表、引用来源、操作卡片。
4. 底部输入区：多行输入、发送、停止生成、清空上下文。
5. 回答底部：有帮助/没帮助、复制、重新生成、查看来源。

### 3.3 回答形态

助手回答不应只返回一段自然语言，应返回“自然语言 + 结构化操作建议”：

```json
{
  "answer": "创建需求需要先进入需求管理，再点击右上角“新建需求”。填写标题、描述和所属项目后保存。",
  "intent": "CREATE_REQUIREMENT",
  "confidence": 0.96,
  "actions": [
    {
      "type": "NAVIGATE",
      "label": "进入需求管理",
      "route": "/requirements",
      "query": {},
      "requiresConfirmation": false
    },
    {
      "type": "NAVIGATE",
      "label": "直接新建需求",
      "route": "/requirements/create",
      "query": { "source": "assistant" },
      "requiresConfirmation": false
    }
  ],
  "sources": [
    { "type": "OPERATION_CATALOG", "code": "requirement.create" },
    { "type": "KNOWLEDGE", "documentId": 12, "title": "需求管理操作手册" }
  ]
}
```

操作卡片类型建议先支持：

| 类型 | 作用 | MVP |
|---|---|---|
| `NAVIGATE` | 跳转到白名单路由 | 支持 |
| `HIGHLIGHT` | 高亮当前页面的按钮、字段或区域 | 支持，需页面埋点 |
| `OPEN_DIALOG` | 打开页面内指定弹窗 | 第二阶段 |
| `FILTER` | 带查询条件进入列表页 | 第二阶段 |
| `EXECUTE` | 执行业务写操作 | 第三阶段，默认关闭 |
| `NONE` | 仅回答，不提供动作 | 支持 |

## 4. 核心使用场景

### 场景 A：不知道入口

用户：“我想新建一个需求。”

助手：识别为 `CREATE_REQUIREMENT`，说明前置条件，给出 `/requirements/create` 的“立即新建”按钮；若用户无创建权限，则提示联系管理员，并给出需求列表查看入口，而不是返回无效按钮。

### 场景 B：当前页面操作指导

用户在需求详情页：“这个需求怎么提交评审？”

助手读取当前路由、需求 ID、用户权限和页面可用操作，回答评审前置条件、按钮位置、提交后的状态变化，并提供高亮/打开评审弹窗动作。

### 场景 C：多轮澄清

用户：“帮我处理一下这个。”

助手不猜测写操作，追问：“你希望对当前需求执行什么操作？可以选择：提交评审、加入迭代、关联需求、上传附件。”用户选择后再给出动作。

### 场景 D：知识库问答

用户：“评审通过后需求会进入什么状态？”

助手先检索系统操作手册和工作流配置，再结合当前项目工作流回答，并显示引用文档；没有可靠证据时明确说明“当前知识库没有找到确定依据”。

### 场景 E：权限和配置问题

用户：“为什么我看不到模型配置？”

助手根据权限上下文解释：模型配置属于系统配置菜单，通常需要管理员或对应菜单权限；提供“查看我的权限/联系管理员”的说明，不展示其他用户敏感信息。

## 5. 意图体系

建议不要把所有问题都直接交给通用 RAG，先做轻量意图路由：

| 意图 | 说明 | 处理策略 |
|---|---|---|
| `PAGE_NAVIGATION` | 去哪里找功能 | 操作目录 + 路由卡片 |
| `OPERATION_GUIDE` | 当前功能如何操作 | 当前页面上下文 + 操作目录 + 手册 RAG |
| `CREATE_REQUIREMENT` | 新建需求 | 需求模块操作目录 |
| `WORKFLOW_GUIDE` | 审批、评审、状态流转 | 工作流配置 + 手册 RAG |
| `DATA_QUERY` | 查找需求/迭代/统计数据 | 只读查询工具，MVP 可先导航 |
| `KNOWLEDGE_QA` | 制度、规范、文档问答 | 知识库 RAG |
| `TROUBLESHOOTING` | 报错、无权限、配置异常 | FAQ/错误码知识库 + 安全提示 |
| `ACTION_REQUEST` | 请求直接新增、修改、删除 | 进入确认流程，MVP 不直接执行 |
| `CHITCHAT` | 非业务闲聊 | 简短回复并引导回系统操作 |

建议复用现有 `IntentRecognizer` 的调用和降级模式，但为助手单独新增 `AssistantIntentService`，避免把“知识库检索意图”和“全局操作导航意图”强耦合。

## 6. 系统上下文设计

每次打开面板或发送消息时，前端向后端传递最小必要上下文：

```json
{
  "route": "/requirements/123",
  "routeName": "RequirementDetail",
  "pageTitle": "需求详情",
  "activeMenu": "/requirements",
  "entity": { "type": "requirement", "id": 123 },
  "pageCapabilities": [
    "requirement.view",
    "requirement.edit",
    "requirement.submit_review"
  ],
  "selectedProjectId": 10
}
```

上下文规则：

- 只发送页面路由、页面标题、业务对象类型/ID、可用能力编码等必要信息。
- 不发送 API Key、密码、完整表单内容、无关用户隐私和未授权数据。
- 后端必须重新从登录态和数据库校验权限，不能信任前端传来的 `pageCapabilities`。
- 对需求 ID、项目 ID 只作为查询上下文，实际数据读取仍走后端权限控制。
- 页面可通过 `data-assistant-key="requirement.submit-review"` 标记可高亮的控件。

## 7. 推荐技术架构

```mermaid
flowchart LR
  A[悬浮助手入口] --> B[AssistantPanel]
  B --> C[assistant store]
  C --> D[POST /assistant/sessions/{id}/messages/stream]
  D --> E[AssistantService]
  E --> F[权限与页面上下文校验]
  E --> G[IntentRouter]
  G --> H[OperationCatalog]
  G --> I[KnowledgeSearch/RAG]
  G --> J[LlmGateway + Chat Model]
  H --> K[结构化动作校验]
  I --> K
  J --> K
  K --> L[SSE meta/delta/actions/done]
  L --> B
  B --> M[router.push / 页面高亮 / 打开弹窗]
```

### 7.1 前端建议新增文件

```text
src/components/assistant/SystemAssistant.vue       # 全局悬浮入口 + 面板
src/components/assistant/AssistantMessage.vue      # 消息渲染、Markdown、引用、操作卡片
src/components/assistant/AssistantActionCard.vue   # 导航/高亮/确认卡片
src/components/assistant/AssistantQuickPrompts.vue # 快捷问题
src/api/modules/assistant.ts                       # 会话和流式问答 API
src/stores/assistant.ts                            # 会话、消息、流式状态
src/composables/useAssistantContext.ts             # 当前路由/页面能力上下文
src/types/assistant.ts                             # DTO、SSE 事件、动作类型
```

在 `src/layouts/DefaultLayout.vue` 中挂载：

```vue
<SystemAssistant v-if="userStore.userInfo" />
```

### 7.2 后端建议新增模块

```text
module/assistant/
├── controller/AssistantController.java
├── dto/AssistantChatRequest.java
├── dto/AssistantPageContext.java
├── dto/AssistantAction.java
├── dto/AssistantStreamEvent.java
├── entity/AssistantSession.java
├── entity/AssistantMessage.java
├── entity/AssistantOperation.java
├── mapper/AssistantSessionMapper.java
├── mapper/AssistantMessageMapper.java
├── mapper/AssistantOperationMapper.java
├── service/AssistantService.java
├── service/AssistantIntentRouter.java
├── service/AssistantOperationCatalogService.java
├── service/impl/AssistantServiceImpl.java
└── validator/AssistantActionValidator.java
```

### 7.3 推荐处理链路

1. 校验登录态、会话归属和消息长度。
2. 读取当前用户角色、权限、项目范围和当前路由。
3. 合并最近 6~10 轮会话摘要，不建议无限拼接历史消息。
4. 先匹配操作目录关键词和当前页面能力，低成本识别明确导航问题。
5. 需要解释或文档依据时，调用现有知识库检索。
6. 使用已启用的 Chat 模型生成自然语言回答和结构化动作。
7. 严格解析 JSON；解析失败时只保留文本回答，不执行动作。
8. 校验动作：路由白名单、权限、实体范围、危险操作确认标记。
9. 通过 SSE 返回 `meta`、`delta`、`actions`、`done`；保存会话和消息。

## 8. API 规划

### 8.1 会话

```http
GET    /api/v1/assistant/sessions
POST   /api/v1/assistant/sessions
GET    /api/v1/assistant/sessions/{sessionId}/messages
DELETE /api/v1/assistant/sessions/{sessionId}
```

### 8.2 流式问答

```http
POST /api/v1/assistant/sessions/{sessionId}/messages/stream
Accept: text/event-stream
```

请求：

```json
{
  "message": "如何把当前需求加入迭代？",
  "mode": "hybrid",
  "llmModelId": null,
  "pageContext": {
    "route": "/requirements/123",
    "routeName": "RequirementDetail",
    "pageTitle": "需求详情",
    "entity": { "type": "requirement", "id": 123 }
  }
}
```

SSE 事件：

```text
event: meta
data: {"messageId": "...", "intent": "OPERATION_GUIDE"}

event: delta
data: {"text": "可以先确认当前需求..."}

event: actions
data: {"actions": [{"type": "NAVIGATE", "label": "进入迭代管理", "route": "/iterations"}]}

event: done
data: {"messageId": "...", "sources": [], "usage": {"totalTokens": 0}}
```

### 8.3 反馈

```http
POST /api/v1/assistant/messages/{messageId}/feedback
```

记录 `helpful`、`reason`、`comment`，用于后续优化提示词和操作目录，不将用户原始对话直接作为训练数据。

## 9. 数据模型建议

第一阶段建议采用数据库持久化，保证多轮会话可恢复；消息正文和结构化元数据使用 JSON/TEXT，兼容当前 MySQL 设计。

### `assistant_sessions`

| 字段 | 说明 |
|---|---|
| `id` | 会话 ID |
| `user_id` | 所属用户 |
| `title` | 会话标题，可由首问截取 |
| `current_route` | 最近一次所在路由 |
| `context_json` | 会话摘要、偏好上下文 |
| `status` | active/archived |
| `created_at`、`updated_at` | 时间 |
| `deleted_at` | 软删除 |

### `assistant_messages`

| 字段 | 说明 |
|---|---|
| `id` | 消息 ID |
| `session_id` | 会话 ID |
| `role` | user/assistant/system |
| `content` | 文本内容 |
| `intent` | 识别出的意图 |
| `actions_json` | 结构化动作 |
| `sources_json` | 知识库/操作目录引用 |
| `model_id` | 实际使用的模型 ID |
| `token_usage_json` | token 和耗时 |
| `feedback` | helpful/unhelpful/null |
| `created_at` | 创建时间 |

### `assistant_operations`

作为系统操作目录，建议由管理员或开发版本化维护：

| 字段 | 说明 |
|---|---|
| `code` | 如 `requirement.create`、`requirement.submit-review` |
| `name` | 操作名称 |
| `module` | 模块编码 |
| `route` | 允许导航的路由 |
| `description` | 给模型看的操作说明 |
| `preconditions_json` | 前置条件 |
| `steps_json` | 操作步骤 |
| `permission_code` | 需要的权限 |
| `target_key` | 前端高亮标识 |
| `risk_level` | read/guide/write/danger |
| `enabled`、`version` | 是否启用和版本 |

## 10. Prompt 与模型约束

系统 Prompt 必须明确以下规则：

1. 只能基于系统操作目录、当前上下文和检索证据回答。
2. 不确定时先追问，不要编造菜单、按钮、接口或权限。
3. 路由只能从允许的操作目录中选择。
4. 写入、删除、提交、审批等动作默认只给建议，不直接执行。
5. 涉及权限时说明“可能需要某权限”，不能泄露其他用户权限详情。
6. 需要引用知识库时附带来源；没有证据时标记为“经验性建议”。
7. 严格输出 JSON 结构；前端展示文本使用 `answer`，动作使用 `actions`。

模型选择建议：

- MVP 默认复用现有 `general` 或默认 Chat 模型。
- 允许管理员在 `/settings/llm` 配置助手使用的模型，优先使用已启用且测试成功的模型。
- 复用现有 `/api/v1/llm-providers/chat-models` 获取可用模型，前端只显示名称，不接触 API Key。
- 后端记录实际模型和耗时，便于定位费用、超时和降级问题。
- 无可用模型时，助手仍可使用操作目录进行规则式导航；无模型不应导致整个入口不可用。

## 11. 权限、安全和可靠性

### 必须做

- 所有助手接口要求 `isAuthenticated()`，会话只能访问当前用户自己的记录。
- 后端重新校验路由和动作权限，不能只依赖 LLM 返回的 `route`。
- 路由动作采用白名单，禁止模型返回任意 URL、JavaScript 或外部跳转。
- 禁止把 API Key、密码、Token、完整表单秘密字段送入模型。
- 对话输入限制长度、频率和并发数；单用户同时只允许一个流式请求。
- SSE 断开时释放资源，保存已生成内容并标记消息为中断。
- LLM 超时、429、无模型时降级为规则导航或检索结果摘要。
- 对“删除、审批、提交、发布、批量修改”等动作明确显示风险和确认按钮。

### 不建议在 MVP 做

- 让模型直接调用任意后端 API。
- 允许模型自动创建、修改或删除需求。
- 把整个页面 DOM、全部列表数据或用户完整权限对象发送给模型。
- 仅凭模型自然语言自动执行路由跳转。

## 12. 分阶段实施计划

### P0：可用的操作导航助手

- 全局悬浮入口和抽屉式聊天面板。
- 会话/消息后端持久化。
- 使用默认 Chat 模型实现多轮 SSE 对话。
- 建立核心操作目录：需求、迭代、评审、工作流、多维表格、知识库、统计、模型配置。
- 支持 `NAVIGATE`、`NONE` 两种动作。
- 路由白名单和权限二次校验。
- 无模型时提供关键词规则导航兜底。

### P1：上下文和知识增强

- 接入当前页面、业务对象和页面能力上下文。
- 接入“系统操作手册”知识库，显示引用来源。
- 支持 `HIGHLIGHT`、`OPEN_DIALOG`、`FILTER`。
- 支持回答反馈、会话摘要、推荐问题。
- 增加助手管理页面：模型选择、是否启用 RAG、最大上下文轮数、回答风格。

### P2：受控业务协同

- 只读数据查询：查询需求状态、迭代进度、评审记录、统计指标。
- 对写操作生成预览变更，用户确认后再调用明确的白名单 Tool。
- 增加操作审计日志、失败重试、管理员关闭高风险动作。
- 根据反馈优化操作目录、FAQ 和意图识别。

## 13. 验收标准

### 功能验收

- 任意登录用户在业务页面都能打开悬浮助手，位置不遮挡主要操作。
- 可以新建、切换、删除自己的会话，并恢复历史消息。
- 对“如何新建需求/提交评审/加入迭代/配置模型”等问题，回答包含正确入口和可点击导航。
- 跳转前按当前用户权限校验；无权限时不展示或禁用动作，并给出原因。
- 当前页面问题能引用当前页面上下文，不要求用户重复说明所在模块。
- 连续追问至少支持 6 轮上下文，刷新页面后会话仍可恢复。
- 流式输出期间可停止生成，网络断开后界面能显示可重试状态。
- 未配置 Chat 模型时仍能完成规则式菜单导航，并给出“管理员需配置模型”的提示。

### 安全验收

- 普通用户无法读取其他用户的助手会话。
- 模型不能跳转到未注册路由、外部 URL 或执行未授权操作。
- API Key、密码、Token 不出现在请求日志、消息记录或模型上下文中。
- 删除、提交、审批、发布等高风险动作不会在 MVP 中被自动执行。

### 性能建议

- 首字节响应目标：正常网络下 3 秒内开始返回。
- 单次会话上下文控制在可配置范围，默认最多 8 轮或 8,000~12,000 字符。
- 操作目录优先走本地/数据库匹配，减少不必要的 LLM 调用。
- 失败重试最多 1 次，避免重复扣费和消息重复。

## 14. 推荐落地顺序

1. 先新增操作目录和动作白名单，不先做复杂 Agent。
2. 再把 `DefaultLayout.vue`、助手组件、assistant store 和 SSE API 串通。
3. 接入现有 LLM Provider/Model 解析和 `LlmGateway`，实现 JSON 输出解析。
4. 接入现有知识库 RAG，建立“系统操作手册”知识库。
5. 最后增加页面高亮和只读工具；写操作工具单独评审并默认关闭。

这样可以在不影响现有 RAG 文档中心的前提下，将已有 LLM 能力转化为面向全系统的操作导航能力，同时保留严格的权限边界和后续扩展空间。
