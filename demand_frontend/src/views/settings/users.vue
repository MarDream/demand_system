<template>
  <PageContainer title="用户管理" subtitle="管理系统成员、组织架构与花名册">
    <template #headerActions>
      <el-radio-group v-model="managementMode" class="mode-switch">
        <el-radio-button value="basic">基础管理模式</el-radio-button>
        <el-radio-button value="hr">人事管理模式</el-radio-button>
      </el-radio-group>
    </template>

    <div class="member-console">
      <div
        v-if="managementMode === 'basic'"
        class="member-layout"
        :style="memberSidebar.styleVars"
        v-loading="loading"
      >
        <aside class="member-sidebar" :class="{ 'is-collapsed': memberSidebar.collapsed }">
          <div class="sidebar-head">
            <span class="sidebar-head__title">组织架构</span>
            <el-button
              link
              class="sidebar-collapse-trigger"
              title="收起侧边栏"
              @click="memberSidebar.toggle"
            >
              <el-icon><ArrowLeft /></el-icon>
            </el-button>
          </div>
          <el-input v-model="orgKeyword" placeholder="搜索成员、部门、角色" clearable>
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>

          <div class="sidebar-actions">
            <el-button v-if="allowedNewTypes.length > 0" @click="openCreateDepartment">
              <el-icon><Plus /></el-icon>
              添加{{ allowedNewTypes.length === 1 ? ORG_TYPE_LABELS[allowedNewTypes[0]] : '子组织' }}
            </el-button>
            <el-button @click="openDepartmentManagement">
              <el-icon><Operation /></el-icon>
              组织管理
            </el-button>
          </div>

          <div v-if="isOrgSortMode" class="org-sort-tip">
            <el-icon><InfoFilled /></el-icon>
            <span>拖拽组织调整同级顺序，调整后自动保存</span>
            <el-button v-if="orgSortSaving" link :loading="orgSortSaving">保存中</el-button>
          </div>

          <div class="org-list">
            <div
              v-for="node in visibleOrgNodes"
              :key="node.key"
              class="org-item"
              :data-key="node.key"
              :class="{ 'is-active': activeOrgKey === node.key, 'is-sortable': isOrgSortMode }"
              :style="{ paddingLeft: `${12 + node.level * 18}px` }"
            >
              <span
                v-if="node.hasChildren"
                class="org-toggle"
                @click.stop="toggleExpand(node.key)"
              >
                <el-icon :class="{ 'is-expanded': expandedKeys.has(node.key) }"><ArrowRight /></el-icon>
              </span>
              <span v-else class="org-toggle org-toggle--leaf" />
              <button class="org-item-btn" type="button" @click="selectOrg(node)">
                <el-icon><component :is="node.icon" /></el-icon>
                <span class="org-name">{{ node.name }}</span>
                <span class="org-count">({{ node.count }}人)</span>
              </button>
            </div>
          </div>
        </aside>

        <div class="sidebar-resizer" @mousedown="memberSidebar.startResize" @dblclick="memberSidebar.toggle" />
        <button
          v-if="memberSidebar.collapsed"
          class="sidebar-expand-btn"
          type="button"
          title="展开侧边栏"
          @click="memberSidebar.toggle"
        >
          <el-icon><ArrowRight /></el-icon>
        </button>

        <main class="member-main">
          <template v-if="memberView === 'members'">
          <div class="org-header">
            <div>
              <nav v-if="orgBreadcrumb.length > 1" class="org-breadcrumb">
                <template v-for="(item, idx) in orgBreadcrumb" :key="item.key">
                  <span v-if="idx < orgBreadcrumb.length - 1" class="org-breadcrumb-link" @click="selectOrgByKey(item.key)">{{ item.name }}</span>
                  <span v-else class="org-breadcrumb-current">{{ item.name }}</span>
                  <span v-if="idx < orgBreadcrumb.length - 1" class="org-breadcrumb-sep">/</span>
                </template>
              </nav>
              <div class="org-title">
                {{ activeOrgName }}
                <el-tag size="small" type="warning" effect="plain">{{ orgTypeLabel }}</el-tag>
              </div>
            </div>
            <el-button link type="primary" @click="openEditOrgDrawer">
              <el-icon><Setting /></el-icon>
              {{ editOrgButtonText }}
            </el-button>
          </div>

          <div class="action-row">
            <AppButton type="primary" permission="button:user:create" @click="handleCreate">
              <el-icon><Plus /></el-icon>
              添加成员
            </AppButton>
            <AppButton permission="button:user:invite">
              <el-dropdown @command="handleInviteCommand">
                <span>
                  邀请成员
                  <el-icon class="el-icon--right"><ArrowDown /></el-icon>
                </span>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="link">通过链接邀请</el-dropdown-item>
                    <el-dropdown-item command="batch">批量邀请</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </AppButton>
            <AppButton permission="button:user:update" @click="openApplicationRecords">添加/申请记录</AppButton>
            <AppButton permission="button:user:batch-delete">
              <el-dropdown @command="handleBatchCommand">
                <span>
                  批量管理
                  <el-icon class="el-icon--right"><ArrowDown /></el-icon>
                </span>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="enable">批量启用</el-dropdown-item>
                    <el-dropdown-item command="disable">批量停用</el-dropdown-item>
                    <el-dropdown-item command="delete" divided>批量删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </AppButton>
            <AppButton permission="button:user:update" :type="isOrgSortMode ? 'primary' : 'default'" @click="toggleOrgSortMode">
              {{ isOrgSortMode ? '完成排序' : '调整排序' }}
            </AppButton>
            <el-button :icon="Setting" circle aria-label="列表字段设置" title="列表字段设置" @click="openColumnConfig" />
          </div>

          <el-table :data="userList" border class="member-table" @selection-change="selectedUsers = $event">
            <el-table-column type="selection" width="48" />
            <template v-for="col in visibleColumns" :key="col.key">
              <el-table-column
                :label="col.label"
                :width="col.width"
                :min-width="col.minWidth"
                :align="col.align || 'center'"
                :fixed="col.fixed"
                :prop="col.key === 'email' ? 'email' : undefined"
                :show-overflow-tooltip="col.key === 'email'"
              >
                <template v-if="col.key === 'realName'" #default="{ row }">
                  <div class="member-cell">
                    <el-avatar :size="34" :src="userAvatarUrl(row) || undefined">{{ avatarText(row) }}</el-avatar>
                    <div class="member-info">
                      <div class="member-name">
                        {{ row.realName || row.username }}
                        <el-tag v-if="row.id === 1" size="small" type="primary">主管理员</el-tag>
                      </div>
                      <div class="member-sub">{{ row.username }}</div>
                    </div>
                  </div>
                </template>
                <template v-else-if="col.key === 'orgName'" #default="{ row }">{{ orgName(row.orgId) || '-' }}</template>
                <template v-else-if="col.key === 'status'" #default="{ row }">
                  <el-switch
                    v-model="row.status"
                    active-value="active"
                    inactive-value="inactive"
                    inline-prompt
                    active-text="启用"
                    inactive-text="停用"
                    :disabled="!canToggleUser"
                    :before-change="() => toggleUserStatus(row)"
                  />
                </template>
                <template v-else-if="col.key === 'systemRole'" #default="{ row }">{{ row.systemRole || '-' }}</template>
                <template v-else-if="col.key === 'jobNumber'" #default="{ row }">{{ row.jobNumber || '-' }}</template>
                <template v-else-if="col.key === 'phone'" #default="{ row }">{{ maskPhone(row.phone) }}</template>
                <template v-else-if="col.key === 'username'" #default="{ row }">{{ row.username }}</template>
                <template v-else-if="col.key === 'operations'" #default="{ row }">
                  <div class="table-action-icons">
                    <AppButton link type="primary" size="small" permission="button:user:update" @click="handleEdit(row)">
                      <el-icon><Edit /></el-icon>
                    </AppButton>
                  <el-dropdown @command="(command: string) => handleUserCommand(command, row)">
                        <el-button link type="primary" size="small" title="更多操作">
                          <el-icon><MoreFilled /></el-icon>
                        </el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item v-if="hasPermission('button:user:update')" command="reset">
                          <el-icon><Key /></el-icon>
                          重置密码
                        </el-dropdown-item>
                        <el-dropdown-item v-if="hasPermission('button:user:delete')" command="delete" divided>
                          <el-icon><Delete /></el-icon>
                          删除
                        </el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                  </div>
                </template>
              </el-table-column>
            </template>
          </el-table>
          </template>

          <template v-else>
          <div class="department-head">
            <div>
              <div class="department-title">{{ activeOrgName }}</div>
              <p>你是主管理员，拥有全部权限</p>
            </div>
            <el-button link type="primary" @click="memberView = 'members'">返回成员列表</el-button>
          </div>

          <div class="department-actions">
            <AppButton type="primary" permission="button:org:create" @click="openCreateDepartment">
              <el-icon><Plus /></el-icon>
              添加{{ allowedNewTypes.length === 1 ? ORG_TYPE_LABELS[allowedNewTypes[0]] : '子组织' }}
            </AppButton>
            <AppButton permission="button:org:batch-create" @click="openBatchCreateDept">批量创建部门</AppButton>
            <AppButton permission="button:org:update" :disabled="selectedDepartments.length !== 1" @click="openEditDepartment">
              编辑选中部门
            </AppButton>
            <AppButton permission="button:org:delete" type="danger" plain :disabled="selectedDepartments.length === 0" @click="handleDeleteDepartments">
              删除选中部门
            </AppButton>
          </div>

          <el-table
            :data="departmentRows"
            border
            row-key="id"
            class="department-table"
            @selection-change="selectedDepartments = $event"
          >
            <el-table-column type="selection" width="52" />
            <el-table-column label="部门名称" min-width="260" prop="name">
              <template #default="{ row }">
                <div class="department-name-cell" :style="{ paddingLeft: `${row.level * 20}px` }">
                  <span>{{ row.name }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="成员数" width="200">
              <template #default="{ row }">{{ row.count }}</template>
            </el-table-column>
            <el-table-column label="部门成员查看范围" min-width="260">
              <template #default>全员</template>
            </el-table-column>
            <el-table-column label="操作" width="86">
              <template #default="{ row }">
                <el-dropdown @command="(command: string) => handleDepartmentCommand(command, row)">
                  <el-button link type="primary" size="small" title="部门操作">
                    <el-icon><MoreFilled /></el-icon>
                  </el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="add">
                        <el-icon><Plus /></el-icon>
                        添加子部门
                      </el-dropdown-item>
                      <el-dropdown-item command="edit">
                        <el-icon><Edit /></el-icon>
                        编辑部门
                      </el-dropdown-item>
                      <el-dropdown-item command="delete" divided>
                        <el-icon><Delete /></el-icon>
                        删除部门
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </template>
            </el-table-column>
          </el-table>
          </template>
        </main>
      </div>

      <div v-else class="roster-layout" :style="rosterSidebar.styleVars" v-loading="loading">
        <aside class="roster-nav" :class="{ 'is-collapsed': rosterSidebar.collapsed }">
          <div class="sidebar-head">
            <span class="sidebar-head__title">人事导航</span>
            <el-button
              link
              class="sidebar-collapse-trigger"
              title="收起侧边栏"
              @click="rosterSidebar.toggle"
            >
              <el-icon><ArrowLeft /></el-icon>
            </el-button>
          </div>
          <div class="nav-section">
            <div class="nav-title">
              <el-icon><User /></el-icon>
              员工管理
            </div>
            <button class="nav-item" :class="{ 'is-active': rosterView === 'roster' }" type="button" @click="switchRosterView('roster')">花名册</button>
            <button class="nav-item" :class="{ 'is-active': rosterView === 'resigned' }" type="button" @click="switchRosterView('resigned')">已离职员工</button>
            <button class="nav-item" :class="{ 'is-active': rosterView === 'safety' }" type="button" @click="switchRosterView('safety')">用工安全</button>
          </div>
          <div class="nav-section">
            <div class="nav-title">
              <el-icon><Connection /></el-icon>
              员工关系
            </div>
            <button
              v-for="item in hrNavItems"
              :key="item.key"
              class="nav-item"
              :class="{ 'is-active': rosterView === item.key }"
              type="button"
              @click="switchRosterView(item.key)"
            >
              {{ item.label }}
            </button>
          </div>
        </aside>

        <div class="sidebar-resizer" @mousedown="rosterSidebar.startResize" @dblclick="rosterSidebar.toggle" />

        <button
          v-if="rosterSidebar.collapsed"
          class="sidebar-expand-btn"
          type="button"
          title="展开侧边栏"
          @click="rosterSidebar.toggle"
        >
          <el-icon><ArrowRight /></el-icon>
        </button>

        <main class="roster-main">
          <!-- ═══ 花名册 / 已离职员工 ═══ -->
          <template v-if="isRosterListView">
            <div class="roster-header">
              <h3>{{ rosterView === 'resigned' ? '已离职员工' : '花名册' }}</h3>
              <div class="roster-links">
                <el-button v-if="rosterView === 'roster'" link @click="switchRosterView('resigned')">
                  <el-icon><UserFilled /></el-icon>
                  已离职员工
                </el-button>
                <el-button v-else link @click="switchRosterView('roster')">
                  <el-icon><User /></el-icon>
                  返回花名册
                </el-button>
                <el-button link @click="openRosterColumnConfig">
                  <el-icon><Setting /></el-icon>
                  自定义字段设置
                </el-button>
                <el-button link @click="openExportHistory">
                  <el-icon><Clock /></el-icon>
                  导出历史花名册
                </el-button>
              </div>
            </div>

            <div v-if="rosterView === 'roster'" class="stats-board">
              <div class="stat-card is-primary">
                <span>在职员工</span>
                <strong>{{ rosterStatsData.active }}</strong>
              </div>
              <div class="stat-card"><span>全职</span><strong>{{ rosterStatsData.fullTime }}</strong></div>
              <div class="stat-card"><span>兼职</span><strong>{{ rosterStatsData.partTime }}</strong></div>
              <div class="stat-card"><span>实习</span><strong>{{ rosterStatsData.intern }}</strong></div>
              <div class="stat-card"><span>劳务派遣</span><strong>{{ rosterStatsData.dispatch }}</strong></div>
              <div class="stat-card"><span>其他类型</span><strong>{{ rosterStatsData.other }}</strong></div>
              <div class="stat-card"><span>试用期</span><strong>{{ rosterStatsData.probation }}</strong></div>
              <div class="stat-card"><span>已转正</span><strong>{{ rosterStatsData.confirmed }}</strong></div>
              <div class="stat-card"><span>待离职</span><strong>{{ rosterStatsData.pendingResign }}</strong></div>
            </div>
            <div v-else class="stats-board">
              <div class="stat-card is-primary">
                <span>已离职员工</span>
                <strong>{{ rosterStatsData.resigned }}</strong>
              </div>
            </div>

            <div class="roster-filter">
              <el-input v-model="queryParams.realName" placeholder="搜索员工" clearable @keyup.enter="handleSearch">
                <template #prefix>
                  <el-icon><Search /></el-icon>
                </template>
              </el-input>
              <el-select
                v-if="rosterView === 'roster'"
                v-model="queryParams.workStatus"
                placeholder="用工状态"
                clearable
                style="width: 132px"
                @change="handleRosterFilterChange"
              >
                <el-option label="试用期" value="probation" />
                <el-option label="已转正" value="confirmed" />
                <el-option label="待离职" value="pending_resign" />
              </el-select>
              <el-button @click="openAdvancedFilter">
                高级筛选
                <el-icon class="el-icon--right"><Filter /></el-icon>
                <el-badge v-if="advancedFilterCount > 0" :value="advancedFilterCount" class="advanced-filter-badge" />
              </el-button>
              <el-button v-if="advancedFilterCount > 0" link type="primary" @click="clearAdvancedFilter">清除筛选</el-button>
              <div class="filter-spacer" />
              <template v-if="rosterView === 'roster'">
                <AppButton permission="button:user:create" @click="handleCreate">添加员工</AppButton>
                <AppButton permission="button:user:invite" :disabled="!selectedUsers.length" @click="handleInviteVerify">邀请认证</AppButton>
                <AppButton permission="button:user:export" :loading="exporting" @click="handleExport">导出</AppButton>
                <AppButton permission="button:user:import" type="primary" @click="openImportDialog">导入花名册</AppButton>
              </template>
              <template v-else>
                <AppButton permission="button:user:update" :disabled="!selectedUsers.length" @click="handleRestoreUsers">复职</AppButton>
              </template>
            </div>

            <el-table :data="userList" border class="member-table" @selection-change="selectedUsers = $event">
              <el-table-column type="selection" width="48" />
              <template v-for="col in rosterVisibleColumns" :key="col.key">
                <el-table-column
                  :label="col.label"
                  :width="col.width"
                  :min-width="col.minWidth"
                  :align="col.align || 'center'"
                  :fixed="col.fixed"
                >
                  <template v-if="col.key === 'realName'" #default="{ row }">
                    <div class="member-cell">
                      <el-avatar :size="34" :src="userAvatarUrl(row) || undefined">{{ avatarText(row) }}</el-avatar>
                      <div class="member-info">
                        <div class="member-name">{{ row.realName || row.username }}</div>
                        <div class="member-sub">{{ row.jobNumber || row.username }}</div>
                      </div>
                    </div>
                  </template>
                  <template v-else-if="col.key === 'orgName'" #default="{ row }">{{ orgName(row.orgId) || '-' }}</template>
                  <template v-else-if="col.key === 'systemRole'" #default="{ row }">{{ row.systemRole || '-' }}</template>
                  <template v-else-if="col.key === 'employeeType'" #default="{ row }">
                    <el-tag size="small" :type="EMPLOYEE_TYPE_TAG[row.employeeType] || 'info'" effect="plain">
                      {{ EMPLOYEE_TYPE_LABELS[row.employeeType] || '未设置' }}
                    </el-tag>
                  </template>
                  <template v-else-if="col.key === 'workStatus'" #default="{ row }">
                    <el-tag size="small" :type="WORK_STATUS_TAG[row.workStatus] || 'info'" effect="plain">
                      {{ WORK_STATUS_LABELS[row.workStatus] || '未设置' }}
                    </el-tag>
                  </template>
                  <template v-else-if="col.key === 'hireDate'" #default="{ row }">{{ row.hireDate || '-' }}</template>
                  <template v-else-if="col.key === 'birthday'" #default="{ row }">{{ row.birthday || '-' }}</template>
                  <template v-else-if="col.key === 'phone'" #default="{ row }">{{ maskPhone(row.phone) }}</template>
                  <template v-else-if="col.key === 'email'" #default="{ row }">{{ row.email || '-' }}</template>
                  <template v-else-if="col.key === 'jobNumber'" #default="{ row }">{{ row.jobNumber || '-' }}</template>
                  <template v-else-if="col.key === 'createdAt'" #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
                  <template v-else-if="col.key === 'operations'" #default="{ row }">
                    <div class="table-action-icons">
                      <AppButton link type="primary" size="small" permission="button:user:update" @click="handleEdit(row)">
                        <el-icon><Edit /></el-icon>
                      </AppButton>
                      <el-button
                        v-if="rosterView === 'resigned'"
                        link
                        type="primary"
                        size="small"
                        title="复职"
                        @click="handleRestoreUsers([row])"
                      >
                        <el-icon><RefreshLeft /></el-icon>
                      </el-button>
                    </div>
                  </template>
                </el-table-column>
              </template>
            </el-table>
          </template>

          <!-- ═══ 人事子模块（入职/新人成长/转正/异动/离职/合同/退休/员工关怀/用工安全） ═══ -->
          <template v-else>
            <div class="hr-module">
              <div class="roster-header">
                <h3>{{ activeHrModule.label }}</h3>
                <div class="roster-links">
                  <el-button type="primary" permission="button:user:create" @click="openHrRecordDialog()">
                    <el-icon><Plus /></el-icon>
                    {{ activeHrModule.createLabel }}
                  </el-button>
                </div>
              </div>

              <div class="stats-board">
                <div class="stat-card is-primary"><span>办理中</span><strong>{{ hrSummary.processing ?? 0 }}</strong></div>
                <div class="stat-card"><span>已完成</span><strong>{{ hrSummary.done ?? 0 }}</strong></div>
                <div class="stat-card"><span>已取消</span><strong>{{ hrSummary.cancelled ?? 0 }}</strong></div>
                <template v-if="activeHrModule.type === 'contract'">
                  <div class="stat-card is-warning"><span>30天内到期</span><strong>{{ hrSummary.expiringSoon ?? 0 }}</strong></div>
                  <div class="stat-card"><span>已到期未续签</span><strong>{{ hrSummary.expired ?? 0 }}</strong></div>
                </template>
                <template v-else-if="activeHrModule.type === 'onboarding'">
                  <div class="stat-card is-warning"><span>待入职</span><strong>{{ hrOverview.inactive ?? 0 }}</strong></div>
                </template>
                <template v-else-if="activeHrModule.type === 'regularization'">
                  <div class="stat-card is-warning"><span>试用期员工</span><strong>{{ hrOverview.probation ?? 0 }}</strong></div>
                </template>
                <template v-else-if="activeHrModule.type === 'resignation'">
                  <div class="stat-card is-warning"><span>待离职员工</span><strong>{{ hrOverview.pendingResign ?? 0 }}</strong></div>
                </template>
                <template v-else-if="activeHrModule.type === 'care'">
                  <div class="stat-card is-warning"><span>本月生日</span><strong>{{ hrOverview.birthdayThisMonth ?? 0 }}</strong></div>
                </template>
                <template v-else-if="activeHrModule.type === 'safety'">
                  <div class="stat-card is-warning"><span>未完善手机号</span><strong>{{ hrOverview.missingPhone ?? 0 }}</strong></div>
                  <div class="stat-card is-warning"><span>未签合同</span><strong>{{ hrOverview.noContract ?? 0 }}</strong></div>
                </template>
              </div>

              <!-- 员工关怀：本月寿星速览（钉钉关怀场景） -->
              <div
                v-if="activeHrModule.type === 'care' && (hrOverview.birthdayUsers?.length ?? 0) > 0"
                class="care-birthday"
              >
                <div class="care-birthday__title">
                  <el-icon><UserFilled /></el-icon>
                  本月寿星
                </div>
                <div class="care-birthday__list">
                  <div v-for="user in hrOverview.birthdayUsers" :key="user.id" class="care-birthday__item">
                    <el-avatar :size="30" class="care-birthday__avatar">{{ user.realName?.slice(0, 1) }}</el-avatar>
                    <span class="care-birthday__name">{{ user.realName }}</span>
                    <el-tag size="small" type="warning" effect="plain">{{ Number(user.birthday.slice(5, 7)) }}月{{ user.day }}日</el-tag>
                  </div>
                </div>
              </div>

              <div class="roster-filter">
                <el-select v-model="hrQueryStatus" placeholder="全部状态" clearable style="width: 132px" @change="fetchHrRecords(true)">
                  <el-option label="办理中" value="processing" />
                  <el-option label="已完成" value="done" />
                  <el-option label="已取消" value="cancelled" />
                </el-select>
              </div>

              <el-table :data="hrRecords" border class="member-table" v-loading="hrLoading">
                <el-table-column label="员工" min-width="140">
                  <template #default="{ row }">{{ row.userName || '-' }}</template>
                </el-table-column>
                <el-table-column label="事项" min-width="180" show-overflow-tooltip>
                  <template #default="{ row }">{{ row.title }}</template>
                </el-table-column>
                <el-table-column label="明细" min-width="260">
                  <template #default="{ row }">{{ hrDetailSummary(row) || '-' }}</template>
                </el-table-column>
                <el-table-column label="业务日期" width="120">
                  <template #default="{ row }">{{ row.recordDate || '-' }}</template>
                </el-table-column>
                <el-table-column label="状态" width="100">
                  <template #default="{ row }">
                    <el-tag size="small" :type="HR_STATUS_TAG[row.status] || 'info'" effect="plain">
                      {{ HR_STATUS_LABELS[row.status] || row.status }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="经办人" width="110">
                  <template #default="{ row }">{{ row.operatorName || '-' }}</template>
                </el-table-column>
                <el-table-column label="操作" width="170" fixed="right">
                  <template #default="{ row }">
                    <div class="table-action-icons">
                      <el-button
                        v-if="row.status === 'processing'"
                        link
                        type="primary"
                        size="small"
                        @click="completeHrRecord(row)"
                      >办理完成</el-button>
                      <el-button link type="primary" size="small" @click="openHrRecordDialog(row)">编辑</el-button>
                      <el-button link type="danger" size="small" @click="removeHrRecord(row)">删除</el-button>
                    </div>
                  </template>
                </el-table-column>
              </el-table>

              <div class="pagination-row">
                <AppPagination
                  v-model:page-num="hrPageNum"
                  v-model:page-size="hrPageSize"
                  :total="hrTotal"
                  :page-sizes="[10, 20, 50]"
                  @change="fetchHrRecords()"
                />
              </div>
            </div>
          </template>
        </main>
      </div>

      <div class="pagination-row">
        <AppPagination
          v-model:page-num="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          @change="fetchList"
        />
      </div>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑成员' : '添加成员'"
      width="600px"
      class="settings-form-dialog"
      @close="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <!-- 头像：编辑态可设置（预设选择 / 上传裁剪） -->
        <el-form-item v-if="isEdit" label="头像">
          <div class="avatar-edit-block">
            <el-avatar :size="56" :src="editAvatarUrl || undefined" class="avatar-edit-block__preview">
              {{ (form.realName || '?').slice(0, 1) }}
            </el-avatar>
            <div class="avatar-edit-block__actions">
              <el-button size="small" @click="avatarEditVisible = true">设置头像</el-button>
              <el-button v-if="form.avatar" size="small" text type="danger" @click="form.avatar = ''">
                清除
              </el-button>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="form.realName" placeholder="请输入姓名" @input="handleRealNameInput" />
        </el-form-item>
        <el-form-item prop="username">
          <template #label>
            <span class="form-label-with-tip">
              账号名
              <el-tooltip
                v-if="!isEdit"
                content="初始密码默认生成为“账号名 + 手机号后3位”，创建成功后系统会通过邮箱发送给用户。"
                placement="top"
                popper-class="initial-password-tooltip"
              >
                <el-icon class="form-label-tip-icon" tabindex="0" aria-label="查看初始密码说明">
                  <InfoFilled />
                </el-icon>
              </el-tooltip>
            </span>
          </template>
          <el-input
            v-model="form.username"
            placeholder="输入姓名后自动填入拼音，可手动修改"
            :disabled="isEdit"
            @input="handleUsernameInput"
          />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入手机号" />
        </el-form-item>

        <el-divider content-position="left">组织信息</el-divider>

        <el-form-item label="所属组织" prop="orgId">
          <el-tree-select
            v-model="form.orgId"
            :data="orgTree"
            :props="{ label: 'name', value: 'id', children: 'children' }"
            placeholder="请选择组织"
            check-strictly
            style="width: 100%"
          >
            <template #default="{ data }">
              <span class="department-option">
                <el-icon><component :is="orgIcon(data.orgType)" /></el-icon>
                {{ data.name }}
                <el-tag size="small" :type="data.orgType === 'department' || data.orgType === 'group' ? 'info' : 'warning'" style="margin-left: 4px;">{{ ORG_TYPE_LABELS[data.orgType] || data.orgType }}</el-tag>
              </span>
            </template>
          </el-tree-select>
        </el-form-item>
        <el-form-item label="角色" prop="roleIds">
          <RoleSelect
            v-model="form.roleIds"
            multiple
            :exclude-codes="['SUPER_ADMIN']"
            placeholder="请选择角色（可多选）"
          />
        </el-form-item>

        <el-divider content-position="left">花名册信息</el-divider>
        <el-form-item label="员工类型">
          <el-select v-model="form.employeeType" style="width: 100%">
            <el-option v-for="(label, key) in EMPLOYEE_TYPE_LABELS" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="用工状态">
          <el-select v-model="form.workStatus" style="width: 100%">
            <el-option label="试用期" value="probation" />
            <el-option label="已转正" value="confirmed" />
            <el-option label="待离职" value="pending_resign" />
            <el-option label="已离职" value="resigned" />
          </el-select>
        </el-form-item>
        <el-form-item label="入职日期">
          <el-date-picker
            v-model="form.hireDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="默认为创建日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="生日">
          <el-date-picker
            v-model="form.birthday"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择生日（员工关怀用）"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item v-if="isEdit" label="工号">
          <el-input :model-value="editJobNumber" disabled placeholder="系统自动生成" />
        </el-form-item>
        <el-form-item v-if="isEdit" label="状态" prop="status">
          <el-switch
            v-model="form.status"
            active-value="active"
            inactive-value="inactive"
            inline-prompt
            active-text="启用"
            inactive-text="停用"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button v-permission="isEdit ? 'button:user:update' : 'button:user:create'" type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 头像编辑弹窗：预设选择 / 上传裁剪（管理员为成员设置） -->
    <AvatarEditorDialog
      :visible="avatarEditVisible"
      :current-avatar="form.avatar || null"
      @close="avatarEditVisible = false"
      @confirm="handleEditAvatarConfirm"
    />

    <el-drawer
      v-model="departmentDrawerVisible"
      :title="departmentEditingId ? `编辑${editDrawerOrgTypeLabel}` : '新增组织'"
      direction="rtl"
      size="492px"
      class="department-drawer"
      @close="resetDepartmentForm"
    >
      <div class="drawer-section-title">组织信息</div>
      <el-form
        ref="departmentFormRef"
        :model="departmentForm"
        :rules="departmentRules"
        label-position="top"
        class="department-form"
      >
        <el-form-item label="名称" prop="name" required>
          <el-input v-model="departmentForm.name" placeholder="请输入" clearable />
        </el-form-item>

        <el-form-item v-if="!departmentEditingId" label="组织类型" prop="orgType" required>
          <el-radio-group v-model="departmentForm.orgType">
            <el-radio v-for="t in allowedNewTypes" :key="t" :value="t">{{ ORG_TYPE_LABELS[t] }}</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item v-if="departmentEditingId && editAllowedTypes.length > 1" label="组织类型" prop="orgType" required>
          <el-radio-group v-model="departmentForm.orgType" @change="onEditOrgTypeChange">
            <el-radio v-for="t in editAllowedTypes" :key="t" :value="t" :disabled="!canChangeEditType(t)">{{ ORG_TYPE_LABELS[t] }}</el-radio>
          </el-radio-group>
          <div v-if="editTypeChangeWarning" class="effective-tip">{{ editTypeChangeWarning }}</div>
        </el-form-item>

        <el-form-item v-if="!editingIsRoot" label="上级组织" prop="parentId" required>
          <el-tree-select
            v-model="departmentForm.parentId"
            :data="orgTreeForParentSelect"
            :props="{ label: 'name', value: 'id', children: 'children' }"
            placeholder="请选择上级组织"
            clearable
            check-strictly
            style="width: 100%"
          >
            <template #default="{ data }">
              <span class="department-option">
                <el-icon><component :is="orgIcon(data.orgType)" /></el-icon>
                {{ data.name }}
                <el-tag size="small" :type="data.orgType === 'department' || data.orgType === 'group' ? 'info' : 'warning'" style="margin-left: 4px;">{{ ORG_TYPE_LABELS[data.orgType] || data.orgType }}</el-tag>
              </span>
            </template>
          </el-tree-select>
        </el-form-item>

      </el-form>

      <template #footer>
        <div class="drawer-footer">
          <el-button @click="departmentDrawerVisible = false">取消</el-button>
          <el-button v-permission="departmentEditingId ? 'button:org:update' : 'button:org:create'" type="primary" :loading="departmentSubmitting" @click="handleSubmitDepartment">确定</el-button>
        </div>
      </template>
    </el-drawer>

    <!-- 邀请成员（通过链接邀请 / 批量邀请） -->
    <InviteMemberDialog
      v-model="inviteDialogVisible"
      :org-tree="orgTree"
      :default-org-id="activeOrgId"
      :initial-tab="inviteDialogTab"
      @invited="handleInvited"
    />

    <!-- 添加/申请记录 -->
    <ApplicationRecordsDialog
      v-model="recordsDialogVisible"
      :org-tree="orgTree"
      @changed="fetchList"
    />

    <!-- 列配置弹窗 -->
    <ColumnConfigDialog
      v-model="showColumnConfig"
      :column-groups="columnGroups"
      :draft-selected-columns="draftSelectedColumns"
      :draft-column-keys="draftColumnKeys"
      @update:draft-column-keys="draftColumnKeys = $event"
      @remove="removeDraftColumn"
      @save="saveColumns"
    />

    <!-- 花名册列配置弹窗（自定义字段设置） -->
    <ColumnConfigDialog
      v-model="showRosterColumnConfig"
      :column-groups="rosterColumnGroups"
      :draft-selected-columns="rosterDraftSelectedColumns"
      :draft-column-keys="rosterDraftColumnKeys"
      @update:draft-column-keys="rosterDraftColumnKeys = $event"
      @remove="rosterRemoveDraftColumn"
      @save="rosterSaveColumns"
    />

    <!-- 高级筛选抽屉 -->
    <el-drawer v-model="advancedFilterVisible" title="高级筛选" size="420px">
      <el-form label-width="90px" class="advanced-filter-form">
        <el-form-item label="员工类型">
          <el-select v-model="advancedFilter.employeeType" placeholder="全部" clearable style="width: 100%">
            <el-option v-for="(label, key) in EMPLOYEE_TYPE_LABELS" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="员工状态">
          <el-select v-model="advancedFilter.workStatus" placeholder="全部" clearable style="width: 100%">
            <el-option label="试用期" value="probation" />
            <el-option label="已转正" value="confirmed" />
            <el-option label="待离职" value="pending_resign" />
            <el-option label="已离职" value="resigned" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属部门">
          <el-tree-select
            v-model="advancedFilter.orgId"
            :data="orgTree"
            :props="{ label: 'name', value: 'id', children: 'children' }"
            placeholder="全部部门"
            check-strictly
            clearable
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="入职时间">
          <el-date-picker
            v-model="advancedFilter.hireRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="drawer-footer">
          <el-button @click="clearAdvancedFilter">清除</el-button>
          <el-button type="primary" @click="applyAdvancedFilter">查询</el-button>
        </div>
      </template>
    </el-drawer>

    <!-- 导入花名册 -->
    <el-dialog v-model="importDialogVisible" title="导入花名册" width="560px" class="settings-form-dialog">
      <div class="import-steps">
        <div class="import-step">
          <div class="import-step__title">1. 下载模板，按模板列填写员工信息</div>
          <el-button size="small" @click="handleDownloadTemplate">
            <el-icon><Download /></el-icon>
            下载导入模板
          </el-button>
        </div>
        <div class="import-step">
          <div class="import-step__title">2. 选择导入后员工归属的部门（可选）</div>
          <el-tree-select
            v-model="importOrgId"
            :data="orgTree"
            :props="{ label: 'name', value: 'id', children: 'children' }"
            placeholder="默认不指定（仅超级管理员可导入全员）"
            check-strictly
            clearable
            style="width: 100%"
          />
        </div>
        <div class="import-step">
          <div class="import-step__title">3. 上传填写好的 Excel 文件</div>
          <el-upload
            :auto-upload="false"
            :limit="1"
            accept=".xlsx,.xls"
            :on-change="(file: any) => (importFile = file.raw)"
            :on-remove="() => (importFile = null)"
            drag
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
            <template #tip>
              <div class="el-upload__tip">仅支持 .xlsx 文件；手机号将作为登录账号，初始密码通过邮件发送</div>
            </template>
          </el-upload>
        </div>
        <div v-if="importResult" class="import-result">
          <el-alert
            :type="importResult.failCount > 0 ? 'warning' : 'success'"
            :closable="false"
            :title="`导入完成：成功 ${importResult.successCount} 条，失败 ${importResult.failCount} 条`"
          />
          <ul v-if="importResult.failures.length" class="import-result__failures">
            <li v-for="(msg, idx) in importResult.failures" :key="idx">{{ msg }}</li>
          </ul>
        </div>
      </div>
      <template #footer>
        <el-button @click="importDialogVisible = false">关闭</el-button>
        <el-button type="primary" :loading="importing" :disabled="!importFile" @click="handleImportSubmit">开始导入</el-button>
      </template>
    </el-dialog>

    <!-- 导出历史花名册 -->
    <el-dialog v-model="exportHistoryVisible" title="导出历史花名册" width="640px">
      <el-table :data="exportHistoryList" v-loading="exportHistoryLoading" max-height="360">
        <el-table-column label="文件名" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.fileName }}</template>
        </el-table-column>
        <el-table-column label="行数" width="80" prop="total" />
        <el-table-column label="操作人" width="110">
          <template #default="{ row }">{{ row.operatorName || '-' }}</template>
        </el-table-column>
        <el-table-column label="导出时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleDownloadExportLog(row)">下载</el-button>
            <el-button link type="danger" size="small" @click="handleDeleteExportLog(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-row">
        <AppPagination
          v-model:page-num="exportHistoryPageNum"
          v-model:page-size="exportHistoryPageSize"
          :total="exportHistoryTotal"
          :page-sizes="[10, 20]"
          @change="fetchExportHistory"
        />
      </div>
    </el-dialog>

    <!-- 人事事项（办理入职/转正/异动/离职/合同登记等） -->
    <el-dialog v-model="hrRecordDialogVisible" :title="hrRecordEditingId ? `编辑${activeHrModule.label}事项` : activeHrModule.createLabel" width="560px" class="settings-form-dialog">
      <el-form ref="hrRecordFormRef" :model="hrRecordForm" label-width="100px">
        <el-form-item label="员工" required>
          <el-select v-model="hrRecordForm.userId" filterable placeholder="选择员工" style="width: 100%">
            <el-option
              v-for="user in hrUserOptions"
              :key="user.id"
              :label="`${user.realName || user.username}（${user.jobNumber || user.username}）`"
              :value="user.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="事项标题" required>
          <el-input v-model="hrRecordForm.title" :placeholder="`如：${activeHrModule.titleExample}`" />
        </el-form-item>
        <el-form-item
          v-for="field in activeHrModule.fields"
          :key="field.key"
          :label="field.label"
          :required="field.required"
        >
          <el-date-picker
            v-if="field.type === 'date'"
            v-model="hrRecordForm.model[field.key]"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
            style="width: 100%"
          />
          <el-select
            v-else-if="field.type === 'select'"
            v-model="hrRecordForm.model[field.key]"
            :placeholder="`请选择${field.label}`"
            clearable
            style="width: 100%"
          >
            <el-option v-for="opt in field.options" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
          <el-tree-select
            v-else-if="field.type === 'org'"
            v-model="hrRecordForm.model[field.key]"
            :data="orgTree"
            :props="{ label: 'name', value: 'id', children: 'children' }"
            :placeholder="`请选择${field.label}`"
            check-strictly
            clearable
            style="width: 100%"
          />
          <el-input-number
            v-else-if="field.type === 'number'"
            v-model="hrRecordForm.model[field.key]"
            :min="0"
            style="width: 100%"
          />
          <el-input
            v-else-if="field.type === 'textarea'"
            v-model="hrRecordForm.model[field.key]"
            type="textarea"
            :rows="2"
            :placeholder="`请输入${field.label}`"
          />
          <el-input v-else v-model="hrRecordForm.model[field.key]" :placeholder="`请输入${field.label}`" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="hrRecordDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="hrRecordSubmitting" @click="submitHrRecord(hrRecordDialogDone)">保存</el-button>
        <el-button
          v-if="!hrRecordEditingId"
          type="success"
          :loading="hrRecordSubmitting"
          @click="submitHrRecord(true)"
        >保存并办理完成</el-button>
      </template>
    </el-dialog>

    <!-- 批量创建部门 -->
    <el-dialog v-model="batchDeptVisible" title="批量创建部门" width="520px" class="settings-form-dialog">
      <el-form label-width="90px">
        <el-form-item label="上级部门">
          <el-tree-select
            v-model="batchDeptParentId"
            :data="orgTree"
            :props="{ label: 'name', value: 'id', children: 'children' }"
            placeholder="选择上级部门"
            check-strictly
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="部门名称">
          <el-input
            v-model="batchDeptNames"
            type="textarea"
            :rows="6"
            placeholder="每行一个部门名称，例如：&#10;综合部&#10;财务部&#10;人力资源部"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchDeptVisible = false">取消</el-button>
        <el-button type="primary" :loading="batchDeptSubmitting" @click="submitBatchDepartments">创建</el-button>
      </template>
    </el-dialog>
  </PageContainer>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch, type Component } from 'vue'
