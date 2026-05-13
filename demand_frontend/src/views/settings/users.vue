<template>
  <PageContainer :breadcrumb="false">
    <div class="member-console">
      <div class="member-topbar">
        <div>
          <div class="page-crumb">通讯录 / 成员管理</div>
          <h2>成员管理</h2>
        </div>
        <el-radio-group v-model="managementMode" class="mode-switch">
          <el-radio-button value="basic">基础管理模式</el-radio-button>
          <el-radio-button value="hr">人事管理模式</el-radio-button>
        </el-radio-group>
      </div>

      <div
        v-if="managementMode === 'basic' && memberView === 'members'"
        class="member-layout"
        :style="{ gridTemplateColumns: sidebarCollapsed ? '0px 0px minmax(0, 1fr)' : `${sidebarWidth}px 4px minmax(0, 1fr)` }"
        v-loading="loading"
      >
        <aside class="member-sidebar" :class="{ 'is-collapsed': sidebarCollapsed }">
          <el-input v-model="orgKeyword" placeholder="搜索成员、部门、角色" clearable>
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>

          <div class="sidebar-actions">
            <el-button @click="openCreateDepartment">
              <el-icon><Plus /></el-icon>
              添加子部门
            </el-button>
            <el-button @click="openDepartmentManagement">
              <el-icon><Operation /></el-icon>
              部门管理
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

        <div class="sidebar-resizer" @mousedown="startResize" @dblclick="toggleSidebar" />
        <button
          v-if="sidebarCollapsed"
          class="sidebar-expand-btn"
          type="button"
          title="展开侧边栏"
          @click="toggleSidebar"
        >
          <el-icon><ArrowRight /></el-icon>
        </button>

        <main class="member-main">
          <div class="org-header">
            <div>
              <div class="org-title">
                {{ activeOrgName }}
                <el-tag size="small" type="warning" effect="plain">全员群</el-tag>
              </div>
              <p>你是主管理员，拥有全部权限</p>
            </div>
            <el-button link type="primary" @click="showTodo('编辑部门')">
              <el-icon><Setting /></el-icon>
              编辑部门
            </el-button>
          </div>

          <div class="action-row">
            <AppButton type="primary" permission="button:user:create" @click="handleCreate">
              <el-icon><Plus /></el-icon>
              添加成员
            </AppButton>
            <el-dropdown @command="showTodo">
              <el-button>
                邀请成员
                <el-icon class="el-icon--right"><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="通过链接邀请">通过链接邀请</el-dropdown-item>
                  <el-dropdown-item command="批量邀请">批量邀请</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-button @click="showTodo('添加/申请记录')">添加/申请记录</el-button>
            <el-dropdown @command="showTodo">
              <el-button>
                批量管理
                <el-icon class="el-icon--right"><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="批量启用">批量启用</el-dropdown-item>
                  <el-dropdown-item command="批量停用">批量停用</el-dropdown-item>
                  <el-dropdown-item command="批量删除">批量删除</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-button @click="showTodo('调整排序')">调整排序</el-button>
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
            <el-table-column label="账号类型" width="140">
              <template #default>
                <el-tag size="small" type="success">个人账号</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="账号状态" width="140">
              <template #default="{ row }">
                <span class="status-dot" :class="{ 'is-disabled': row.status !== 'active' }" />
                {{ row.status === 'active' ? '正常' : '停用' }}
              </template>
            </el-table-column>
            <el-table-column label="职位" min-width="140">
              <template #default="{ row }">{{ positionName(row.positionId) }}</template>
            </el-table-column>
            <el-table-column prop="id" label="工号" width="120" />
            <el-table-column prop="email" label="邮箱" min-width="190" show-overflow-tooltip />
            <el-table-column label="员工UserID" min-width="150">
              <template #default="{ row }">{{ row.username }}</template>
            </el-table-column>
            <el-table-column label="操作" width="112" fixed="right">
              <template #default="{ row }">
                <div class="table-action-icons">
                  <el-tooltip content="编辑成员" placement="top">
                    <el-button link type="primary" size="small" @click="handleEdit(row)">
                      <el-icon><Edit /></el-icon>
                    </el-button>
                  </el-tooltip>
                <el-dropdown @command="(command: string) => handleUserCommand(command, row)">
                    <el-tooltip content="更多操作" placement="top">
                      <el-button link type="primary" size="small">
                        <el-icon><MoreFilled /></el-icon>
                      </el-button>
                    </el-tooltip>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="reset">
                        <el-icon><Key /></el-icon>
                        重置密码
                      </el-dropdown-item>
                      <el-dropdown-item command="toggle">
                        <el-icon><SwitchButton /></el-icon>
                        {{ row.status === 'active' ? '停用' : '启用' }}
                      </el-dropdown-item>
                      <el-dropdown-item command="delete" divided>
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
        </main>
      </div>

      <div v-else-if="managementMode === 'basic'" class="department-management" v-loading="loading">
        <div class="department-head">
          <div>
            <div class="department-title">一体化运营运维团队</div>
            <p>你是主管理员，拥有全部权限</p>
          </div>
          <el-button link type="primary" @click="memberView = 'members'">返回成员列表</el-button>
        </div>

        <div class="department-actions">
          <el-button type="primary" @click="openCreateDepartment">
            <el-icon><Plus /></el-icon>
            添加部门
          </el-button>
          <el-button @click="showTodo('批量创建部门')">批量创建部门</el-button>
          <el-button :disabled="selectedDepartments.length !== 1" @click="openEditDepartment">
            编辑选中部门
          </el-button>
          <el-button type="danger" plain :disabled="selectedDepartments.length === 0" @click="handleDeleteDepartments">
            删除选中部门
          </el-button>
          <el-button link type="primary" @click="showTodo('使用手册')">使用手册</el-button>
        </div>

        <el-table
          :data="departmentRows"
          border
          row-key="id"
          class="department-table"
          @selection-change="selectedDepartments = $event"
        >
          <el-table-column type="selection" width="52" />
          <el-table-column label="部门名称" min-width="260">
            <template #default="{ row }">
              <div class="department-name-cell" :style="{ paddingLeft: `${row.level * 20}px` }">
                <el-icon><ArrowDown /></el-icon>
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
                <el-tooltip content="部门操作" placement="top">
                  <el-button link type="primary" size="small">
                    <el-icon><MoreFilled /></el-icon>
                  </el-button>
                </el-tooltip>
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
      </div>

      <div v-else class="roster-layout" v-loading="loading">
        <aside class="roster-nav">
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
            <el-table-column label="职位" min-width="130">
              <template #default="{ row }">{{ positionName(row.positionId) }}</template>
            </el-table-column>
            <el-table-column label="入职时间" width="160">
              <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="员工类型" width="130">
              <template #default="{ row }">{{ row.status === 'active' ? '全职' : '待确认' }}</template>
            </el-table-column>
            <el-table-column label="手机号" width="170">
              <template #default="{ row }">{{ maskPhone(row.phone) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="86" fixed="right">
              <template #default="{ row }">
                <el-tooltip content="编辑成员" placement="top">
                  <el-button link type="primary" size="small" @click="handleEdit(row)">
                    <el-icon><Edit /></el-icon>
                  </el-button>
                </el-tooltip>
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
      @close="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
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

        <el-form-item label="区域" prop="regionId">
          <el-tree-select
            v-model="form.regionId"
            :data="regionTree"
            :props="{ label: 'name', value: 'id' }"
            placeholder="请选择区域"
            clearable
            check-strictly
            :disabled="!isEdit"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="部门" prop="departmentId">
          <el-tree-select
            v-model="form.departmentId"
            :data="departmentTree"
            :props="{ label: 'name', value: 'id' }"
            placeholder="请选择部门"
            clearable
            check-strictly
            :disabled="!isEdit"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="岗位" prop="positionId">
          <el-select v-model="form.positionId" placeholder="请选择岗位" clearable style="width: 100%">
            <el-option
              v-for="position in positionList"
              :key="position.id"
              :label="position.name"
              :value="position.id"
            />
          </el-select>
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
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <el-drawer
      v-model="departmentDrawerVisible"
      :title="departmentEditingId ? '编辑部门' : '新增组织'"
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
            <el-radio value="region">区域</el-radio>
            <el-radio value="department">部门</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="上级组织" prop="parentId" required>
          <el-tree-select
            v-model="departmentForm.parentId"
            :data="orgTree"
            :props="{ label: 'name', value: 'id', children: 'children' }"
            placeholder="请选择上级组织"
            clearable
            check-strictly
            style="width: 100%"
          >
            <template #default="{ data }">
              <span class="department-option">
                <el-icon><component :is="data.orgType === 'department' ? FolderOpened : OfficeBuilding" /></el-icon>
                {{ data.name }}
                <el-tag size="small" :type="data.orgType === 'department' ? 'info' : 'warning'" style="margin-left: 4px;">{{ data.orgType === 'department' ? '部门' : '区域' }}</el-tag>
              </span>
            </template>
          </el-tree-select>
        </el-form-item>

        <el-form-item label="创建部门群">
          <el-checkbox v-model="departmentForm.createGroup">
            创建一个关联此部门的企业群，如果有新人加入部门会自动加入该群
          </el-checkbox>
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="drawer-footer">
          <el-button @click="departmentDrawerVisible = false">取消</el-button>
          <el-button type="primary" :loading="departmentSubmitting" @click="handleSubmitDepartment">确定</el-button>
        </div>
      </template>
    </el-drawer>
  </PageContainer>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, type Component } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowDown,
  ArrowRight,
  Clock,
  Connection,
  Delete,
  Edit,
  Filter,
  FolderOpened,
  Key,
  MoreFilled,
  OfficeBuilding,
  Operation,
  Plus,
  Search,
  Setting,
  SwitchButton,
  User,
  UserFilled,
} from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import * as userApi from '@/api/modules/user'
import type { OrgNode, Position, User as UserInfo } from '@/types/user'
import PageContainer from '@/components/common/PageContainer.vue'
import AppButton from '@/components/common/AppButton.vue'

