# 专题：bitable 分组树 + 数据表级权限管理

> 从 `MEMORY.md` 拆出（原文过长导致注入截断）。改动这两个子系统前先读本文件。

## 分组树

- **两个分组树别混淆**：
  - `TableGroupTree.vue` = **已删除**（2026-09-19 清理，`grep -rn TableGroupTree` 只剩日志与本文件）：
    编辑器的左侧树在本轮重构里被摘掉，表/分组管理统一搬到列表页的 `BaseGroupTree`。
    连带的 4 个失效脚本（`verify-table-group-tree/edge/polish.cjs`、`cleanup-table-group-testdata.cjs`）
    也一并删除 —— 它们都 `goto('/bitable/:baseId')` 后驱动 `.tgt-node`，而编辑器已无这棵树。
    断言已按统一树重写进 `verify-bitable-group-tree-ui.cjs`(56)。
    **注意两套后端的区别**：被删的那套 UI 用的是 `table-groups`（`/bases/{baseId}/table-groups`，按 Base 分表）；
    列表页统一树用的是 **`base-groups`**（`/v1/bitable/base-groups`，按 Base 分组）。
    `verify-table-group-api.cjs`(28) 测的是**旧 table-groups** 语义，后端仍在、脚本仍绿，
    但已**没有对应 UI**，改 `base-groups` 时别拿它当护栏。
  - `BaseGroupTree.vue` = 列表页左侧「数据」树，**已重构为统一树**：`NodeKind =
    all | ungrouped | group | base | table | dashboard | editor`，key `all` / `t:<表id>` /
    `d:<仪表盘id>` / `g:<分组id>`，css 前缀 `bgtree-*`（节点修饰类 `bgtree-node--table/--dashboard/--group`）。
    当前数据下**不渲染 `--base` 节点**；点数据表走 `@open-table` → `index.vue` 用
    `<BitableEditor embedded>` 在右侧内嵌渲染（**用户截图里的「工具栏」就是这个内嵌编辑器**）。
  - 统一树节点菜单（`tableMenu`/`dashboardMenu`）= 重命名 / 删除，**「打开」已移除**
    （点节点行本身就是打开，属重复入口）；重命名是**行内编辑**（`.bgtree-node__rename input`），
    不是 `ElMessageBox.prompt`。分组菜单 = 新建数据表 / 重命名 / 删除分组。
  - 统一树的**选中态语义**（实测，写断言前必读）：`isActive` 只在 `group` 节点
    （`isGroupActive`）与 `all` 节点（`selection.type === 'all'`）上可能为 true；
    `toTableNode`/`toDashboardNode` 里 **`isActive` 硬编码 `false`** ——
    表/仪表盘是**导航目标**（点行 = 打开编辑器），不是筛选范围。
  - **点分组行永远只能展开、不能折叠**：`handleClick` 先 `emit('select', {type:'group',...})`（每次都是**新对象**）
    → `props.selection` 引用变化 → `watch` 里的 `revealSelection()` 把 `g:<id>` 从 `collapsedKeys` 里删掉
    → 强制展开。**折叠只能点箭头**（`.bgtree-node__chev`，`@click.stop`，不触发 select）。
  - 统一树折叠持久化 key 是 `localStorage['bitable.baseGroupTree.collapsed']`（**不是**旧树的
    `bitable.tableGroupTree.collapsed.<baseId>`）；存的是折叠 key 数组（如 `["all","g:5"]`）。
  - 「全部」是**平铺视图**：折叠时每张表只出现一次（在其归属处）；展开后**同一张表会出现两次**
    （`allTables()` 平铺一份 + `pushGroupLeaves`/`walkGroups` 的归属一份），
    平铺那份 depth=1（`paddingLeft = 6 + depth*14` = 20）。拖拽时「全部」不能作为父级。
  - 已知**不可达分支**（2026-09-19 实测，别当 bug 修、也别写断言期待它出现）：
    模板 `.bgtree-empty` 的 `v-if="!nodes.length"` 永不成立（`nodes` 恒含「全部」节点）；
    `kind === 'ungrouped'` 的图标分支与 `ugKey` 也不可达（`nodes` 从不产出 ungrouped）；
    `baseMenu()` 及其 `handleCommand` 的 `node.kind === 'base'` 整段不可达
    （`baseMenu` 有注释说明是**有意保留**以便 base 节点回归，别删），
    连带 `openMoveDialog` / 「移动到分组」对话框 / `moveOptions` 也是死 UI。
  - **删除分组确认框的说明文案只在分组内有「表/仪表盘」时出现**：
    `count` 只统计叶子（`tablesOfGroup + dashboardsOfGroup`），**不含子分组**。
    所以一个只含子分组的分组，删它时**不会**提示「子分组将上移到父级」——写断言时要么放个表进去，
    要么就别期待这段文案。
