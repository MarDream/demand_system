# MEMORY.md — demand_system 项目约定

> 只留跨会话必守的结论；详细过程见 `memory/YYYY-MM-DD.md`。
> **专题文件**（原文过长导致注入截断，已按子系统拆出，动对应模块前先读）：
> - `memory/topics/bitable.md` —— bitable 分组树 + 数据表级权限管理 + `@TableLogic` 恒真守卫坑
> - `memory/topics/ai-assistant.md` —— AI 助手弹框 / NL2SQL / 模型配置与 LLM 网关

## 构建 / 运行
- 本机 mvn 启动器坏 → 绕过方式见用户级 MEMORY.md；mvn 输出含 NUL，先 `tr -d '\000'` 再 grep。
- 前端类型检查 `./node_modules/.bin/vue-tsc --noEmit -p tsconfig.json`。
- 日志 `logs/demand-system.log` / `error.log`。登录 500 时 error.log 只有 `/error` 的 `AuthorizationDeniedException`（二次症状）；**根因去 demand-system.log 搜 `APPLICATION FAILED TO START` / `Port xxx was already in use`**。
- **compile 与 spring-boot:run 不可并发**：target/classes 交错写会丢 class（症状 `ClassNotFoundException` / `Failed to parse mapping resource`）→ `rm -rf target/classes` + clean compile。
- 手工起后端：`dependency:build-classpath -Dmdep.outputFile=target/cp.txt` → java argfile `target/run.args`（`-cp "target/classes;<cp>"`，**反斜杠全转正斜杠**）→ 用**后台任务前台跑** `java @target/run.args`。不要经 `cmd.exe`/`mvn.cmd`（安全策略拦）；`Start-Process -WindowStyle Hidden` 起的进程会被沙箱杀掉（banner 打完就消失、无异常、jps 看不到）。`.bat` 别从 Git Bash 跑。

## 数据库
- MySQL `localhost:3306`，root/admin123，库 `demand_system`；改端口后必须重新 compile。
- **列注释不可信**：枚举值运行时从字典表读（`priorities`/`requirement_types`/`workflow_states`，终态 `is_final=1`）。
- 未接 Flyway：`database/migrations/*.sql` 手动 `docker exec -i mysql` 执行，并同步进 `database/init.sql`。
- 手写 SQL 自带 `AND deleted_at = 0`（`@TableLogic` 只管 MP 生成的 SQL）。
- **`docker exec mysql mysql` 的 `character_set_client` 默认 latin1**：中文条件 `DELETE ... WHERE 列='中文'` **静默命中 0 行**，必须加 `--default-character-set=utf8mb4`。

## 验证技巧
- 本机 http(s)_proxy 把本地请求拦成 502：curl 用 `--noproxy '*'`；Python urllib 需 `ProxyHandler({})`。
- Playwright：复用 `demand_frontend/node_modules`；dev server 绑 `127.0.0.1:5170`；token 在 cookie `access_token`（非 localStorage）；**`NODE_PATH` 必须传 Windows 路径**（`/e/...` 形式解析不到，报 Cannot find module 'playwright'）。
- 无 mysql CLI → JDK harness + `mysql-connector-j`，中文输出加 `-Dstdout.encoding=UTF-8`。
- **页面内 `fetch` 默认不带鉴权**：token 虽在 cookie `access_token`，但后端认 `Authorization` 头。
  `page.evaluate` 里要手动带 `Bearer`（token 从 `ctx.cookies(BASE)` 取），否则全接口 401
  `未登录或登录已过期` —— 而 UI 操作其实是成功的，极易误判成后端挂了。
- **编辑被中断会留下「部分清空」的 target/classes**（目录空、class 数骤降）→ 直接
  `rm -rf target/classes` 全量重编，别增量补；症状常伪装成登录 500 + `NoClassDefFoundError: Xxx$Builder`。