import {
  ArrowLeft,
  ArrowDown,
  ArrowRight,
  Clock,
  Connection,
  Delete,
  Download,
  Edit,
  Filter,
  FolderOpened,
  InfoFilled,
  Key,
  MapLocation,
  MoreFilled,
  OfficeBuilding,
  Operation,
  Plus,
  RefreshLeft,
  Search,
  Setting,
  Stamp,
  Suitcase,
  SwitchButton,
  UploadFilled,
  User,
  UserFilled,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import * as userApi from '@/api/modules/user'
import * as hrApi from '@/api/modules/hr'
import { uploadFile } from '@/api/modules/file'
import type { OrgNode, User as UserInfo, RosterStats, RosterImportResult, RosterExportLog, HrRecord, EmployeeType, WorkStatus, HrOverview } from '@/types/user'
import { saveBlob } from '@/utils/download'
import PageContainer from '@/components/common/PageContainer.vue'
import AppButton from '@/components/common/AppButton.vue'
import ColumnConfigDialog from '@/components/common/ColumnConfigDialog.vue'
import RoleSelect from '@/components/common/RoleSelect.vue'
import InviteMemberDialog from './components/InviteMemberDialog.vue'
import ApplicationRecordsDialog from './components/ApplicationRecordsDialog.vue'
import AvatarEditorDialog from './components/AvatarEditorDialog.vue'
import { resolveAvatarUrl } from '@/utils/presetAvatars'
import { useColumnConfig, type ColumnDef } from '@/composables/useColumnConfig'
import { formatDate as formatDateTime } from '@/utils/format'
import { getRoleList } from '@/api/modules/role'
import { resolveErrorMessage } from '@/utils/error'
import type { RoleItem } from '@/api/modules/menu'
import { pinyin } from 'pinyin-pro'
import { useUserStore } from '@/stores/modules/user'
import { useCollapsibleSidebar } from '@/composables/useCollapsibleSidebar'
import { usePermission } from '@/composables/usePermission'
import { loadOrgTree, normalizeArray } from '@/composables/useOrgTree'
import Sortable, { type SortableEvent } from 'sortablejs'

interface FlatOrgNode {
  key: string
  id: number
  name: string
  level: number
  count: number
  icon: Component
  hasChildren: boolean
  parentKey: string | null
  orgType?: string | null
  sortOrder: number
}

interface DepartmentRow {
  id: number
  parentId: number | null
  name: string
  level: number
  count: number
  children?: DepartmentRow[]
}

interface UserForm {
  username: string
  realName: string
  email: string
  phone: string
  orgId: number | null
  regionId: number | null
  departmentId: number | null
  roleIds: number[]
  status: string
  /** 员工类型(全职/兼职/实习/劳务派遣/其他) */
  employeeType: EmployeeType | ''
  /** 用工状态(试用期/已转正/待离职/已离职) */
  workStatus: WorkStatus | ''
  /** 入职日期 YYYY-MM-DD */
  hireDate: string
  /** 生日 YYYY-MM-DD（员工关怀用） */
  birthday: string
  /** 头像：preset:xxx / URL；空串 = 清除；undefined = 未修改 */
  avatar?: string
}

// ── 列表字段设置 ──
const userAllColumns: ColumnDef[] = [
  { key: 'realName', label: '姓名', group: '基础字段', minWidth: 220, align: 'left', fixed: false },
  { key: 'orgName', label: '所属组织', group: '基础字段', minWidth: 140 },
  { key: 'status', label: '账号状态', group: '基础字段', width: 140 },
  { key: 'systemRole', label: '角色', group: '基础字段', minWidth: 140 },
  { key: 'jobNumber', label: '工号', group: '基础字段', width: 120 },
  { key: 'phone', label: '手机号', group: '联系信息', width: 140 },
  { key: 'email', label: '邮箱', group: '联系信息', minWidth: 190 },
  { key: 'username', label: '员工UserID', group: '联系信息', minWidth: 150 },
  { key: 'operations', label: '操作', width: 112, fixed: 'right' },
]

const userDefaultKeys = ['realName', 'orgName', 'status', 'systemRole', 'jobNumber', 'phone', 'email', 'username', 'operations']

const {
  showColumnConfig,
  openColumnConfig,
  saveColumns,
  loadColumnConfig,
  columnGroups,
  draftSelectedColumns,
  draftColumnKeys,
  visibleColumns,
  removeDraftColumn,
} = useColumnConfig({
  pageKey: 'user_list',
  columns: userAllColumns,
  defaultKeys: userDefaultKeys,
})
// ── 列表字段设置 END ──

// ── 花名册字段设置（人事模式 · 自定义字段设置）──
const rosterAllColumns: ColumnDef[] = [
  { key: 'realName', label: '姓名', group: '基础字段', minWidth: 200, align: 'left' },
  { key: 'orgName', label: '部门', group: '基础字段', minWidth: 150 },
  { key: 'systemRole', label: '角色', group: '基础字段', minWidth: 130 },
  { key: 'employeeType', label: '员工类型', group: '花名册字段', width: 110 },
  { key: 'workStatus', label: '员工状态', group: '花名册字段', width: 110 },
  { key: 'hireDate', label: '入职时间', group: '花名册字段', width: 120 },
  { key: 'birthday', label: '生日', group: '花名册字段', width: 120 },
  { key: 'jobNumber', label: '工号', group: '花名册字段', width: 110 },
  { key: 'phone', label: '手机号', group: '联系信息', width: 150 },
  { key: 'email', label: '邮箱', group: '联系信息', minWidth: 180 },
  { key: 'createdAt', label: '创建时间', group: '系统字段', width: 160 },
  { key: 'operations', label: '操作', group: '系统字段', width: 100, fixed: 'right' },
]
const rosterDefaultKeys = ['realName', 'orgName', 'employeeType', 'workStatus', 'hireDate', 'phone', 'operations']

const {
  showColumnConfig: showRosterColumnConfig,
  openColumnConfig: openRosterColumnConfig,
  saveColumns: rosterSaveColumns,
  loadColumnConfig: rosterLoadColumnConfig,
  columnGroups: rosterColumnGroups,
  draftSelectedColumns: rosterDraftSelectedColumns,
  draftColumnKeys: rosterDraftColumnKeys,
  visibleColumns: rosterVisibleColumns,
  removeDraftColumn: rosterRemoveDraftColumn,
} = useColumnConfig({
  pageKey: 'hr_roster',
  columns: rosterAllColumns,
  defaultKeys: rosterDefaultKeys,
})
// ── 花名册字段设置 END ──

const loading = ref(false)
const submitting = ref(false)
const userStore = useUserStore()
const { hasPermission } = usePermission()
const isSuperAdmin = computed(() => userStore.isSuperAdmin)
const userList = ref<UserInfo[]>([])
const selectedUsers = ref<UserInfo[]>([])
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const managementMode = ref<'basic' | 'hr'>('basic')
const memberView = ref<'members' | 'departments'>('members')
const orgKeyword = ref('')
const activeOrgKey = ref('')
const selectedDepartments = ref<DepartmentRow[]>([])
const expandedKeys = ref<Set<string>>(new Set())
/** 组织同层级排序模式：开启后左侧组织树可拖拽调整同级顺序 */
const isOrgSortMode = ref(false)
const orgSortSaving = ref(false)
let orgSortableInstance: Sortable | null = null
const memberSidebar = useCollapsibleSidebar({
  defaultWidth: 284,
  minWidth: 200,
  maxWidth: 500,
  resizerWidth: 4,
  widthVar: '--member-sidebar-width',
  resizerWidthVar: '--member-sidebar-resizer-width',
})
const rosterSidebar = useCollapsibleSidebar({
  defaultWidth: 244,
  minWidth: 220,
  maxWidth: 320,
  widthVar: '--roster-sidebar-width',
  resizerWidth: 4,
  resizerWidthVar: '--roster-sidebar-resizer-width',
})

const dialogVisible = ref(false)
const inviteDialogVisible = ref(false)
const inviteDialogTab = ref<'link' | 'batch'>('link')
const recordsDialogVisible = ref(false)
const batchSubmitting = ref(false)
const departmentDrawerVisible = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)
const editJobNumber = ref<string | null>(null)
const formRef = ref<FormInstance>()
const departmentFormRef = ref<FormInstance>()
const departmentSubmitting = ref(false)