- `TableGroupTree.vue` 必须**先压平嵌套树再按 parentId 过滤**，否则静默查不到子节点。
- **Base 树归属依赖 `BitableBaseMapper.xml` 手写 SELECT 里的 `group_id`**：三个查询
  （`selectByCreator` / `selectByMember` / `selectDetailById`）**任一漏列**都会让
  `GET /bases` 的 `groupId` 恒为 null → 全部 Base 落进「未分组」、自定义分组永远空、
  右侧「共 0 个」。**指纹：分组徽标计数有值（走 MP 自动 SQL，含全列）但展开后没有子节点**。
  写操作（`PUT /bases/{id}/group`）是好的，光看 DB 会误判成"没保存"。
  回归：`verify-base-group-move.cjs`(32)。
- 搜索高亮用 `highlightSegments()` 切片段 + `<em>`，**不要用 v-html**。
  （旧树的折叠 key `bitable.tableGroupTree.collapsed.<baseId>` 已随 `TableGroupTree` 一起作废；
  统一树是 `bitable.baseGroupTree.collapsed`，见上方。）
- `activeTableId` watcher 的 `{ immediate: true }` 不要无条件写 `collapsedKeys`：已改为 `if (next.size !== collapsedKeys.value.size)` 再赋值，否则首次进入页面就产生一次无意义的持久化写入。
- 后端 `BitableTableGroupController`（树/建/改名/move/delete + `tables/{id}/group`）全部经 `BitableAuthorizationService` 校验；建表/改表也要校验 `group_id` 归属同一 base。
- （**该文件 2026-09-19 已删除**，以下仅作历史参考）编辑器 `TableGroupTree.vue` 菜单：`tableMenu()` = 重命名/移动到分组…/删除数据表；`groupMenu()` = 新建子分组/新建数据表/重命名/删除分组。
  emit 风格**故意不一致**：`rename-group` 传对象 `{id,name}`，`rename-table` 传**位置参数** `(tableId, name)` —— 这样能直接接上 editor.vue 里 Toolbar 早就用的 `handleRenameTable`。
- `BaseGroupTree.vue` 节点菜单（2026-09-19 核对）：
  `tableMenu()` = 重命名/删除，`dashboardMenu()` = 重命名/删除 —— **都没有「打开」**
  （点节点行就是打开，`handleCommand('open')` 与 `handleClick` 是同一个 emit，属重复入口）；
  `groupMenu()` = 新建数据表/重命名/删除分组；
  `baseMenu()` = 重命名/打开仪表盘/新建仪表盘/新建数据表/移动到分组…/删除，但**当前是死代码**
  （`nodes` computed 只产出 all/group/table/dashboard/editor，从不产出 base 节点）。
  重命名是**行内编辑**，不是 `ElMessageBox.prompt`（早期版本的描述已作废）。
- 编辑器面包屑三级可点：`多维表格` → `/bitable`；`{base}` → 回退到默认数据表+默认视图；第三级 = 当前数据表名（span，不可点）。
  **回退必须先 `await router.replace({query: 去掉 viewId})` 再 `handleSelectTable(tables[0].id)`** —— `setActiveView()` 读的是 `route.query.viewId`，不清掉会沿用上一张表带过来的视图 ID。
  面包屑用 `<button>` 承载，靠 `.editor-header__crumb.is-clickable` 抹默认按钮样式（border/background/font inherit）并补 hover 与 `:focus-visible`。
