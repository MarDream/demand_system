<template>
  <PageContainer :breadcrumb="false">
    <div class="role-console">
      <div class="role-page" :style="roleSidebar.styleVars" v-loading="loading">
      <aside class="role-sidebar" :class="{ 'is-collapsed': roleSidebar.collapsed }">
        <div class="sidebar-head">
          <span class="sidebar-head__title">角色导航</span>
          <el-button
            link
            class="sidebar-collapse-trigger"
            title="收起侧边栏"
            @click="roleSidebar.toggle"
          >
            <el-icon><ArrowLeft /></el-icon>
          </el-button>
        </div>
        <el-input v-model="keyword" placeholder="搜索角色" clearable>
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>

        <div class="sidebar-actions">
          <AppButton permission="button:role:create" @click="openCreateRoleGroup">新增角色组</AppButton>
          <AppButton permission="button:role:create" @click="openCreate">新增角色</AppButton>
          <input ref="importInputRef" type="file" accept=".xlsx,.xls" style="display: none" @change="handleImportFileChange" />
          <el-dropdown @command="handleBatchCommand">
            <el-button :loading="batchImporting">
              批量管理
              <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="import">批量导入</el-dropdown-item>
                <el-dropdown-item command="export">批量导出</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>

        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="拖拽下方节点即可排序"
        />

        <el-tree
          ref="roleTreeRef"
          :data="roleTreeData"
          :props="{ label: 'name', children: 'children' }"
          node-key="id"
          default-expand-all
          :expand-on-click-node="false"
          highlight-current
          draggable
          :allow-drop="handleAllowDrop"
          :allow-drag="handleAllowDrag"
          @node-click="handleTreeNodeClick"
          @node-drag-end="handleDragEnd"
        >
          <template #default="{ node, data }">
            <span class="tree-node-content">
              <span class="tree-node-label">
                <el-icon><component :is="data.icon" /></el-icon>
                <span>{{ node.label }}</span>
                <span v-if="data.type === 'group' && data.children?.length" class="tree-node-count">
                  {{ data.children.length }}
                </span>
              </span>
              <span v-if="data.type === 'group' && data.id !== DEFAULT_ROLE_GROUP_KEY" class="tree-node-actions">
                <el-button link type="primary" size="small" @click.stop="openEditRoleGroup(data.data)" v-permission="'button:role:update'">编辑</el-button>
                <el-button link type="danger" size="small" @click.stop="handleDeleteRoleGroup(data.data)" v-permission="'button:role:delete'">删除</el-button>
              </span>
              <span v-else-if="data.type === 'role'" class="tree-node-actions">
                <el-button link type="primary" size="small" @click.stop="openEdit(data.data)" v-permission="'button:role:update'">编辑</el-button>
                <el-button link type="danger" size="small" @click.stop="handleDelete(data.data)" :disabled="isSystemRole(data.data)" v-permission="'button:role:delete'">删除</el-button>
              </span>
            </span>
          </template>
        </el-tree>
      </aside>

      <div class="sidebar-resizer" @mousedown="roleSidebar.startResize" @dblclick="roleSidebar.toggle" />
      <button
        v-if="roleSidebar.collapsed"
        class="sidebar-expand-btn"
        type="button"
        title="展开侧边栏"
        @click="roleSidebar.toggle"
      >
        <el-icon><ArrowRight /></el-icon>
      </button>

      <main class="role-main">
        <section v-if="selectedRole" class="role-detail">
          <div class="detail-header">
            <div>
              <div class="detail-title">
                <span>{{ selectedRole.name }}</span>
                <el-tag v-if="isSystemRole(selectedRole)" size="small" type="info">系统角色</el-tag>
                <el-tag v-else size="small" type="success">自定义角色</el-tag>
                <el-tag size="small" effect="plain">{{ roleGroupNameById(selectedRole.roleGroupId) }}</el-tag>
              </div>
              <div class="detail-code">{{ selectedRole.code }}</div>
            </div>
            <el-button link type="primary" @click="selectedRole = null">查看角色说明</el-button>
          </div>

          <p class="detail-description">{{ selectedRole.description || '暂无角色说明' }}</p>

          <div class="role-actions-bar">
            <AppButton type="primary" permission="button:role:create" @click="openCreate">
              <el-icon><Plus /></el-icon>
              新增角色
            </AppButton>
            <AppButton
              permission="button:role:update"
              :disabled="isSystemRole(selectedRole)"
              @click="openEdit(selectedRole)"
            >
              编辑角色
            </AppButton>
            <AppButton
              type="danger"
              plain
              permission="button:role:delete"
              :disabled="isSystemRole(selectedRole)"
              @click="handleDelete(selectedRole)"
            >
              删除角色
            </AppButton>
            <AppButton
              type="primary"
              permission="button:role:grant"
              :loading="permissionSaving"
              :disabled="!canGrantSelectedRole"
              @click="handleSavePermissions"
            >
              保存权限
            </AppButton>
          </div>

          <div class="permission-panel">
            <div class="panel-head">
              <div>
                <h3>权限范围</h3>
                <p>{{ canGrantSelectedRole ? '按菜单及菜单下按钮授权，保存后即时影响菜单可见性、需求操作和工作流处理范围。' : grantDisabledReason }}</p>
              </div>
            </div>

            <el-skeleton v-if="permissionLoading" :rows="6" animated />
            <div v-else class="permission-content">
              <div class="permission-toolbar">
                <el-input v-model="permissionKeyword" placeholder="搜索菜单、按钮或权限编码" clearable>
                  <template #prefix>
                    <el-icon><Search /></el-icon>
                  </template>
                </el-input>
                <el-button :disabled="!canGrantSelectedRole" @click="selectAllVisiblePermissions">全选当前</el-button>
                <el-button :disabled="!canGrantSelectedRole" @click="clearVisiblePermissions">清空当前</el-button>
              </div>

              <el-alert
                v-if="isSuperAdminRole(selectedRole)"
                type="warning"
                :closable="false"
                show-icon
                title="超级管理员角色拥有系统最高权限，不支持在此调整权限范围。"
              />

              <div v-if="filteredMenuPermissionTree.length > 0" class="menu-permission-list">
                <div v-for="node in filteredMenuPermissionTree" :key="node.key" class="menu-permission-node">
                  <div class="menu-permission-row" :style="{ paddingLeft: `${node.level * 18}px` }">
                    <el-button
                      v-if="node.children.length || node.buttons.length"
                      link
                      class="expand-button"
                      @click="toggleMenuExpand(node.key)"
                    >
                      <el-icon><component :is="expandedMenuKeys.includes(node.key) ? ArrowDown : ArrowRight" /></el-icon>
                    </el-button>
                    <span v-else class="expand-placeholder" />

                    <el-checkbox
                      :model-value="isMenuChecked(node)"
                      :indeterminate="isMenuIndeterminate(node)"
                      :disabled="!canGrantSelectedRole || !node.menuPermission || !isGrantablePermission(node.menuPermission.code)"
                      @change="(checked: boolean) => handleMenuCheck(node, checked)"
                    >
                      <span class="menu-name">{{ node.name }}</span>
                      <el-tag size="small" effect="plain">{{ menuTypeLabel(node.menuType) }}</el-tag>
                    </el-checkbox>

                    <span v-if="node.menuPermission" class="permission-code">{{ node.menuPermission.code }}</span>
                    <span class="menu-count">{{ selectedCount(node.allPermissions) }}/{{ node.allPermissions.length }}</span>
                  </div>

                  <!-- 一级菜单的按钮始终展开显示 -->
                  <div v-if="node.buttons.length" class="button-permission-grid" :style="{ marginLeft: `${34 + node.level * 18}px` }">
                    <el-checkbox
                      v-for="button in node.buttons"
                      :key="button.code"
                      :model-value="selectedPermissions.includes(button.code)"
                      :disabled="!canGrantSelectedRole || !isGrantablePermission(button.code)"
                      border
                      @change="(checked: boolean) => setPermissionChecked(button.code, checked)"
                    >
                      <span class="permission-name">{{ button.name }}</span>
                    </el-checkbox>
                  </div>

                  <!-- 子菜单（二级、三级）需要展开后才显示 -->
                  <div v-if="expandedMenuKeys.includes(node.key) && node.children.length" class="menu-permission-children">
                      <div
                        v-for="child in node.children"
                        :key="child.key"
                        class="menu-permission-node is-nested"
                      >
                        <div class="menu-permission-row" :style="{ paddingLeft: `${child.level * 18}px` }">
                          <el-button
                            v-if="child.children.length || child.buttons.length"
                            link
                            class="expand-button"
                            @click="toggleMenuExpand(child.key)"
                          >
                            <el-icon><component :is="expandedMenuKeys.includes(child.key) ? ArrowDown : ArrowRight" /></el-icon>
                          </el-button>
                          <span v-else class="expand-placeholder" />

                          <el-checkbox
                            :model-value="isMenuChecked(child)"
                            :indeterminate="isMenuIndeterminate(child)"
                            :disabled="!canGrantSelectedRole || !child.menuPermission || !isGrantablePermission(child.menuPermission.code)"
                            @change="(checked: boolean) => handleMenuCheck(child, checked)"
                          >
                            <span class="menu-name">{{ child.name }}</span>
                            <el-tag size="small" effect="plain">{{ menuTypeLabel(child.menuType) }}</el-tag>
                          </el-checkbox>

                          <span v-if="child.menuPermission" class="permission-code">{{ child.menuPermission.code }}</span>
                          <span class="menu-count">{{ selectedCount(child.allPermissions) }}/{{ child.allPermissions.length }}</span>
                        </div>
                        <div v-if="expandedMenuKeys.includes(child.key) && child.buttons.length" class="button-permission-grid" :style="{ marginLeft: `${34 + child.level * 18}px` }">
                          <el-checkbox
                            v-for="button in child.buttons"
                            :key="button.code"
                            :model-value="selectedPermissions.includes(button.code)"
                            :disabled="!canGrantSelectedRole || !isGrantablePermission(button.code)"
                            border
                            @change="(checked: boolean) => setPermissionChecked(button.code, checked)"
                          >
                            <span class="permission-name">{{ button.name }}</span>
                          </el-checkbox>
                        </div>
                      </div>
                    </div>
                </div>
              </div>

              <div v-if="filteredOrphanPermissions.length" class="orphan-permissions">
                <div class="orphan-title">未归类权限</div>
                <div class="button-permission-grid">
                  <el-checkbox
                    v-for="permission in filteredOrphanPermissions"
                    :key="permission.code"
                    :model-value="selectedPermissions.includes(permission.code)"
                    :disabled="!canGrantSelectedRole || !isGrantablePermission(permission.code)"
                    border
                    @change="(checked: boolean) => setPermissionChecked(permission.code, checked)"
                  >
                    <span class="permission-name">{{ permission.name }}</span>
                  </el-checkbox>
                </div>
              </div>

              <el-empty
                v-if="filteredMenuPermissionTree.length === 0 && filteredOrphanPermissions.length === 0"
                description="暂无匹配权限"
                :image-size="96"
              />
            </div>
          </div>
        </section>

        <section v-else class="empty-guide">
          <div class="guide-copy">
            <h2>什么是角色？</h2>
            <p>角色指团队成员的专业分工类别，如产品、研发、测试、项目负责人等。成员拥有角色后，会继承该角色对应的菜单、需求操作和工作流权限。</p>
            <h3>怎么使用角色？</h3>
            <ul>
              <li>审批：在工作流配置中选择指定角色作为审批人，避免因成员离职或变动造成流程失效。</li>
              <li>项目：把角色加入项目成员范围，让需求创建、评审、流转能按职责协作。</li>
              <li>权限：给角色授予菜单和按钮权限，控制成员可见功能与高风险操作。</li>
            </ul>
            <div class="guide-actions">
              <el-button type="primary" @click="openCreate">新增角色</el-button>
              <el-button @click="$router.push('/system/workflow-config')">去审批设置流程</el-button>
              <el-button @click="showTodo('使用手册')">使用手册</el-button>
            </div>
          </div>

          <div class="flow-preview" aria-hidden="true">
            <div class="flow-track">
              <span>提交审批</span>
              <span>流程不中断</span>
            </div>
            <div v-for="(node, index) in previewNodes" :key="node.label" class="flow-node">
              <div class="flow-avatar" :style="{ background: node.color }">
                <el-icon><component :is="node.icon" /></el-icon>
              </div>
              <div class="flow-label">角色：{{ node.label }}</div>
              <div v-if="node.note" class="flow-note">{{ node.note }}</div>
              <div v-if="index < previewNodes.length - 1" class="flow-line" />
            </div>
          </div>
        </section>
      </main>
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="editingRole ? '编辑角色' : '新增角色'" width="520px" class="settings-form-dialog" @close="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="角色名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入角色名称" @input="handleRoleNameInput" />
        </el-form-item>
        <el-form-item label="所属分组">
          <el-select v-model="form.roleGroupId" clearable placeholder="默认分组">
            <el-option
              v-for="group in roleGroups"
              :key="group.id"
              :label="group.name"
              :value="group.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="角色编码" prop="code">
          <el-input
            v-model="form.code"
            placeholder="例如 PRODUCT_OWNER"
            :disabled="!!editingRole"
            @input="codeManuallyEdited = true"
          />
        </el-form-item>
        <el-form-item label="角色说明" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="4" placeholder="说明角色职责与使用范围" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="roleGroupDialogVisible"
      :title="editingRoleGroup ? '编辑角色组' : '新增角色组'"
      width="520px"
      class="settings-form-dialog"
      @close="resetRoleGroupForm"
    >
      <el-form ref="roleGroupFormRef" :model="roleGroupForm" :rules="roleGroupRules" label-width="110px">
        <el-form-item label="角色组名称" prop="name">
          <el-input v-model="roleGroupForm.name" placeholder="请输入角色组名称" />
        </el-form-item>
        <el-form-item label="角色组说明" prop="description">
          <el-input
            v-model="roleGroupForm.description"
            type="textarea"
            :rows="3"
            placeholder="用于区分角色分类和职责范围"
          />
        </el-form-item>
        <el-form-item v-if="!editingRoleGroup" label="关联角色">
          <el-select
            v-model="roleGroupForm.roleIds"
            multiple
            clearable
            collapse-tags
            collapse-tags-tooltip
            placeholder="选择未纳入其他角色组的角色"
            style="width: 100%"
          >
            <el-option
              v-for="role in unassignedRoles"
              :key="role.id"
              :label="role.name"
              :value="role.id"
            />
          </el-select>
          <div class="form-tip">仅显示未纳入其他角色组的角色，创建后可通过拖拽调整</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleGroupDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="roleGroupSubmitting" @click="handleSubmitRoleGroup">保存</el-button>
      </template>
    </el-dialog>
  </PageContainer>
