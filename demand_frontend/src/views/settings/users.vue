<template>
  <PageContainer :breadcrumb="false">
    <div class="member-console">
      <div class="member-topbar">
        <div />
        <el-radio-group v-model="managementMode" class="mode-switch">
          <el-radio-button value="basic">基础管理模式</el-radio-button>
          <el-radio-button value="hr">人事管理模式</el-radio-button>
        </el-radio-group>
      </div>

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

          <div class="org-list">
            <div
              v-for="node in visibleOrgNodes"
              :key="node.key"
              class="org-item"
              :class="{ 'is-active': activeOrgKey === node.key }"
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
              <el-dropdown @command="showTodo">
                <span>
                  邀请成员
                  <el-icon class="el-icon--right"><ArrowDown /></el-icon>
                </span>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="通过链接邀请">通过链接邀请</el-dropdown-item>
                    <el-dropdown-item command="批量邀请">批量邀请</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </AppButton>
            <AppButton permission="button:user:update" @click="showTodo('添加/申请记录')">添加/申请记录</AppButton>
            <AppButton permission="button:user:batch-delete">
              <el-dropdown @command="showTodo">
                <span>
                  批量管理
                  <el-icon class="el-icon--right"><ArrowDown /></el-icon>
                </span>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-permission="'button:user:batch-delete'" command="批量启用">批量启用</el-dropdown-item>
                    <el-dropdown-item v-permission="'button:user:batch-delete'" command="批量停用">批量停用</el-dropdown-item>
                    <el-dropdown-item v-permission="'button:user:batch-delete'" command="批量删除">批量删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </AppButton>
            <AppButton permission="button:user:update" @click="showTodo('调整排序')">调整排序</AppButton>
            <el-dropdown @command="handleColumnVisibilityChange" trigger="click">
              <AppButton>
                <el-icon><Setting /></el-icon>
                列设置
              </AppButton>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-for="col in columnConfig" :key="col.key" :command="col.key">
                    <el-checkbox :model-value="col.visible" @click.prevent>
                      {{ col.label }}
                    </el-checkbox>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>

          <el-table :data="userList" border class="member-table" @selection-change="selectedUsers = $event">
            <el-table-column type="selection" width="48" />
            <el-table-column label="姓名" min-width="220">
              <template #default="{ row }">
                <div class="member-cell">
                  <el-avatar :size="34" :src="row.avatar || undefined">{{ avatarText(row) }}</el-avatar>
                  <div>
                    <div class="member-name">
                      {{ row.realName || row.username }}
                      <el-tag v-if="row.id === 1" size="small" type="primary">主管理员</el-tag>
                    </div>
                    <div class="member-sub">{{ row.username }}</div>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column v-if="getColumnVisible('org')" label="所属组织" min-width="140">
              <template #default="{ row }">{{ orgName(row.orgId) || '-' }}</template>
            </el-table-column>
            <el-table-column v-if="getColumnVisible('status')" label="账号状态" width="140">
              <template #default="{ row }">
                <span class="status-dot" :class="{ 'is-disabled': row.status !== 'active' }" />
                {{ row.status === 'active' ? '正常' : '停用' }}
              </template>
            </el-table-column>
            <el-table-column v-if="getColumnVisible('role')" label="角色" min-width="140">
              <template #default="{ row }">{{ row.systemRole || '-' }}</template>
            </el-table-column>
            <el-table-column v-if="getColumnVisible('jobNumber')" label="工号" width="120">
              <template #default="{ row }">{{ row.jobNumber || '-' }}</template>
            </el-table-column>
            <el-table-column v-if="getColumnVisible('phone')" label="手机号" width="140">
              <template #default="{ row }">{{ maskPhone(row.phone) }}</template>
            </el-table-column>
            <el-table-column v-if="getColumnVisible('email')" prop="email" label="邮箱" min-width="190" show-overflow-tooltip />
            <el-table-column v-if="getColumnVisible('userId')" label="员工UserID" min-width="150">
              <template #default="{ row }">{{ row.username }}</template>
            </el-table-column>
            <el-table-column label="操作" width="112" fixed="right">
              <template #default="{ row }">
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
                      <el-dropdown-item v-if="hasPermission('button:user:update')" command="toggle">
                        <el-icon><SwitchButton /></el-icon>
                        {{ row.status === 'active' ? '停用' : '启用' }}
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
            <AppButton permission="button:org:batch-create" @click="showTodo('批量创建部门')">批量创建部门</AppButton>
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
            <button class="nav-item is-active" type="button">花名册</button>
            <button class="nav-item" type="button" @click="showTodo('用工安全')">用工安全</button>
          </div>
          <div class="nav-section">
            <div class="nav-title">
              <el-icon><Connection /></el-icon>
              员工关系
            </div>
            <button v-for="item in hrNavItems" :key="item" class="nav-item" type="button" @click="showTodo(item)">
              {{ item }}
            </button>
          </div>
        </aside>

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
          <div class="roster-header">
            <h3>花名册</h3>
            <div class="roster-links">
              <el-button link @click="showTodo('已离职员工')">
                <el-icon><UserFilled /></el-icon>
                已离职员工
              </el-button>
              <el-button link @click="showTodo('自定义字段设置')">
                <el-icon><Setting /></el-icon>
                自定义字段设置
              </el-button>
              <el-button link @click="showTodo('导出历史花名册')">
                <el-icon><Clock /></el-icon>
                导出历史花名册
              </el-button>
            </div>
          </div>

          <div class="stats-board">
            <div class="stat-card is-primary">
              <span>在职员工</span>
              <strong>{{ activeUsers.length }}</strong>
            </div>
            <div v-for="item in rosterStats" :key="item.label" class="stat-card">
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </div>
          </div>

          <div class="roster-filter">
            <el-input v-model="queryParams.realName" placeholder="搜索员工" clearable @keyup.enter="handleSearch">
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
            <el-select v-model="queryParams.status" placeholder="实名认证" clearable>
              <el-option label="已认证" value="active" />
              <el-option label="未认证/停用" value="disabled" />
            </el-select>
            <el-button @click="showTodo('高级筛选')">
              高级筛选
              <el-icon class="el-icon--right"><Filter /></el-icon>
            </el-button>
            <div class="filter-spacer" />
            <AppButton permission="button:user:create" @click="handleCreate">添加员工</AppButton>
            <el-button @click="showTodo('邀请认证')">邀请认证</el-button>
            <el-button @click="showTodo('导出')">导出</el-button>
            <el-button type="primary" @click="showTodo('导入花名册')">导入花名册</el-button>
          </div>

          <el-table :data="userList" border class="member-table" @selection-change="selectedUsers = $event">
            <el-table-column type="selection" width="48" />
            <el-table-column label="姓名" min-width="220">
              <template #default="{ row }">
                <div class="member-cell">
                  <el-avatar :size="34" :src="row.avatar || undefined">{{ avatarText(row) }}</el-avatar>
                  <span class="member-name">{{ row.realName || row.username }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="部门" min-width="150">
              <template #default="{ row }">{{ orgName(row.departmentId) }}</template>
            </el-table-column>
            <el-table-column label="角色" min-width="130">
              <template #default="{ row }">{{ row.systemRole || '-' }}</template>
            </el-table-column>
            <el-table-column label="入职时间" width="160">
              <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="员工类型" width="130">
              <template #default="{ row }">{{ row.status === 'active' ? '全职' : '待确认' }}</template>
            </el-table-column>
            <el-table-column label="手机号" width="170">
              <template #default="{ row }">{{ maskPhone(row.phone) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="86" fixed="right">
              <template #default="{ row }">
                <AppButton link type="primary" size="small" permission="button:user:update" @click="handleEdit(row)">
                  <el-icon><Edit /></el-icon>
                </AppButton>
              </template>
            </el-table-column>
          </el-table>
        </main>
      </div>

      <div class="pagination-row">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="fetchList"
          @current-change="fetchList"
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
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" :disabled="isEdit" />
        </el-form-item>
        <el-alert
          v-if="!isEdit"
          type="info"
          :closable="false"
          show-icon
          title="初始密码默认生成为“用户名 + 手机号后3位”，创建成功后系统会通过邮箱发送给用户。"
          style="margin-bottom: 16px"
        />
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="form.realName" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入手机号" />
        </el-form-item>

        <el-divider content-position="left">组织信息</el-divider>

        <template v-if="isEdit && isSuperAdmin">
          <el-form-item label="所属组织" prop="orgId">
            <el-tree-select
              v-model="form.orgId"
              :data="orgTree"
              :props="{ label: 'name', value: 'id', children: 'children' }"
              placeholder="请选择组织"
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
        </template>
        <template v-else>
          <el-form-item label="所属组织">
            <span class="org-chain-text">{{ createOrgChainText }}</span>
          </el-form-item>
        </template>
        <el-form-item label="角色" prop="roleId">
          <el-select v-model="form.roleId" placeholder="请选择角色" clearable style="width: 100%">
            <el-option
              v-for="role in roleList"
              :key="role.id"
              :label="role.name"
              :value="role.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item v-if="isEdit" label="工号">
          <el-input :model-value="editJobNumber" disabled placeholder="系统自动生成" />
        </el-form-item>
        <el-form-item v-if="isEdit" label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio value="active">启用</el-radio>
            <el-radio value="disabled">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button v-permission="isEdit ? 'button:user:update' : 'button:user:create'" type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

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
  </PageContainer>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, type Component } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft,
  ArrowDown,
  ArrowRight,
  Clock,
  Connection,
  Delete,
  Edit,
  Filter,
  FolderOpened,
  Key,
  MapLocation,
  MoreFilled,
  OfficeBuilding,
  Operation,
  Plus,
  Search,
  Setting,
  Stamp,
  Suitcase,
  SwitchButton,
  User,
  UserFilled,
} from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import * as userApi from '@/api/modules/user'
import type { OrgNode, User as UserInfo } from '@/types/user'
import PageContainer from '@/components/common/PageContainer.vue'
import AppButton from '@/components/common/AppButton.vue'
import { formatDate as formatDateTime } from '@/utils/format'
import { getRoleList } from '@/api/modules/role'
import type { RoleItem } from '@/api/modules/menu'
import { useUserStore } from '@/stores/modules/user'
import { useCollapsibleSidebar } from '@/composables/useCollapsibleSidebar'
import { usePermission } from '@/composables/usePermission'

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
  roleId: number | null
  status: string
}

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