- **工具栏不再显示数据表文件名**（用户要求「去掉显示与占位」）：`Toolbar.vue` 的
  `.bitable-toolbar__name` / 内联改名 `el-input`、`editingName`/`tempName`/`startEditName`/
  `handleNameBlur`/`cancelEdit`、`renameTable` emit、对应 CSS 全部已删。`__left` 首元素 = 视图选择器。
  **表名只在面包屑第三级展示；重命名入口只在列表页统一树的节点菜单** —— 不要再往工具栏加回文件名。
  回归：`scripts/verify-toolbar-no-table-name.cjs`(10)。
- **导航/重命名回归**：`scripts/verify-bitable-tree-nav-and-rename.cjs`(27)，替代已失效的
  `verify-editor-breadcrumb-and-table-rename.cjs`（**已删除**）。覆盖：统一树点表→内嵌编辑器、
  面包屑三级 tag(button/button/span)、第二级回退默认数据表（`tables[0]`，**实测有效**）、
  独立编辑器 `/bitable/:baseId` 的「返回」与「多维表格」回列表、节点菜单行内重命名（表 + 分组，
  含刷新落库与改回原名）。自清理、幂等（连跑两次 27/0）。
  踩坑：树上每个节点的 `el-dropdown` 浮层**全部挂在 body 上**（未展开的也在 DOM 里），
  定位菜单项必须 `.el-dropdown-menu__item:visible` + `hasText`，否则会命中隐藏项并一直等它可见。

## 回归脚本

- **叶子跨层级移动**：`verify-bitable-leaf-move.cjs`(18)。核心回归：**拖单个数据表/仪表盘到别的分组
  只动该叶子**（历史 bug：拖表 = 拖整个 Base，把原分组同 Base 的表全带走）。
  覆盖：拖 T1 到 G2 后 `base_group_id=g2` 且同 Base 的 T2 归属仍为 null、Base 归属不变、
  树渲染 T1→G2、刷新持久化、拖回 G1（**注意：拖回后 `base_group_id=g1` 显式独立归属，不是复位 null**）、
  往返后 `leaves/sort` 不误伤、**仪表盘 move 落库 + 批量接口透出 + 树渲染跟随**（API 直调建的节点要 `page.reload` 后树才有）。自清理 + **连跑两次 23/0**。
  踩坑 1：**空分组的 chev 是 `visibility:hidden`（`is-leaf` 类）**，脚本不能点箭头展开
  （分组默认就是展开的，collapsedKeys 默认只含 'all'）。
  踩坑 2：**断言必须轮询等待**（`waitFor` + 后端 `base_group_id` 真值），固定 sleep 会跑在
  异步刷新前面；且 try 块的异常若被 `finally` 里的 `process.exit` 吞掉，核心断言没跑也显示「全绿」
  ——必须 `catch` 落为 FAIL。
- **叶子级分组归属数据模型（2026-09-19）**：`bitable_tables.base_group_id` /
  `bitable_dashboards.base_group_id`（NULL=跟随所属 Base 分组，迁移
  `V20260919_01__bitable_leaf_group.sql`，已同步 init.sql）。
  - 后端：`PUT /api/v1/bitable/leaves/move`（`BitableLeafMoveRequest{kind,id,targetGroupId}`）只移单个叶子；
    `sortLeaves` 的「同一层级」校验改为按**叶子有效分组**（`COALESCE(base_group_id, base.group_id)`，
    锚点取第一个叶子的有效分组，不再用 baseId 的分组）；两个 mapper XML 的
    `countSameNameInGroupScope`/`selectMaxSortOrderInGroup` 同步改 COALESCE。
  - 前端：`BaseGroupTree.vue` 的 `baseGroupIdOf` 改名 `leafGroupIdOf(leafBaseId, leafGroupId)`
    （独立归属优先），`commitDrop` 叶子拖拽 emit **`move-leaf`**（不再 emit `move-base`）；
    `isLeafKind` 必须写成 **type predicate**（`kind is 'table' | 'dashboard'`），否则 emit 类型收窄报 TS2769。
  - **MapStruct 大坑**：VO 是纯手写 POJO（无 Lombok），加字段**必须连 getter/setter 一起加**——
    只加字段时 MapStruct 静默不映射（生成代码里没有 `setBaseGroupId`，接口返回恒 null），
    编译不报错、DB 里值是对的，症状像「move 没生效」。检查方法：看
    `target/generated-sources/annotations/.../BitableConverterImpl.java` 里有没有对应 setter 调用。
  - 仪表盘批量接口（`/bases/dashboards/batch`）已补 `baseGroupId` 字段；`listTables` 走 VO 自动带出。