</template>

<script setup lang="ts">
import { computed, markRaw, onMounted, onUnmounted, reactive, ref, type Component } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { ArrowDown, ArrowLeft, ArrowRight, Search, Suitcase, Tickets, UserFilled, User, Tools, Plus, FolderOpened, Rank } from '@element-plus/icons-vue'
import Sortable from 'sortablejs'
import * as XLSX from 'xlsx'
import PageContainer from '@/components/common/PageContainer.vue'
import AppButton from '@/components/common/AppButton.vue'
import { useCollapsibleSidebar } from '@/composables/useCollapsibleSidebar'
import { useUserStore } from '@/stores/modules/user'
import { exportToExcel } from '@/utils/excel'
import {
  createRoleGroup,
  createRole,
  deleteRoleGroup,
  deleteRole,
  getGrantablePermissions,
  getRoleGroups,
  getRoleList,
  getRolePermissions,
  saveRolePermissions,
  updateRoleGroup,
  updateRole,
  batchSortRoleGroups,
  batchSortRoles,
  type RoleGroupItem,
  type RolePayload,
} from '@/api/modules/role'
import { getAllMenus, type MenuItem, type RoleItem } from '@/api/modules/menu'

interface PermissionOption {
  code: string
  name: string
}

interface MenuPermissionNode {
  key: string
  id: number
  name: string
  menuType: string
  level: number
  menuPermission: PermissionOption | null
  buttons: PermissionOption[]
  children: MenuPermissionNode[]
  allPermissions: PermissionOption[]
}

interface RoleImportRow {
  code: string
  name: string
  description?: string | null
}

interface RoleGroupSection {
  key: string
  name: string
  roles: RoleItem[]
  isDefault: boolean
  icon: Component
  source: RoleGroupItem | null
}

const RAW_USER_ICON = markRaw(User)
const RAW_SUITCASE_ICON = markRaw(Suitcase)
const RAW_TICKETS_ICON = markRaw(Tickets)
const RAW_TOOLS_ICON = markRaw(Tools)
const RAW_FOLDER_OPENED_ICON = markRaw(FolderOpened)
const RAW_USER_FILLED_ICON = markRaw(UserFilled)

interface RoleTreeNode {
  id: string
  name: string
  type: 'group' | 'role'
  icon: Component
  data: RoleGroupItem | RoleItem | null
  children?: RoleTreeNode[]
}

const SUPER_ADMIN_CODES = new Set(['super_admin', 'SUPER_ADMIN'])
const DEFAULT_ROLE_GROUP_KEY = 'role-group-default'