const orgTree = ref<OrgNode[]>([])
const regionTree = ref<OrgNode[]>([])
const departmentTree = ref<OrgNode[]>([])
const roleList = ref<RoleItem[]>([])
/** 已有的超级管理员角色 ID（本表单不管理，保存时原样保留） */
const preservedSuperRoleIds = ref<number[]>([])

const queryParams = reactive({
  username: '',
  realName: '',
  status: '',
  /** 人事模式快速筛选：用工状态 */
  workStatus: '',
  orgId: undefined as number | undefined,
  regionId: undefined as number | undefined,
  departmentId: undefined as number | undefined,
})

const form = reactive<UserForm>({
  username: '',
  realName: '',
  email: '',
  phone: '',
  orgId: null,
  regionId: null,
  departmentId: null,
  roleIds: [] as number[],
  status: 'active',
  employeeType: 'full_time',
  workStatus: 'confirmed',
  hireDate: '',
  birthday: '',
})

// ── 编辑成员-头像设置 ──
const avatarEditVisible = ref(false)
/** 编辑弹窗头像预览地址（preset: 前缀转 data URL） */
const editAvatarUrl = computed(() => resolveAvatarUrl(form.avatar))

/** 头像弹窗确认：preset 直接写值；上传裁剪结果由弹窗内先裁剪、这里接收 Blob 再上传 */
async function handleEditAvatarConfirm(payload: { kind: 'preset'; value: string } | { kind: 'crop'; blob: Blob }) {
  if (payload.kind === 'preset') {
    form.avatar = payload.value
    avatarEditVisible.value = false
    return
  }
  try {
    const file = new File([payload.blob], 'avatar.png', { type: 'image/png' })
    const res = await uploadFile(file) as any
    const url: string = res?.url || (typeof res === 'string' ? res : '')
    if (!url) {
      ElMessage.error('头像上传失败')
      return
    }
    form.avatar = url.startsWith('http') || url.startsWith('/') ? url : `/${url}`
    avatarEditVisible.value = false
  } catch {
    ElMessage.error('头像上传失败')
  }
}