// 列配置
const columnConfig = ref([
  { key: 'org', label: '所属组织', visible: true },
  { key: 'status', label: '账号状态', visible: true },
  { key: 'role', label: '角色', visible: true },
  { key: 'jobNumber', label: '工号', visible: true },
  { key: 'phone', label: '手机号', visible: true },
  { key: 'email', label: '邮箱', visible: true },
  { key: 'userId', label: '员工UserID', visible: true },
])

function getColumnVisible(key: string): boolean {
  return columnConfig.value.find(col => col.key === key)?.visible ?? true
}

function handleColumnVisibilityChange(key: string) {
  const col = columnConfig.value.find(c => c.key === key)
  if (col) {
    col.visible = !col.visible
  }
}
const orgKeyword = ref('')
const activeOrgKey = ref('')
const selectedDepartments = ref<DepartmentRow[]>([])
const expandedKeys = ref<Set<string>>(new Set())
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
})

const dialogVisible = ref(false)
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

const queryParams = reactive({
  username: '',
  realName: '',
  status: '',
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
  roleId: null,
  status: 'active',
})

const departmentForm = reactive({
  name: '',
  parentId: null as number | null,
  orgType: 'department' as 'region' | 'company' | 'bureau' | 'department',
  createGroup: false,
})
const departmentEditingId = ref<number | null>(null)
const canChangeOrgType = ref(true)