const permissionLoading = ref(false)
const permissionSaving = ref(false)
const loading = ref(false)
const submitting = ref(false)
const batchImporting = ref(false)
const roles = ref<RoleItem[]>([])
const roleGroups = ref<RoleGroupItem[]>([])
const selectedRole = ref<RoleItem | null>(null)
const selectedPermissions = ref<string[]>([])
const grantablePermissions = ref<string[]>([])
const menuTree = ref<MenuItem[]>([])
const expandedMenuKeys = ref<string[]>([])
const expandedRoleGroupKeys = ref<string[]>([])
const roleTreeRef = ref<any>(null)
const roleSidebar = useCollapsibleSidebar({
  defaultWidth: 360,
  minWidth: 240,
  maxWidth: 520,
  resizerWidth: 4,
  widthVar: '--role-sidebar-width',
  resizerWidthVar: '--role-sidebar-resizer-width',
})
const keyword = ref('')
const permissionKeyword = ref('')
const dialogVisible = ref(false)
const editingRole = ref<RoleItem | null>(null)
const formRef = ref<FormInstance>()
const importInputRef = ref<HTMLInputElement>()
const roleGroupDialogVisible = ref(false)
const editingRoleGroup = ref<RoleGroupItem | null>(null)
const roleGroupFormRef = ref<FormInstance>()
const roleGroupSubmitting = ref(false)
const userStore = useUserStore()
const codeManuallyEdited = ref(false)
const roleGroupsRef = ref<HTMLElement | null>(null)
let sortableGroupInstance: Sortable | null = null
let sortableRoleInstances: Map<string, Sortable> = new Map()

const form = reactive<RolePayload>({
  code: '',
  name: '',
  description: '',
  roleGroupId: null,
})

const roleGroupForm = reactive({
  name: '',
  description: '',
  roleIds: [] as number[],
})

const rules: FormRules = {
  name: [
    { required: true, message: '请输入角色名称', trigger: 'blur' },
    { max: 100, message: '角色名称不能超过100个字符', trigger: 'blur' },
    { validator: validateRoleNameUnique, trigger: 'blur' },
  ],
  code: [
    { required: true, message: '请输入角色编码', trigger: 'blur' },
    { pattern: /^[A-Z][A-Z0-9_]*$/, message: '仅支持大写字母、数字和下划线，且以字母开头', trigger: 'blur' },
  ],
  description: [
    { max: 500, message: '角色说明不能超过500个字符', trigger: 'blur' },
  ],
}

const roleGroupRules: FormRules = {
  name: [
    { required: true, message: '请输入角色组名称', trigger: 'blur' },
    { max: 100, message: '角色组名称不能超过100个字符', trigger: 'blur' },
    { validator: validateRoleGroupNameUnique, trigger: 'blur' },
  ],
  description: [
    { max: 500, message: '角色组说明不能超过500个字符', trigger: 'blur' },
  ],
}

const previewNodes: Array<{ label: string; color: string; icon: Component; note?: string }> = [
  { label: '发起人', color: '#0084ff', icon: RAW_USER_ICON },
  { label: '项目经理', color: '#1f6feb', icon: RAW_SUITCASE_ICON },
  { label: '技术负责人', color: '#20b26b', icon: RAW_TICKETS_ICON },
  { label: '测试负责人', color: '#8b5cf6', icon: RAW_TOOLS_ICON, note: '已离职' },
  { label: '运维负责人', color: '#0ea5e9', icon: RAW_TOOLS_ICON },
]

const filteredRoles = computed(() => {
  const value = keyword.value.trim().toLowerCase()
  if (!value) return roles.value
  return roles.value.filter(role => {
    return role.name.toLowerCase().includes(value)
      || role.code.toLowerCase().includes(value)
      || (role.description || '').toLowerCase().includes(value)
  })
})

const unassignedRoles = computed(() => {
  return roles.value.filter(role => !role.roleGroupId)
})

const roleTreeData = computed<RoleTreeNode[]>(() => {
  const keywordLower = keyword.value.trim().toLowerCase()
  const nodes: RoleTreeNode[] = []

  // Add role groups with their roles as children
  roleGroups.value.forEach(group => {
    const groupRoles = roles.value.filter(role => role.roleGroupId === group.id)
    const filteredRoles = keywordLower
      ? groupRoles.filter(role =>
          role.name.toLowerCase().includes(keywordLower) ||
          role.code.toLowerCase().includes(keywordLower)
        )
      : groupRoles

    const groupNode: RoleTreeNode = {
      id: `group-${group.id}`,
      name: group.name,
      type: 'group',
      icon: RAW_FOLDER_OPENED_ICON,
      data: group,
      children: filteredRoles.map(role => ({
        id: `role-${role.id}`,
        name: role.name,
        type: 'role',
        icon: RAW_USER_ICON,
        data: role,
      })),
    }
    nodes.push(groupNode)
  })

  // Add unassigned roles under "默认" group
  const unassigned = roles.value.filter(role => !role.roleGroupId)
  const filteredUnassigned = keywordLower
    ? unassigned.filter(role =>
        role.name.toLowerCase().includes(keywordLower) ||
        role.code.toLowerCase().includes(keywordLower)
      )
    : unassigned

  if (filteredUnassigned.length > 0 || !keywordLower) {
    nodes.unshift({
      id: DEFAULT_ROLE_GROUP_KEY,
      name: '默认',
      type: 'group',
      icon: RAW_USER_FILLED_ICON,
      data: null,
      children: filteredUnassigned.map(role => ({
        id: `role-${role.id}`,
        name: role.name,
        type: 'role',
        icon: RAW_USER_ICON,
        data: role,
      })),
    })
  }

  return nodes
})

const roleGroupSections = computed<RoleGroupSection[]>(() => {
  const groupMap = new Map(roleGroups.value.map(group => [group.id, group]))
  const sections: RoleGroupSection[] = []
  const groupedRoles = new Map<string, RoleItem[]>()

  roleGroups.value.forEach(group => {
    groupedRoles.set(`role-group-${group.id}`, [])
  })

  filteredRoles.value.forEach((role) => {
    const group = role.roleGroupId ? groupMap.get(role.roleGroupId) : null
    const key = group ? `role-group-${group.id}` : DEFAULT_ROLE_GROUP_KEY
    if (!groupedRoles.has(key)) {
      groupedRoles.set(key, [])
    }
    groupedRoles.get(key)!.push(role)
  })

  roleGroups.value.forEach((group) => {
    const key = `role-group-${group.id}`
    const groupRoles = groupedRoles.get(key) || []
    if (keyword.value.trim() && groupRoles.length === 0 && !group.name.toLowerCase().includes(keyword.value.trim().toLowerCase())) {
      return
    }
    sections.push({
      key,
      name: group.name,
      roles: groupRoles,
      isDefault: false,
      icon: RAW_FOLDER_OPENED_ICON,
      source: group,
    })
  })

  const defaultRoles = groupedRoles.get(DEFAULT_ROLE_GROUP_KEY) || []
  if (defaultRoles.length > 0 || (!keyword.value.trim() && sections.length === 0)) {
    sections.unshift({
      key: DEFAULT_ROLE_GROUP_KEY,
      name: '默认',
      roles: defaultRoles,
      isDefault: true,
      icon: RAW_USER_FILLED_ICON,
      source: null,
    })
  }

  return sections
})

const allRoleGroupsExpanded = computed(() => {
  return roleGroupSections.value.length > 0
    && roleGroupSections.value.every(group => expandedRoleGroupKeys.value.includes(group.key))
})

const isCurrentSuperAdmin = computed(() => userStore.isSuperAdmin)
const canGrantSelectedRole = computed(() => {
  return !!selectedRole.value && !isSuperAdminRole(selectedRole.value) && (isCurrentSuperAdmin.value || !isSystemRole(selectedRole.value))
})
const grantDisabledReason = computed(() => {
  if (!selectedRole.value) return '请先选择一个角色。'
  if (isSuperAdminRole(selectedRole.value)) return '超级管理员角色拥有系统最高权限，不支持调整权限范围。'
  if (isSystemRole(selectedRole.value) && !isCurrentSuperAdmin.value) return '系统角色仅允许超级管理员配置。'
  return '当前账号无权配置该角色权限。'
})

const menuPermissionNameMap = computed(() => {
  const names = new Map<string, string>()
  const walk = (items: MenuItem[]) => {
    items.forEach(item => {
      if (item.permissionCode && item.name) {
        names.set(item.permissionCode, item.name)
      }
      walk(item.children || [])
    })
  }
  walk(menuTree.value)
  return names
})