const departmentForm = reactive({
  name: '',
  parentId: null as number | null,
  orgType: 'department' as 'region' | 'company' | 'bureau' | 'department',
  createGroup: false,
})
const departmentEditingId = ref<number | null>(null)
const canChangeOrgType = ref(true)

// ── 人事管理模式：花名册 + 人事子模块（参考钉钉人事管理）──
interface HrFieldDef {
  key: string
  label: string
  type: 'text' | 'textarea' | 'date' | 'select' | 'org' | 'user' | 'number'
  options?: { label: string; value: string }[]
  required?: boolean
}

interface HrModuleDef {
  key: string
  label: string
  type: import('@/types/user').HrRecordType
  createLabel: string
  titleExample: string
  fields: HrFieldDef[]
}

/** key 与导航对应：roster/resigned 为花名册视图，其余为人事事件台账模块 */
const HR_MODULES: HrModuleDef[] = [
  {
    key: 'onboarding', label: '入职管理', type: 'onboarding', createLabel: '办理入职', titleExample: '张三入职办理',
    fields: [
      { key: 'recordDate', label: '入职日期', type: 'date', required: true },
      { key: 'departmentId', label: '入职部门', type: 'org' },
      { key: 'position', label: '岗位', type: 'text' },
      { key: 'notes', label: '备注', type: 'textarea' },
    ],
  },
  {
    key: 'newcomer', label: '新人成长', type: 'newcomer', createLabel: '添加成长任务', titleExample: '张三入职 30 天跟进',
    fields: [
      { key: 'milestone', label: '成长节点', type: 'select', options: ['入职第一天', '入职第一周', '入职满30天', '入职满60天', '入职满90天'].map(v => ({ label: v, value: v })), required: true },
      { key: 'recordDate', label: '计划日期', type: 'date', required: true },
      { key: 'content', label: '跟进内容', type: 'textarea' },
      { key: 'result', label: '完成情况', type: 'select', options: ['已掌握', '进行中', '待跟进'].map(v => ({ label: v, value: v })) },
    ],
  },
  {
    key: 'regularization', label: '转正管理', type: 'regularization', createLabel: '办理转正', titleExample: '张三转正办理',
    fields: [
      { key: 'recordDate', label: '计划转正日期', type: 'date', required: true },
      { key: 'comment', label: '转正评语', type: 'textarea' },
    ],
  },
  {
    key: 'transfer', label: '异动管理', type: 'transfer', createLabel: '发起异动', titleExample: '张三部门异动',
    fields: [
      { key: 'recordDate', label: '生效日期', type: 'date', required: true },
      { key: 'newDepartmentId', label: '新部门', type: 'org', required: true },
      { key: 'newPosition', label: '新岗位', type: 'text' },
      { key: 'reason', label: '异动原因', type: 'textarea' },
    ],
  },
  {
    key: 'resignation', label: '离职管理', type: 'resignation', createLabel: '办理离职', titleExample: '张三离职办理',
    fields: [
      { key: 'recordDate', label: '最后工作日', type: 'date', required: true },
      { key: 'reasonType', label: '离职原因', type: 'select', options: ['个人发展', '家庭原因', '薪酬原因', '合同到期', '其他'].map(v => ({ label: v, value: v })) },
      { key: 'handoverUserId', label: '交接人', type: 'user' },
      { key: 'notes', label: '备注', type: 'textarea' },
    ],
  },
  {
    key: 'contract', label: '合同管理', type: 'contract', createLabel: '登记合同', titleExample: '张三劳动合同登记',
    fields: [
      { key: 'contractNo', label: '合同编号', type: 'text', required: true },
      { key: 'contractType', label: '合同类型', type: 'select', options: ['固定期限合同', '无固定期限合同', '实习协议', '劳务协议'].map(v => ({ label: v, value: v })), required: true },
      { key: 'signDate', label: '签订日期', type: 'date' },
      { key: 'recordDate', label: '到期日期', type: 'date', required: true },
      { key: 'notes', label: '备注', type: 'textarea' },
    ],
  },
  {
    key: 'retirement', label: '退休管理', type: 'retirement', createLabel: '办理退休', titleExample: '张三退休办理',
    fields: [
      { key: 'recordDate', label: '退休日期', type: 'date', required: true },
      { key: 'notes', label: '备注', type: 'textarea' },
    ],
  },
  {
    key: 'care', label: '员工关怀', type: 'care', createLabel: '添加关怀', titleExample: '张三生日关怀',
    fields: [
      { key: 'careType', label: '关怀类型', type: 'select', options: ['生日祝福', '节日慰问', '住院探望', '困难帮扶'].map(v => ({ label: v, value: v })), required: true },
      { key: 'recordDate', label: '关怀日期', type: 'date', required: true },
      { key: 'content', label: '关怀内容', type: 'textarea' },
    ],
  },
  {
    key: 'safety', label: '用工安全', type: 'safety', createLabel: '添加用工检查', titleExample: '张三实名认证核查',
    fields: [
      { key: 'checkType', label: '检查项', type: 'select', options: ['实名认证', '合同签署', '证件核验', '背景调查'].map(v => ({ label: v, value: v })), required: true },
      { key: 'recordDate', label: '检查日期', type: 'date', required: true },
      { key: 'result', label: '检查结果', type: 'select', options: ['通过', '待整改'].map(v => ({ label: v, value: v })) },
      { key: 'notes', label: '备注', type: 'textarea' },
    ],
  },
]

/** 员工关系分组导航（用工安全固定在员工管理分组，不在此列） */
const hrNavItems = HR_MODULES.filter(m => m.key !== 'safety').map(m => ({ key: m.key, label: m.label }))

const EMPLOYEE_TYPE_LABELS: Record<string, string> = {
  full_time: '全职',
  part_time: '兼职',
  intern: '实习',
  dispatch: '劳务派遣',
  other: '其他类型',
}
const EMPLOYEE_TYPE_TAG: Record<string, string> = {
  full_time: 'primary',
  part_time: 'warning',
  intern: 'success',
  dispatch: 'danger',
  other: 'info',
}
const WORK_STATUS_LABELS: Record<string, string> = {
  probation: '试用期',
  confirmed: '已转正',
  pending_resign: '待离职',
  resigned: '已离职',
}
const WORK_STATUS_TAG: Record<string, string> = {
  probation: 'warning',
  confirmed: 'success',
  pending_resign: 'danger',
  resigned: 'info',
}
const HR_STATUS_LABELS: Record<string, string> = {
  processing: '办理中',
  done: '已完成',
  cancelled: '已取消',
}
const HR_STATUS_TAG: Record<string, string> = {
  processing: 'warning',
  done: 'success',
  cancelled: 'info',
}

const rosterView = ref<string>('roster')
const isRosterListView = computed(() => rosterView.value === 'roster' || rosterView.value === 'resigned')
const activeHrModule = computed(() => HR_MODULES.find(m => m.key === rosterView.value) || HR_MODULES[0])

const emptyRosterStats = (): RosterStats => ({
  active: 0, inactive: 0, probation: 0, confirmed: 0, pendingResign: 0, resigned: 0,
  fullTime: 0, partTime: 0, intern: 0, dispatch: 0, other: 0,
})
const rosterStatsData = ref<RosterStats>(emptyRosterStats())

// ── 人事模块场景统计（待入职/试用期/待离职/用工安全/本月生日）──
const emptyHrOverview = (): HrOverview => ({
  probation: 0, pendingResign: 0, inactive: 0, missingPhone: 0, noContract: 0,
  birthdayThisMonth: 0, birthdayUsers: [],
})
const hrOverview = ref<HrOverview>(emptyHrOverview())

async function fetchHrOverview() {
  try {
    hrOverview.value = await hrApi.getHrOverview() || emptyHrOverview()
  } catch {
    hrOverview.value = emptyHrOverview()
  }
}

// ── 高级筛选 ──
const advancedFilterVisible = ref(false)
const advancedFilter = reactive({
  employeeType: '',
  workStatus: '',
  orgId: undefined as number | undefined,
  hireRange: null as string[] | null,
})
const advancedFilterCount = computed(() =>
  (advancedFilter.employeeType ? 1 : 0) +
  (advancedFilter.workStatus ? 1 : 0) +
  (advancedFilter.orgId != null ? 1 : 0) +
  (advancedFilter.hireRange && advancedFilter.hireRange.length === 2 ? 1 : 0))

function openAdvancedFilter() {
  advancedFilterVisible.value = true
}

function applyAdvancedFilter() {
  advancedFilterVisible.value = false
  handleRosterFilterChange()
}

