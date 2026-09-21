# 专题：工作流配置（系统配置 / 工作流配置）

> 入口 `views/system/workflow-config/`（index.vue = 版本管理 + 审核记录两个 tab；editor.vue = 画布编辑器）。
> 后端 `module/workflow/`（controller / service / service.impl / engine / support / validator）。

## 版本管理列表的数据链路（最容易搞错的地方）

- 列表接口 = **`GET /api/v1/workflows/{projectId}/versions`**
  （`WorkflowVisualConfigController#getVersionHistory` → `WorkflowConfigServiceImpl#getVersionHistory`），
  返回 **`WorkflowVersionDTO`**。全局工作流的 projectId 是 **0**（前端 `GLOBAL_WORKFLOW_PROJECT_ID`）。
- ⚠️ **同一个模块里有两套版本视图对象，别混用**：
  - `WorkflowVersionDTO`（`dto/`）= 上面这个列表接口用的，`BeanUtils.copyProperties(entity, dto)` 按**同名字段**拷贝
    —— **DTO 里没有的字段前端永远拿不到**。
  - `WorkflowVersionVO`（`dto/`）= 只有 `WorkflowVersionController` 的 `/api/v1/workflow/versions/list` 用。
- **踩过的坑（「编辑时间」恒等于创建时间）**：`WorkflowVersionDTO` 原本**没有 `updatedAt` 字段**，
  copyProperties 拷不过来 → 接口 JSON 里没有 `updatedAt` → 前端 `row.updatedAt ? ... : row.createdAt`
  永远走兜底 → 列表里「编辑时间」看起来等于「创建时间」，像数据没更新。
  同理 `changeLog` 也不在 DTO 里，所以「最近发布」列（渲染 `row.changeLog`）**恒为空**，已删除该列。
  **判断这类问题先看接口实际返回的字段清单**（页面内 `fetch` 打印 `Object.keys(row)`），别只看前端类型声明
  —— `types/workflow-visual.d.ts` 里的字段比后端实际返回的多。

## 时间列语义

- **创建时间** `created_at`；**编辑时间** `updated_at`（最近一次保存/启停/复制/重命名等变更）；
  **发布时间 = 启用时间** `activated_at`（`deploy/deactivate` 不会清空它，所以停用后仍能看到最后一次发布时间）。
- 时间列的可见性走 `useColumnConfig`（`pageKey = 'workflow_version_list'`，后端表 `user_column_configs`）：
  **保存过的配置会整体覆盖默认列**（`loadColumnConfig` 直接替换 `selectedColumnKeys`），
  所以「加一个默认可见列」对**已经保存过列配置**的用户不生效 —— 当前库里没有 workflow 的已保存配置，
  默认列表才生效。加列时记得同时改 `versionAllColumns` 与 `versionDefaultKeys`。

## `updated_at` 的维护（改任何版本写路径前先看这里）

**两层保障，缺一不可**：

1. **代码层显式赋值**。`updateById(entity)` 的实体是从库里查出来的，`updatedAt` 带着旧值，
   MyBatis-Plus 会把它**原样写回**（显式赋值优先于 DB 的 ON UPDATE），时间就不动。
   已补 `setUpdatedAt(LocalDateTime.now())` 的位置：
   - `WorkflowConfigServiceImpl#updateVersionMeta`（编辑版本信息：版本号/名称/知识库/评价开关）
   - `WorkflowConfigServiceImpl#deleteApproval` / `#clearAllApprovals`（待审核回退 draft）
   - `WorkflowServiceImpl#updateVersion`（编辑工作流定义/名称）
   - `WorkflowServiceImpl#activateVersion` 的 JSON 分支（`UpdateWrapper` 补 `updated_at`）
   - `WorkflowVersionService#approveWorkflow` 的**驳回**分支（原来只写状态/审批人/意见）
   原本就有：`WorkflowActivationServiceImpl#activate/#deactivate`、`WorkflowConfigServiceImpl` 各保存草稿处、
   `WorkflowCopyServiceImpl#markAsTemplate`。