const permissionOptions = computed<PermissionOption[]>(() => {
  return grantablePermissions.value.map(code => ({
    code,
    name: menuPermissionNameMap.value.get(code) || permissionName(code),
  }))
})

const permissionOptionMap = computed(() => {
  return new Map(permissionOptions.value.map(item => [item.code, item]))
})

const menuPermissionTree = computed<MenuPermissionNode[]>(() => {
  return buildMenuPermissionTree(menuTree.value, 0)
})

const menuPermissionCodes = computed(() => {
  const codes = new Set<string>()
  const walk = (nodes: MenuPermissionNode[]) => {
    nodes.forEach(node => {
      node.allPermissions.forEach(permission => codes.add(permission.code))
      walk(node.children)
    })
  }
  walk(menuPermissionTree.value)
  return codes
})

const orphanPermissions = computed(() => {
  return permissionOptions.value.filter(item => !menuPermissionCodes.value.has(item.code))
})

const filteredMenuPermissionTree = computed(() => {
  const value = permissionKeyword.value.trim().toLowerCase()
  if (!value) return menuPermissionTree.value
  return filterMenuPermissionTree(menuPermissionTree.value, value)
})

const filteredOrphanPermissions = computed(() => {
  const value = permissionKeyword.value.trim().toLowerCase()
  if (!value) return orphanPermissions.value
  return orphanPermissions.value.filter(item => isPermissionMatched(item, value))
})

onMounted(async () => {
  await fetchRoles()
  initSortable()
})

onUnmounted(() => {
  if (sortableGroupInstance) {
    sortableGroupInstance.destroy()
    sortableGroupInstance = null
  }
  sortableRoleInstances.forEach(instance => instance.destroy())
  sortableRoleInstances.clear()
})

function initSortable() {
  if (!roleGroupsRef.value) return

  // Initialize role groups drag
  sortableGroupInstance = new Sortable(roleGroupsRef.value, {
    group: 'roleGroups',
    handle: '.drag-handle--group',
    animation: 150,
    ghostClass: 'sortable-ghost',
    chosenClass: 'sortable-chosen',
    onEnd: async (evt) => {
      const items: Array<{ id: number; sortOrder: number }> = []
      const groupElements = roleGroupsRef.value?.querySelectorAll('.role-group')
      if (!groupElements) return
      groupElements.forEach((el, index) => {
        const groupId = el.getAttribute('data-group-id')
        if (groupId) {
          items.push({ id: Number(groupId), sortOrder: index })
        }
      })
      if (items.length > 0) {
        try {
          await batchSortRoleGroups(items)
          ElMessage.success('角色组排序已保存')
        } catch {
          ElMessage.error('排序保存失败')
        }
      }
    },
  })

  // Initialize roles drag within each group
  initRoleSortable()
}

function initRoleSortable(reinitAll = true) {
  if (reinitAll) {
    // Destroy existing instances first to handle new groups and roles
    sortableRoleInstances.forEach(instance => instance.destroy())
    sortableRoleInstances.clear()
  }

  const bodyElements = roleGroupsRef.value?.querySelectorAll('.role-group__body')
  if (!bodyElements) return
  bodyElements.forEach((body) => {
    const key = body.getAttribute('data-body-key') || ''
    // Skip already initialized instances unless reinitAll is true
    if (!reinitAll && sortableRoleInstances.has(key)) return
    const instance = new Sortable(body as HTMLElement, {
      group: {
        name: 'roles',
        put: true,
      },
      handle: '.role-item',
      animation: 150,
      delay: 400,
      delayOnTouchOnly: false,
      ghostClass: 'sortable-ghost',
      chosenClass: 'sortable-chosen',
      onEnd: async (evt) => {
        const items: Array<{ id: number; roleGroupId: number | null; sortOrder: number }> = []
        const roleElements = evt.to.querySelectorAll('.role-item')
        const targetGroupId = evt.to.getAttribute('data-body-key')
        const targetGroupSourceId = targetGroupId?.replace('role-group-', '') || null
        roleElements.forEach((el, index) => {
          const roleId = el.getAttribute('data-role-id')
          if (roleId) {
            const roleGroupId = targetGroupId === DEFAULT_ROLE_GROUP_KEY ? null : Number(targetGroupSourceId)
            items.push({ id: Number(roleId), roleGroupId, sortOrder: index })
          }
        })
        if (items.length > 0) {
          try {
            await batchSortRoles(items)
            ElMessage.success('角色排序已保存')
            await fetchRoles()
          } catch {
            ElMessage.error('排序保存失败')
          }
        }
      },
    })
    sortableRoleInstances.set(key, instance)
  })
}

function isSystemRole(role: RoleItem) {
  return role.isSystem === 1
}

async function fetchRoles() {
  loading.value = true
  try {
    const [roleList, groupList, menuListResult] = await Promise.all([
      getRoleList(),
      getRoleGroups(),
      getAllMenus().catch(() => []),
    ]) as any[]
    roles.value = roleList || []
    roleGroups.value = groupList || []
    menuTree.value = menuListResult || []
    syncExpandedRoleGroups()
    if (selectedRole.value) {
      selectedRole.value = roles.value.find(item => item.id === selectedRole.value?.id) || null
    }
    if (!selectedRole.value && roles.value.length > 0) {
      await selectRole(roles.value[0])
    }
    // Re-initialize role sortable after data fetch
    setTimeout(() => initRoleSortable(), 100)
  } finally {
    loading.value = false
  }
}

async function selectRole(role: RoleItem) {
  selectedRole.value = role
  permissionKeyword.value = ''
  await fetchRolePermissions(role.id)
}

function handleTreeNodeClick(data: RoleTreeNode) {
  if (data.type === 'role') {
    selectRole(data.data as RoleItem)
  }
}

function handleAllowDrag(draggingNode: any) {
  return draggingNode.data.type === 'role'
}

function handleAllowDrop(draggingNode: any, dropNode: any, type: string) {
  if (dropNode.data.type === 'group' && type === 'inner') {
    return true
  }
  if (draggingNode.data.type === 'role' && dropNode.data.type === 'role' && type === 'after') {
    return true
  }
  if (draggingNode.data.type === 'role' && dropNode.data.type === 'role' && type === 'before') {
    return true
  }
  return false
}

async function handleDragEnd(draggingNode: any, dropNode: any, dropType: string, ev: DragEvent) {
  if (!dropNode || draggingNode.data.id === dropNode.data.id) {
    return
  }
  const draggingData = draggingNode.data as RoleTreeNode
  if (draggingData.type !== 'role') {
    return
  }
  const role = draggingData.data as RoleItem
  let newRoleGroupId: number | null = null

  if (dropType === 'inner') {
    const dropData = dropNode.data as RoleTreeNode
    if (dropData.type === 'group' && dropData.id !== DEFAULT_ROLE_GROUP_KEY) {
      const group = dropData.data as RoleGroupItem
      newRoleGroupId = group?.id ?? null
    }
  } else if (dropType === 'after' || dropType === 'before') {
    const dropData = dropNode.data as RoleTreeNode
    if (dropData.type === 'group') {
      newRoleGroupId = null
    } else if (dropData.type === 'role') {
      const targetRole = dropData.data as RoleItem
      newRoleGroupId = targetRole.roleGroupId ?? null
    }
  }

  const sameGroup = role.roleGroupId === newRoleGroupId

  try {
    if (!sameGroup) {
      await updateRole(role.id, {
        code: role.code,
        name: role.name,
        description: role.description,
        roleGroupId: newRoleGroupId,
      })
    }

    const tree = roleTreeRef.value
    if (tree) {
      const allSortItems: { id: number; roleGroupId: number | null; sortOrder: number }[] = []
      tree.store.root.childNodes.forEach((groupNode: any) => {
        const groupId = groupNode.data.id === DEFAULT_ROLE_GROUP_KEY
          ? null
          : (groupNode.data.data as RoleGroupItem)?.id ?? null
        groupNode.childNodes.forEach((roleNode: any, index: number) => {
          if (roleNode.data.type === 'role') {
            allSortItems.push({
              id: (roleNode.data.data as RoleItem).id,
              roleGroupId: groupId,
              sortOrder: index + 1,
            })
          }
        })
      })
      if (allSortItems.length > 0) {
        await batchSortRoles(allSortItems)
      }
    }

    ElMessage.success(sameGroup ? '排序已更新' : '角色分组已调整')
    await fetchRoles()
  } catch {
    ElMessage.error(sameGroup ? '排序更新失败' : '调整分组失败')
    await fetchRoles()
  }
}

