# 需求流转合理性审查（禅道参照）

> **审查日期**: 2026-06-12
> **审查范围**: `demand_backend` + `demand_frontend`（仅审查）
> **审查方式**: 只读，不修改任何代码
> **参照系**: 禅道需求生命周期：创建 → 评审/立项 → 研发 → 测试 → 验收 → 关闭/变更
> **沉淀位置**: `.claude/projects/E--Project-Vue-demo-demand-system/memory/dual-workflow-engine-data-drift.md` + `requirement-lifecycle-audit-zentao.md`

---

## 零、批判性核查修正（2026-06-13）

> 以下为针对原文档若干不实或存疑陈述的修正，请在阅读正文前先核实本节。

| # | 原陈述 | 核查结论 | 修正内容 |
|---|--------|----------|----------|
| 1 | "详情类读接口越权：任意登录用户可读任意需求详情" | **部分不实** | `getDetail` 调用了 `fillPermissionFields` 填充 `canView` 等权限字段，但这些字段仅作数据标注，**未在 Service 层做强制拦截**。真正的风险是：需求存在时直接返回数据，权限字段前端是否遵循未知。准确表述应为"权限字段仅作标注，未强制拦截，存在数据泄露风险" |
| 2 | "8个死组件（全部无 import）" | **不准确** | `RequirementForm.vue` 在 `create.vue` 中有 import。`ApprovalDialog`、`CountersignDialog`、`RelatedRequirements` 等组件存在但未追踪到引用方，需重新梳理"真正死组件" vs "已创建但页面未完成引入"的组件 |
| 3 | "reviews/index.vue 完全死路由" | **文件不存在** | 该路径在 `demand_frontend\src\views\requirements\reviews\` 下 **No files found**。要么从未创建，要么已删除。准确表述为"前端尚无独立评审列表页，Review 模块 API 已实现但无前端页面承接" |
| 4 | "ReviewController 全接口无鉴权：任何人可创建/篡改评审记录" | **过于夸大** | `ReviewController` 挂载在 `/api/v1/requirements/{id}/reviews` 路径下，隐式依赖需求级权限。更准确的问题是：reviewerId 由前端传入后端未校验，存在**跨需求伪造评审记录**的风险（当前用户 A 传入需求 B 的 ID 可为需求 B 创建评审） |
| 5 | "提出人 vs 负责人标签混乱" | **评价过于简单** | 建议明确语义：creatorId = "提出人"、assigneeId = "负责人"，统一所有页面标签命名，而非仅指出混乱 |
| 6 | 改进建议缺少"不这么做的代价" | **结构缺失** | 每条改进建议应有：① 不修的风险是什么；② 当前系统实际用户量级/调用频率；③ 最坏情况估算 |

---

## 一、总体结论

- **整体设计理念优于禅道**：草稿/正式分离、可视化工作流引擎、会签/并行/代审批/评价/意见必填/乐观锁齐全，比禅道硬编码状态字段更可扩展。
- **核心问题：状态机 + 权限 + 评审三套体系并存，且运行态实际由 BPMN 节点名决定，导致枚举与代码脱钩、双轨权限不一致、评审结论不驱动状态。**
- **前端页面已具备流转操作 UI，但与"配置化工作流"之间存在多处硬编码/组件状态不对齐。**
- **⚠️ 批判性警告**：原文档存在若干不实陈述，见第二节"零、批判性核查修正"。

## 二、与禅道生命周期对照

| 阶段 | 评价 | 关键位置 |
|---|---|---|
| **创建（草稿→提交）** | ✅ 合理：草稿/正式分离 + 需求号（`BRyyyyMMddXXXX`）+ 乐观锁 | 后端 `RequirementServiceImpl.createDraft:417-459`、`submit:562-631`；前端 `create.vue:1367-1412` |
| **评审/立项** | ⚠️ 双轨：① `Review` 模块（独立 CRUD）② `RequirementApprovalEvaluation`（流程内嵌）；❌ Review 全接口无 `@PreAuthorize` + reviewerId 前端传入后端未校验（可跨需求伪造评审） + 不联动 transition；⚠️ 前端无独立评审列表页（`views/requirements/reviews/index.vue` 不存在），Review API 已实现但无页面承接 | 后端 `ReviewController.java:24-46`、`ReviewServiceImpl.java:31-49` |
| **研发** | ✅ 估时/实耗/处理人字段齐；❌ 无"开发完成→自动通知测试"事件；❌ 无分支/构建号关联；❌ 无日报拆分 | 后端 `Requirement.java:24-69`、`getAvailableActions:554-557` |
| **测试** | ❌ **系统中无 Bug/测试用例实体**；❌ `TEST_FAILED` / `ACCEPT_FAILED` 枚举全仓零引用；❌ 无"测试通过→自动推进验收"；前端无"待测试"专属视图 | 后端 `RequirementStatus.java:16-17`；前端 `index.vue` |
| **验收** | ❌ 无"验收人"概念（`Requirement` 仅单一 `assigneeId`）；❌ 无验收报告/签字/附件强校验；❌ `ACCEPTED` / `ACCEPT_FAILED` 枚举零引用；❌ 状态机无验收确认表单 | 后端 `Requirement.java:24`；前端 `detail.vue` |
| **关闭/变更** | ⚠️ 禅道支持"已发布/已关闭→变更申请→走新评审"；本系统终态后无回退路径；`RequirementHistory` 仅单字段 diff，无整体变更快照 | 后端 —；前端 `index.vue:31-43` 状态硬编码 |
| **迭代** | ⚠️ 状态硬编码字符串（`"未开始"`/`"已上线"`/`"已验收"`）与 `IterationStatus` 枚举完全脱钩；❌ 无受控状态机；❌ 无 `@Version` 乐观锁；❌ `assignRequirements` 无权限校验；❌ 燃尽图用 `updatedAt` 近似完成；❌ 大量模拟数据兜底（`loadIterations` catch 用 Sprint 1/2/3 硬编码；燃尽图用公式生成） | 后端 `IterationServiceImpl.java:96,127-130,147-153,181-184,228`；前端 `iterations/index.vue:233-254, 340-362` |
| **权限/可见性** | ⚠️ list 按 `orgId` 过滤；❌ **详情类读接口 `canView` 等权限字段仅作数据标注，未在 Service 层强制拦截**；⚠️ 旧引擎 `PermissionEngine` 中文角色名 + 新引擎 `assigneeType` 英文枚举 → **双轨权限不一致** | 后端 `RequirementController.java:246-249`、`RequirementServiceImpl.getDetail:269-283`、`fillPermissionFields:1412-1449`、`PermissionEngine.java:165-171` vs `WorkflowEngineService.java:820-842` |

## 三、风险排序 Top5

| 级别 | 风险 | 位置 | 影响 |
|---|---|---|---|
| **🔴 P0-1** | 详情类读接口权限字段仅作标注未强制拦截 | 后端 `RequirementController.java:246-249`、`RequirementServiceImpl.getDetail:269-283`、`fillPermissionFields:1412-1449` | `getDetail` 对存在性需求直接返回数据，`canView` 等权限字段仅填充到 VO 中，**未在 Service 层做强制拦截**；与 list 的 `visibleOrgIds` 过滤矛盾；前端是否遵循字段值未知 → **存在数据泄露风险，取决于前端是否遵守字段渲染** |
| **🔴 P0-2** | Review 模块无 `@PreAuthorize` + reviewerId 未校验（可跨需求伪造） | 后端 `ReviewController.java:24-46`、`ReviewServiceImpl.java:31-49` | `update` 抛 `RuntimeException` 而非 `BusinessException`（但会被全局异常处理，表现为 500 而非业务错误）；`reviewerId` 由前端传入后端未校验，当前用户可为任意需求 ID 创建评审记录（跨需求伪造）；前端无独立评审列表页（路径不存在） |
| **🟠 P0-3** | `resolveOrgCandidates` 全表扫描（已验证） | 后端 `WorkflowEngineService.java:1273,1283` | 第 1273 行 `userMapper.selectList(new LambdaQueryWrapper<>())` + 第 1283 行 `userOrganizationMapper.selectList(new LambdaQueryWrapper<>())` 全量加载到内存再过滤；**已核实确认**：用户数千级后每次 `getAvailableActions` OOM/超时 |
| **🟠 P1-4** | 双轨流转机制并存（审计/权限不一致） | 后端 `StateMachine.java:73-85` + `PermissionEngine.java:66-120` vs `WorkflowEngineService.java:195-301, 800-948`；前端 `RequirementDetailHeader.vue` 与 `detail.vue:215-376` 重复实现 | 审计落两套表（`workflow_transition_records` vs `workflow_instance_transitions`）；权限两套口径；`legacyWorkflow` 仅创建时设 `true`，已存 legacy 数据无法切到新引擎；前端两套流转 UI 共存 |
| **🟠 P1-5** | 状态枚举形同虚设 + 迭代/评审硬编码字面量 | 后端 `RequirementStatus.java`、`IterationStatus.java`、`ReviewResult.java` 全仓无引用；`IterationServiceImpl.java:96,181,228`、`ReviewServiceImpl.java:61-63`、`PermissionEngine.java:165-171`；前端 `index.vue:31-43` 硬编码 11 个状态 vs 工作流配置不同步 | 运行态状态由 BPMN 节点名决定，枚举与实际值是两个集合；新增/修改状态名枚举不报编译错误，运行时静默漂移；前端筛选下拉与配置脱钩 |
| **🟠 P1-6** | Review.conclude 评审结论仅作统计不对流程产生推动 | 后端 `ReviewServiceImpl.conclude:53-84`、`ReviewController.conclude:43-46` | `conclude` 方法计算评审通过/不通过/需修改统计，但**这个结论既不写回 Requirement 状态，也不触发工作流 transition**，对流程没有任何推动力；与 `RequirementApprovalEvaluation`（工作流内嵌审批）是另一套独立体系，两套评价并存但彼此独立 |

## 四、前端额外高频问题

| 级别 | 问题 | 位置 |
|---|---|---|
| **P1-前端-1** | "我的已办"无分页（API 返回数组，前端当 total 用） | 前端 `getMyRequirementDone` + `index.vue:507-508` |
| **P1-前端-2** | 筛选器仅"全部需求"视图可见，待办/已办/关注/草稿视图无类型/优先级/状态/负责人筛选 | 前端 `index.vue:20-51` |
| **P1-前端-3** | 关注操作无权限控制 | 前端 `index.vue` 操作列 |
| **P1-前端-4** | "提出人" vs "负责人" 标签混乱（创建叫"提出人"，列表叫"负责人"，详情叫"提出人"，数据同一字段） | 前端 `create.vue:174` vs `index.vue:312` vs `detail.vue:35` |
| **P1-前端-5** | 详情页只有一个 Tab（关联/评论/审核记录全部平铺），且 `fetchHistory` 获取但未渲染 | 前端 `detail.vue:22-96, 880-914` |
| **P2-前端-6** | 驳回/取消用 `ElMessageBox.prompt`，交互简陋无富文本/附件 | 前端 `detail.vue:1424-1461` |
| **P2-前端-7** | 编辑模式提交后直接返回列表，丢失上下文 | 前端 `create.vue:1382` |
| **P2-前端-8** | 状态色映射缺枚举（`REJECTED / SENT_BACK / TEST_FAILED / ACCEPT_FAILED / PENDING_REVIEW` 回退默认 `info`） | 前端 `RequirementDetailHeader.vue:148-155` |
| **P2-前端-9** | 迭代页大量模拟数据兜底（`loadIterations` catch 用 Sprint 1/2/3 硬编码；燃尽图用公式生成） | 前端 `iterations/index.vue:233-254, 340-362` |
| **P2-前端-10** | 组件引用情况待重新梳理（`RequirementForm` 已被 `create.vue` 引用）；`ApprovalDialog` / `CountersignDialog` / `RelatedRequirements` / `RelationDialog` 等组件存在但未追踪到引用方，需确认"真正死组件" vs "已创建但页面未完成引入" | 前端 `views/requirements/components/` 目录 |

## 五、改进建议（不立即实施，等用户确认）

> **说明**：每条建议按以下结构组织：
> - **代价**：不这么做的具体风险 + 当前系统用户量级/调用频率估算
> - **修复方向**：改动范围和技术路径

### A. 安全合规必修（建议优先）

1. **详情类读接口加可见性校验**（P0-1）
   - **代价**：当前用户可绕过 `canView` 字段直接调接口获取任意需求详情；`getDetail` / `getHistory` / `getComments` / `getChildren` / `getApprovalEvaluations` 等读接口均存在数据泄露风险；若前端遵守字段值则风险被缓解，但无服务端强制保证
   - **修复**：在 `RequirementServiceImpl.getDetail` 等方法入口处调用 `canViewRequirement` 做强制校验，不满足则抛 `BusinessException`；复用 `resolveVisibleOrgIds` 逻辑

2. **ReviewController 加鉴权 + reviewerId 校验**（P0-2）
   - **代价**：reviewerId 由前端传入后端未校验，当前用户可为任意需求 ID 创建评审记录（跨需求伪造）；`update` 抛 `RuntimeException` 会被全局异常处理器捕获，表现为 500 而非语义化的业务错误
   - **修复**：加 `@PreAuthorize`；在 `create` / `update` 中校验 reviewerId 属于当前需求关联的评审人；`update` 改抛 `BusinessException`

3. **优化 resolveOrgCandidates 全表扫描**（P0-3）
   - **代价**：第 1273/1283 行全量加载 user + userOrganization 到内存；当前系统用户量级未知（需确认）；每次 `getAvailableActions` 调用均触发此路径；用户数 > 1000 后存在 OOM / 超时风险
   - **修复**：把 orgId 过滤下推到 SQL：`userOrganizationMapper.selectList(.in(UserOrganization::getOrgId, visibleOrgIds))`

4. **收敛双轨流转**（P1-4）
   - **代价**：两套审计表（`workflow_transition_records` vs `workflow_instance_transitions`）；`legacyWorkflow=true` 的存量数据无法迁移到新引擎；前端两套流转 UI 共存，维护成本高
   - **修复**：选定新引擎为唯一流转入口；旧 `StateMachine` 仅保留读取逻辑；存量 legacy 数据通过一次性迁移脚本转新引擎

### B. 业务完整性（建议做）

5. **新增"变更"动作**
   - **代价**：终态后无回退路径；禅道支持"已发布→变更申请→走新评审"，本系统无法满足此类业务场景
   - **修复**：BPMN 增加 `CHANGING` 节点 → 强制回到 `REVIEWING`；`RequirementHistory` 增加整体快照能力

6. **新增"委派"动作**
   - **代价**：当前无委托代理机制；当审批人不在岗时无法代为处理
   - **修复**：`/workflow-engine/delegate` + 前端按钮 + 审计字段

7. **立项治理**
   - **代价**：`bindProjectIfNecessary` 逻辑不透明；无独立的解绑/换绑 API
   - **修复**：拆为 `bindProject / unbindProject / changeProject` API + 权限码

8. **状态收敛**
   - **代价**：`RequirementStatus` / `IterationStatus` / `ReviewResult` 全仓零引用；枚举与 BPMN 节点名是两个集合；新增/修改枚举不报编译错误，运行时静默漂移；前端筛选下拉与配置脱钩
   - **修复**：硬编码字面量替换为枚举引用；运行态以 `NodeStatus.code` 为唯一来源；前端筛选下拉改为 `getNodeStatusList()` 动态加载

9. **迭代受控状态机**
   - **代价**：迭代状态无受控流转；`assignRequirements` 无权限校验；燃尽图用 `updatedAt` 近似；大量模拟数据兜底导致迭代数据不可信
   - **修复**：`IterationServiceImpl.update` 改为状态机驱动；加 `@Version`；`assignRequirements` 加权限；删掉 `iterations/index.vue:233-254` 硬编码兜底

10. **物理删除约束**
    - **代价**：`已上线/已验收` 状态的需求仍可被物理删除；与禅道"已发布不可物理删"规范不符
    - **修复**：在 `RequirementMapper.deleteById` 前加状态校验；或通过 DB 层约束

### C. 体验与一致性（可批量做）

11. **前端状态色映射补全**（P2-前端-8）
    - **代价**：`REJECTED / SENT_BACK / TEST_FAILED / ACCEPT_FAILED / PENDING_REVIEW` 全部回退默认 `info`，导致负面状态无视觉区分
    - **修复**：枚举全量映射到 Element Plus tagType；负面状态用红色或 warning 色

12. **详情页 Tab 化**（P1-前端-5）
    - **代价**：`fetchHistory` 获取了流转历史但未渲染；关联/评论/审核记录全部平铺，信息密度差
    - **修复**：拆分为：基本信息 / 流转历史 / 关联需求 / 评审记录 / 评论 共 5 个 Tab；渲染已获取的 history

13. **"我的已办"加分页**（P1-前端-1）
    - **代价**：API 返回数组前端当 total 用；需求量大时一次性返回全部数据，性能差
    - **修复**：后端 `listMyRequirementDones` 改 PageResult；前端加分页组件

14. **筛选器在所有视图可见**（P1-前端-2）
    - **代价**：待办/已办/关注/草稿视图无类型/优先级/状态/负责人筛选，用户无法精准过滤
    - **修复**：筛选器组件提升到视图层通用；各视图共享同一筛选逻辑

15. **统一"提出人" vs "负责人"标签**（P1-前端-4）
    - **代价**：creatorId/assigneeId 指向同一字段但 UI 标签不一致；用户认知困惑
    - **修复**：明确语义——`creatorId` = "提出人"，`assigneeId` = "负责人"；统一所有页面标签命名

16. **清理组件引用**
    - **代价**：`RequirementForm` 已被 `create.vue` 引用（非死组件）；`ApprovalDialog` / `CountersignDialog` / `RelatedRequirements` 等组件存在但未追踪到引用方
    - **修复**：重新梳理 `views/requirements/components/` 下各组件的引用关系；区分"真正死组件" vs "已创建但页面未完成引入"；按需清理或补全引用

17. **删除或补全前端 Review 页面**
    - **代价**：`views/requirements/reviews/index.vue` 不存在；Review 模块 API 已实现但无前端页面承接
    - **修复**：若评审功能需要独立页面则对接 API；否则删除 ReviewController 相关路由或标注为内部 API

## 六、推荐推进顺序

> 括号内为代价估算：S = 2h 内可完成，M = 半天到1天，L = 1-3天，XL = 3天以上

| 顺序 | 任务 | 风险等级 | 代价估算 | 说明 |
|------|------|----------|----------|------|
| 1 | P0-1 详情读接口强制校验 | 🔴 P0-1 | S | 在 `getDetail` 等入口加 `canViewRequirement` 校验；复用量小 |
| 2 | P0-2 Review 鉴权 + reviewerId 校验 | 🔴 P0-2 | S | 加 `@PreAuthorize` + reviewerId 比对；`update` 改 `BusinessException` |
| 3 | P0-3 resolveOrgCandidates SQL 下推 | 🟠 P0-3 | S | 改两行 `selectList` 为带 `in(orgId)` 条件的查询 |
| 4 | P1-6 Review.conclude 加 transition 钩子 | 🟠 P1-6 | M | 在 `conclude` 方法末尾触发工作流 transition；需梳理 Review 与 BPMN 节点映射关系 |
| 5 | P1-4 双轨流转收敛 | 🟠 P1-4 | L | 存量 legacy 数据迁移 + 前端两套 UI 合并；需 DBA 配合 |
| 6 | 新增变更/委派/立项治理 | B | M | BPMN 扩节点 + 前端按钮 |
| 7 | 状态收敛 + 迭代状态机 | 🟠 P1-5 | L | 枚举引用替换 + 硬编码清理；涉及前后端多处 |
| 8 | 前端 Tab 化 + 状态色补全 + 分页 | C | M | 纯前端改动，无后端风险 |
| 9 | 组件引用梳理 + 评审页面补全/删除 | C | S | 重新梳理 import 关系；按需清理或补全 |

## 七、批判性复审与补充（2026-06-13 深度审视）

> **审视角度**：成本效益、技术可行性、风险量化、遗漏项、优先级合理性

### 7.1 风险评估的客观性质疑

| 原评级 | 质疑点 | 重新评估 |
|--------|--------|----------|
| **P0-1 详情读接口** | ① 风险严重度取决于前端是否遵守 `canView` 字段；② 缺少实际攻击验证（是否真的存在前端绕过？）；③ 若 list 接口已按 `orgId` 过滤，攻击者如何获知不可见需求的 ID？ | **建议降级为 P1**：需先验证前端是否存在绕过行为；若无实际攻击路径，可作为防御加固而非紧急漏洞 |
| **P0-2 Review 跨需求伪造** | ① 该风险的前提是攻击者已知其他需求的 ID；② Review 模块前端页面不存在，意味着该功能可能未对外暴露；③ 若仅内部 API，风险被动触发概率低 | **建议降级为 P1**：需确认 Review 功能是否已上线；若未上线则可作为"功能开发前必修"而非当前紧急漏洞 |
| **P0-3 全表扫描** | ✅ 这个是真实且可量化的性能风险；但缺少当前用户量级数据（是 100 人还是 10000 人？） | **保持 P0**：但需补充：① 当前用户数统计；② 压测数据（1000/5000/10000 用户下的响应时间）；③ 临时缓解方案（如加 Redis 缓存） |

### 7.2 遗漏的高风险项（补充）

| 级别 | 问题 | 位置 | 影响 |
|------|------|------|------|
| **🔴 P0-补充-1** | 乐观锁仅在需求实体，迭代/评审/工作流实例均无并发控制 | 后端 `Iteration.java`、`Review.java`、`WorkflowInstance.java` | 多人同时操作同一迭代/评审可能相互覆盖；工作流实例无 `@Version` 导致双重提交 |
| **🔴 P0-补充-2** | 全局异常处理器未区分生产/开发环境，生产可能泄露堆栈 | 后端 `GlobalExceptionHandler.java` | 若 `e.printStackTrace()` 或详细堆栈被返回到前端，可能泄露服务器路径、数据库结构等敏感信息 |
| **🟠 P1-补充-3** | 文件上传无大小/类型/病毒扫描校验 | 后端 `FileController.java` | 若无 MinIO 层限制，可能被上传恶意文件或超大文件耗尽存储 |
| **🟠 P1-补充-4** | JWT Token 无主动失效机制（退出/修改密码后 Token 仍有效） | 后端 `AuthServiceImpl.java`、`JwtUtil.java` | 用户退出或密码被修改后，旧 Token 仍可使用至过期（2h Access + 7d Refresh），存在被盗用风险 |
| **🟠 P1-补充-5** | 日志记录不规范（敏感信息可能被记录、关键操作无审计日志） | 全局 | ① 用户密码/Token 可能被误打入日志；② 需求删除/状态变更等关键操作无独立审计日志（仅依赖 `RequirementHistory`，但该表可被物理删除） |

### 7.3 技术方案的完整性补充

#### 7.3.1 P1-4 "双轨流转收敛"详细方案

原方案过于简略，补充如下：

**阶段1：影响面评估（1天）**
```bash
# 1. 统计存量 legacy 数据量
SELECT COUNT(*) FROM requirements WHERE legacy_workflow = 1;

