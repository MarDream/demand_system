# MEMORY.md — demand_system 项目约定

> 只留跨会话必守的结论。专题详见 `memory/topics/`：
> - `bitable.md` —— 多维表格（分组树 / 表级权限 / vxe 渲染器 / 字段类型清单）
> - `ai-assistant.md` —— AI 助手 / NL2SQL / 模型配置 / LLM 网关
> - `workflow.md` —— 工作流配置（版本列表 DTO 缺字段的坑 / 时间列语义 / updated_at 维护点 / 起后端）
> 详细过程见 `memory/YYYY-MM-DD.md`。

## 构建 / 运行
- 本机 mvn 启动器坏 → 绕过见用户级 MEMORY.md；mvn 输出含 NUL，先 `tr -d '\000'` 再 grep。
- 后端 `:8081`，前端 dev `127.0.0.1:5170`。日志 `logs/demand-system.log` / `error.log`。
- 前端类型检查 `./node_modules/.bin/vue-tsc --noEmit -p tsconfig.json`。
- 登录 500 的二次症状在 error.log（`AuthorizationDeniedException`）；**根因去 demand-system.log 搜 `APPLICATION FAILED TO START` / `Port xxx was already in use`**。
- **compile 与 spring-boot:run 不可并发**：target/classes 交错写会丢 class（症状 `ClassNotFoundException` / `Failed to parse mapping resource`）→ `rm -rf target/classes` 全量重编，别增量补。
- 手工起后端：`target/cp.txt` + `target/run.args`（`-cp "target/classes;<cp>"`，**反斜杠全转正斜杠**），用**后台任务跑** `java @target/run.args`。正常时 jps 显示 `com.demand.system.DemandSystemApplication`（走 fat jar 会显示 JarLauncher）。不要经 `cmd.exe`/`mvn.cmd`（安全策略拦）；`Start-Process -WindowStyle Hidden` 起的会被沙箱杀掉。

## 数据库
- MySQL `localhost:3306` root/admin123 库 `demand_system`；改端口后必须重新 compile。
- **列注释不可信**：枚举值运行时从字典表读（`priorities`/`requirement_types`/`workflow_states`，终态 `is_final=1`）。
- 未接 Flyway：`database/migrations/*.sql` 手动 `docker exec -i mysql` 执行，并同步进 `init.sql`。
- **`init.sql` ≠ 线上库**：库中曾缺 `knowledge_document_requirement_refs` 等 8 张表 → 建需求直接 500 `bad SQL grammar []`。上新功能前先对比 `init.sql` 与 `information_schema.tables`。
- 手写 SQL 自带 `AND deleted_at = 0`（`@TableLogic` 只管 MP 生成的 SQL）。
- `docker exec mysql mysql` 的 `character_set_client` 默认 latin1 → 中文条件静默命中 0 行，必须加 `--default-character-set=utf8mb4`。
- **手写列清单的 mapper 必须逐个核对实体字段**（JSON 配置列、乐观锁列是重灾区，漏了会「写完就丢」）。
- **删分组不是级联删除**：子分组与数据表上移到父级，清理嵌套分组要先删根、再删被顶到根层级的子分组。

## 验证技巧
- 本机 http(s)_proxy 把本地请求拦成 502：curl 用 `--noproxy '*'`；Python urllib 需 `ProxyHandler({})`。
- **页面内 `fetch` 默认不带鉴权**：后端认 `Authorization` 头，`page.evaluate` 里要手动带 `Bearer`，否则全接口 401 而 UI 操作其实成功。
- Playwright：复用 `demand_frontend/node_modules`；dev server 绑 `127.0.0.1:5170`；token 在 cookie `access_token`；**`NODE_PATH` 必须传 Windows 路径**。
- 无 mysql CLI → JDK harness + `mysql-connector-j`，中文输出加 `-Dstdout.encoding=UTF-8`。
- **回归脚本必须自带前置条件 + 自清理，并连跑两次验证幂等**；别取「树/列表里的第一个」（库里堆着历史 fixture）。
- **脚本里的取数 helper 必须非 2xx 直接抛错**：接口挂掉时若 `catch` 成空数组，`filter(...).length === 0` 这类「无残留」断言会**空过**（实测库里留着 fixture 却报 PASS）。同理把「后端可用」写成一条前置断言。
- **多会话并发改同一仓库、抢同一个后端**：后端会被反复重启 → 测试假红（但日志里那条请求其实是 200）。**先探端口 / 看日志再怀疑代码**；同一轮里不要串跑多个脚本。
- **定位 SQL 类问题**：给 run.args 追加 `--logging.level.<mapper包>=DEBUG` 重启，看 `Preparing:` / `Total: n`。
- **几何阈值断言（间距/高度/换行）要挑「不受同页其它合法元素影响」的参照物**：
  实测「搜索框底边 → 列表顶边 <=45px」在某个权限态因顶部多一条 `el-alert` 而假红（53px）；
  改量「操作行底边 → 面板容器顶边」才对。**同一个页面的不同状态（角色/权限/空态）必须各测一遍**，
  否则断言只在默认态成立。