- **分组树 UI 套件**：`verify-bitable-group-tree-ui.cjs`(56)。测的是**统一树 + `base-groups`**，
  覆盖：「全部」默认折叠/计数自洽/展开平铺（同表出现两次）、折叠状态持久化到
  `bitable.baseGroupTree.collapsed`（含刷新后保持）、搜索 `<em>` 高亮（黄底、非斜体）+ 无命中只剩「全部」、
  三层嵌套缩进 6/20/34、点分组激活 + 行点击不折叠而箭头可折叠、长按拖拽拒绝
  （拖入自身子孙 / 拖到「全部」→ `.is-reject`）、删除分组不级联（确认文案 + 子分组上移 + 组内 Base 上移）。
  自清理（`__GTUI_A/B/C` + 临时把某个「未归组且有表」的 Base 移进分组再还原）、**连跑两次 56/0**。
  - 替代已删除的 `verify-table-group-tree/edge/polish.cjs` + `cleanup-table-group-testdata.cjs`（共 53 项）。
  - 踩坑 1：**必须挑「未归组**且**含数据表」的 Base 做「组内 Base 上移」用例**。
    `FINAL`/`周KPI` 这类空 Base 移进分组后 `count` 仍是 0，确认文案里的说明就不会出现。
  - 踩坑 2：拖拽要**真实鼠标**且 `pointerType === 'mouse'`（源码里触摸直接 return）：
    `mouse.move` → `down` → 等 `>300ms`（`PRESS_DELAY_MS`）→ `move` 到目标行中间 50% 区 → 断言 → `up`。
  - 踩坑 3：关闭搜索框不能点「搜索」按钮（`searchOpen` 时该按钮 `v-if` 不渲染），
    要先清空 keyword 再 `Escape`（`closeSearch()` 仅在 keyword 为空时才真关）。
- **`verify-table-group-api.cjs`(28)**：纯接口测**旧 `table-groups`**（见上方「两个分组树」）。
  后端仍在、脚本仍绿，但**已无对应 UI**；不要拿它当 `base-groups` 的护栏。