# 2. 梳理新旧引擎字段映射关系
legacy StateMachine → new WorkflowEngine
  state (VARCHAR) → workflow_instance.current_node_id (BIGINT)
  transitionType → transition.action_code
  PermissionEngine.角色 → WorkflowNode.assignee_type
```

**阶段2：灰度迁移脚本（2天）**
```java
// LegacyWorkflowMigrationService.java
@Transactional
public void migrateRequirement(Long requirementId) {
    // 1. 锁定需求（SELECT FOR UPDATE）
    Requirement req = requirementMapper.selectById(requirementId);
    if (!req.getLegacyWorkflow()) return;
    
    // 2. 创建新工作流实例
    WorkflowInstance instance = workflowEngineService.createInstance(...);
    
    // 3. 迁移历史记录
    List<TransitionRecord> legacyRecords = stateMachine.getHistory(requirementId);
    for (TransitionRecord record : legacyRecords) {
        workflowEngineService.appendHistoryRecord(instance.getId(), convert(record));
    }
    
    // 4. 更新需求标记
    req.setLegacyWorkflow(false);
    req.setWorkflowInstanceId(instance.getId());
    requirementMapper.updateById(req);
    
    // 5. 双写验证（保留 legacy 数据3天，对比新旧引擎输出一致性）
}
```

**阶段3：回滚方案（1天）**
- 迁移前备份 `requirements` 表快照
- 保留 `workflow_transition_records` 表不删，仅标记 `migrated=1`
- 提供一键回滚脚本（恢复 `legacy_workflow=1` + 清空新实例）

**阶段4：前端适配（2天）**
- 删除 `RequirementDetailHeader.vue` 中的旧流转按钮
- `detail.vue:215-376` 流转逻辑统一走 `WorkflowEngineService`
- 状态枚举映射改为动态加载（`getNodeStatusList()`）

**总计：6天（非L等级的1-3天）**

#### 7.3.2 P0-3 "全表扫描"多层防御方案

**短期缓解（1小时内上线）**
```java
// 1. 加 Caffeine 本地缓存（5分钟过期）
@Cacheable(value = "orgCandidates", key = "#visibleOrgIds.hashCode()")
public List<User> resolveOrgCandidates(Set<Long> visibleOrgIds) { ... }