## 编码规范
- Service 放业务逻辑，Controller 只校验调用；当前用户 `SecurityUtils.getCurrentUserId()`。
- **同一文件不要在同一条消息里发多个 Edit**（静默丢失），改完 grep 复核。
- 模块结构 `module/<name>/{controller,service,mapper,entity,dto,converter}`；JSON 列用 `@TableField(typeHandler=JacksonTypeHandler.class)` + `@TableName(autoResultMap=true)`。
- 前端 Composition API + `<script setup>`；API `api/modules/`、状态 `stores/`、类型 `types/`；**`<script setup>` 不能 `export interface`** → 共享类型另开 `.ts`。

## UI 文案原则（用户明确要求；规划阶段就要按这个来）
- **提示语极简，禁止过度提示**。不要在输入框/按钮旁再补一句复述 UI 已有信息的话：
  「输入新的 xxx 名称」「回车确认」「（清空 = 移出分组）」「可在字段配置中恢复」「请先添加数据」都属违规。
- **`ElMessageBox.prompt` 第一个参数（message）传 `''`** 就能让说明整块不渲染
  （模板是 `v-if="hasMessage"`，`hasMessage = !!state.message`）→ 不留空行、不需要 CSS 兜。
  所有"输入名称"型弹框一律传 `''`。
- **toast 只报结果**，不写后续指引；空状态只写状态，不写"点击右上角创建"这类指路文案。
- **"需要解释的功能不是优雅设计"**：若一处 UI 不写说明就看不懂，那是**设计问题**，
  要在**规划阶段**改设计（把操作变显式、把状态变可见），**不是**再加一行提示语。
  评审方案时遇到"必须加说明才说得清"的设计，先回头改设计再写代码。
- **例外（文案承载真实信息时保留）**：选项清单（`requirements/create.vue` 的模板列表）、
  行为/可见性说明（`ApplicationRecordsDialog` 的拒绝原因）、权限级别定义（`PermissionManageDialog` 的 `level.desc`）、
  前置条件校验（「请先选择工作流」「请先添加字段」）。

## 多维表格字段属性（跨视图取值口径）
- **取值口径只有一个**：`utils/bitableFieldConfig.ts` 的 `formatCellDisplay(field, cell)`（单元格文本）
  与 `resolveRecordTitle(fields, record, preferredFieldId?)`（卡片标题）。
  网格/看板/画廊/日历/甘特全部调它。**禁止再在任何视图里自己写
  `cell.valueText ?? cell.valueNumber ?? ...` 的兜底链** —— 曾因此出现
  「进度 85 在网格显示 85%、在看板显示 8500%」。
- **日期占位符要认两套写法**：`DATE_FORMAT_OPTIONS`/默认 config 用**大写** `YYYY-MM-DD`，
  Element Plus 的 `value-format` 用**小写** `yyyy-MM-dd`。`formatDateCell` 必须同时替换
  `YYYY|yyyy` 与 `DD|dd`（曾只认小写 → 日期列原样显示 `YYYY/03/DD`）。
- **关联/人员/群组列（`user`/`group`/`link`/`bidirectional_link`）不给文本编辑器**：
  值可能是 id 数组或 `[{id,name}]`，文本编辑器会把 `String(数组)` 写回库破坏关联。
  用 `BitableRelation` 渲染 + `editRender = null`。
- **`bitable_field_permissions` 没有外键**：删字段/删表不会级联 → 必须显式
  `deleteByFieldId` / `deleteByTableId`（已接进 `BitableFieldServiceImpl#deleteField`、
  `BitableTableServiceImpl#deleteTable`），否则留孤儿行且唯一键会挡住重新配置。
- **创建类接口返回的是裸 ID**（base/table/field/record/view 全是 number），
  但 `api/modules/bitable.ts` 的 TS 类型声明成了对象（**类型撒谎**）→
  一律 `typeof x === 'object' ? x.id : Number(x)` 兜。
- 回归：`verify-bitable-field-attributes.py`(49) + `verify-bitable-field-attribute-ui.cjs`(51)
  + `verify-bitable-field-attr-cross-view.cjs`(24)，都自建自清理。