2. **DB 层兜底**：`workflow_versions.updated_at` 已加 `ON UPDATE CURRENT_TIMESTAMP`
   （迁移 `database/migrations/2026-09-19-workflow-version-updated-at.sql`，已同步 `init.sql`），
   与本库 `user_column_configs.updated_at` 写法一致。实测：INSERT 后为 NULL，
   `UPDATE ... SET name=...`（不提 updated_at）后自动刷新。

存量 NULL 不回填：这些版本创建后从未变更过，「最后一次编辑时间」就等于创建时间，
查询侧 `COALESCE(updated_at, created_at)`（`WorkflowVersionService#listVersions` 排序）与前端兜底展示处理。

## 回归脚本

- `scripts/verify-workflow-version-time-columns.cjs`（23 项）：接口必须返回 `updatedAt`；
  表头有 编辑时间/发布时间、无 最近发布；**逐行拿 UI 单元格与接口字段对账**（编辑时间、发布时间各 10 行）；
  再做一次真实编辑（`PUT /workflows/versions/{id}/meta`，只动 `approvalEvaluationEnabled`）验证
  `updatedAt` 刷新且 UI 跟随，最后改回原值。幂等（连跑两次 23/0）。
  **副作用**：被选中的那个未启用版本的「编辑时间」会被推进到运行时刻（业务字段会复原，时间不回滚）。

## 节点配置：绑定节点状态的自动默认值

当节点出边只有一条时，打开节点配置会把「绑定节点状态」**自动填为下一个节点的 `nodeStatusCode`**：
- 实现点在 `editor.vue#handleNodeClick` → `applySingleSuccessorStatusDefault(nodeId)`。
- 仅当当前节点**未填状态**时生效（已有人工值不覆盖）。
- 多出边节点不触发（无法判断"该选哪个后继"）。
- 节点名称**不受影响**（只改状态字段）。
- 依赖 `nodeMap`（全节点映射）+ `nodeTargetMap`（出边一对一映射），两者都是 reactive computed，无需额外请求。

**回归**：`scripts/verify-workflow-node-status-default.cjs`（12 项），含空状态填充 + 已填状态保持 + 多出边不干预。

## 本地起后端（mvn 启动器坏 + 沙箱会杀脱离进程）

`mvn spring-boot:run` 起的应用是 maven 的子进程；**不要在它运行时编译**（target/classes 交错写会丢 class）。
手工起法：先用 plexus 绕过方式 `-DskipTests compile`，再拿运行中 JVM 的 classpath 生成 `target/run.args`：
```bash
jcmd <pid> VM.command_line | grep "java_class_path" | sed 's/^.*initial): //' > target/cp_raw.txt
CP=$(tr -d '\r\n' < target/cp_raw.txt | tr '\\' '/')
{ echo '-cp'; echo "\"$CP\""; echo 'com.demand.system.DemandSystemApplication';
  echo '--spring.profiles.active=dev'; echo '--server.port=8081'; } > target/run.args
```
然后用**后台任务**跑 `java @target/run.args`。
**`nohup ... &` 会被沙箱回收**（日志里能看到 `Started DemandSystemApplication` 但端口随即消失），
必须让命令本身常驻在后台任务里。

## 保存配置 500：`stripPrefix` 的 prefix 可为 null（已修复，别改回去）

`WorkflowChangeLogBuilder.diff()` 对**新配置**故意传 `buildNodeNameMap(newNodes, newEdges, null)`
（新节点/边 ID 无 `v{versionId}_` 前缀）。当边引用**裸终态 ID**（`cancelled`/`accepted`/`rejected`，
前端 `validateGraph` 明确允许连线指向不存在的终态节点）时，`putTerminalName` →
`stripPrefix(id, null)` → `id.startsWith(null)` NPE。
`stripPrefix` 现在**对 `prefix == null` 直接返回原 id**——这是有意语义，不要"防御性"改回判 null 抛错。
回归形态：全节点 + 一条 `targetNodeId='accepted'` 的边 POST `/workflows/0/config` 应 200，
且 change_log 落「已通过」。