async function fetchRolePermissions(roleId: number) {
  permissionLoading.value = true
  try {
    const [grantable, rolePermission] = await Promise.all([
      getGrantablePermissions(),
      getRolePermissions(roleId),
    ]) as any[]
    // grantablePermissions 只存储当前用户可授权的权限
    grantablePermissions.value = grantable || []
    selectedPermissions.value = rolePermission?.permissionCodes || []
    expandedMenuKeys.value = defaultExpandedKeys(menuPermissionTree.value)
  } catch (err) {
    // 错误已由 request 拦截器弹 ElMessage 提示，这里只兜底避免错误向上冒泡触发 Vue 警告
    console.error('加载角色权限失败:', err)
  } finally {
    permissionLoading.value = false
  }
}

/**
 * 判断权限是否在当前用户可授权范围内
 * 用于控制未归类权限的 checkbox 禁用状态
 */
function isGrantablePermission(code: string): boolean {
  return grantablePermissions.value.includes(code)
}

function openCreate() {
  editingRole.value = null
  codeManuallyEdited.value = false
  resetForm()
  dialogVisible.value = true
}

function openEdit(role: RoleItem) {
  editingRole.value = role
  codeManuallyEdited.value = true
  form.name = role.name
  form.code = role.code
  form.description = role.description || ''
  form.roleGroupId = role.roleGroupId ?? null
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  if (!form.code && form.name) {
    form.code = generateRoleCode(form.name)
  }
  await formRef.value.validate()
  submitting.value = true
  try {
    const payload = {
      code: form.code.trim().toUpperCase(),
      name: form.name.trim(),
      description: form.description?.trim() || null,
      roleGroupId: form.roleGroupId || null,
    }
    const saved = editingRole.value
      ? await updateRole(editingRole.value.id, payload) as any
      : await createRole(payload) as any
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await fetchRoles()
    selectedRole.value = roles.value.find(item => item.id === saved?.id) || selectedRole.value
  } finally {
    submitting.value = false
  }
}

async function handleDelete(role: RoleItem) {
  await ElMessageBox.confirm(`确认删除角色“${role.name}”吗？`, '删除角色', { type: 'warning' })
  await deleteRole(role.id)
  ElMessage.success('删除成功')
  if (selectedRole.value?.id === role.id) {
    selectedRole.value = null
    selectedPermissions.value = []
  }
  await fetchRoles()
}

async function handleSavePermissions() {
  if (!selectedRole.value || !canGrantSelectedRole.value) return
  permissionSaving.value = true
  try {
    await saveRolePermissions(selectedRole.value.id, selectedPermissions.value)
    ElMessage.success('权限保存成功')
    await fetchRolePermissions(selectedRole.value.id)
  } catch (err) {
    // 错误已由 request 拦截器弹 ElMessage 提示，这里只兜底避免错误向上冒泡触发 Vue 警告
    console.error('保存权限失败:', err)
  } finally {
    permissionSaving.value = false
  }
}

function handleBatchCommand(command: string) {
  if (command === 'import') {
    triggerImport()
    return
  }
  if (command === 'export') {
    handleExportRoles()
  }
}

function triggerImport() {
  importInputRef.value?.click()
}

function handleExportRoles() {
  const exportRows = filteredRoles.value.map(role => ({
    角色组: roleGroupNameById(role.roleGroupId),
    角色名称: role.name,
    角色编码: role.code,
    角色类型: isSystemRole(role) ? '系统角色' : '自定义角色',
    角色说明: role.description || '',
  }))
  if (exportRows.length === 0) {
    ElMessage.warning('当前没有可导出的角色数据')
    return
  }
  exportToExcel(exportRows, '角色列表', '角色列表', [
    { wch: 24 },
    { wch: 28 },
    { wch: 14 },
    { wch: 42 },
  ])
  ElMessage.success('导出成功')
}

async function handleImportFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  batchImporting.value = true
  try {
    const importRows = await readRoleImportRows(file)
    if (importRows.length === 0) {
      ElMessage.warning('导入文件中没有有效角色数据')
      return
    }
    const existingCodes = new Set(roles.value.map(role => role.code.toUpperCase()))
    const existingNames = new Set(roles.value.map(role => role.name.trim()))
    let successCount = 0
    const failures: Array<RoleImportRow & { reason: string }> = []

    for (const row of importRows) {
      if (existingCodes.has(row.code.toUpperCase())) {
        failures.push({ ...row, reason: '角色编码已存在' })
        continue
      }
      if (existingNames.has(row.name.trim())) {
        failures.push({ ...row, reason: '角色名称已存在' })
        continue
      }
      try {
        await createRole(row)
        successCount += 1
        existingCodes.add(row.code.toUpperCase())
        existingNames.add(row.name.trim())
      } catch (error) {
        failures.push({ ...row, reason: error instanceof Error ? error.message : '导入失败' })
      }
    }

    await fetchRoles()
    if (failures.length > 0) {
      exportImportFailures(failures)
      ElMessage.warning(`导入完成：成功 ${successCount} 条，失败 ${failures.length} 条，失败明细已导出`)
    } else {
      ElMessage.success(`导入完成：成功 ${successCount} 条`)
    }
  } catch {
    ElMessage.error('角色导入失败，请检查文件格式')
  } finally {
    batchImporting.value = false
    input.value = ''
  }
}

function readRoleImportRows(file: File): Promise<RoleImportRow[]> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => {
      try {
        const workbook = XLSX.read(reader.result, { type: 'array' })
        const worksheet = workbook.Sheets[workbook.SheetNames[0]]
        if (!worksheet) {
          resolve([])
          return
        }
        const records = XLSX.utils.sheet_to_json<Record<string, unknown>>(worksheet, { defval: '' })
        const rows = records
          .map(normalizeRoleImportRow)
          .filter((row): row is RoleImportRow => !!row)
        resolve(rows)
      } catch (error) {
        reject(error)
      }
    }
    reader.onerror = () => reject(reader.error)
    reader.readAsArrayBuffer(file)
  })
}

function normalizeRoleImportRow(record: Record<string, unknown>): RoleImportRow | null {
  const name = readCell(record, ['角色名称', '名称', 'name']).trim()
  const rawCode = readCell(record, ['角色编码', '编码', 'code']).trim()
  const description = readCell(record, ['角色说明', '说明', 'description']).trim()
  const code = rawCode ? rawCode.toUpperCase() : generateRoleCode(name)
  if (!name || !/^[A-Z][A-Z0-9_]*$/.test(code)) {
    return null
  }
  return {
    code,
    name,
    description: description || null,
  }
}

function readCell(record: Record<string, unknown>, keys: string[]) {
  for (const key of keys) {
    const value = record[key]
    if (value !== undefined && value !== null) {
      return String(value)
    }
  }
  return ''
}

function exportImportFailures(failures: Array<RoleImportRow & { reason: string }>) {
  const exportRows = failures.map(item => ({
    角色名称: item.name,
    角色编码: item.code,
    角色说明: item.description || '',
    失败原因: item.reason,
  }))
  exportToExcel(exportRows, '导入失败明细', '角色导入失败明细', [
    { wch: 24 },
    { wch: 28 },
    { wch: 42 },
    { wch: 32 },
  ])
}

function selectAllVisiblePermissions() {
  const values = [
    ...collectPermissionCodes(filteredMenuPermissionTree.value),
    ...filteredOrphanPermissions.value.map(item => item.code),
  ]
  selectedPermissions.value = Array.from(new Set([...selectedPermissions.value, ...values]))
}

function clearVisiblePermissions() {
  const visible = new Set([
    ...collectPermissionCodes(filteredMenuPermissionTree.value),
    ...filteredOrphanPermissions.value.map(item => item.code),
  ])
  selectedPermissions.value = selectedPermissions.value.filter(code => !visible.has(code))
}

function selectedCount(items: PermissionOption[]) {
  const selected = new Set(selectedPermissions.value)
  return items.filter(item => selected.has(item.code)).length
}

function isSuperAdminRole(role: RoleItem | null) {
  return !!role && SUPER_ADMIN_CODES.has(role.code)
}

function buildMenuPermissionTree(items: MenuItem[], level: number): MenuPermissionNode[] {
  return items
    .filter(item => item.menuType !== 'BUTTON')
    .map(item => {
      const menuPermission = item.permissionCode ? permissionOptionMap.value.get(item.permissionCode) || null : null
      const buttons = (item.children || [])
        .filter(child => child.menuType === 'BUTTON' && child.permissionCode)
        .map(child => {
          const code = child.permissionCode || ''
          const option = permissionOptionMap.value.get(code)
          return {
            code,
            name: child.name || option?.name || permissionName(code),
          }
        })
        .filter(button => !!button.code)
      const children = buildMenuPermissionTree(item.children || [], level + 1)
      const allPermissions = [
        ...(menuPermission ? [menuPermission] : []),
        ...buttons,
        ...children.flatMap(child => child.allPermissions),
      ]
      return {
        key: `menu-${item.id}`,
        id: item.id,
        name: item.name,
        menuType: item.menuType,
        level,
        menuPermission,
        buttons,
        children,
        allPermissions,
      }
    })
    .filter(item => item.allPermissions.length > 0 || item.children.length > 0)
}