- `verify-base-group-rename.cjs`(17)：Base 树重命名。
- `verify-base-group-move.cjs`(32)：Base 树「移动到分组」往返（未分组↔自定义分组）+ 刷新持久化，
  自清理（临时分组 `__MOVE_TGT` 建→用→删）。**改 Base 树归属/移动逻辑必跑**。
  踩坑：点分组行会 `toggleExpand`，之后按 `data-key` 定位其下 Base 可能找不到 →
  脚本内用 `ensureExpanded(key)` 读 `.bgtree-node__chev.is-open` 补点一次。
  （注意：**首次点某个未选中的分组只会展开不会折叠** —— `revealSelection` 会把选中分组强制展开；
  只有点已选中的分组才会真的折叠。要可靠折叠请点箭头。）
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
- Playwright 定位树节点必须带 `data-key` 消歧（旧 `.tgt-node` 的 `data-nid` 会同时命中分组与其下的未分组节点；
  统一树用 `.bgtree-node[data-key="t:<id>"]`，同一张表在「全部」展开时会**命中两个**，要 `.first()` 或按缩进区分）。
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
- **所有绑 `:src` 的 avatar 必须过 `resolveAvatarUrl`**（`utils/presetAvatars.ts`）：users.avatar / memberAvatar / presence avatar 存的是 `preset:cartoon-9` 这类原始串，裸绑会被浏览器当 URL 请求 → `ERR_UNKNOWN_URL_SCHEME`，头像裂图。已覆盖：editor 协作者栏（collaborators computed 统一解析）、权限弹窗（fetchRoles 遍历 member + openAddMember **私有** getFilterUsers 也要 map——别只改 useUserOptions 共享缓存就收工）、CommentPanel loadComments、useUserOptions。后端 presence/回填**不改**（原始串是存储格式）。
- **每个角色有独立的「成员维护页」**（`panelMode: 'permission' | 'members'`，参考设计稿「X包含的成员」）：成员条右侧 `.perm-members__manage` 进入；成员页 = 返回箭头 + `{{角色}}包含的成员` + 搜索 + `+` 加人 + 成员行（头像、`属于N个角色`、移除）。`'members'` 态用 `<main v-if>` + `<template v-else>` 替换掉原来的 main+aside，**不显示右侧权限面板**。打开弹窗时重置回 `'permission'`。
- **`属于N个角色` 是纯前端聚合，不要加后端接口**：`rolesByUserId` 把已加载的全部角色（系统+自定义）按 `memberId` 聚合，`rolesOfMember()/roleCountOf()` 供模板用。后端 `listRolesWithPermissions` 本来就把每个角色的 members 都填好了（`vo.setMembers(...)` + `fillMemberProfiles` 遍历全部角色）。
- **加人用「内联勾选列表」，不要退回 `el-select` 下拉**（用户要求「勾选多个用户」）：`.member-add-dialog__list`（`max-height:260px; overflow-y:auto`）+ 每行 `el-checkbox.member-pick`，配 `pickKeyword` / `filteredAvailableUsers` / `toggleMemberPick()` / `newMemberUserIds: number[]`。
  **原因（实测过，别再试下拉）**：`el-select multiple` 的浮层会**盖住弹窗底部「确定」**——不过滤时下拉 box `y 307~581`，而按钮中心 `y≈396`，`elementFromPoint` 命中的是 `<li.el-select-dropdown__item>`。真实用户点「确定」会**点到候选行、把刚勾的人又取消掉**（静默改错数据）。`placement="top-start"` 也救不了：`el-select` 上方只有约 80px，popper 空间不足会自动翻回下方。
- **回归脚本等编辑器加载用 `.bitable-toolbar`，别用 `.editor-sidebar`**：统一树重构后编辑器自身侧栏已移除（类名只剩样式残留），4 个成员脚本曾因此集体 30s 超时；「更多」菜单也不再含「字段配置」（已是工具栏独立按钮），断言别写回去。
- 系统角色勾多人 = 逐个 `addBaseMember`（upsert 成该角色）；**所有者是所有权转移**，`systemRoleCode === 'owner' && userIds.length > 1` 直接 warning 拒绝。
- **`bitable_base_role_permissions` 关联自定义角色的列叫 `custom_role_id`，不是 `role_id`**（`bitable_base_custom_role_members` 才叫 `role_id`）。清理 SQL 写错会抛异常并**跳过后续清理**——清理语句要逐条独立 try。
- 成员页每行一个 `el-popover` → **未展开的浮层也在 DOM 里**，定位浮层内容必须加 `:visible`（否则 strict mode violation）；`popper-class` 渲染在 body 上，样式要用 `:global(.xxx)`。

## 授权校验的历史坑（重要）

- **`BitableBaseRoleServiceImpl` 的守卫只能写 `if (role == null)`**：`@TableLogic` 已让 `selectById` 追加 `deleted_at=0`，能查出来的行必然未删除，而存活行的 `deleted_at` 是 `0`（Integer 非 null），所以 `role.getDeletedAt() != null` **恒为 true** → 改名/删除/成员增删 100% 抛「角色不存在」。
- 改这个方法时同时确认 `checkManagePermission(role.getBaseId(), userId)` 在：`updateCustomRole` / `deleteCustomRole` / `removeRoleMember` 曾因那个恒真守卫掩盖而**一直没有任何授权校验**（只有 `addRoleMember` / `setRolePermission` 有）。已补：三个方法签名加 `Long userId`，controller 取 `SecurityUtils.getCurrentUserId()` 透传。回归 `scripts/verify-role-authz.mjs`(20)。

---

## vxe-table 自定义渲染器（字段属性生效的关键，动 `bitableCellRenderers.ts` 前必读）

