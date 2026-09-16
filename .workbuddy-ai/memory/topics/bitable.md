# 专题：bitable 分组树 + 数据表级权限管理

> 从 `MEMORY.md` 拆出（原文过长导致注入截断）。改动这两个子系统前先读本文件。

## 分组树

- **两个分组树别混淆**：
  - `TableGroupTree.vue` = 编辑器左侧「数据表」树，表分组 `bitable_table_groups`，key `t:`/`g:`/`ug:`，css 前缀 `tgt-*`。
  - `BaseGroupTree.vue` = 列表页左侧「多维表格」树，Base 分组 `bitable_base_groups`，key `b:`/`g:`，css 前缀 `bgtree-*`。
- `TableGroupTree.vue` 必须**先压平嵌套树再按 parentId 过滤**，否则静默查不到子节点。
- **Base 树归属依赖 `BitableBaseMapper.xml` 手写 SELECT 里的 `group_id`**：三个查询
  （`selectByCreator` / `selectByMember` / `selectDetailById`）**任一漏列**都会让
  `GET /bases` 的 `groupId` 恒为 null → 全部 Base 落进「未分组」、自定义分组永远空、
  右侧「共 0 个」。**指纹：分组徽标计数有值（走 MP 自动 SQL，含全列）但展开后没有子节点**。
  写操作（`PUT /bases/{id}/group`）是好的，光看 DB 会误判成"没保存"。
  回归：`verify-base-group-move.cjs`(32)。
- 折叠状态按 base 存 `localStorage['bitable.tableGroupTree.collapsed.<baseId>']`；搜索高亮用 `highlightSegments()` 切片段 + `<em>`，**不要用 v-html**。
- `activeTableId` watcher 的 `{ immediate: true }` 不要无条件写 `collapsedKeys`：已改为 `if (next.size !== collapsedKeys.value.size)` 再赋值，否则首次进入页面就产生一次无意义的持久化写入。
- 后端 `BitableTableGroupController`（树/建/改名/move/delete + `tables/{id}/group`）全部经 `BitableAuthorizationService` 校验；建表/改表也要校验 `group_id` 归属同一 base。
- 编辑器 `TableGroupTree.vue` 菜单：`tableMenu()` = 重命名/移动到分组…/删除数据表；`groupMenu()` = 新建子分组/新建数据表/重命名/删除分组。
  emit 风格**故意不一致**：`rename-group` 传对象 `{id,name}`，`rename-table` 传**位置参数** `(tableId, name)` —— 这样能直接接上 editor.vue 里 Toolbar 早就用的 `handleRenameTable`。
- `BaseGroupTree.vue` 节点菜单：`baseMenu()` = 打开/重命名/仪表盘/移动到分组…/删除；`groupMenu()` = 新建多维表格/重命名/删除分组。重命名都走 `ElMessageBox.prompt`，名称未变则不发请求。
- 编辑器面包屑三级可点：`多维表格` → `/bitable`；`{base}` → 回退到默认数据表+默认视图；第三级 = 当前数据表名（span，不可点）。
  **回退必须先 `await router.replace({query: 去掉 viewId})` 再 `handleSelectTable(tables[0].id)`** —— `setActiveView()` 读的是 `route.query.viewId`，不清掉会沿用上一张表带过来的视图 ID。
  面包屑用 `<button>` 承载，靠 `.editor-header__crumb.is-clickable` 抹默认按钮样式（border/background/font inherit）并补 hover 与 `:focus-visible`。

## 回归脚本