- 已知缺口：日历/甘特的「日期字段」选择未持久化（`ViewConfig.calendar.startFieldId` 已预留未接线）。

## 后端通用踩坑
- **`@TableLogic` 实体禁用 `getDeletedAt() != null` 判空**：能查出来的行必然未删除，而存活行 `deleted_at` 是 `0`（Integer 非 null）→ 该判断**恒为 true**。只判 `xxx == null`；判已删除写 `!= null && != 0`。
- **修掉"永远抛异常"的守卫后，必须回头查被它挡住的分支还缺什么**（曾因此遮住越权漏洞，详见 `topics/bitable.md`）。
- **`@Valid` 在方法体前跑**：DTO 上 `@NotNull` 字段即使 controller 用 `@PathVariable` 覆盖，body 也必须带。
- **`POST /auth/login` 响应不含 user**（只有 token 字段），要 userId 得再调 `GET /auth/me`。
- **`GET /v1/users/active`（选人下拉数据源）只返回有归属的用户**（org/region/department 至少一个非空）；`/auth/register` 建的用户三字段全空 → 不出现在下拉（既定行为）。测试脚本要 `UPDATE users SET org_id=1, region_id=1`。
- 数据权限 `RequirementServiceImpl#resolveVisibleOrgIds(userId, isSuperAdmin)`。
- **定位 SQL 类问题**：给 run.args 追加 `--logging.level.<mapper包>=DEBUG` 重启，看 `Preparing:` / `Total: n`，分辨"行没查到"还是"Java 守卫写错"。

## 用户管理（邀请成员 / 申请记录 / 批量管理）
- 表 `sys_invitations`（链接/批量共用）+ `sys_join_requests`；迁移 `2026-09-15-user-invitation.sql` 已同步进 init.sql。
- 闭环：生成邀请 → 打开 `/public/invite/{code}` 提交 → pending 申请 → 审批通过自动建号（账号名取邮箱前缀剔除非 `[a-zA-Z0-9_]`，退化 `u`+手机后 8 位）+ 归组织 + 授角色 → 邀请回写 `accepted`。
- 后端 `module/invitation/`；`UserService.batchUpdateStatus/batchDelete`（`POST /v1/users/batch/status|delete`）；匿名侧 `/api/v1/public/invitations`（`SecurityConfig` permitAll）。
- **「已过期」不落库** = `pending + expires_at < now` 派生，Java 侧算，别指望定时任务。
- 批量邀请 `max_uses=1`（一人一码）且**逐行返回跳过原因**；链接邀请默认不限次 + 7 天。
- 守卫：不能改自己状态；主管理员(id=1) 不可停用/删除；审批时手机号已是成员直接报错。
- 前端 `components/InviteMemberDialog.vue` / `ApplicationRecordsDialog.vue` / `views/public/InviteAcceptPage.vue`；`api/modules/invitation.ts`。
- **`users.status` 只能 `active`/`inactive`**（ENUM + STRICT_TRANS_TABLES）：前端曾写 `disabled` → 单行停用一直报数据截断，已修；后端 `normalizeStatus` 兼容旧值。
- 回归：`verify-user-invitation-api.py`(42) + `verify-user-invitation-ui.cjs`(29)，都自清理。

## AI 助手 / NL2SQL / 模型配置
- 入口 `SystemAssistant.vue`；`AssistantServiceImpl#dispatchByIntent` 分发 data_query(NL2SQL)/knowledge_qa/web_search/导航。
- **禁止重复入口**（用户明确要求，助手弹框尤其严）：改助手 UI 后三个回归脚本都要跑。
- NL2SQL 安全铁律：模型 SQL 一律不可信，测试 `module/nl2sql/`(98) 改安全逻辑必跑。
- 模型解析铁律：未绑定时取该类型 `is_default=1` 的**全局默认**模型，不是 `assistant.chat` 绑的。
- 以上细节全部在 `memory/topics/ai-assistant.md`。