### 渲染优先级：`editRender` > `cellRender`
`vxe-table/es/table/src/cell.js` 里：
```js
const renderOpts = editRenderOpts || cellRenderOpts
const renderFn = editRenderOpts
  ? (compConf.renderTableCell || compConf.renderCell)      // 有 editRender 时
  : (compConf.renderTableDefault || compConf.renderDefault) // 只有 cellRender 时
```
**列上同时配了 `editRender` 和 `cellRender` 时，`cellRender` 的 `renderTableDefault` 根本不会被调用。**
这就是「字段属性配了却不生效」的根因 —— 进度不画进度条、货币没符号、电话不脱敏，
全被原生编辑器的纯文本展示顶掉了。

### 因此可编辑列要注册两个钩子
- `renderTableCell` —— 展示态（列上有 editRender 时真正生效的那条），指向本渲染器的展示函数
- `renderTableEdit` —— 编辑态，**委托给原生编辑器**

`VxeUI.renderer.add(name, conf)` 是**合并**语义（补新键不会覆盖已有键），
重复注册同名键只会打一条 `Renderer.X 的参数 Y 重复定义` 警告。

### 委托原生编辑器时**必须把 name 换回原生名**（踩过，症状很隐蔽）
原生编辑器靠 `renderOpts.name` 反查组件实例：
`render/index.js` 的 `getDefaultComponent({name}) → getComponent(name)`。
沿用 `BitableNumber` 这个名字 → `getComponent` 返回 null → `h(null)` →
Vue 抛 **`Invalid vnode type when creating vnode: null`**，
现象是**格子进得了编辑态（有 `col--edit col--active`）却挂不出任何输入框**。
`getEditOns` 里还有一处 `['VxeInput','VxeNumberInput','VxeTextarea','$input','$textarea'].includes(name)`
也会判错（影响 `isImmediate` 与 change 分支）。

```ts
const EDITOR_BINDINGS = { BitableNumber: 'VxeNumberInput', BitableText: 'VxeInput', ... }
renderTableEdit(renderOpts, params) {
  const target = EDITOR_BINDINGS[name]
  const stock = VxeUI.renderer.get(target)
  const rtEdit = stock.renderTableEdit || stock.renderEdit || stock.renderTableDefault
  return rtEdit({ ...renderOpts, name: target }, params)   // ← name 必须换回去
}
```
`VxeRate` / `VxeSwitch` **没有 `renderTableEdit`**，它们的 `renderTableDefault` 本身就是
可交互的编辑态 UI，所以要回落到它（`VxeRate` 的 `renderTableDefault = defaultEditRender`）。

### 内联编辑什么时候提交
`edit-closed` 只由 `handleClearEdit` 派发（`table/module/edit/hook.js`），触发时机是
**点击别的格子**；Enter 只有在 `keyboard-config.isEnter = true` 时才会走到 `handleClearEdit`。
`GridView` 原本没配 `keyboard-config`，所以 **Enter 完全不提交**，只能点别处落库。
已补：
```html
:keyboard-config="{ isArrow:false, isDel:false, isEnter:true, isTab:false,
                    isEdit:false, isChecked:false, isEsc:true }"
```
**只开 `isEnter`**：`isDel` 会让 Del 键清空单元格内容，别乱开。
`isLastEnterAppendRow` 默认为假，回车不会自动追加行（已用探针确认行数/记录数不变）。

### 结构化值必须走自己的渲染器，不能落到文本分支
`GridView#buildRecordData` 里有一组「结构化值」case（`attachment`/`location`/`date_range`/
`user`/`group`/`link`/`bidirectional_link`）保留原始 JSON，落到 default 就会被
`String(对象)` 变成 `[object Object]`。**新增结构化类型时两处都要加**：
`buildRecordData` 的 case + 列构造的 `cellRender`/`editRender = null`。
可编辑列（文本编辑器）**绝不能**接结构化值 —— 会把 `String(数组)` 写回库破坏数据。

### Playwright 交互
- **`fill()` 对「受控 + 带格式化」的 vxe 输入框不可靠**：它直接改 DOM value，
  vxe 的格式化会把旧值拼回去（实测数字列落库 `1234.5777` 而不是 `777.77`）。
  必须走真实键盘：`click({clickCount:3})` → `Control+A` → `Backspace` → `type(v, {delay:30})`。