- 分组树三件套（共 53 项）：`verify-table-group-tree.cjs`(19) / `verify-table-group-edge.cjs`(16) / `verify-table-group-polish.cjs`(18)；跑完用 `cleanup-table-group-testdata.cjs` 清测试分组并把数据表拖回根层级。
- **polish 套件的收尾断言是"树恢复 4 节点"**：若之前跑过 tree 套件，会残留 `进销存系统（JXC）` / `采购管理` → 首轮 17/18。**先 `cleanup-table-group-testdata.cjs` 再跑 polish** 即 18/18。
- `verify-base-group-rename.cjs`(17)：Base 树重命名。
- `verify-base-group-move.cjs`(32)：Base 树「移动到分组」往返（未分组↔自定义分组）+ 刷新持久化，
  自清理（临时分组 `__MOVE_TGT` 建→用→删）。**改 Base 树归属/移动逻辑必跑**。
  踩坑：点击分组行会同时选中并 `toggleExpand`（折叠），之后按 `data-key` 定位其下 Base 会找不到 →
  脚本内用 `ensureExpanded(key)` 读 `.bgtree-node__chev.is-open` 补点一次。
- `verify-editor-breadcrumb-and-table-rename.cjs`(29)：改编辑器面包屑或树菜单必跑。断言面包屑 tag（button/button/span）、切表跟随、点 base 回退默认表、点「多维表格」回列表、数据表重命名（含刷新后落库 + 改回原名）、分组重命名、0 JS 报错。脚本检测到 base 只有 1 张数据表时会**临时新建 `__ED_TMP_TBL`** 覆盖「切表后回退」用例并自动删除，换任意 base 都能跑。
- **权限弹窗四件套（共 83 项，改 `PermissionManageDialog.vue` 全跑）**：
  `verify-role-member-page.cjs`(28，成员维护页 + `属于N个角色` + 勾选多人) /
  `verify-role-member-avatar.cjs`(28，重命名/头像缩略图) /
  `verify-system-role-members.cjs`(19，系统角色增删成员 + 一人一角色语义) /
  `verify-member-entry-consolidated.cjs`(8，「成员管理」入口已收敛)。全部自清理。
  加人交互已从下拉改成 `.member-pick` 内联勾选列表，**旧脚本里 `el-select-dropdown__item` 的写法要同步换掉**；
  `verify-system-role-members.cjs` 的 `closeAddMemberDialog()` 已去掉 `Escape`（没下拉了，Escape 会直接关掉 el-dialog）。

## Element Plus / Playwright 踩坑（这两个树专用）

- `ElTreeSelect` 渲染成 `.el-select`（**没有** `el-tree-select` 类名），选项是 `.el-select-dropdown__item`，label 带全角缩进 → 比较要用 `includes`。
- `ElMessageBox.prompt` 输入框是 `.el-message-box__input input`（`.el-message-box input` 会命中按钮区），确认按钮 `.el-message-box__btns .el-button--primary`。
- **`ElMessageBox.prompt` 第一个参数（message）传 `''` 会让说明文案整块不渲染**：模板里是 `v-if="hasMessage"`，`hasMessage = !!state.message`，所以不会留空行、不需要 CSS 兜。**改名/新建这类"输入名称"弹框一律传 `''`**（用户明确要求：标题已说明动作，输入框本身就是提示，别再顶一行「输入新的 xxx 名称」）。
  但 message 承载真实信息时（如 `requirements/create.vue` 的模板选项清单、`ApplicationRecordsDialog` 的行为说明）要保留。
- Playwright 定位树节点必须带 `data-key` 消歧（`.tgt-node[data-nid=x]` 会同时命中分组与其下的未分组节点）。
- `.el-message` 会堆叠，断言前先 `waitForFunction(() => !document.querySelector('.el-message'))` 再取 `.last()`。

## 操作记录（OperationHistoryPanel）

- 面板 `views/bitable/components/OperationHistoryPanel.vue`，入口在编辑器 Toolbar「更多 → 操作记录」。
- **筛选能力后端早就全有**：`/bases/{id}/operations` 与 `/tables/{id}/operations` 都接
  `userId` / `startTime` / `endTime`（mapper `filterConditions` 里 `created_at >= / <=`）。
  要加筛选条件时先看后端有没有，别重复造。
- 时间范围走 `daterange` + `value-format="YYYY-MM-DD"`，发请求补 `T00:00:00`/`T23:59:59`。
  带 `:shortcuts`（今天/最近 7 天/最近 30 天）——**同时是回归脚本的确定性抓手**。