function clearAdvancedFilter() {
  advancedFilter.employeeType = ''
  advancedFilter.workStatus = ''
  advancedFilter.orgId = undefined
  advancedFilter.hireRange = null
  advancedFilterVisible.value = false
  handleRosterFilterChange()
}

function handleRosterFilterChange() {
  pageNum.value = 1
  fetchList()
  fetchRosterStats()
}

/** 花名册/导出/统计共用的查询参数（含高级筛选；已离职视图强制 workStatus=resigned） */
function effectiveRosterParams() {
  const resigned = rosterView.value === 'resigned'
  return {
    realName: queryParams.realName || undefined,
    status: queryParams.status || undefined,
    orgId: (rosterView.value.startsWith('roster') || resigned ? advancedFilter.orgId : queryParams.orgId) ?? undefined,
    employeeType: advancedFilter.employeeType || undefined,
    workStatus: resigned ? 'resigned' : (queryParams.workStatus || advancedFilter.workStatus || undefined),
    hireDateFrom: advancedFilter.hireRange?.[0] || undefined,
    hireDateTo: advancedFilter.hireRange?.[1] || undefined,
  }
}

async function fetchRosterStats() {
  try {
    rosterStatsData.value = await userApi.getRosterStats(effectiveRosterParams() as any) || emptyRosterStats()
  } catch {
    rosterStatsData.value = emptyRosterStats()
  }
}

function switchRosterView(view: string) {
  if (rosterView.value === view) return
  rosterView.value = view
  if (isRosterListView.value) {
    fetchList()
    fetchRosterStats()
  } else {
    hrQueryStatus.value = ''
    hrPageNum.value = 1
    fetchHrSummary()
    fetchHrOverview()
    fetchHrRecords()
  }
}

// ── 花名册导入 / 导出 / 导出历史 ──
const importing = ref(false)
const importDialogVisible = ref(false)
const importFile = ref<File | null>(null)
const importOrgId = ref<number | null>(null)
const importResult = ref<RosterImportResult | null>(null)
const exporting = ref(false)
const exportHistoryVisible = ref(false)
const exportHistoryLoading = ref(false)
const exportHistoryList = ref<RosterExportLog[]>([])
const exportHistoryPageNum = ref(1)
const exportHistoryPageSize = ref(10)
const exportHistoryTotal = ref(0)

function openImportDialog() {
  importFile.value = null
  importOrgId.value = null
  importResult.value = null
  importDialogVisible.value = true
}

async function handleDownloadTemplate() {
  try {
    const res: any = await userApi.downloadRosterImportTemplate()
    await saveBlob(res.data, '花名册导入模板.xlsx', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet')
  } catch {
    ElMessage.error('模板下载失败')
  }
}

async function handleImportSubmit() {
  if (!importFile.value) return
  importing.value = true
  try {
    importResult.value = await userApi.importRoster(importFile.value, importOrgId.value ?? undefined)
    ElMessage.success(`导入完成：成功 ${importResult.value.successCount} 条`)
    fetchList()
    fetchRosterStats()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '导入失败，请检查文件格式'))
  } finally {
    importing.value = false
  }
}

async function handleExport() {
  exporting.value = true
  try {
    const log = await userApi.exportRoster(effectiveRosterParams() as any)
    ElMessage.success(`已生成「${log.fileName}」（${log.total} 条），可在导出历史中下载`)
    // 生成后立即触发一次下载
    const res: any = await userApi.downloadRosterExportLog(log.id)
    await saveBlob(res.data, log.fileName, 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet')
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '导出失败'))
  } finally {
    exporting.value = false
  }
}

function openExportHistory() {
  exportHistoryPageNum.value = 1
  exportHistoryVisible.value = true
  fetchExportHistory()
}

async function fetchExportHistory() {
  exportHistoryLoading.value = true
  try {
    const res = await userApi.getRosterExportHistory(exportHistoryPageNum.value, exportHistoryPageSize.value)
    exportHistoryList.value = res?.list ?? []
    exportHistoryTotal.value = res?.total ?? 0
  } catch {
    exportHistoryList.value = []
    exportHistoryTotal.value = 0
  } finally {
    exportHistoryLoading.value = false
  }
}

async function handleDownloadExportLog(row: RosterExportLog) {
  try {
    const res: any = await userApi.downloadRosterExportLog(row.id)
    await saveBlob(res.data, row.fileName, 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet')
  } catch {
    ElMessage.error('下载失败')
  }
}

async function handleDeleteExportLog(row: RosterExportLog) {
  try {
    await ElMessageBox.confirm(`确定删除导出记录「${row.fileName}」吗？删除后文件不可再下载。`, '删除确认', {
      confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning',
    })
  } catch {
    return
  }
  try {
    await userApi.deleteRosterExportLog(row.id)
    ElMessage.success('已删除')
    fetchExportHistory()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '删除失败'))
  }
}

// ── 邀请认证 / 复职 ──
async function handleInviteVerify() {
  const targets = selectedUsers.value
  if (!targets.length) return
  try {
    await ElMessageBox.confirm(
      `将向选中的 ${targets.length} 名员工重新发送初始密码（认证邀请）邮件，确定继续吗？`,
      '邀请认证',
      { confirmButtonText: '发送', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  let sent = 0
  const failed: string[] = []
  for (const user of targets) {
    try {
      await userApi.sendInitialPassword(user.id)
      sent += 1
    } catch {
      failed.push(user.realName || user.username)
    }
  }
  if (failed.length) {
    ElMessage.warning(`已发送 ${sent} 条，失败：${failed.join('、')}`)
  } else {
    ElMessage.success(`已向 ${sent} 名员工发送认证邮件`)
  }
}

async function handleRestoreUsers(rows?: UserInfo[]) {
  const targets = rows ?? selectedUsers.value
  if (!targets.length) return
  try {
    await ElMessageBox.confirm(
      `确定将选中的 ${targets.length} 名员工恢复为「已转正」在职状态吗？`,
      '复职确认',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  let restored = 0
  for (const user of targets) {
    try {
      await userApi.updateUser(user.id, { workStatus: 'confirmed' } as any)
      restored += 1
    } catch {
      // 单个失败继续
    }
  }
  ElMessage.success(`已复职 ${restored} 名员工`)
  fetchList()
  fetchRosterStats()
}

// ── 人事事件台账 ──
const hrRecords = ref<HrRecord[]>([])
const hrLoading = ref(false)
const hrPageNum = ref(1)
const hrPageSize = ref(10)
const hrTotal = ref(0)
const hrQueryStatus = ref('')
const hrSummary = ref<Record<string, number>>({})
const hrUserOptions = ref<UserInfo[]>([])
const hrRecordDialogVisible = ref(false)
const hrRecordEditingId = ref<number | null>(null)
const hrRecordSubmitting = ref(false)
const hrRecordFormRef = ref<FormInstance>()
const hrRecordForm = reactive({
  userId: null as number | null,
  title: '',
  model: {} as Record<string, any>,
})
/** 「保存」时 false（办理中）；「保存并办理完成」时 true */
const hrRecordDialogDone = ref(false)

async function fetchHrSummary() {
  try {
    hrSummary.value = await hrApi.getHrRecordSummary(activeHrModule.value.type) || {}
  } catch {
    hrSummary.value = {}
  }
}

async function fetchHrRecords(resetPage = false) {
  if (resetPage) hrPageNum.value = 1
  hrLoading.value = true
  try {
    const res = await hrApi.getHrRecords({
      recordType: activeHrModule.value.type,
      status: (hrQueryStatus.value || '') as any,
      pageNum: hrPageNum.value,
      pageSize: hrPageSize.value,
    })
    hrRecords.value = res?.list ?? []
    hrTotal.value = res?.total ?? 0
  } catch {
    hrRecords.value = []
    hrTotal.value = 0
  } finally {
    hrLoading.value = false
  }
}

/** 事项弹窗的员工候选：入职办理选未激活账号（待入职），其余模块选在职员工 */
async function loadHrUserOptions() {
  try {
    const params: any = { pageNum: 1, pageSize: 200 }
    if (activeHrModule.value.type === 'onboarding') params.status = 'inactive'
    else params.status = 'active'
    const res: any = await userApi.getUserList(params)
    hrUserOptions.value = res?.list ?? []
  } catch {
    hrUserOptions.value = []
  }
}

function openHrRecordDialog(record?: HrRecord) {
  hrRecordEditingId.value = record?.id ?? null
  hrRecordForm.userId = record?.userId ?? null
  hrRecordForm.title = record?.title ?? ''
  hrRecordForm.model = {}
  const detail = record?.detail ?? {}
  for (const field of activeHrModule.value.fields) {
    if (field.key === 'recordDate') {
      hrRecordForm.model[field.key] = record?.recordDate ?? ''
    } else if (field.type === 'org') {
      hrRecordForm.model[field.key] = detail[field.key] != null ? Number(detail[field.key]) : ''
    } else {
      hrRecordForm.model[field.key] = detail[field.key] ?? ''
    }
  }
  hrRecordDialogDone.value = false
  loadHrUserOptions()
  hrRecordDialogVisible.value = true
}

async function submitHrRecord(andDone: boolean) {
  if (!hrRecordForm.userId) {
    ElMessage.warning('请选择员工')
    return
  }
  if (!hrRecordForm.title.trim()) {
    ElMessage.warning('请填写事项标题')
    return
  }
  for (const field of activeHrModule.value.fields) {
    if (field.required && !hrRecordForm.model[field.key]) {
      ElMessage.warning(`请填写「${field.label}」`)
      return
    }
  }
  const detail: Record<string, any> = {}
  let recordDate: string | undefined
  for (const field of activeHrModule.value.fields) {
    const value = hrRecordForm.model[field.key]
    if (value === '' || value == null) continue
    if (field.key === 'recordDate') {
      recordDate = value
    } else {
      detail[field.key] = value
    }
  }
  const payload = {
    recordType: activeHrModule.value.type,
    userId: hrRecordForm.userId,
    title: hrRecordForm.title.trim(),
    detail,
    recordDate,
    status: (andDone ? 'done' : 'processing') as any,
  }
  hrRecordSubmitting.value = true
  try {
    if (hrRecordEditingId.value) {
      await hrApi.updateHrRecord(hrRecordEditingId.value, {
        title: payload.title,
        detail: payload.detail,
        recordDate: payload.recordDate,
        ...(andDone ? { status: 'done' as any } : {}),
      })
    } else {
      await hrApi.createHrRecord(payload)
    }
    ElMessage.success(andDone ? '已办理完成' : '已保存')
    hrRecordDialogVisible.value = false
    fetchHrRecords()
    fetchHrSummary()
    fetchHrOverview()
    // 入职/离职/转正/异动等办理完成后员工档案有变化
    if (isRosterListView.value) {
      fetchList()
      fetchRosterStats()
    }
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '保存失败'))
  } finally {
    hrRecordSubmitting.value = false
  }
}

async function completeHrRecord(row: HrRecord) {
  try {
    await ElMessageBox.confirm(`确定完成事项「${row.title}」吗？完成后将同步更新员工档案。`, '办理完成', {
      confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning',
    })
  } catch {
    return
  }
  try {
    await hrApi.updateHrRecord(row.id, { status: 'done' })
    ElMessage.success('已办理完成')
    fetchHrRecords()
    fetchHrSummary()
    fetchHrOverview()
    if (isRosterListView.value) {
      fetchList()
      fetchRosterStats()
    }
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '操作失败'))
  }
}

async function removeHrRecord(row: HrRecord) {
  try {
    await ElMessageBox.confirm(`确定删除事项「${row.title}」吗？`, '删除确认', {
      confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning',
    })
  } catch {
    return
  }
  try {
    await hrApi.deleteHrRecord(row.id)
    ElMessage.success('已删除')
    fetchHrRecords()
    fetchHrSummary()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '删除失败'))
  }
}

/** 明细列摘要：按模块字段定义把 detail 拼成「标签 值」文本 */
function hrDetailSummary(row: HrRecord): string {
  const module = HR_MODULES.find(m => m.type === row.recordType)
  if (!module) return ''
  const parts: string[] = []
  const detail = row.detail ?? {}
  for (const field of module.fields) {
    if (field.key === 'recordDate') continue
    const raw = detail[field.key]
    if (raw === undefined || raw === null || raw === '') continue
    let display: string = String(raw)
    if (field.type === 'org') {
      display = orgName(Number(raw)) || display
    } else if (field.type === 'user') {
      const user = hrUserOptions.value.find(u => u.id === Number(raw))
      display = user?.realName || display
    } else if (field.type === 'select' && field.options) {
      display = field.options.find(o => o.value === raw)?.label || display
    }
    parts.push(`${field.label} ${display}`)
  }
  return parts.join('；')
}

// ── 批量创建部门 ──
const batchDeptVisible = ref(false)
const batchDeptParentId = ref<number | null>(null)
const batchDeptNames = ref('')
const batchDeptSubmitting = ref(false)

function openBatchCreateDept() {
  batchDeptParentId.value = activeOrgId.value
  batchDeptNames.value = ''
  batchDeptVisible.value = true
}

async function submitBatchDepartments() {
  const names = batchDeptNames.value
    .split('\n')
    .map(name => name.trim())
    .filter(Boolean)
  if (!names.length) {
    ElMessage.warning('请输入部门名称，每行一个')
    return
  }
  if (!batchDeptParentId.value) {
    ElMessage.warning('请选择上级部门')
    return
  }
  batchDeptSubmitting.value = true
  let created = 0
  const failed: string[] = []
  try {
    for (const name of names) {
      try {
        await userApi.createOrg({ name, parentId: batchDeptParentId.value, orgType: 'department' })
        created += 1
      } catch {
        failed.push(name)
      }
    }
    if (failed.length) {
      ElMessage.warning(`已创建 ${created} 个部门，失败：${failed.join('、')}`)
    } else {
      ElMessage.success(`已创建 ${created} 个部门`)
    }
    batchDeptVisible.value = false
    await loadOrgData()
  } finally {
    batchDeptSubmitting.value = false
  }
}

const activeUsers = computed(() => userList.value.filter(user => user.status === 'active'))
const activeOrgNode = computed(() => flatOrgNodes.value.find(node => node.key === activeOrgKey.value))
const activeOrgName = computed(() => activeOrgNode.value?.name || '')
/** 当前选中组织的真实ID（组织树节点的 key 带 `org-` 前缀，不能当 id 用） */
const activeOrgId = computed(() => activeOrgNode.value?.id ?? null)
const editOrgButtonText = computed(() => {
  const t = activeOrgNode.value?.orgType
  if (t === 'region') return '编辑区域'
  if (t === 'company') return '编辑公司'
  if (t === 'bureau') return '编辑委办局'
  if (t === 'department') return '编辑部门'
  if (t === 'group') return '编辑团队'
  return '编辑'
})
const orgTypeLabel = computed(() => {
  const t = activeOrgNode.value?.orgType
  return ORG_TYPE_LABELS[t ?? ''] || ''
})