- `innerText` **不含 `<input>` 的 value**：编辑态单元格在 innerText 里是空的，
  别据此判断「格子没渲染出来」。
- 诊断渲染器问题时，先在浏览器里 `VxeUI.renderer.get(name)` 看键集合、看返回的 vnode，
  比读源码猜快得多。

---

## 字段类型覆盖（后端 `FieldType` ↔ 前端落地的对照）

后端 `FieldType` 有 37 个枚举值，前端**不是每个都落地**。加新类型时按这张清单核对，
漏一项就会出现「选了类型却没有属性可配」或「值渲染成裸 id / [object Object]」：

| 落地点 | 文件 | 漏了会怎样 |
|---|---|---|
| `TYPE_KEYS[type]` 白名单 | `utils/bitableFieldConfig.ts` | `sanitizeFieldConfig` 把专属键全裁掉 → 属性存不进库 |
| `createDefaultFieldConfig` case | 同上 | 面板/切换类型时该键是 `undefined`，`v-model` 直接写会失焦 |
| `validateFieldValue` case | 同上 | 只校验必填，格式/白名单形同虚设 |
| `formatCellDisplay` case | 同上 | 卡片/表单/只读区展示错（结构化值会变 `[object Object]`） |
| `FieldAttributeForm.vue` 小节 | `views/bitable/components/` | **属性面板一片空白**，这正是本次补开发要解决的问题 |
| `FIELD_TYPE_GROUPS` | `views/bitable/editor.vue` | 类型下拉里选不到 |
| `fieldTypeLabelMap` | 同上 | 文案回退成英文 code |
| 网格 `cellRender`（可编辑 + 只读两处 switch） | `views/bitable/components/GridView.vue` | 列展示错 / 结构化值被 String 成裸串 |
| `GridView#buildRecordData` case | 同上 | 同上，且编辑态写回会破坏数据 |
| `RecordEditDialog` `supportedTypes` + 模板分支 + `handleSave` | `views/bitable/components/RecordEditDialog.vue` | 弹框里改不了这个字段 |
| `FormView` 模板分支 + `buildCells` | `views/bitable/components/FormView.vue` | 表单视图收不到值 |

**目前 `department` / `email` 已按上表补全**。仍有几个类型前端只做了「能选、能存文本」的
最低限度（`user`/`group`/`link` 在 `FormView` 里就是纯文本输入框，
`RecordEditDialog` 里也不可编辑）—— 属既有限制，未在本次范围内。

### 新增类型时的两个易错点

1. **`onMounted` 拉不到「后选类型」需要的远程数据。**
   新增字段弹框里字段类型是**后选**的：组件挂载时类型还是 `text`。
   凡是「按字段类型决定要不要拉远程数据」的逻辑，都必须写成
   `watch(() => props.fieldType, ..., { immediate: true })`，不能只在 `onMounted` 里判断。
   （`FieldAttributeForm` 的组织树就栽在这上面：切到「部门」后树是空的。）
   同理，别按「当前可选范围」（如 `userScope === 'dept'`）决定要不要拉 ——
   范围是可切换的，等切过去再拉必然先闪一个空下拉。

2. **`v-model="config.someArray[idx]"` 过不了 `vue-tsc`**（`possibly 'undefined'`）。
   模板里 `v-for` 的类型收窄不会传递到同级的 `v-model` 表达式。
   做法：`computed(() => props.config.someArray || [])` 拿一个非空类型；
   并且「数组必须存在」的守卫**不要按当前类型判断**
   （与既有的 `props.config.userDeptIds = []` 保持一致）—— 类型后选时按当前类型判断会漏掉。
   `createDefaultFieldConfig` 里顺手给 `[]`，两条路都堵上。

### 网格里「结构化 id 数组」要展示名称而不是 id
`department` 这类值内部是 id 数组（`{valueJson:[3], valueText:'研发部'}`），
而 `buildRecordData` 的结构化分支取的是 `valueJson` → `relationText` 只能吐出 `"3"`。
需要展示名称的类型要**单独分支**，优先 `cell.displayText || cell.valueText`。
（`user`/`group`/`link` 有同样的毛病，尚未修。）