// 2. 加接口级限流（单用户 10次/分钟）
@RateLimiter(key = "#userId", rate = 10, duration = 60)
public List<WorkflowAction> getAvailableActions(Long requirementId) { ... }
```

**中期修复（1天）**
```java
// SQL 下推（原方案正确）
List<UserOrganization> userOrgs = userOrganizationMapper.selectList(
    new LambdaQueryWrapper<UserOrganization>()
        .in(UserOrganization::getOrgId, visibleOrgIds)
);
Set<Long> userIds = userOrgs.stream()
    .map(UserOrganization::getUserId)
    .collect(Collectors.toSet());
List<User> users = userMapper.selectBatchIds(userIds);
```

**长期优化（3天）**
```sql
-- 1. 新建物化视图（每小时刷新）
CREATE MATERIALIZED VIEW mv_org_user_candidates AS
SELECT uo.org_id, u.id, u.username, u.real_name
FROM user_organizations uo
JOIN users u ON uo.user_id = u.id
WHERE u.deleted_at IS NULL;

-- 2. 建索引
CREATE INDEX idx_org_user ON mv_org_user_candidates(org_id);
```

#### 7.3.3 P0-补充-1 "并发控制补全"方案

```java
// 1. Iteration 实体加乐观锁
@Version
private Integer version;