const createOrgChainText = computed(() => {
  const targetId = isEdit.value ? form.orgId : activeOrgNode.value?.id
  if (!targetId) return '-'
  const targetNode = flatOrgNodes.value.find(n => n.id === targetId)
  if (!targetNode) return '-'
  const chain: string[] = []
  let node: FlatOrgNode | undefined = targetNode
  while (node) {
    chain.unshift(node.name)
    node = node.parentKey ? flatOrgNodes.value.find(n => n.key === node!.parentKey) : undefined
  }
  return chain.join(' / ') || '-'
})

const orgBreadcrumb = computed(() => {
  const chain: { key: string; name: string }[] = []
  let node = activeOrgNode.value
  while (node) {
    chain.unshift({ key: node.key, name: node.name })
    node = node.parentKey ? flatOrgNodes.value.find(n => n.key === node!.parentKey) : undefined
  }
  return chain
})

function selectOrgByKey(key: string) {
  const node = flatOrgNodes.value.find(n => n.key === key)
  if (node) selectOrg(node)
}

const editingIsRoot = computed(() => {
  if (!departmentEditingId.value) return false
  return !flatOrgNodes.value.find(n => n.id === departmentEditingId.value)?.parentKey
})

const editAllowedTypes = computed<string[]>(() => {
  if (!departmentEditingId.value) return []
  const node = flatOrgNodes.value.find(n => n.id === departmentEditingId.value)
  if (!node) return []
  const nodeUserCount = node.count
  if (node.orgType === 'group' && nodeUserCount > 0) return ['group']
  if (node.orgType === 'group' && nodeUserCount === 0) return ['region', 'company', 'bureau', 'department', 'group']
  const parentType = node.parentKey ? flatOrgNodes.value.find(n => n.key === node.parentKey)?.orgType : null
  if (parentType) {
    const allowed = ALLOWED_CHILD_TYPES[parentType] || []
    return [...new Set([...allowed, node.orgType])].filter(Boolean) as string[]
  }
  return ['region', 'company']
})

const editTypeChangeWarning = computed(() => {
  if (!departmentEditingId.value) return ''
  const node = flatOrgNodes.value.find(n => n.id === departmentEditingId.value)
  if (!node) return ''
  if (node.orgType === 'department' && departmentForm.orgType !== 'department') {
    const hasChildDepts = hasChildOfType(node.id, 'department') || hasChildOfType(node.id, 'group')
    if (hasChildDepts) return '该部门下存在子部门或团队，无法修改类型'
  }
  return ''
})

function hasChildOfType(orgId: number, targetType: string): boolean {
  function check(nodes: OrgNode[]): boolean {
    for (const n of nodes) {
      if (n.id === orgId && n.children?.length) {
        if (n.children.some(c => c.orgType === targetType)) return true
        if (n.children.some(c => check([c]))) return true
      }
      if (n.children?.length && check(n.children)) return true
    }
    return false
  }
  return check(orgTree.value)
}

function canChangeEditType(t: string): boolean {
  if (!departmentEditingId.value) return false
  const node = flatOrgNodes.value.find(n => n.id === departmentEditingId.value)
  if (!node) return false
  if (node.orgType === t) return true
  if (node.orgType === 'department' && (hasChildOfType(node.id, 'department') || hasChildOfType(node.id, 'group'))) return false
  return true
}

function onEditOrgTypeChange() {
  // reset warning state — computed handles it
}

const orgTreeForParentSelect = computed(() => {
  if (!departmentEditingId.value) return orgTree.value
  const editingId = departmentEditingId.value
  function excludeNode(nodes: OrgNode[]): OrgNode[] {
    return nodes
      .filter(n => n.id !== editingId)
      .map(n => ({ ...n, children: n.children ? excludeNode(n.children) : undefined }))
  }
  return excludeNode(orgTree.value)
})
const editDrawerOrgTypeLabel = computed(() => ORG_TYPE_LABELS[departmentForm.orgType] || '')

const ORG_TYPE_LABELS: Record<string, string> = {
  region: '区域',
  company: '公司',
  bureau: '委办局',
  department: '部门',
  group: '团队',
}

function orgIcon(orgType: string): Component {
  if (orgType === 'region') return MapLocation
  if (orgType === 'company') return OfficeBuilding
  if (orgType === 'bureau') return Stamp
  if (orgType === 'department') return Suitcase
  if (orgType === 'group') return UserFilled
  return OfficeBuilding
}

const flatOrgNodes = computed<FlatOrgNode[]>(() => {
  const nodes: FlatOrgNode[] = []

  const walk = (items: OrgNode[], level: number, parentKey: string | null) => {
    items.forEach(item => {
      const key = `org-${item.id}`
      nodes.push({
        key,
        id: item.id,
        name: item.name,
        level,
        count: resolveOrgMemberCount(item),
        icon: orgIcon(item.orgType),
        hasChildren: (item.children?.length ?? 0) > 0,
        parentKey,
        orgType: item.orgType,
        sortOrder: item.sortOrder ?? 0,
      })
      if (item.children?.length) {
        walk(item.children, level + 1, key)
      }
    })
  }
  walk(orgTree.value, 0, null)
  return nodes
})

const visibleOrgNodes = computed(() => {
  const keyword = orgKeyword.value.trim().toLowerCase()

  const isVisible = (node: FlatOrgNode): boolean => {
    if (keyword) return node.name.toLowerCase().includes(keyword)
    if (node.parentKey === null) return true
    return expandedKeys.value.has(node.parentKey) && isVisible(flatOrgNodes.value.find(n => n.key === node.parentKey)!)
  }

  return flatOrgNodes.value.filter(isVisible)
})

const departmentRows = computed<DepartmentRow[]>(() => {
  return orgTree.value.map(node => toDepartmentRow(node, 0))
})

const rules: FormRules = {
  username: [
    { required: true, message: '请输入账号名', trigger: 'blur' },
    { min: 3, max: 20, message: '账号名长度为3-20个字符', trigger: 'blur' },
    { validator: validateUsername, trigger: 'blur' },
  ],
  realName: [
    { required: true, message: '请输入姓名', trigger: 'blur' },
    { max: 50, message: '姓名长度不能超过50个字符', trigger: 'blur' },
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { validator: validateEmail, trigger: 'blur' },
  ],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { validator: validatePhone, trigger: 'blur' },
  ],
  orgId: [
    { required: true, message: '请选择所属组织', trigger: 'change' },
  ],
}

const departmentRules: FormRules = {
  name: [
    { required: true, message: '请输入部门名称', trigger: 'blur' },
    { max: 100, message: '部门名称不能超过100个字符', trigger: 'blur' },
    { validator: validateDepartmentNameUnique, trigger: 'blur' },
  ],
  parentId: [
    { required: true, message: '请选择上级部门', trigger: 'change' },
  ],
}

function filterOrgTree(nodes: OrgNode[], targetType: string): OrgNode[] {
  return nodes
    .filter(n => n.orgType === targetType || n.children?.some(child => hasOrgType(child, targetType)))
    .map(n => ({
      ...n,
      children: n.children ? filterOrgTree(n.children, targetType) : undefined,
    }))
}

function filterOrgTreeMulti(nodes: OrgNode[], types: string[]): OrgNode[] {
  return nodes
    .filter(n => types.includes(n.orgType) || n.children?.some(child => hasOrgTypeMulti(child, types)))
    .map(n => ({
      ...n,
      children: n.children ? filterOrgTreeMulti(n.children, types) : undefined,
    }))
}

function hasOrgType(node: OrgNode, targetType: string): boolean {
  return node.orgType === targetType || !!node.children?.some(child => hasOrgType(child, targetType))
}

function hasOrgTypeMulti(node: OrgNode, types: string[]): boolean {
  return types.includes(node.orgType) || !!node.children?.some(child => hasOrgTypeMulti(child, types))
}

async function loadOrgData() {
  try {
    const [org, rolesRes] = await Promise.all([
      // force=true：绕过 useOrgTree 的进程内缓存，否则删除成员后人数角标不刷新
      loadOrgTree(true),
      getRoleList(),
    ])
    orgTree.value = org
    regionTree.value = filterOrgTreeMulti(orgTree.value, ['region', 'company', 'bureau'])
    departmentTree.value = filterOrgTreeMulti(orgTree.value, ['company', 'bureau', 'department'])
    roleList.value = normalizeArray<RoleItem>(rolesRes)
    if (!activeOrgKey.value && orgTree.value.length > 0) {
      activeOrgKey.value = resolveInitialOrgKey()
      expandedKeys.value = new Set(orgTree.value.map(n => `org-${n.id}`))
    }
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '加载组织树失败'))
  }
}