## 编码规范
- Service 放业务逻辑，Controller 只校验调用；当前用户 `SecurityUtils.getCurrentUserId()`。
- **同一文件不要在同一条消息里发多个 Edit**（静默丢失），改完 grep 复核。
- 模块结构 `module/<name>/{controller,service,mapper,entity,dto,converter}`；JSON 列用 `@TableField(typeHandler=JacksonTypeHandler.class)` + `@TableName(autoResultMap=true)`。
- 前端 Composition API + `<script setup>`；API `api/modules/`、状态 `stores/`、类型 `types/`；**`<script setup>` 不能 `export interface`**。
- **`AppButton`（`components/common/AppButton.vue`）缺 `permission` 时 `v-if` 从 DOM 移除**（不是禁用）
  → 布局不能依赖它的占位。**禁用按钮不派发鼠标事件**，要给它挂 tooltip 必须外包一层 `<span>` 承接 hover，
  并加 `.el-button.is-disabled { pointer-events: none }`（实测：把不可操作的原因写成"一条长文案按钮"
  会把工具栏撑成两行，改用「图标按钮 + tooltip」才回到单行）。

## UI 文案原则（用户明确要求，规划阶段就要遵守）
- **提示语极简**：不写复述 UI 已有信息的话（「输入新的 xxx 名称」「回车确认」「可在 xx 中恢复」）；toast 只报结果，空状态只写状态。
- `ElMessageBox.prompt` 第一个参数传 `''` 即可让说明整块不渲染。
- **"需要解释的功能不是优雅设计"**：不写说明就看不懂 → 在规划阶段改设计，不是加提示语。
- 例外：文案承载真实信息时保留（选项清单、行为说明、枚举定义、前置条件校验）。

## 登录首页 = 首个有权限菜单（2026-09-21 起）
- 首页不走硬编码 `/dashboard`：统一走 `useAppStore.resolveHomePath()`（按 menus/current 顺序取首个可导航 path）。
  接线点：登录、guards（`/` 与 `/dashboard` 的无权限兜底）、角色切换。新加"登录后跳转"必须用同一个函数。
- **仪表盘有权限门槛 `menu:dashboard`**（sys_permissions id=141，菜单 id=1）。给新角色授权时
  不勾它 = 该角色用户登录落到其它菜单。
- **needOrgBind 分支也必须用 resolveHomePath**：OrgBindDialog 挂在 DefaultLayout（任何页面都弹），
  旧逻辑强制回 /dashboard 会和无 dashboard 权限的首页回退互相重定向死循环。

## 角色切换的语义口径（2026-09-21 用户明确要求）
- **处理角色归属随 `X-Active-Role` 收窄**：待办列表/待办角标/已办/待办操作权限（流转办理）
  都按「当前生效角色」判定，不能用用户角色并集。`SecurityUtils.getCurrentUserRoles()` 已被
  JwtAuthenticationFilter 收窄，但**角色 ID 一定要走 `getCurrentUserRoleIds()`**（生效码→roles 表反查，
  RequirementServiceImpl 上是 public），别用 `getUserRoleIds(userId)`（查 `user_roles` 全集）做权限判定。
- **数据可见范围例外**：`resolveRoleDataScopeOrgIds`（可见组织）保持全部角色并集——
  收窄它会出现"待办在列表里、组织却不可见"的自相矛盾。
- 回归：`scripts/verify-my-pending-active-role.cjs`（列表+角标 10 项，改待办/角标必跑）、
  `verify-pending-role-ui.cjs`（UI 切角色 3 项）。
- 踩坑样本：`selectMyPendingV2` 的 XML 曾忽略 `roleIds` 参数、自 JOIN `user_roles`，
  Service 层收窄了也没用——**mapper XML 与接口参数必须核对真的被使用**。
- `RequirementPendingTaskMapper.xml` 的 `pendingUserAccessJoins/Condition` 片段支持可选 `roleIds`：
  传了按生效角色收窄，不传保持旧全集口径（`selectPendingUserIds` 等展示类查询不传）。
- **切角色全站刷新**：`DefaultLayout.vue` 的 router-view 内容 div key = `activeRole:path`，
  切角色强制重建当前页（数据按新角色重拉）。新页面不要再自己做角色切换刷新逻辑，
  但页内缓存（如 tabDataCache）key 仍须含 activeRole，防同 key 串角色数据。