function filterMenuPermissionTree(nodes: MenuPermissionNode[], keyword: string): MenuPermissionNode[] {
  return nodes
    .map(node => {
      const children = filterMenuPermissionTree(node.children, keyword)
      const buttons = node.buttons.filter(item => isPermissionMatched(item, keyword))
      const selfMatched = node.name.toLowerCase().includes(keyword)
        || node.menuType.toLowerCase().includes(keyword)
        || (node.menuPermission ? isPermissionMatched(node.menuPermission, keyword) : false)
      if (!selfMatched && children.length === 0 && buttons.length === 0) {
        return null
      }
      const allPermissions = [
        ...(node.menuPermission ? [node.menuPermission] : []),
        ...buttons,
        ...children.flatMap(child => child.allPermissions),
      ]
      return {
        ...node,
        buttons: selfMatched ? node.buttons : buttons,
        children,
        allPermissions: selfMatched ? node.allPermissions : allPermissions,
      }
    })
    .filter((node): node is MenuPermissionNode => !!node)
}

function collectPermissionCodes(nodes: MenuPermissionNode[]) {
  return nodes.flatMap(node => node.allPermissions.map(item => item.code))
}

function isPermissionMatched(permission: PermissionOption, keyword: string) {
  return permission.name.toLowerCase().includes(keyword) || permission.code.toLowerCase().includes(keyword)
}

function isMenuChecked(node: MenuPermissionNode) {
  const selected = new Set(selectedPermissions.value)
  return node.allPermissions.length > 0 && node.allPermissions.every(item => selected.has(item.code))
}

function isMenuIndeterminate(node: MenuPermissionNode) {
  const selected = new Set(selectedPermissions.value)
  const count = node.allPermissions.filter(item => selected.has(item.code)).length
  return count > 0 && count < node.allPermissions.length
}

function handleMenuCheck(node: MenuPermissionNode, checked: boolean) {
  setPermissionsChecked(node.allPermissions.map(item => item.code), checked)
  if (checked && !expandedMenuKeys.value.includes(node.key)) {
    expandedMenuKeys.value.push(node.key)
  }
}

function setPermissionChecked(code: string, checked: boolean) {
  setPermissionsChecked([code], checked)
}

function setPermissionsChecked(codes: string[], checked: boolean) {
  const next = new Set(selectedPermissions.value)
  codes.forEach(code => {
    if (checked) {
      next.add(code)
    } else {
      next.delete(code)
    }
  })
  selectedPermissions.value = Array.from(next)
}

function toggleMenuExpand(key: string) {
  expandedMenuKeys.value = expandedMenuKeys.value.includes(key)
    ? expandedMenuKeys.value.filter(item => item !== key)
    : [...expandedMenuKeys.value, key]
}

function defaultExpandedKeys(nodes: MenuPermissionNode[]) {
  // 默认只展开一级菜单（顶级），二级、三级菜单保持折叠
  const keys: string[] = []
  nodes.forEach(item => {
    if (item.level === 0) {
      keys.push(item.key)
    }
  })
  return keys
}

function menuTypeLabel(type: string) {
  const labelMap: Record<string, string> = {
    DIRECTORY: '目录',
    MENU: '菜单',
  }
  return labelMap[type] || type
}

function resetForm() {
  form.code = ''
  form.name = ''
  form.description = ''
  form.roleGroupId = null
  codeManuallyEdited.value = false
  formRef.value?.resetFields()
}

function toggleAllRoleGroups() {
  expandedRoleGroupKeys.value = allRoleGroupsExpanded.value
    ? []
    : roleGroupSections.value.map(group => group.key)
}

function isRoleGroupExpanded(key: string) {
  return expandedRoleGroupKeys.value.includes(key)
}

function toggleRoleGroup(key: string) {
  const wasExpanded = isRoleGroupExpanded(key)
  expandedRoleGroupKeys.value = wasExpanded
    ? expandedRoleGroupKeys.value.filter(item => item !== key)
    : [...expandedRoleGroupKeys.value, key]
  // Re-initialize sortable when group is expanded to handle newly visible body
  if (!wasExpanded) {
    // Group was collapsed, now expanding - need to init sortable for this group only
    setTimeout(() => initRoleSortable(false), 50)
  }
}

function syncExpandedRoleGroups() {
  const keys = roleGroupSections.value.map(group => group.key)
  if (keys.length === 0) {
    expandedRoleGroupKeys.value = []
    return
  }
  const previous = new Set(expandedRoleGroupKeys.value)
  expandedRoleGroupKeys.value = keys.filter(key => previous.size === 0 || previous.has(key))
}

function roleGroupNameById(roleGroupId?: number | null) {
  if (!roleGroupId) return '默认'
  return roleGroups.value.find(group => group.id === roleGroupId)?.name || '默认'
}

function openCreateRoleGroup() {
  editingRoleGroup.value = null
  resetRoleGroupForm()
  roleGroupDialogVisible.value = true
}

function openEditRoleGroup(group?: RoleGroupItem | null) {
  if (!group) return
  editingRoleGroup.value = group
  roleGroupForm.name = group.name
  roleGroupForm.description = group.description || ''
  roleGroupDialogVisible.value = true
}

async function handleSubmitRoleGroup() {
  if (!roleGroupFormRef.value) return
  await roleGroupFormRef.value.validate()
  roleGroupSubmitting.value = true
  try {
    const payload = {
      name: roleGroupForm.name.trim(),
      description: roleGroupForm.description?.trim() || null,
      roleIds: editingRoleGroup.value ? undefined : roleGroupForm.roleIds,
    }
    if (editingRoleGroup.value) {
      await updateRoleGroup(editingRoleGroup.value.id, { name: payload.name, description: payload.description })
    } else {
      await createRoleGroup(payload)
    }
    ElMessage.success('角色组保存成功')
    roleGroupDialogVisible.value = false
    await fetchRoles()
  } finally {
    roleGroupSubmitting.value = false
  }
}

async function handleDeleteRoleGroup(group?: RoleGroupItem | null) {
  if (!group) return
  await ElMessageBox.confirm(`确认删除角色组“${group.name}”吗？组内角色会自动回到默认分组。`, '删除角色组', { type: 'warning' })
  await deleteRoleGroup(group.id)
  ElMessage.success('角色组删除成功')
  await fetchRoles()
}

function resetRoleGroupForm() {
  roleGroupForm.name = ''
  roleGroupForm.description = ''
  roleGroupForm.roleIds = []
  roleGroupFormRef.value?.resetFields()
}

function validateRoleGroupNameUnique(_rule: unknown, value: string, callback: (error?: Error) => void) {
  const name = value.trim()
  const conflict = roleGroups.value.some(group => group.name.trim() === name && group.id !== editingRoleGroup.value?.id)
  if (conflict) {
    callback(new Error('角色组名称已存在'))
  } else {
    callback()
  }
}

function handleRoleNameInput() {
  if (!editingRole.value && !codeManuallyEdited.value) {
    form.code = generateRoleCode(form.name)
  }
}

function validateRoleNameUnique(_rule: unknown, value: string, callback: (error?: Error) => void) {
  const name = value.trim()
  const conflict = roles.value.some(role => {
    return role.name.trim() === name && role.id !== editingRole.value?.id
  })
  if (conflict) {
    callback(new Error('角色名称已存在'))
  } else {
    callback()
  }
}