interface FlatOrgNode {
  key: string
  id: number | null
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
  regionId: number | null
  departmentId: number | null
  positionId: number | null
  status: string
}

const loading = ref(false)
const submitting = ref(false)
const userList = ref<UserInfo[]>([])
const selectedUsers = ref<UserInfo[]>([])
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const managementMode = ref<'basic' | 'hr'>('basic')
const memberView = ref<'members' | 'departments'>('members')
const orgKeyword = ref('')
const activeOrgKey = ref('all')
const selectedDepartments = ref<DepartmentRow[]>([])
const expandedKeys = ref<Set<string>>(new Set(['all']))
const sidebarWidth = ref(284)
const sidebarCollapsed = ref(false)
const SIDEBAR_DEFAULT = 284

const dialogVisible = ref(false)
const departmentDrawerVisible = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const departmentFormRef = ref<FormInstance>()
const departmentSubmitting = ref(false)

const orgTree = ref<OrgNode[]>([])
const regionTree = ref<OrgNode[]>([])
const departmentTree = ref<OrgNode[]>([])
const positionList = ref<Position[]>([])

const queryParams = reactive({
  username: '',
  realName: '',
  status: '',
  departmentId: undefined as number | undefined,
})

const form = reactive<UserForm>({
  username: '',
  realName: '',
  email: '',
  phone: '',
  regionId: null,
  departmentId: null,
  positionId: null,
  status: 'active',
})