const hrNavItems = ['入职管理', '新人成长', '转正管理', '异动管理', '离职管理', '合同管理', '退休管理', '员工关怀']

const activeUsers = computed(() => userList.value.filter(user => user.status === 'active'))
const activeOrgNode = computed(() => flatOrgNodes.value.find(node => node.key === activeOrgKey.value))
const activeOrgName = computed(() => activeOrgNode.value?.name || '')
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
const rosterStats = computed(() => [
  { label: '全职', value: activeUsers.value.length },
  { label: '兼职', value: 0 },
  { label: '实习', value: 0 },
  { label: '劳务派遣', value: 0 },
  { label: '其他类型', value: userList.value.filter(user => user.status !== 'active').length },
  { label: '试用期', value: 0 },
  { label: '已转正', value: activeUsers.value.length },
  { label: '待离职', value: 0 },
])

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
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度为3-20个字符', trigger: 'blur' },
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

// 手机号脱敏显示
function maskPhone(phone: string | null | undefined): string {
  if (!phone) return '-'
  if (phone.length === 11) {
    return phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2')
  }
  return phone
}

function normalizeArray<T>(value: unknown): T[] {
  if (Array.isArray(value)) return value as T[]
  const data = (value as any)?.data
  if (Array.isArray(data)) return data as T[]
  if (Array.isArray(data?.data)) return data.data as T[]
  return []
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
    const [orgRes, rolesRes] = await Promise.all([
      userApi.getOrgTree(),
      getRoleList(),
    ])
    orgTree.value = normalizeArray<OrgNode>(orgRes)
    regionTree.value = filterOrgTreeMulti(orgTree.value, ['region', 'company', 'bureau'])
    departmentTree.value = filterOrgTreeMulti(orgTree.value, ['company', 'bureau', 'department'])
    roleList.value = normalizeArray<RoleItem>(rolesRes)

    if (!activeOrgKey.value && orgTree.value.length > 0) {
      activeOrgKey.value = resolveInitialOrgKey()
      expandedKeys.value = new Set(orgTree.value.map(n => `org-${n.id}`))
    }
  } catch (error) {
  }
}