- 操作人下拉数据源 = `listBaseMembers(baseId)`；**只有 Base 成员能被筛**，
  已退出成员的记录筛不到（用户知情的取舍）。接口失败时降级为「记录里出现过的用户」。
- **合并重复的签名规则**：`operationType + userId + fieldId/recordId/tableId/name/role + newValue`，
  且相邻两条间隔 ≤ 10 分钟（`MERGE_WINDOW_MS`）。`newValue` 必须进签名 ——
  「工时 → 5」与「工时 → -0.04」是两次不同编辑，合并会丢信息。
- 默认**收起**（展开一键可达）；`expandAll` / `mergeDuplicates` 是**展示偏好**写 localStorage，
  **不是筛选条件**，所以不在 `handleOpen` 里重置。筛选条件（范围/类型/时间/操作人）每次打开都重置。
- `formatValue()` 负责把 `{"valueDate":"..."}` / `{"valueText":""}` 这类单元格值对象
  渲染成人类可读文本；新增单元格类型时在这里补 key。
- 回归：`scripts/verify-operation-history-filter.cjs`(47)。脚本会**临时**把 user 40 加为
  base 15 的 viewer 来验证「筛到无操作用户 → 暂无操作记录」，`finally` 里移除。

## 数据表级权限管理

- 3 张表：`bitable_base_custom_roles`（按 base 隔离）/ `bitable_base_custom_role_members`（user/dept）/ `bitable_base_role_permissions`（角色 × 表 × 类型 × 级别）。系统角色复用 `MemberRole` 枚举，成员读 `bitable_base_members`。
- `bitable_base_members` **没有 `deleted_at` 列**（`removeMember` 物理删除），写核对 SQL 别给它加软删条件，否则 `Unknown column`。
- 四级权限 `full/edit/view/none`；**只有 `data` 一种权限类型**（「自动化权限」tab 已按用户要求移除，`activeTab` ref 保留但恒为 `'data'`，DB 里历史 `automation` 行已成死数据）；**无兜底角色，未配置即无权限**。
- **「高级权限」是成员管理的唯一入口**（用户明确要求「同一功能只留一个入口」）：Toolbar「更多」里的「成员管理」菜单项、`editor.vue` 的接线、`MemberManager.vue` **已全部删除，不要再加回来**。回归 `verify-member-entry-consolidated.cjs`(8) 专门守这条。
  已知取舍：`GET /bases/{id}/members` 只要 VIEWER，高级权限要 ADMIN —— 收敛后**只读/评论者看不到成员名单**，用户知情并接受。
- **前端攒变更再提交**（用户明确要求）：`pendingChanges: Map<`${roleType}:${roleId}:${tableId}:${permType}`, PermissionLevel>`；点选项/批量设置只写本地 Map，底部「保存」才调 `POST /bases/{baseId}/role-permissions/batch-save` 事务批量提交；有未保存改动关闭弹窗会二次确认。**别再退回"点一项存一项"的实时保存**。
- **角色栏支持重命名/删除 + 成员头像条**：自定义角色行 hover 出 `.perm-role-item__more`（`el-dropdown`，trigger=click）→ 重命名/删除角色；tab 上方 `.perm-members` 用 `el-avatar :src="member.memberAvatar || undefined"`，无头像走 fallback 插槽放**姓名首字** + `avatarColor(name)` 稳定底色。
- **系统角色也能直接增删成员**（用户明确要求；旧的「系统角色只读 / 去协作成员维护」提示已删除，**别再改回去**）：系统角色走 `addBaseMember(baseId,{userId,role:systemRoleCode})` / `removeBaseMember(baseId,userId)`，自定义角色走 `addRoleMember` / `removeRoleMember`。
  **`bitable_base_members` 一人只有一个系统角色**，所以"加人"本质是**调整**而非并列新增 —— 选人列表必须标注「当前：X」（`memberRoleByUserId`），并用 `.member-add-dialog__note` 说明；`isRemovableMember()` 让**所有者成员不可移除**。
  `addMember` 是 **upsert**，授予 owner = **所有权转移**（要求操作者是 OWNER 并降级其他 owner）；`removeMember` 是**物理删除**（失去该 Base 全部权限）。