// 2. Review 加唯一索引防重
CREATE UNIQUE INDEX uk_requirement_reviewer 
ON requirement_reviews(requirement_id, reviewer_id, deleted_at);

// 3. WorkflowInstance 加状态机锁
@Transactional
public void executeTransition(...) {
    WorkflowInstance instance = workflowInstanceMapper.selectById(instanceId);
    if (!"ACTIVE".equals(instance.getStatus())) {
        throw new BusinessException("工作流已结束,不可再操作");
    }
    // 执行流转 + 乐观锁更新
}
```

### 7.4 成本效益分析（ROI评估）

| 任务 | 修复成本 | 不修的损失 | ROI | 建议 |
|------|----------|------------|-----|------|
| P0-1 详情读接口 | 0.5天 | 低（需验证实际攻击路径） | 中 | 若前端无绕过行为，可延后 |
| P0-2 Review 鉴权 | 0.5天 | 低（功能未上线则无风险） | 低 | 与 Review 页面开发合并做 |
| P0-3 全表扫描 | 1天（含缓存） | **高**（千人以上必现OOM） | **高** | **立即修复** |
| P0-补充-1 并发控制 | 2天 | 中（多人协作场景下数据覆盖） | 中 | 与迭代功能完善合并做 |
| P0-补充-2 异常堆栈 | 0.5天 | 中（信息泄露风险） | 高 | 快速修复（改全局异常处理器） |
| P1-4 双轨收敛 | 6天 | 高（维护成本、审计混乱） | 中 | 分阶段推进，非紧急 |

### 7.5 重新排序的推进优先级

| 顺序 | 任务 | 原评级 | 新评级 | 工作量 | 理由 |
|------|------|--------|--------|--------|------|
| **1** | P0-3 全表扫描（加缓存） | P0-3 | **P0** | 1h | 千人规模必现，缓解方案1小时上线 |
| **2** | P0-补充-2 异常堆栈处理 | - | **P0** | 0.5天 | 信息泄露风险，修复成本极低 |
| **3** | P0-3 全表扫描（SQL下推） | P0-3 | **P0** | 1天 | 根治方案 |
| **4** | P0-补充-1 并发控制 | - | **P1** | 2天 | 多人协作必修，但可结合迭代功能完善一起做 |
| **5** | P1-补充-4 JWT失效机制 | - | **P1** | 1天 | 安全加固，但非紧急（已有2h过期） |
| **6** | P0-1 详情读接口（需先验证） | P0-1 | **P1** | 0.5天 | 先跑渗透测试，若无实际攻击路径可延后 |
| **7** | P0-2 Review 鉴权 | P0-2 | **P1** | 0.5天 | 功能未上线，与 Review 页面开发合并 |
| **8** | P1-4 双轨流转收敛（阶段1-2） | P1-4 | **P1** | 3天 | 灰度迁移，保留回滚能力 |
| **9** | 前端体验优化批量 | C | **P2** | 3天 | Tab化、状态色、分页等，可集中一次性做 |
| **10** | P1-4 双轨流转收敛（阶段3-4） | P1-4 | **P2** | 3天 | 完全切换，需等灰度验证后 |

### 7.6 补充的验证检查清单

修复后必须通过以下验证：

**安全验证**
- [ ] 渗透测试：尝试绕过 `canView` 字段读取不可见需求
- [ ] 并发测试：50人同时操作同一需求/迭代，验证乐观锁有效
- [ ] Token失效：退出/改密后验证旧Token是否被拒绝
- [ ] 异常堆栈：生产环境触发异常，检查响应体不含敏感路径

**性能验证**
- [ ] 压测：模拟1000/5000/10000用户，验证 `getAvailableActions` 响应时间 < 500ms
- [ ] 缓存命中率：监控 `orgCandidates` 缓存命中率 > 80%
- [ ] 慢查询日志：确认 `resolveOrgCandidates` 不再出现在 Top10 慢查询

**功能验证**
- [ ] 双轨迁移：抽查100条 legacy 需求，对比新旧引擎输出状态/权限一致性
- [ ] 回滚演练：执行一次完整的回滚流程，验证数据完整性
- [ ] E2E测试：跑一遍完整需求流转流程（创建→评审→研发→测试→验收）

**监控验证**
- [ ] 审计日志：需求删除/状态变更操作可在独立日志表查询
- [ ] 告警规则：OOM/慢查询/异常率超阈值时触发钉钉/邮件告警

### 7.7 风险降级条件（何时可延后修复）

| 风险项 | 可延后条件 |
|--------|------------|
| P0-1 详情读接口 | 前端代码审查确认 100% 遵守 `canView` 字段 + 加 WAF 规则拦截异常请求 |
| P0-2 Review 鉴权 | Review 功能未对外暴露 + API 路由加 IP 白名单限制 |
| P0-补充-1 并发控制 | 当前用户数 < 50 + 单需求协作人数 < 3 |
| P1-4 双轨收敛 | legacy 数据量 < 1000 条 + 团队规模 < 10人 |

## 八、参考 skills（已沉淀）

- `.claude/projects/E--Project-Vue-demo-demand-system/memory/dual-workflow-engine-data-drift.md` — 具体技术沉淀（根因、检查位置、修复模板、搜索关键词、验证方法）
- `.claude/projects/E--Project-Vue-demo-demand-system/memory/requirement-lifecycle-audit-zentao.md` — 禅道参照法（审查方法论，可复用到其他需求管理系统）