async function fetchList() {
  loading.value = true
  try {
    const res: any = await userApi.getUserList({
      username: queryParams.username || undefined,
      realName: queryParams.realName || undefined,
      status: queryParams.status || undefined,
      orgId: queryParams.orgId,
      regionId: queryParams.regionId,
      departmentId: queryParams.departmentId,
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
    editJobNumber.value = userDetail.jobNumber || null
    // 加载用户角色
    const roleIds: any = await userApi.getUserRoles(row.id)
    form.roleId = roleIds?.[0] || null

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

async function handleStatusChange(row: UserInfo, value: boolean) {
  const newStatus = value ? 'active' : 'disabled'
  const statusText = value ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(`确定要${statusText}成员“${row.realName || row.username}”吗？`, '状态切换', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await userApi.updateUser(row.id, { status: newStatus })
    row.status = newStatus
    ElMessage.success(`${statusText}成功`)
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(`${statusText}失败`)
    }
  }
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate()
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
        
      })
      ElMessage.success('更新成功')
      // 分配角色
      if (form.roleId) {
        await userApi.assignRoles(editId.value, [form.roleId])
      }
    } else {
      const createResult: any = await userApi.createUser({
        username: form.username,
        realName: form.realName,
        email: form.email || null,
        phone: form.phone || null,
        orgId: form.orgId,
        regionId: form.regionId,
        departmentId: form.departmentId,

      })
      // 获取新创建用户的ID（响应拦截器已解包 data 字段，createResult 直接就是 userId）
      const newUserId = createResult
      // 新建用户后立即分配角色
      if (form.roleId && newUserId) {
        await userApi.assignRoles(newUserId, [form.roleId])
      }
      ElMessage.success('创建成功，系统已按默认规则生成初始密码并尝试发送邮件')
    }
    dialogVisible.value = false
    await refreshUserManagement(shouldResetPage)
  } finally {
    submitting.value = false
  }
}