function generateRoleCode(name: string) {
  const normalized = name.trim()
  if (!normalized) return ''
  const ascii = normalized
    .replace(/([a-z])([A-Z])/g, '$1_$2')
    .replace(/[^a-zA-Z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '')
    .toUpperCase()
  if (/^[A-Z][A-Z0-9_]*$/.test(ascii)) {
    return ascii.slice(0, 50)
  }
  const translated = translateChineseRoleName(normalized)
  if (translated) {
    return translated.slice(0, 50)
  }
  return `ROLE_${shortHash(normalized)}`.slice(0, 50)
}

function translateChineseRoleName(name: string) {
  const exactMap: Record<string, string> = {
    超级管理员: 'SUPER_ADMIN',
    系统管理员: 'SYSTEM_ADMIN',
    管理员: 'ADMIN',
    产品经理: 'PRODUCT_MANAGER',
    项目经理: 'PROJECT_MANAGER',
    需求管理员: 'DEMAND_MANAGER',
    需求负责人: 'DEMAND_OWNER',
    需求评审人: 'DEMAND_REVIEWER',
    开发负责人: 'DEVELOPMENT_LEAD',
    技术负责人: 'TECH_LEAD',
    测试负责人: 'QA_LEAD',
    测试人员: 'TESTER',
    开发人员: 'DEVELOPER',
    运维负责人: 'OPS_LEAD',
    运维人员: 'OPS_ENGINEER',
    普通用户: 'USER',
    访客: 'GUEST',
  }
  if (exactMap[name]) return exactMap[name]

  const segments: Array<[RegExp, string]> = [
    [/产品/g, 'PRODUCT'],
    [/项目/g, 'PROJECT'],
    [/需求/g, 'DEMAND'],
    [/研发|开发/g, 'DEVELOPMENT'],
    [/技术/g, 'TECH'],
    [/测试|质量/g, 'QA'],
    [/运维/g, 'OPS'],
    [/系统/g, 'SYSTEM'],
    [/安全/g, 'SECURITY'],
    [/管理员|管理/g, 'ADMIN'],
    [/负责人|主管/g, 'LEAD'],
    [/经理/g, 'MANAGER'],
    [/审核|评审|审批/g, 'REVIEWER'],
    [/成员|人员|用户/g, 'USER'],
    [/访客/g, 'GUEST'],
  ]
  const parts = segments
    .filter(([pattern]) => pattern.test(name))
    .map(([, word]) => word)
  return Array.from(new Set(parts)).join('_')
}

function shortHash(value: string) {
  let hash = 0
  for (let index = 0; index < value.length; index += 1) {
    hash = ((hash << 5) - hash + value.charCodeAt(index)) >>> 0
  }
  return hash.toString(36).toUpperCase().padStart(6, '0')
}

function showTodo(action: string) {
  ElMessage.info(`${action}能力将在后续接口完善后接入`)
}

function permissionName(code: string) {
  const labelMap: Record<string, string> = {
    'menu:dashboard': '仪表盘菜单',
    'menu:requirement': '需求管理菜单',
    'menu:iteration': '迭代管理菜单',
    'menu:system-config': '系统配置菜单',
    'menu:settings:project': '项目管理菜单',
    'menu:settings:user': '用户管理菜单',
    'menu:settings:requirement': '需求配置菜单',
    'menu:settings:workflow': '工作流配置菜单',
    'menu:settings:role': '角色管理菜单',
    'menu:menu-management': '菜单管理菜单',
    'menu:rag': 'RAG文档中心菜单',
    'menu:settings:llm': '模型配置菜单',
    'menu:requirement:view:all': '全部需求',
    'menu:requirement:view:pending': '我的待办',
    'menu:requirement:view:done': '我的已办',
    'menu:requirement:view:draft': '我的草稿',
    'menu:requirement:view:follow': '我的关注',
    'button:role:create': '新增角色',
    'button:role:update': '编辑角色',
    'button:role:delete': '删除角色',
    'button:role:grant': '角色授权',
    'button:menu:create': '新增菜单',
    'button:menu:update': '编辑菜单',
    'button:menu:delete': '删除菜单',
    'button:menu:grant': '菜单授权',
    'button:user:create': '新增用户',
    'button:user:update': '编辑用户',
    'button:user:delete': '删除用户',
    'button:project:create': '新建项目',
    'button:project:update': '编辑项目',
    'button:project:delete': '删除项目',
    'button:project:import': '导入项目',
    'button:project:export': '导出项目',
    'button:iteration:create': '新建迭代',
    'button:iteration:update': '编辑迭代',
    'button:iteration:delete': '删除迭代',
    'button:review:create': '发起评审',
    'button:review:update': '编辑评审',
    'button:review:submit': '提交评审',
    'button:knowledge:create': '新建知识库',
    'button:knowledge:update': '编辑知识库',
    'button:knowledge:delete': '删除知识库',
    'button:knowledge:upload': '上传文档',
    'button:knowledge:download': '下载文档',
    'button:knowledge:share': '分享文档',
    'button:requirement-config:create': '新增配置项',
    'button:requirement-config:update': '编辑配置项',
    'button:requirement-config:delete': '删除配置项',
    'button:workflow:config': '工作流配置',
    'button:workflow:create': '新建工作流',
    'button:workflow:update': '编辑工作流',
    'button:workflow:delete': '删除工作流',
    'button:workflow:activate': '启用/停用工作流',
    'button:workflow:approve': '审批工作流',
    'button:requirement:create': '新建需求',
    'button:requirement:update': '编辑',
    'button:requirement:delete': '删除',
    'button:requirement:export': '导出Excel',
    'button:requirement:submit': '提交',
    'button:requirement:split': '拆分子需求',
    'button:requirement:comment': '评论',
    'button:requirement:rollback': '驳回',
    'button:requirement:cancel': '取消',
    'button:requirement:batch-delete': '批量删除',
    'button:requirement-template:create': '新建需求模板',
    'button:requirement-template:update': '编辑需求模板',
    'button:requirement-template:delete': '删除需求模板',
    'button:requirement-template:toggle': '启停需求模板',
    'button:rag:upload': '文档上传',
    'button:rag:search': '文档搜索',
    'button:llm:create': '新增模型配置',
    'button:llm:update': '编辑模型配置',
    'button:llm:delete': '删除模型配置',
    'button:llm-provider:create': '新建模型提供商',
    'button:llm-provider:update': '编辑模型提供商',
    'button:llm-provider:delete': '删除模型提供商',
    'button:llm-provider:test': '测试模型提供商',
  }
  return labelMap[code] || code
}
</script>

<style scoped lang="scss">
.role-console {
  min-height: calc(100vh - 82px);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.role-heading {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
}

.role-heading__title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--color-text-primary);
  font-size: var(--font-size-lg);
  font-weight: 700;
  white-space: nowrap;
}

.role-heading__hint {
  padding: 8px 12px;
  border-radius: 6px;
  background: var(--color-text-secondary);
  color: #fff;
  font-size: var(--font-size-sm);
}

.mode-switch {
  flex-shrink: 0;
  padding: 4px;
  border-radius: 6px;
  background: #e5e7ec;
}

.mode-switch :deep(.el-radio-button__inner) {
  min-width: 140px;
  border: 0;
  box-shadow: none;
  background: transparent;
  font-weight: 600;
}

.mode-switch :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background: #fff;
  color: var(--color-text-primary);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.12);
}

.role-page {
  min-height: calc(100vh - 148px);
  display: grid;
  grid-template-columns: var(--role-sidebar-width, 360px) var(--role-sidebar-resizer-width, 4px) minmax(0, 1fr);
  background: #fff;
  position: relative;
}

.role-sidebar {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
  padding: var(--spacing-md);
  border-right: 1px solid var(--color-border);
  background: #fff;
  overflow: auto;
}