- **H5 端同口径**：`demand_h5` 请求拦截器也发 `X-Active-Role`（`h5_active_role` key，
  与 PC 的 `active_role` key 各自独立记忆）；新增发请求的端必须带这个头，否则走的还是全集口径。

## 后端通用踩坑
- **`@TableLogic` 实体禁用 `getDeletedAt() != null` 判空**：存活行 `deleted_at` 是 `0`（非 null）→ 恒为 true。判已删除写 `!= null && != 0`。
- **修掉"永远抛异常"的守卫后，必须回头查被它挡住的分支还缺什么**（曾因此遮住越权漏洞）。
- `@Valid` 在方法体前跑：DTO 上 `@NotNull` 字段即使 controller 用 `@PathVariable` 覆盖，body 也必须带。
- `POST /auth/login` 响应不含 user，要 userId 得再调 `GET /auth/me`。
- `GET /v1/users/active` 只返回有归属的用户；`/auth/register` 建的用户三字段全空 → 不出现在下拉。
- 需求列表接口的 `typeName`/`priorityName` 恒为 null（前端按 config 映射），不是 bug。

## 需求批量录入（scripts/req-import/）
- 需求书 = 目录下的 `*需求FRS文档*.docx`；「紧急/高/中/一般/低」→ P0/P1/P2/P2/P3。
- 脚本均已参数化：`build_manifest.py --root/--skip/--attach/--default-priority/--out`、
  `import_requirements.py --manifest/--user/--password/--assignee`、`make_report.py`。换批次只改参数。
- **`--attach` 三档**：`sibling`（同目录全部）/ `self`（仅 FRS）/ `sibling+no`（同目录 + 全树同需求编号）。
  **FRS 平铺在扫描根目录时禁用 `sibling`** —— 根目录的其它文档会被挂到每一条需求上。
  编号匹配要带边界 `(?<![0-9A-Za-z_])p12_xxxx(?![0-9])`，否则 `p12_17` 命中 `p12_1701`。
- **`REQUIREMENT_DEVELOPER` 有必填自定义字段 `requirement`（需求厂商 SELECT，默认 `opt_mu0u6l2n_tff0`）**，建草稿不带会失败。
- 流程：`POST /requirements/drafts` → `GET /requirements/{id}` 取 version → `GET /requirements/{id}/next-nodes` → `POST /requirements/{id}/submit`。
- **文档内「需求名称」可能是模板残留**：判据 = 同一名称出现在**不同目录**的 FRS 里
  （同目录多版本共用名称属正常）→ 改用文件名标题。
- 部分 FRS 的「需求优先级」单元格为空（不是解析失败），按约定兜底 P2。
- 附件走 `POST /files/upload`（multipart，UTF-8 中文文件名可直接用）；落库同时写 `requirements.attachments` JSON 与 `knowledge_document_requirement_refs`。
- 校验口径：`JSON_LENGTH(attachments)` 应逐条等于 `knowledge_document_requirement_refs` 计数；
  `workflow_instances` 应每条一个且停在「待分析」。
- **账号密码规律 `<用户名><固定后缀>`**（用户名 + 同一 3 位数字后缀；明文不入库，需要时问用户或看本地凭证）。
- 失败条目多是瞬时 `WinError 10054/10061`（上传/建草稿），**不是数据问题**：按 `src` 从原 manifest
  抽出失败项生成子清单重跑即可（实测 29/29 成功）。补录前先量化与已有记录的重叠
  （`difflib` 归一化标题比对），把「可能重复」变成确切比例再让用户拍板。
- `workflow_instances` 关联列是 **`requirement_id`**（不是 `business_id`）；同表还有
  `current_node_id / previous_node_id / status / workflow_version_id / lock_version`。写校验 SQL 前先 `DESC`。

## 其他子系统
- 用户管理（邀请/申请/批量）见 `memory/2026-09-15.md`；「已过期」不落库（`pending + expires_at` 派生）；`users.status` 只能 `active`/`inactive`。
- AI 助手 / NL2SQL / 模型配置见 `topics/ai-assistant.md`；多维表格见 `topics/bitable.md`；工作流配置见 `topics/workflow.md`。
- **「页面某列显示恒为同一个值」先打印接口返回的字段清单**：后端 DTO/VO 漏字段时（`BeanUtils.copyProperties` 只拷同名），
  前端类型声明看着没问题、实际 JSON 里根本没这个 key，前端兜底逻辑就会把旧值/创建时间顶上来。
  实例：`WorkflowVersionDTO` 缺 `updatedAt` → 「编辑时间」恒等于「创建时间」。