async function fetchList() {
  loading.value = true
  try {
    const base = managementMode.value === 'hr' ? effectiveRosterParams() : {
      realName: queryParams.realName || undefined,
      status: queryParams.status || undefined,
      orgId: queryParams.orgId,
      regionId: queryParams.regionId,
      departmentId: queryParams.departmentId,
    }
    const res: any = await userApi.getUserList({
      ...base,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    userList.value = res?.list ?? []
    total.value = res?.total ?? 0
  } catch {
    userList.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function refreshUserManagement(resetPage = false) {
  if (resetPage) {
    pageNum.value = 1
  }
  await loadOrgData()
  await fetchList()
}

function handleSearch() {
  queryParams.orgId = undefined
  queryParams.regionId = undefined
  queryParams.departmentId = undefined
  if (orgTree.value.length > 0) {
    activeOrgKey.value = `org-${orgTree.value[0].id}`
  }
  pageNum.value = 1
  fetchList()
}

function handleCreate() {
  isEdit.value = false
  editId.value = null
  resetForm()

  const node = activeOrgNode.value
  if (node && node.id) {
    form.orgId = node.id
  }

  dialogVisible.value = true
}

const ALLOWED_CHILD_TYPES: Record<string, string[]> = {
  region: ['region', 'company', 'bureau'],
  company: ['company', 'department', 'group'],
  bureau: ['department'],
  department: ['group'],
  group: [],
}

const allowedNewTypes = computed<string[]>(() => {
  const t = activeOrgNode.value?.orgType
  if (!t) return ['region', 'company']
  return ALLOWED_CHILD_TYPES[t] || []
})

function openCreateDepartment() {
  departmentForm.name = ''
  departmentForm.parentId = null
  departmentForm.orgType = 'department'
  departmentForm.createGroup = false
  departmentEditingId.value = null
  const node = activeOrgNode.value
  if (!node) return

  const allowed = allowedNewTypes.value
  if (allowed.length === 0) {
    ElMessage.warning('该节点类型下不允许添加子节点')
    return
  }

  departmentForm.parentId = node.id
  departmentForm.orgType = allowed[0] as any
  canChangeOrgType.value = allowed.length > 1
  departmentDrawerVisible.value = true
}

function openDepartmentManagement() {
  memberView.value = 'departments'
  selectedDepartments.value = []
}

function openEditDepartment() {
  const target = selectedDepartments.value[0]
  if (!target) return
  departmentEditingId.value = target.id
  departmentForm.name = target.name
  departmentForm.parentId = target.parentId
  departmentForm.createGroup = false
  departmentDrawerVisible.value = true
}

function openEditOrgDrawer() {
  const node = activeOrgNode.value
  if (!node || !node.id) return

  resetDepartmentForm()
  departmentEditingId.value = node.id
  departmentForm.name = node.name
  departmentForm.parentId = node.parentKey ? (flatOrgNodes.value.find(n => n.key === node.parentKey)?.id ?? null) : null
  departmentForm.orgType = (node.orgType as any) || 'region'
  departmentForm.createGroup = false
  departmentDrawerVisible.value = true
}

async function handleEdit(row: UserInfo) {
  isEdit.value = true
  editId.value = row.id
  editJobNumber.value = row.jobNumber || null
  resetUsernameAutoState()
  try {
    const userDetail: any = await userApi.getUserById(row.id)
    form.username = userDetail.username
    form.realName = userDetail.realName
    form.email = userDetail.email || ''
    form.phone = userDetail.phone || ''
    form.status = userDetail.status || 'active'
    form.orgId = userDetail.orgId || null
    form.regionId = userDetail.regionId || null
    form.departmentId = userDetail.departmentId || null
    form.avatar = userDetail.avatar ?? ''
    form.employeeType = userDetail.employeeType || 'full_time'
    form.workStatus = userDetail.workStatus || 'confirmed'
    form.hireDate = userDetail.hireDate || ''
    form.birthday = userDetail.birthday || ''
    editJobNumber.value = userDetail.jobNumber || null
    // 加载用户角色：超级管理员角色不在本表单管理范围，单独保留避免保存时丢失
    const roleIds: any = await userApi.getUserRoles(row.id)
    const list: number[] = Array.isArray(roleIds) ? roleIds : []
    const superIds = new Set(
      roleList.value.filter((r) => r.code === 'SUPER_ADMIN').map((r) => r.id),
    )
    preservedSuperRoleIds.value = list.filter((id) => superIds.has(id))
    form.roleIds = list.filter((id) => !superIds.has(id))

  } catch {
    ElMessage.error('加载成员信息失败')
    return
  }
  dialogVisible.value = true
}

async function handleDelete(row: UserInfo) {
  try {
    await ElMessageBox.confirm(`确定要删除成员“${row.realName || row.username}”吗？`, '删除确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await userApi.deleteUser(row.id)
    ElMessage.success('删除成功')
    await refreshUserManagement()
  } catch {
    // user cancelled or error
  }
}

async function handleResetPassword(row: UserInfo) {
  try {
    await ElMessageBox.confirm(`确定要重置成员“${row.realName || row.username}”的密码吗？`, '重置密码', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    const result: any = await userApi.sendInitialPassword(row.id)
    ElMessage.success(typeof result === 'string' ? result : '初始密码已重置')
  } catch {
    // user cancelled
  }
}

const canToggleUser = computed(() => hasPermission('button:user:update'))

async function toggleUserStatus(row: UserInfo): Promise<boolean> {
  const nextActive = row.status !== 'active'
  // 必须用 inactive：users.status 是 ENUM('active','inactive')，
  // 写 'disabled' 在严格模式下会直接报数据截断错误
  const newStatus = nextActive ? 'active' : 'inactive'
  const statusText = nextActive ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(`确定要${statusText}成员"${row.realName || row.username}"吗？`, '状态切换', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await userApi.updateUser(row.id, { status: newStatus })
    ElMessage.success(`${statusText}成功`)
    return true
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(resolveErrorMessage(error, `${statusText}失败`))
    }
    return false
  }
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate()
  if (!form.orgId) {
    ElMessage.warning('请选择所属组织')
    return
  }
  submitting.value = true

  try {
    const shouldResetPage = !isEdit.value
    if (isEdit.value && editId.value) {
      await userApi.updateUser(editId.value, {
        realName: form.realName,
        email: form.email || null,
        phone: form.phone || null,
        status: form.status,
        orgId: form.orgId,
        regionId: form.regionId,
        departmentId: form.departmentId,
        employeeType: (form.employeeType || null) as any,
        workStatus: (form.workStatus || null) as any,
        hireDate: form.hireDate || null,
        birthday: form.birthday || null,
        // 头像：undefined=未修改不传；空串=清除；preset:/URL=设置
        ...(form.avatar !== undefined ? { avatar: form.avatar || '' } : {}),
      })
      ElMessage.success('更新成功')
      // 分配角色（合并保留已有的超级管理员角色）
      await userApi.assignRoles(editId.value, [...form.roleIds, ...preservedSuperRoleIds.value])
    } else {
      const createResult: any = await userApi.createUser({
        username: form.username,
        realName: form.realName,
        email: form.email || null,
        phone: form.phone || null,
        orgId: form.orgId,
        regionId: form.regionId,
        departmentId: form.departmentId,
        employeeType: (form.employeeType || null) as any,
        workStatus: (form.workStatus || null) as any,
        hireDate: form.hireDate || null,
        birthday: form.birthday || null,
      })
      // 获取新创建用户的ID（响应拦截器已解包 data 字段，createResult 直接就是 userId）
      const newUserId = createResult
      // 新建用户后立即分配角色
      if (newUserId) {
        await userApi.assignRoles(newUserId, [...form.roleIds, ...preservedSuperRoleIds.value])
      }
      ElMessage.success('创建成功，系统已按默认规则生成初始密码并尝试发送邮件')
    }
    dialogVisible.value = false
    await refreshUserManagement(shouldResetPage)
  } catch {
    // 响应拦截器已显示错误消息，这里处理用户体验：
    // - 不关闭对话框，让用户可以修改后重新提交
    // - 对于账号名重复（code 50001）等场景，用户可以修改账号名后再试
    // 对话框保持打开状态，用户可以修改表单重新提交
  } finally {
    submitting.value = false
  }
}

function handleUserCommand(command: string, row: UserInfo) {
  if (command === 'reset') {
    handleResetPassword(row)
    return
  }
  if (command === 'delete') {
    handleDelete(row)
  }
}

function resetForm() {
  form.username = ''
  form.realName = ''
  form.email = ''
  form.phone = ''
  form.orgId = null
  form.regionId = null
  form.departmentId = null
  form.roleIds = []
  form.avatar = undefined
  form.employeeType = 'full_time'
  form.workStatus = 'confirmed'
  form.hireDate = ''
  form.birthday = ''
  preservedSuperRoleIds.value = []
  form.status = 'active'
  editJobNumber.value = null
  resetUsernameAutoState()
  formRef.value?.resetFields()
}

function resetDepartmentForm() {
  departmentForm.name = ''
  departmentForm.parentId = null
  departmentForm.orgType = 'department'
  departmentForm.createGroup = false
  departmentEditingId.value = null
  departmentFormRef.value?.resetFields()
}

async function handleSubmitDepartment() {
  if (!departmentFormRef.value) return
  await departmentFormRef.value.validate()

  if (departmentEditingId.value) {
    const node = flatOrgNodes.value.find(n => n.id === departmentEditingId.value)
    if (node && node.orgType === 'department' && departmentForm.orgType !== 'department') {
      if (hasChildOfType(node.id, 'department') || hasChildOfType(node.id, 'group')) {
        ElMessage.warning('该部门下存在子部门或团队，无法修改类型')
        return
      }
    }
  }

  departmentSubmitting.value = true
  departmentSubmitting.value = true
  try {
    const payload = {
      name: departmentForm.name.trim(),
      parentId: departmentForm.parentId,
      orgType: departmentForm.orgType,
      description: departmentForm.createGroup ? '创建部门时勾选了关联企业群' : null,
    }
    if (departmentEditingId.value) {
      await userApi.updateOrg(departmentEditingId.value, payload)
      ElMessage.success('组织更新成功')
    } else {
      await userApi.createOrg(payload)
      ElMessage.success(`${ORG_TYPE_LABELS[departmentForm.orgType] || '组织'}添加成功`)
    }
    departmentDrawerVisible.value = false
    await loadOrgData()
  } finally {
    departmentSubmitting.value = false
  }
}

async function handleDeleteDepartments() {
  const names = selectedDepartments.value.map(item => item.name).join('、')
  try {
    await ElMessageBox.confirm(`确定要删除选中的部门“${names}”吗？`, '删除部门', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await Promise.all(selectedDepartments.value.map(item => userApi.deleteOrg(item.id)))
    ElMessage.success('删除成功')
    selectedDepartments.value = []
    await loadOrgData()
  } catch {
    // user cancelled or error
  }
}

function handleDepartmentCommand(command: string, row: DepartmentRow) {
  if (command === 'add') {
    resetDepartmentForm()
    departmentForm.parentId = row.id
    departmentDrawerVisible.value = true
    return
  }
  if (command === 'edit') {
    selectedDepartments.value = [row]
    openEditDepartment()
    return
  }
  if (command === 'delete') {
    selectedDepartments.value = [row]
    handleDeleteDepartments()
  }
}

async function selectOrg(node: FlatOrgNode) {
  activeOrgKey.value = node.key
  memberView.value = 'members'
  queryParams.username = ''
  queryParams.realName = ''
  queryParams.status = ''
  queryParams.orgId = node.id
  queryParams.regionId = undefined
  queryParams.departmentId = undefined
  pageNum.value = 1
  await fetchList()
}

/** 切换组织排序模式 */
async function toggleOrgSortMode() {
  if (isOrgSortMode.value) {
    // 退出排序模式：卸载 Sortable
    destroyOrgSortable()
    isOrgSortMode.value = false
    return
  }
  // 进入排序模式：展开所有节点便于调整
  isOrgSortMode.value = true
  expandedKeys.value = new Set(flatOrgNodes.value.filter(n => n.hasChildren).map(n => n.key))
  await nextTick()
  mountOrgSortable()
}

/** 挂载 Sortable 到组织列表，限制同层级拖拽 */
function mountOrgSortable() {
  const el = document.querySelector('.org-list') as HTMLElement | null
  if (!el) return
  destroyOrgSortable()
  orgSortableInstance = Sortable.create(el, {
    animation: 150,
    handle: '.org-item-btn',
    draggable: '.org-item',
    ghostClass: 'org-sort-ghost',
    chosenClass: 'org-sort-chosen',
    // 只允许同层级（parentKey 相同）拖拽
    onMove: (evt) => {
      const dragged = evt.dragged as HTMLElement
      const related = evt.related as HTMLElement
      // 通过扁平节点的 parentKey 判断是否同级
      const dragNode = flatOrgNodes.value.find(n => n.key === dragged.dataset.key)
      const relatedNode = flatOrgNodes.value.find(n => n.key === related.dataset.key)
      if (!dragNode || !relatedNode) return false
      return dragNode.parentKey === relatedNode.parentKey
    },
    onEnd: handleOrgSortEnd,
  })
}

/** 拖拽结束：将被移动项的新位置提交保存，后端 reindex 同级 */
async function handleOrgSortEnd(evt: SortableEvent) {
  const movedKey = (evt.item as HTMLElement).dataset.key
  const movedNode = flatOrgNodes.value.find(n => n.key === movedKey)
  if (!movedNode) return
  const parentKey = movedNode.parentKey
  const container = evt.to as HTMLElement
  // 在新顺序下定位被移动项的同级索引
  const siblingKeys = Array.from(container.querySelectorAll<HTMLElement>('.org-item'))
    .map(el => el.dataset.key)
    .filter((key): key is string => !!key)
    .filter(key => {
      const node = flatOrgNodes.value.find(n => n.key === key)
      return node && node.parentKey === parentKey
    })
  const newIndex = siblingKeys.indexOf(movedKey!)
  if (newIndex === -1 || newIndex === movedNode.sortOrder) return
  orgSortSaving.value = true
  try {
    await userApi.moveOrgNode({
      id: movedNode.id,
      targetParentId: parentKey ? Number(parentKey.replace('org-', '')) : null,
      targetSortOrder: newIndex,
    })
    await loadOrgData()
    ElMessage.success('组织排序已更新')
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '组织排序保存失败'))
    await loadOrgData()
  } finally {
    orgSortSaving.value = false
  }
}

function destroyOrgSortable() {
  if (orgSortableInstance) {
    orgSortableInstance.destroy()
    orgSortableInstance = null
  }
}

function toggleExpand(key: string) {
  const keys = new Set(expandedKeys.value)
  if (keys.has(key)) {
    keys.delete(key)
  } else {
    keys.add(key)
  }
  expandedKeys.value = keys
}

function countUsersByOrg(id: number) {
  // 收集该组织及所有子组织的 ID
  const orgIds = collectOrgIds(orgTree.value, id)
  // 统计属于这些组织的用户（orgId、departmentId、regionId 任一匹配）
  return userList.value.filter(user =>
    orgIds.includes(user.orgId ?? 0) ||
    orgIds.includes(user.departmentId ?? 0) ||
    orgIds.includes(user.regionId ?? 0)
  ).length
}

/**
 * 递归收集组织及其所有子组织的 ID
 */
function collectOrgIds(nodes: OrgNode[], targetId: number): number[] {
  const ids: number[] = []
  for (const node of nodes) {
    if (node.id === targetId) {
      ids.push(node.id)
      // 递归收集所有子组织
      if (node.children?.length) {
        for (const child of node.children) {
          ids.push(...collectOrgIds([child], child.id))
        }
      }
    } else if (node.children?.length) {
      ids.push(...collectOrgIds(node.children, targetId))
    }
  }
  return ids
}

function resolveOrgMemberCount(node: Pick<OrgNode, 'id' | 'memberCount'>) {
  if (typeof node.memberCount === 'number') {
    return node.memberCount
  }
  return countUsersByOrg(node.id)
}

function orgName(id?: number | null) {
  if (!id) return '-'
  const found = flatOrgNodes.value.find(node => node.id === id)
  return found?.name || '-'
}


function avatarText(row: UserInfo) {
  return (row.realName || row.username || '?').slice(0, 1)
}

/** 列表头像地址解析：preset:xxx → SVG data URL；URL/路径原样；空回退首字 */
function userAvatarUrl(row: UserInfo) {
  return resolveAvatarUrl(row.avatar)
}

function maskPhone(phone?: string | null) {
  if (!phone) return '-'
  return phone.replace(/^(\+?\d{0,4})?(\d{3})\d{4}(\d{4})$/, (_match, prefix = '', start, end) => `${prefix}${start}****${end}`)
}


function showTodo(action: string) {
  ElMessage.info(`${action}能力将在后续接口完善后接入`)
}

/* ── 邀请成员（通过链接邀请 / 批量邀请） ── */
function handleInviteCommand(command: string) {
  inviteDialogTab.value = command === 'batch' ? 'batch' : 'link'
  inviteDialogVisible.value = true
}

function handleInvited() {
  // 邀请本身不新增成员，但组织人数等统计可能已经变化，顺手刷新一次
  fetchList()
}

/* ── 添加/申请记录 ── */
function openApplicationRecords() {
  recordsDialogVisible.value = true
}

/* ── 批量管理（批量启用 / 批量停用 / 批量删除） ── */
const BATCH_ACTION_META: Record<string, { label: string; verb: string; danger: boolean }> = {
  enable: { label: '批量启用', verb: '启用', danger: false },
  disable: { label: '批量停用', verb: '停用', danger: false },
  delete: { label: '批量删除', verb: '删除', danger: true },
}

async function handleBatchCommand(command: string) {
  if (batchSubmitting.value) return
  const meta = BATCH_ACTION_META[command]
  if (!meta) return

  const rows = selectedUsers.value
  if (rows.length === 0) {
    ElMessage.warning('请先在列表中勾选要操作的成员')
    return
  }

  const ids = rows.map(user => user.id)
  const tip = meta.danger
    ? `确定要删除选中的 ${ids.length} 位成员吗？删除后不可恢复。`
    : `确定要${meta.verb}选中的 ${ids.length} 位成员吗？`

  try {
    await ElMessageBox.confirm(tip, meta.label, {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: meta.danger ? 'error' : 'warning',
    })
  } catch {
    return
  }

  batchSubmitting.value = true
  try {
    const affected = command === 'delete'
      ? await userApi.batchDeleteUsers(ids)
      : await userApi.batchUpdateUserStatus(ids, command === 'enable' ? 'active' : 'inactive')
    ElMessage.success(`${meta.label}完成，共 ${affected} 位成员`)
    selectedUsers.value = []
    // 删除会改变各组织的人数统计角标，组织树需要一并刷新
    if (command === 'delete') {
      await refreshUserManagement()
    } else {
      await fetchList()
    }
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, `${meta.label}失败，请稍后重试`))
  } finally {
    batchSubmitting.value = false
  }
}

function validateEmail(_rule: unknown, value: string, callback: (error?: Error) => void) {
  if (value && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) {
    callback(new Error('请输入正确的邮箱格式'))
  } else {
    callback()
  }
}

function validatePhone(_rule: unknown, value: string, callback: (error?: Error) => void) {
  if (value && !/^1[3-9]\d{9}$/.test(value)) {
    callback(new Error('请输入正确的手机号格式'))
  } else {
    callback()
  }
}

function validateUsername(_rule: unknown, value: string, callback: (error?: Error) => void) {
  if (value && !/^[a-zA-Z0-9_]+$/.test(value)) {
    callback(new Error('账号名仅支持字母、数字、下划线'))
  } else {
    callback()
  }
}

/* ── 账号名自动生成（姓名 → 拼音 + 查重加序号） ── */
// 标记账号名是否已被用户手动修改过；改过就不再随姓名联动覆盖
const usernameManuallyEdited = ref(false)

function realNameToBaseUsername(realName: string): string {
  if (!realName) return ''
  const trimmed = realName.trim()
  if (!trimmed) return ''
  try {
    return pinyin(trimmed, {
      toneType: 'none',
      type: 'string',
      v: true,
      nonZh: 'consecutive',
    })
      .replace(/\s+/g, '')
      .replace(/[^a-zA-Z0-9_]/g, '')
      .toLowerCase()
  } catch {
    return ''
  }
}

/**
 * 计算当前用户名列表里已被占用的账号名集合
 * 用于查重加序号
 */
function isUsernameTaken(name: string): boolean {
  const lower = name.toLowerCase()
  return userList.value.some(u => (u.username || '').toLowerCase() === lower)
}

/**
 * 从 base 出发，在已占用集合中挑出第一个未占用的 zhangsan / zhangsan1 / zhangsan2...
 * 长度上限 20
 */
function findFreeUsername(base: string): string {
  if (!base) return ''
  const maxLen = 20
  const trimmedBase = base.slice(0, maxLen)
  if (!isUsernameTaken(trimmedBase)) return trimmedBase
  for (let n = 1; n < 10000; n++) {
    const suffix = String(n)
    const reserve = suffix.length
    if (trimmedBase.length + reserve > maxLen) break
    const candidate = trimmedBase + suffix
    if (!isUsernameTaken(candidate)) return candidate
  }
  // 极端兜底：缩短 base 留 2 位后缀空间
  return trimmedBase.slice(0, Math.max(1, maxLen - 2)) + '99'
}

function handleRealNameInput(value: string | number) {
  if (isEdit.value) return
  if (usernameManuallyEdited.value) return
  const base = realNameToBaseUsername(String(value ?? ''))
  form.username = findFreeUsername(base)
}

function handleUsernameInput() {
  if (isEdit.value) return
  // 用户改过就标记，避免后续姓名变更覆盖其输入
  usernameManuallyEdited.value = true
}

function resetUsernameAutoState() {
  usernameManuallyEdited.value = false
}

function validateDepartmentNameUnique(_rule: unknown, value: string, callback: (error?: Error) => void) {
  const name = value.trim()
  if (!name || !departmentForm.parentId) {
    callback()
    return
  }
  const siblingNames = findOrgChildren(departmentForm.parentId)
    .filter(item => item.id !== departmentEditingId.value)
    .map(item => item.name.trim())
  if (siblingNames.includes(name)) {
    callback(new Error('同级部门名称已存在'))
  } else {
    callback()
  }
}

function toDepartmentRow(node: OrgNode, level: number): DepartmentRow {
  return {
    id: node.id,
    parentId: node.parentId,
    name: node.name,
    level,
    count: resolveOrgMemberCount(node),
    children: node.children?.map(child => toDepartmentRow(child, level + 1)),
  }
}

function findOrgChildren(parentId: number) {
  const stack = [...orgTree.value]
  while (stack.length) {
    const node = stack.shift()
    if (!node) continue
    if (node.id === parentId) {
      return node.children || []
    }
    stack.push(...(node.children || []))
  }
  return []
}

onMounted(async () => {
  loadColumnConfig()
  rosterLoadColumnConfig()
  await loadOrgData()
  const node = activeOrgNode.value
  if (node) {
    await selectOrg(node)
  } else {
    await fetchList()
  }
})

// 切换到人事管理模式时，刷新花名册数据与统计板
watch(managementMode, (mode) => {
  if (mode === 'hr') {
    if (rosterView.value !== 'roster') rosterView.value = 'roster'
    pageNum.value = 1
    fetchList()
    fetchRosterStats()
  }
})

onBeforeUnmount(() => {
  // Sortable 实例由 ColumnConfigDialog 自行管理，无需在此清理
  destroyOrgSortable()
})

function resolveInitialOrgKey() {
  const preferredNode = flatOrgNodes.value.find(node => node.parentKey !== null && node.count > 0)
    || flatOrgNodes.value.find(node => node.count > 0)
    || flatOrgNodes.value[0]
  return preferredNode?.key || ''
}
</script>

<style lang="scss" scoped>
.form-label-with-tip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.form-label-tip-icon {
  color: var(--color-text-secondary);
  cursor: help;
  font-size: 15px;
  outline: none;
  transition: color 0.2s;

  &:hover,
  &:focus-visible {
    color: var(--color-accent);
  }
}

:global(.initial-password-tooltip) {
  max-width: 320px;
  line-height: 1.6;
}

.member-console {
  /* 定高面板：内部区域自行滚动，分页条钉在底部始终可见（不再依赖外层页面滚动） */
  height: calc(100vh - 130px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
}

.page-crumb {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.mode-switch {
  flex-shrink: 0;
  padding: 4px;
  border-radius: 6px;
  background: var(--color-surface);
}

.mode-switch :deep(.el-radio-button__inner) {
  min-width: 130px;
  border: 0;
  background: transparent;
  box-shadow: none;
  color: var(--color-text-primary);
  font-weight: 500;
}

.mode-switch :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background: var(--color-info-light);
  color: var(--color-accent);
  font-weight: 600;
  box-shadow: none;
}

.member-layout,
.roster-layout {
  display: grid;
  /* 占满 console 剩余高度（console 为 flex 列，底部留给分页条） */
  flex: 1;
  min-height: 0;
  position: relative;
}

.member-layout {
  grid-template-columns: var(--member-sidebar-width, 284px) var(--member-sidebar-resizer-width, 4px) minmax(0, 1fr);
}

.roster-layout {
  grid-template-columns: var(--roster-sidebar-width, 244px) var(--roster-sidebar-resizer-width, 4px) minmax(0, 1fr);
}

.sidebar-resizer {
  width: 4px;
  cursor: col-resize;
  background: transparent;
  transition: background 0.15s;
  align-self: stretch;
  z-index: 1;

  &:hover,
  &:active {
    background: var(--color-accent);
  }
}

.sidebar-expand-btn {
  position: absolute;
  top: 50%;
  left: 0;
  transform: translateY(-50%);
  width: 20px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--color-border);
  border-left: 0;
  border-radius: 0 6px 6px 0;
  background: var(--color-surface);
  cursor: pointer;
  z-index: 2;
  box-shadow: 2px 0 6px rgba(0, 0, 0, 0.06);

  &:hover {
    background: var(--color-fill-secondary);
  }
}

.member-sidebar,
.roster-nav {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  padding: var(--spacing-md);
  border-right: 1px solid var(--color-border);
  background: var(--color-surface);
  overflow: auto;
}

.member-sidebar.is-collapsed,
.roster-nav.is-collapsed {
  display: none;
}

.sidebar-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
}

.sidebar-head__title {
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--color-text-primary);
}