function handleUserCommand(command: string, row: UserInfo) {
  if (command === 'reset') {
    handleResetPassword(row)
    return
  }
  if (command === 'toggle') {
    handleStatusChange(row, row.status !== 'active')
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
  form.roleId = null
  form.status = 'active'
  editJobNumber.value = null
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

function maskPhone(phone?: string | null) {
  if (!phone) return '-'
  return phone.replace(/^(\+?\d{0,4})?(\d{3})\d{4}(\d{4})$/, (_match, prefix = '', start, end) => `${prefix}${start}****${end}`)
}


function showTodo(action: string) {
  ElMessage.info(`${action}能力将在后续接口完善后接入`)
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
    callback(new Error('用户名仅支持字母、数字、下划线'))
  } else {
    callback()
  }
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
  await loadOrgData()
  const node = activeOrgNode.value
  if (node) {
    await selectOrg(node)
  } else {
    await fetchList()
  }
})

function resolveInitialOrgKey() {
  const preferredNode = flatOrgNodes.value.find(node => node.parentKey !== null && node.count > 0)
    || flatOrgNodes.value.find(node => node.count > 0)
    || flatOrgNodes.value[0]
  return preferredNode?.key || ''
}
</script>

<style lang="scss" scoped>
.member-console {
  min-height: calc(100vh - 82px);
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: #fff;
}

.member-topbar {
  height: 96px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-md);
  padding: 0 var(--spacing-lg);
  border-bottom: 1px solid var(--color-border);
  background: #fff;

  h2 {
    margin: 8px 0 0;
    color: var(--color-text-primary);
    font-size: var(--font-size-lg);
  }
}

.page-crumb {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.mode-switch {
  flex-shrink: 0;
  padding: 4px;
  border-radius: 6px;
  background: #fff;
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
  min-height: calc(100vh - 220px);
  position: relative;
}

.member-layout {
  grid-template-columns: var(--member-sidebar-width, 284px) var(--member-sidebar-resizer-width, 4px) minmax(0, 1fr);
}

.roster-layout {
  grid-template-columns: var(--roster-sidebar-width, 244px) minmax(0, 1fr);
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
  background: #fff;
  cursor: pointer;
  z-index: 2;
  box-shadow: 2px 0 6px rgba(0, 0, 0, 0.06);

  &:hover {
    background: #f5f7fa;
  }
}

.member-sidebar,
.roster-nav {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  padding: var(--spacing-md);
  border-right: 1px solid var(--color-border);
  background: #fff;
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

.org-item:hover,
.nav-item:hover,
.org-item.is-active,
.nav-item.is-active {
  background: #e8edf3;
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
  background: #fff;
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

.member-name {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-sm);
  color: var(--color-text-primary);
  font-weight: 600;
}

.member-sub {
  margin-top: 2px;
  color: var(--color-text-placeholder);
  font-size: var(--font-size-xs);
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

.stat-card {
  min-height: 72px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6px;
  padding: var(--spacing-md);
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: #fff;
}

.stat-card.is-primary {
  background: #f5f7fa;

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
  color: #000;
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

.pagination-row {
  display: flex;
  justify-content: flex-end;
  padding: var(--spacing-md) var(--spacing-lg);
  border-top: 1px solid var(--color-border);
  background: #fff;
}

.department-management {
  min-height: calc(100vh - 220px);
  padding: var(--spacing-lg);
  background: #fff;
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
  background: #f2f3f5;
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
  .member-topbar {
    height: auto;
    align-items: flex-start;
    flex-direction: column;
    padding: var(--spacing-md);
  }

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
</style>