- **重拉角色后必须 `reselectRole(prev)`**：否则 `selectedRole` 仍引用旧对象，成员列表与权限徽标都不刷新。回归：`verify-role-member-avatar.cjs`(28) + `verify-system-role-members.cjs`(19)，**改这个弹窗两个都要跑**。
- **每个角色有独立的「成员维护页」**（`panelMode: 'permission' | 'members'`，参考设计稿「X包含的成员」）：成员条右侧 `.perm-members__manage` 进入；成员页 = 返回箭头 + `{{角色}}包含的成员` + 搜索 + `+` 加人 + 成员行（头像、`属于N个角色`、移除）。`'members'` 态用 `<main v-if>` + `<template v-else>` 替换掉原来的 main+aside，**不显示右侧权限面板**。打开弹窗时重置回 `'permission'`。
- **`属于N个角色` 是纯前端聚合，不要加后端接口**：`rolesByUserId` 把已加载的全部角色（系统+自定义）按 `memberId` 聚合，`rolesOfMember()/roleCountOf()` 供模板用。后端 `listRolesWithPermissions` 本来就把每个角色的 members 都填好了（`vo.setMembers(...)` + `fillMemberProfiles` 遍历全部角色）。
- **加人用「内联勾选列表」，不要退回 `el-select` 下拉**（用户要求「勾选多个用户」）：`.member-add-dialog__list`（`max-height:260px; overflow-y:auto`）+ 每行 `el-checkbox.member-pick`，配 `pickKeyword` / `filteredAvailableUsers` / `toggleMemberPick()` / `newMemberUserIds: number[]`。
  **原因（实测过，别再试下拉）**：`el-select multiple` 的浮层会**盖住弹窗底部「确定」**——不过滤时下拉 box `y 307~581`，而按钮中心 `y≈396`，`elementFromPoint` 命中的是 `<li.el-select-dropdown__item>`。真实用户点「确定」会**点到候选行、把刚勾的人又取消掉**（静默改错数据）。`placement="top-start"` 也救不了：`el-select` 上方只有约 80px，popper 空间不足会自动翻回下方。
- 系统角色勾多人 = 逐个 `addBaseMember`（upsert 成该角色）；**所有者是所有权转移**，`systemRoleCode === 'owner' && userIds.length > 1` 直接 warning 拒绝。
- **`bitable_base_role_permissions` 关联自定义角色的列叫 `custom_role_id`，不是 `role_id`**（`bitable_base_custom_role_members` 才叫 `role_id`）。清理 SQL 写错会抛异常并**跳过后续清理**——清理语句要逐条独立 try。
- 成员页每行一个 `el-popover` → **未展开的浮层也在 DOM 里**，定位浮层内容必须加 `:visible`（否则 strict mode violation）；`popper-class` 渲染在 body 上，样式要用 `:global(.xxx)`。

## 授权校验的历史坑（重要）

- **`BitableBaseRoleServiceImpl` 的守卫只能写 `if (role == null)`**：`@TableLogic` 已让 `selectById` 追加 `deleted_at=0`，能查出来的行必然未删除，而存活行的 `deleted_at` 是 `0`（Integer 非 null），所以 `role.getDeletedAt() != null` **恒为 true** → 改名/删除/成员增删 100% 抛「角色不存在」。
- 改这个方法时同时确认 `checkManagePermission(role.getBaseId(), userId)` 在：`updateCustomRole` / `deleteCustomRole` / `removeRoleMember` 曾因那个恒真守卫掩盖而**一直没有任何授权校验**（只有 `addRoleMember` / `setRolePermission` 有）。已补：三个方法签名加 `Long userId`，controller 取 `SecurityUtils.getCurrentUserId()` 透传。回归 `scripts/verify-role-authz.mjs`(20)。