const departmentForm = reactive({
  name: '',
  parentId: null as number | null,
  orgType: 'department' as 'region' | 'department',
  createGroup: false,
})
const departmentEditingId = ref<number | null>(null)

const hrNavItems = ['入职管理', '新人成长', '转正管理', '异动管理', '离职管理', '合同管理', '退休管理', '员工关怀']

const activeUsers = computed(() => userList.value.filter(user => user.status === 'active'))
const activeOrgName = computed(() => visibleOrgNodes.value.find(node => node.key === activeOrgKey.value)?.name || '全体成员')
const activeOrgNode = computed(() => flatOrgNodes.value.find(node => node.key === activeOrgKey.value))
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

const flatOrgNodes = computed<FlatOrgNode[]>(() => {
  const nodes: FlatOrgNode[] = [{
    key: 'all',
    id: null,
    name: '一体化运营运维团队',
    level: 0,
    count: total.value,
    icon: OfficeBuilding,
    hasChildren: orgTree.value.length > 0,
    parentKey: null,
  }]

  const walk = (items: OrgNode[], level: number, parentKey: string) => {
    items.forEach(item => {
      const key = `org-${item.id}`
      nodes.push({
        key,
        id: item.id,
        name: item.name,
        level,
        count: countUsersByOrg(item.id),
        icon: item.orgType === 'department' ? FolderOpened : OfficeBuilding,
        hasChildren: (item.children?.length ?? 0) > 0,
        parentKey,
        orgType: item.orgType,
      })
      if (item.children?.length) {
        walk(item.children, level + 1, key)
      }
    })
  }
  walk(orgTree.value, 1, 'all')
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

function hasOrgType(node: OrgNode, targetType: string): boolean {
  return node.orgType === targetType || !!node.children?.some(child => hasOrgType(child, targetType))
}

async function loadOrgData() {
  try {
    const [orgRes, positionsRes] = await Promise.all([
      userApi.getOrgTree(),
      userApi.getPositionList(),
    ])
    orgTree.value = normalizeArray<OrgNode>(orgRes)
    regionTree.value = filterOrgTree(orgTree.value, 'region')
    departmentTree.value = filterOrgTree(orgTree.value, 'department')
    positionList.value = normalizeArray<Position>(positionsRes)
  } catch (error) {
    console.error('加载组织架构数据失败:', error)
  }
}

async function fetchList() {
  loading.value = true
  try {
    const res: any = await userApi.getUserList({
      username: queryParams.username || undefined,
      realName: queryParams.realName || undefined,
      status: queryParams.status || undefined,
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

function handleSearch() {
  queryParams.departmentId = undefined
  activeOrgKey.value = 'all'
  pageNum.value = 1
  fetchList()
}

function handleCreate() {
  isEdit.value = false
  editId.value = null
  resetForm()

  // Auto-fill org info based on current selected node
  const node = activeOrgNode.value
  if (node && node.id) {
    if (node.orgType === 'region' || node.orgType === 'company') {
      form.regionId = node.id
    } else if (node.orgType === 'department' || node.orgType === 'group') {
      form.departmentId = node.id
      // Walk up to find parent region
      const parent = flatOrgNodes.value.find(n => n.key === node.parentKey)
      if (parent?.id && (parent.orgType === 'region' || parent.orgType === 'company')) {
        form.regionId = parent.id
      }
    }
  }

  dialogVisible.value = true
}

function openCreateDepartment() {
  resetDepartmentForm()
  const node = activeOrgNode.value
  if (node && node.id) {
    departmentForm.parentId = node.id
    // Default orgType based on current node type
    if (node.orgType === 'region' || node.orgType === 'company') {
      departmentForm.orgType = 'region'
    } else {
      departmentForm.orgType = 'department'
    }
  } else {
    departmentForm.parentId = orgTree.value[0]?.id ?? null
  }
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

async function handleEdit(row: UserInfo) {
  isEdit.value = true
  editId.value = row.id
  try {
    const userDetail: any = await userApi.getUserById(row.id)
    form.username = userDetail.username
    form.realName = userDetail.realName
    form.email = userDetail.email || ''
    form.phone = userDetail.phone || ''
    form.status = userDetail.status || 'active'
    form.regionId = userDetail.regionId || null
    form.departmentId = userDetail.departmentId || null
    form.positionId = userDetail.positionId || null
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
    fetchList()
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
    if (isEdit.value && editId.value) {
      await userApi.updateUser(editId.value, {
        realName: form.realName,
        email: form.email || null,
        phone: form.phone || null,
        status: form.status,
        regionId: form.regionId,
        departmentId: form.departmentId,
        positionId: form.positionId,
      })
      ElMessage.success('更新成功')
    } else {
      await userApi.createUser({
        username: form.username,
        realName: form.realName,
        email: form.email || null,
        phone: form.phone || null,
        regionId: form.regionId,
        departmentId: form.departmentId,
        positionId: form.positionId,
      })
      ElMessage.success('创建成功，系统已按默认规则生成初始密码并尝试发送邮件')
    }
    dialogVisible.value = false
    fetchList()
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
  form.regionId = null
  form.departmentId = null
  form.positionId = null
  form.status = 'active'
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
      ElMessage.success(`${departmentForm.orgType === 'region' ? '子区域' : '子部门'}添加成功`)
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

function selectOrg(node: FlatOrgNode) {
  activeOrgKey.value = node.key
  queryParams.username = ''
  queryParams.realName = ''
  queryParams.status = ''
  queryParams.departmentId = node.id ?? undefined
  pageNum.value = 1
  fetchList()
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

function startResize(e: MouseEvent) {
  e.preventDefault()
  if (sidebarCollapsed.value) return
  const startX = e.clientX
  const startWidth = sidebarWidth.value

  const onMouseMove = (ev: MouseEvent) => {
    const delta = ev.clientX - startX
    sidebarWidth.value = Math.min(Math.max(startWidth + delta, 200), 500)
  }
  const onMouseUp = () => {
    document.removeEventListener('mousemove', onMouseMove)
    document.removeEventListener('mouseup', onMouseUp)
  }
  document.addEventListener('mousemove', onMouseMove)
  document.addEventListener('mouseup', onMouseUp)
}

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
  if (sidebarCollapsed.value) {
    sidebarWidth.value = 0
  } else {
    sidebarWidth.value = SIDEBAR_DEFAULT
  }
}

function countUsersByOrg(orgId: number) {
  return userList.value.filter(user => user.departmentId === orgId || user.regionId === orgId).length
}

function orgName(id?: number | null) {
  if (!id) return '-'
  const found = flatOrgNodes.value.find(node => node.id === id)
  return found?.name || '-'
}

function positionName(id?: number | null) {
  if (!id) return '-'
  return positionList.value.find(item => item.id === id)?.name || '-'
}

function avatarText(row: UserInfo) {
  return (row.realName || row.username || '?').slice(0, 1)
}

function maskPhone(phone?: string | null) {
  if (!phone) return '-'
  return phone.replace(/^(\+?\d{0,4})?(\d{3})\d{4}(\d{4})$/, (_match, prefix = '', start, end) => `${prefix}${start}****${end}`)
}

function formatDate(value?: string | null) {
  if (!value) return '-'
  return value.slice(0, 10)
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
    count: countUsersByOrg(node.id),
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

onMounted(() => {
  loadOrgData()
  fetchList()
})
</script>

<style lang="scss" scoped>
.member-console {
  min-height: calc(100vh - 82px);
  overflow: hidden;
  border: 1px solid $border-color;
  border-radius: $card-radius;
  background: #fff;
}

.member-topbar {
  height: 96px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $spacing-md;
  padding: 0 $spacing-lg;
  border-bottom: 1px solid $border-color;
  background: #fff;

  h2 {
    margin: 8px 0 0;
    color: $text-color;
    font-size: $font-size-lg;
  }
}

.page-crumb {
  color: $text-color-secondary;
  font-size: $font-size-sm;
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
  color: $text-color;
  font-weight: 500;
}

.mode-switch :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background: #ecf5ff;
  color: #409eff;
  font-weight: 600;
  box-shadow: none;
}

.member-layout,
.roster-layout {
  display: grid;
  grid-template-columns: 284px minmax(0, 1fr);
  min-height: calc(100vh - 220px);
  position: relative;
}

.member-layout {
  grid-template-columns: 284px 4px minmax(0, 1fr);
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
    background: $primary-color;
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
  border: 1px solid $border-color;
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
  padding: $spacing-md;
  border-right: 1px solid $border-color;
  background: #fff;
  overflow: auto;
}

.member-sidebar.is-collapsed {
  padding: 0;
  border-right: 0;
  overflow: hidden;
}

.sidebar-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: $spacing-sm;
  margin: $spacing-md 0;
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
  gap: $spacing-sm;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: $text-color;
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
  gap: $spacing-sm;
  border: 0;
  background: transparent;
  color: $text-color;
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
  color: $text-color-secondary;
}

.member-main,
.roster-main {
  min-width: 0;
  padding: $spacing-lg;
  background: #fff;
}

.org-header,
.roster-header,
.action-row,
.roster-filter {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $spacing-md;
  flex-wrap: wrap;
}

.org-title {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  font-size: 22px;
  font-weight: 700;
  color: $text-color;
}

.org-header p {
  margin: 8px 0 0;
  color: $text-color-secondary;
}

.action-row {
  justify-content: flex-start;
  margin: $spacing-lg 0 $spacing-md;
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
  gap: $spacing-sm;
  color: $text-color;
  font-weight: 600;
}

.member-sub {
  margin-top: 2px;
  color: $text-color-placeholder;
  font-size: $font-size-xs;
}

.status-dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  margin-right: 6px;
  border-radius: 50%;
  background: $success-color;
}

.status-dot.is-disabled {
  background: $text-color-placeholder;
}

.nav-title {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  margin: $spacing-md 0 $spacing-sm;
  color: $text-color;
  font-weight: 700;
}

.roster-header h3 {
  margin: 0;
  font-size: 22px;
  color: $text-color;
}

.roster-links {
  display: flex;
  gap: $spacing-sm;
  flex-wrap: wrap;
}

.stats-board {
  display: grid;
  grid-template-columns: minmax(160px, 1.1fr) repeat(8, minmax(96px, 1fr));
  gap: $spacing-sm;
  margin: $spacing-lg 0 $spacing-md;
}

.stat-card {
  min-height: 72px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6px;
  padding: $spacing-md;
  border: 1px solid $border-color;
  border-radius: 6px;
  background: #fff;
}

.stat-card.is-primary {
  background: #f5f7fa;

  span,
  strong {
    color: $primary-color;
  }
}

.stat-card span {
  color: $text-color;
  font-size: $font-size-sm;
}

.stat-card strong {
  color: #000;
  font-size: 24px;
  line-height: 1;
}

.roster-filter {
  justify-content: flex-start;
  margin-bottom: $spacing-md;
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
  padding: $spacing-md $spacing-lg;
  border-top: 1px solid $border-color;
  background: #fff;
}

.department-management {
  min-height: calc(100vh - 220px);
  padding: $spacing-lg;
  background: #fff;
}

.department-head,
.department-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $spacing-md;
  flex-wrap: wrap;
}

.department-title {
  color: $text-color;
  font-size: 22px;
  font-weight: 700;
}

.department-head p {
  margin: 8px 0 0;
  color: $text-color-secondary;
}

.department-actions {
  justify-content: flex-start;
  margin: $spacing-lg 0 $spacing-sm;
}

.department-table {
  width: 100%;
}

.department-name-cell {
  display: inline-flex;
  align-items: center;
  gap: $spacing-sm;
  color: $text-color;
}

.drawer-section-title {
  margin: -20px -20px 18px;
  padding: 10px 20px;
  background: #f2f3f5;
  color: $text-color-secondary;
  font-size: $font-size-sm;
}

.department-form :deep(.el-form-item__label) {
  color: $text-color;
  font-weight: 600;
}

.effective-tip {
  margin: -6px 0 $spacing-md;
  color: $text-color-secondary;
  font-size: $font-size-sm;
}

.department-option {
  display: inline-flex;
  align-items: center;
  gap: $spacing-xs;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: $spacing-sm;
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
    padding: $spacing-md;
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
    border-bottom: 1px solid $border-color;
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