.sidebar-collapse-trigger {
  padding: 4px;
  color: var(--color-text-secondary);
  border-radius: 4px;

  &:hover {
    color: var(--color-accent);
    background: rgba(64, 158, 255, 0.08);
  }
}

.sidebar-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--spacing-sm);
  margin: var(--spacing-md) 0;
}

.org-list,
.nav-section {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.org-item,
.nav-item {
  width: 100%;
  min-height: 38px;
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--color-text-primary);
  text-align: left;
  cursor: pointer;
}

.org-sort-tip {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0 8px 8px;
  padding: 6px 10px;
  font-size: 12px;
  color: var(--color-muted-text);
  background: var(--color-primary-subtle);
  border: 1px solid var(--color-primary-light);
  border-radius: 6px;

  .el-icon {
    color: var(--color-primary);
  }

  span {
    flex: 1;
  }
}

.org-item.is-sortable {
  cursor: grab;

  &:active {
    cursor: grabbing;
  }
}

.org-sort-ghost {
  opacity: 0.4;
  background: var(--color-primary-subtle) !important;
}

.org-sort-chosen {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.org-item:hover,
.nav-item:hover,
.org-item.is-active,
.nav-item.is-active {
  background: var(--color-surface-alt);
}

.org-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  flex-shrink: 0;
  cursor: pointer;
  border-radius: 3px;
  transition: background 0.15s;

  &:hover {
    background: rgba(0, 0, 0, 0.06);
  }

  .el-icon {
    font-size: 12px;
    transition: transform 0.2s;
  }

  .el-icon.is-expanded {
    transform: rotate(90deg);
  }

  &--leaf {
    cursor: default;

    &:hover {
      background: transparent;
    }
  }
}

.org-item-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-sm);
  border: 0;
  background: transparent;
  color: var(--color-text-primary);
  cursor: pointer;
  padding: 0;
  flex: 1;
  min-width: 0;
}

.org-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.org-count {
  color: var(--color-text-secondary);
}

.member-main,
.roster-main {
  min-width: 0;
  padding: var(--spacing-lg);
  background: var(--color-surface);
  /* 主区内部滚动：表格再长也只在 main 内滚，分页条保持可见 */
  overflow: auto;
  /* 显式指定 grid-column：当 sidebar 折叠（display: none）时，
     防止 main 被错位放到第二个 track 而被压缩到 0 宽 */
  grid-column: 3;
}

.org-header,
.roster-header,
.action-row,
.roster-filter {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-md);
  flex-wrap: wrap;
}

.org-breadcrumb {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 6px;
  font-size: var(--font-size-sm);
}

.org-breadcrumb-link {
  color: var(--color-accent);
  cursor: pointer;

  &:hover {
    text-decoration: underline;
  }
}

.org-breadcrumb-current {
  color: var(--color-text-primary);
  font-weight: 500;
}

.org-breadcrumb-sep {
  color: var(--color-text-placeholder);
}

.org-title {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  font-size: 22px;
  font-weight: 700;
  color: var(--color-text-primary);
}

.org-header p {
  margin: 8px 0 0;
  color: var(--color-text-secondary);
}

.action-row {
  justify-content: flex-start;
  margin: var(--spacing-lg) 0 var(--spacing-md);
}

.member-table {
  width: 100%;
}

.member-table :deep(.el-table__cell) {
  vertical-align: middle;
}

.member-table :deep(.el-table__header th) {
  height: 44px;
}

.member-table :deep(.el-table__body .el-table__row td) {
  height: 56px;
}

.table-action-icons {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.table-action-icons .el-button {
  width: 28px;
  height: 28px;
  padding: 0;
}

.member-cell {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

// ── 编辑成员弹窗-头像设置块 ──
.avatar-edit-block {
  display: flex;
  align-items: center;
  gap: 14px;
}
.avatar-edit-block__preview {
  flex-shrink: 0;
  background: var(--color-primary, var(--color-primary));
  color: #fff;
  font-size: 20px;
  font-weight: 600;
}
.avatar-edit-block__actions {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: flex-start;
}

.member-cell > .el-avatar {
  flex-shrink: 0;
  border: 1.5px solid var(--color-border-light);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}

.member-info {
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 2px;
  overflow: hidden;
}

.member-name {
  min-width: 0;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--color-text-primary);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.3;
  white-space: nowrap;
}

.member-sub {
  color: var(--color-text-placeholder);
  font-size: var(--font-size-xs);
  line-height: 1.3;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.status-dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  margin-right: 6px;
  border-radius: 50%;
  background: var(--color-success);
}

.status-dot.is-disabled {
  background: var(--color-text-placeholder);
}

.nav-title {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin: var(--spacing-md) 0 var(--spacing-sm);
  color: var(--color-text-primary);
  font-weight: 700;
}

.roster-header h3 {
  margin: 0;
  font-size: 22px;
  color: var(--color-text-primary);
}

.roster-links {
  display: flex;
  gap: var(--spacing-sm);
  flex-wrap: wrap;
}

.stats-board {
  display: grid;
  grid-template-columns: minmax(160px, 1.1fr) repeat(8, minmax(96px, 1fr));
  gap: var(--spacing-sm);
  margin: var(--spacing-lg) 0 var(--spacing-md);
}

/* 人事子模块统计卡数量较少（3~5 张），固定列数会拉伸过宽 */
.hr-module .stats-board {
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
}

.stat-card.is-warning {
  border-color: var(--color-warning, #f59e0b);

  span,
  strong {
    color: var(--color-warning, #f59e0b);
  }
}

.stat-card {
  min-height: 72px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6px;
  padding: var(--spacing-md);
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: var(--color-surface);
}

.stat-card.is-primary {
  background: var(--color-fill-secondary);

  span,
  strong {
    color: var(--color-accent);
  }
}

.stat-card span {
  color: var(--color-text-primary);
  font-size: var(--font-size-sm);
}

.stat-card strong {
  color: var(--color-text-primary);
  font-size: 24px;
  line-height: 1;
}

.roster-filter {
  justify-content: flex-start;
  margin-bottom: var(--spacing-md);
}

.roster-filter .el-input {
  width: 170px;
}

.roster-filter .el-select {
  width: 140px;
}

.filter-spacer {
  flex: 1;
}

.advanced-filter-badge {
  margin-left: 6px;
  vertical-align: top;
}

/* ── 人事子模块面板 ── */
.hr-module {
  display: flex;
  flex-direction: column;
  min-height: 100%;
}

/* ── 员工关怀：本月寿星 ── */
.care-birthday {
  margin-bottom: var(--spacing-md);
  padding: var(--spacing-md);
  border: 1px dashed var(--color-border);
  border-radius: 8px;
  background: var(--color-surface);
}

.care-birthday__title {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: var(--spacing-sm);
  color: var(--color-text-primary);
  font-size: var(--font-size-sm);
  font-weight: 600;
}

.care-birthday__list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-md);
}

.care-birthday__item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.care-birthday__avatar {
  background: var(--color-primary-light, #dbeafe);
  color: var(--color-accent);
  font-size: 13px;
}

.care-birthday__name {
  color: var(--color-text-primary);
  font-size: var(--font-size-sm);
}

/* ── 导入花名册向导 ── */
.import-steps {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
}

.import-step {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.import-step__title {
  color: var(--color-text-primary);
  font-size: var(--font-size-sm);
  font-weight: 600;
}

.import-result {
  margin-top: var(--spacing-sm);
}

.import-result__failures {
  max-height: 160px;
  margin: var(--spacing-sm) 0 0;
  padding-left: 18px;
  overflow-y: auto;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.8;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  padding: var(--spacing-md) var(--spacing-lg);
  border-top: 1px solid var(--color-border);
  background: var(--color-surface);
}

.department-management {
  min-height: calc(100vh - 220px);
  padding: var(--spacing-lg);
  background: var(--color-surface);
}

.department-head,
.department-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-md);
  flex-wrap: wrap;
}

.department-title {
  color: var(--color-text-primary);
  font-size: 22px;
  font-weight: 700;
}

.department-head p {
  margin: 8px 0 0;
  color: var(--color-text-secondary);
}

.department-actions {
  justify-content: flex-start;
  margin: var(--spacing-lg) 0 var(--spacing-sm);
}

.department-table {
  width: 100%;
}

.department-name-cell {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-sm);
  color: var(--color-text-primary);
}

.drawer-section-title {
  margin: -20px -20px 18px;
  padding: 10px 20px;
  background: var(--color-fill-secondary);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.department-form :deep(.el-form-item__label) {
  color: var(--color-text-primary);
  font-weight: 600;
}

.effective-tip {
  margin: -6px 0 var(--spacing-md);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.department-option {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-xs);
}

.org-chain-text {
  color: var(--color-text-primary);
  font-size: var(--font-size-base);
  line-height: 32px;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--spacing-sm);
}

@media (max-width: 1200px) {
  .stats-board {
    grid-template-columns: repeat(3, minmax(130px, 1fr));
  }
}

@media (max-width: 900px) {
  .member-layout,
  .roster-layout {
    grid-template-columns: 1fr;
  }

  .sidebar-resizer {
    display: none;
  }

  .member-sidebar,
  .roster-nav {
    border-right: 0;
    border-bottom: 1px solid var(--color-border);
  }

  .mode-switch,
  .mode-switch :deep(.el-radio-button),
  .mode-switch :deep(.el-radio-button__inner) {
    width: 100%;
  }

  .stats-board {
    grid-template-columns: repeat(2, minmax(130px, 1fr));
  }

  .filter-spacer {
    display: none;
  }
}

// 列设置弹窗样式已迁至 src/styles/column-config.scss（全局）
</style>