.role-sidebar.is-collapsed {
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

.sidebar-actions {
  display: flex;
  gap: var(--spacing-sm);
  flex-wrap: wrap;
}

.role-group {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.role-groups {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.role-group__header {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.role-group__title {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  padding: 4px 0;
  border: 0;
  background: transparent;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  font-weight: 600;
  text-align: left;
  cursor: pointer;
}

.role-group__tools {
  display: inline-flex;
  align-items: center;
  flex-shrink: 0;
  gap: 2px;
}

.role-group__body {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.role-group__count {
  margin-left: auto;
  min-width: 22px;
  padding: 1px 7px;
  border-radius: 999px;
  background: #edf2f7;
  color: var(--color-text-placeholder);
  text-align: center;
  font-size: var(--font-size-xs);
}

.role-group__empty {
  padding: 12px;
  border-radius: var(--radius-md);
  background: #f8fafc;
  color: var(--color-text-placeholder);
  font-size: var(--font-size-sm);
  text-align: center;
}

.role-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: 12px;
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  background: transparent;
  text-align: left;
  cursor: grab;
  color: var(--color-text-primary);
  user-select: none;
  transition: background 0.2s, border-color 0.2s, box-shadow 0.2s;
}

.role-item .el-icon {
  flex-shrink: 0;
}

.role-item__main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
}

.role-item:hover {
  background: #f5f7fa;
}

.role-item:active {
  cursor: grabbing;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.role-item.is-active {
  background: var(--color-info-light);
  border-color: #b3d8ff;
}

.role-item.is-dragging {
  opacity: 0.5;
  cursor: grabbing;
}

.role-item__name {
  min-width: 0;
  font-weight: 600;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.role-item__meta {
  color: var(--color-text-placeholder);
  font-size: var(--font-size-xs);
  word-break: break-all;
}

.role-main {
  min-width: 0;
  padding: var(--spacing-lg);
  /* 显式指定 grid-column：当 sidebar 折叠（display: none）时，
     防止 main 被错位放到第二个 track 而被压缩到 0 宽 */
  grid-column: 3;
}

.role-detail {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.detail-header,
.panel-head,
.permission-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--spacing-md);
}

.detail-title {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  font-size: 22px;
  font-weight: 700;
  color: var(--color-text-primary);
}

.detail-code {
  margin-top: 6px;
  color: var(--color-text-placeholder);
  font-size: var(--font-size-sm);
}

.detail-actions {
  display: flex;
  gap: var(--spacing-sm);
  flex-wrap: wrap;
}

.detail-description {
  margin: 0;
  color: var(--color-text-secondary);
  line-height: 1.7;
}

.role-actions-bar {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  flex-wrap: wrap;
  padding: var(--spacing-sm) 0 var(--spacing-md);
}

.permission-panel {
  padding-top: var(--spacing-md);
  border-top: 1px solid var(--color-border);
}

.panel-head {
  margin-bottom: var(--spacing-md);
}

.panel-head h3 {
  margin: 0 0 6px;
  font-size: var(--font-size-lg);
  color: var(--color-text-primary);
}

.panel-head p {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.permission-content {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.permission-toolbar .el-input {
  max-width: 360px;
}

.permission-table {
  border-top: 1px solid var(--color-border);
}

.menu-permission-list {
  display: flex;
  flex-direction: column;
  border-top: 1px solid var(--color-border);
}

.menu-permission-node {
  border-bottom: 1px solid var(--color-border);
}

.menu-permission-node.is-nested {
  border-bottom: 0;
}

.menu-permission-row {
  min-height: 52px;
  display: grid;
  grid-template-columns: 28px minmax(220px, 1fr) minmax(180px, 320px) 88px;
  align-items: center;
  gap: var(--spacing-sm);
  padding: 8px var(--spacing-md);
  background: #fff;
}

.menu-permission-row:hover {
  background: #f8fafc;
}

.expand-button {
  width: 24px;
  min-height: 24px;
  padding: 0;
}

.expand-placeholder {
  width: 24px;
  height: 24px;
}

.menu-name {
  margin-right: var(--spacing-sm);
  color: var(--color-text-primary);
  font-weight: 600;
}

.menu-count {
  color: var(--color-text-secondary);
  text-align: right;
  font-size: var(--font-size-sm);
}

.menu-permission-children {
  padding: 0 0 var(--spacing-sm);
  background: #fbfdff;
}

.nested-permission-list {
  display: flex;
  flex-direction: column;
}

.button-permission-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: var(--spacing-sm);
  padding: var(--spacing-sm) var(--spacing-md) var(--spacing-md);
}

.button-permission-grid :deep(.el-checkbox.is-bordered) {
  height: auto;
  min-height: 56px;
  margin-right: 0;
  padding: 9px 12px;
  align-items: flex-start;
  border-radius: var(--radius-md);
  background: #fff;
}

.button-permission-grid :deep(.el-checkbox__label) {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
  line-height: 1.4;
}

.orphan-permissions {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  padding-top: var(--spacing-md);
}

.orphan-title {
  color: var(--color-text-primary);
  font-weight: 700;
}

.permission-table__row {
  display: grid;
  grid-template-columns: 160px minmax(0, 1fr) 90px;
  gap: var(--spacing-md);
  align-items: start;
  min-height: 64px;
  padding: var(--spacing-md) 0;
  border-bottom: 1px solid var(--color-border);
}

.permission-table__head {
  min-height: 44px;
  align-items: center;
  padding: 0;
  background: #f5f7fa;
  color: var(--color-text-secondary);
  font-weight: 600;
}

.permission-table__head > div {
  padding: 0 var(--spacing-md);
}

.permission-module {
  padding-left: var(--spacing-md);
  color: var(--color-text-primary);
  font-weight: 600;
  line-height: 32px;
}

.permission-count {
  padding-right: var(--spacing-md);
  color: var(--color-text-secondary);
  line-height: 32px;
  text-align: right;
}

.permission-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: var(--spacing-sm);
}

.permission-grid :deep(.el-checkbox.is-bordered) {
  height: auto;
  min-height: 56px;
  margin-right: 0;
  padding: 9px 12px;
  align-items: flex-start;
  border-radius: var(--radius-md);
}

.permission-grid :deep(.el-checkbox__label) {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
  line-height: 1.4;
}

.permission-name {
  color: var(--color-text-primary);
}

.permission-code {
  color: var(--color-text-placeholder);
  font-size: var(--font-size-xs);
  word-break: break-all;
}

.empty-guide {
  min-height: 560px;
  display: grid;
  grid-template-columns: minmax(280px, 520px) minmax(320px, 1fr);
  align-items: center;
  gap: var(--spacing-xl);
}

.guide-copy h2 {
  margin: 0 0 var(--spacing-md);
  font-size: 28px;
  color: var(--color-text-primary);
}

.guide-copy h3 {
  margin: var(--spacing-xl) 0 var(--spacing-sm);
  font-size: var(--font-size-lg);
  color: var(--color-text-primary);
}

.guide-copy p,
.guide-copy li {
  color: var(--color-text-secondary);
  line-height: 1.8;
}

.guide-copy ul {
  padding-left: 18px;
  margin-bottom: var(--spacing-lg);
}

.flow-preview {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xl);
  align-items: center;
}

.flow-node {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
}

.flow-avatar {
  width: 72px;
  height: 72px;
  border-radius: 8px;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 30px;
}

.flow-label {
  min-width: 132px;
  padding: 10px 14px;
  background: var(--color-info-light);
  border-radius: var(--radius-md);
  color: var(--color-text-primary);
  font-weight: 600;
}

.flow-line {
  position: absolute;
  left: 36px;
  top: 80px;
  width: 1px;
  height: var(--spacing-xl);
  border-left: 1px dashed #a8abb2;
}

@media (max-width: 1100px) {
  .role-heading {
    align-items: flex-start;
    flex-direction: column;
    gap: var(--spacing-sm);
  }

  .mode-switch {
    width: 100%;
  }

  .mode-switch :deep(.el-radio-button) {
    width: 50%;
  }

  .mode-switch :deep(.el-radio-button__inner) {
    width: 100%;
    min-width: 0;
  }

  .role-page,
  .empty-guide {
    grid-template-columns: 1fr;
  }

  .sidebar-resizer {
    display: none;
  }

  .role-sidebar {
    border-right: 0;
    border-bottom: 1px solid var(--color-border);
  }

  .flow-preview {
    align-items: flex-start;
  }

  .permission-table__row {
    grid-template-columns: 1fr;
    gap: var(--spacing-sm);
    padding: var(--spacing-md);
  }

  .permission-table__head {
    display: none;
  }

  .permission-module,
  .permission-count {
    padding: 0;
    text-align: left;
  }

  .menu-permission-row {
    grid-template-columns: 28px minmax(180px, 1fr) 1fr 64px;
  }
}

@media (max-width: 760px) {
  .menu-permission-row {
    grid-template-columns: 28px minmax(0, 1fr);
  }

  .menu-permission-row > .permission-code,
  .menu-count {
    grid-column: 2;
    text-align: left;
  }

  .button-permission-grid {
    grid-template-columns: 1fr;
    margin-left: 0 !important;
  }
}

.tree-node-content {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding-right: 8px;
}

.tree-node-label {
  display: flex;
  align-items: center;
  gap: 6px;
}

.tree-node-count {
  margin-left: 4px;
  min-width: 20px;
  padding: 1px 6px;
  border-radius: 10px;
  background: #edf2f7;
  color: var(--color-muted-text);
  font-size: 11px;
  text-align: center;
}

.tree-node-actions {
  display: flex;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.2s;
}

:deep(.el-tree-node__content:hover) .tree-node-actions,
:deep(.el-tree-node__content:hover) .tree-node-actions {
  opacity: 1;
}

:deep(.el-tree-node__content) {
  height: 36px;
}

:deep(.el-tree-node__label) {
  flex: 1;
  display: flex;
  align-items: center;
}

.drag-handle {
  flex-shrink: 0;
  cursor: grab;
  color: var(--color-text-placeholder);
  transition: color 0.2s;

  &:hover {
    color: var(--color-accent);
  }

  &:active {
    cursor: grabbing;
  }
}

.drag-handle--group {
  font-size: 14px;
  margin-right: var(--spacing-xs);
}

.sortable-ghost {
  opacity: 0.4;
  background: var(--color-info-light);
}

.sortable-chosen {
  border-color: var(--color-accent);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
  cursor: grabbing !important;
}

.sortable-chosen.role-item {
  cursor: grabbing !important;
}
</style>
