<template>
  <el-dialog
    v-model="visible"
    title="高级权限"
    width="920px"
    :close-on-click-modal="false"
    class="permission-manage-dialog"
    destroy-on-close
    @close="handleDialogClose"
  >
    <div v-if="loading" class="perm-loading">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>加载中...</span>
    </div>

    <div v-else class="perm-body" :class="{ 'perm-body--members': panelMode === 'members' }">
      <!-- 左侧：角色列表 -->
      <aside class="perm-sidebar">
        <div class="perm-sidebar__section">
          <div class="perm-sidebar__label">系统角色</div>
          <div
            v-for="role in systemRoles"
            :key="role.systemRoleCode"
            class="perm-role-item"
            :class="{ active: isActiveRole(role) }"
            @click="selectRole(role)"
          >
            <el-icon class="perm-role-item__icon"><component :is="getRoleIcon(role.systemRoleCode!)" /></el-icon>
            <span class="perm-role-item__name">{{ role.name }}</span>
            <span class="perm-role-item__badge">{{ role.members?.length || 0 }}</span>
          </div>
        </div>

        <div class="perm-sidebar__section">
          <div class="perm-sidebar__label">自定义角色</div>
          <div
            v-for="role in customRoles"
            :key="role.customRoleId"
            class="perm-role-item perm-role-item--custom"
            :class="{ active: isActiveRole(role) }"
            @click="selectRole(role)"
          >
            <el-icon class="perm-role-item__icon"><User /></el-icon>
            <span class="perm-role-item__name" :title="role.name">{{ role.name }}</span>
            <span class="perm-role-item__badge">{{ role.members?.length || 0 }}</span>
            <el-dropdown
              class="perm-role-item__more"
              trigger="click"
              placement="bottom-end"
              @command="(cmd: string) => handleRoleCommand(cmd, role)"
            >
              <span class="perm-role-item__more-btn" title="更多操作" @click.stop>
                <el-icon><MoreFilled /></el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="rename">重命名</el-dropdown-item>
                  <el-dropdown-item command="delete" divided>删除角色</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
          <div class="perm-role-add" @click="showAddRole = true">
            <el-icon><Plus /></el-icon>
            <span>添加角色</span>
          </div>
        </div>
      </aside>

      <!-- 单个角色的成员维护页（参考设计稿「X包含的成员」）：
           角色成员是全局的，这里增删对该多维表格全局生效 -->
      <main v-if="panelMode === 'members' && selectedRole" class="perm-panel perm-member-page">
        <div class="perm-member-page__header">
          <button class="perm-member-page__back" title="返回权限设计" @click="closeMemberPage">
            <el-icon><ArrowLeft /></el-icon>
          </button>
          <span class="perm-member-page__title">{{ selectedRole.name }}包含的成员</span>
          <el-input
            v-model="memberSearch"
            size="small"
            class="perm-member-page__search"
            placeholder="搜索成员"
            clearable
            :prefix-icon="Search"
          />
          <el-tooltip content="添加成员" placement="top">
            <button class="perm-member-page__add" @click="openAddMember">
              <el-icon><Plus /></el-icon>
            </button>
          </el-tooltip>
        </div>

        <div class="perm-member-page__list">
          <div v-for="member in filteredMembers" :key="memberKey(member)" class="perm-member-row">
            <el-avatar
              :size="32"
              :src="member.memberAvatar || undefined"
              :style="{ backgroundColor: avatarColor(memberLabel(member)) }"
            >
              {{ memberInitial(member) }}
            </el-avatar>
            <span class="perm-member-row__name">{{ memberLabel(member) }}</span>

            <!-- 成员所属角色：悬停展开角色清单（系统角色至多 1 个 + 自定义角色可多个） -->
            <el-popover
              placement="left"
              trigger="hover"
              :width="180"
              popper-class="perm-member-roles-popover"
            >
              <template #reference>
                <span class="perm-member-row__roles">属于{{ roleCountOf(member) }}个角色</span>
              </template>
              <div class="member-roles-pop">
                <div class="member-roles-pop__title">成员所在角色：</div>
                <div
                  v-for="r in rolesOfMember(member)"
                  :key="r.key"
                  class="member-roles-pop__item"
                >
                  <span class="member-roles-pop__dot" :class="`is-${r.type}`" />{{ r.label }}
                </div>
              </div>
            </el-popover>

            <el-tooltip
              :content="isRemovableMember(member) ? '移出该角色' : '不可移除（所有者）'"
              placement="top"
            >
              <button
                class="perm-member-row__remove"
                :class="{ 'is-disabled': !isRemovableMember(member) }"
                :disabled="!isRemovableMember(member)"
                @click="handleRemoveMember(member)"
              >
                <el-icon><Delete /></el-icon>
              </button>
            </el-tooltip>
          </div>

          <div v-if="!filteredMembers.length" class="perm-member-page__empty">
            {{ memberSearch.trim() ? '没有匹配的成员' : '该角色下暂无成员' }}
          </div>
        </div>
      </main>

      <template v-else>
      <!-- 中间：角色成员 + 权限树 -->
      <main class="perm-panel">
        <!-- 角色成员：头像缩略图（有头像用头像，否则用姓名首字） -->
        <div class="perm-members">
          <template v-if="selectedRole">
            <span class="perm-members__label">成员</span>
            <div class="perm-members__list">
              <el-tooltip
                v-for="member in selectedRole.members || []"
                :key="memberKey(member)"
                :content="
                  isRemovableMember(member) ? `${memberLabel(member)}（点击移除）` : memberLabel(member)
                "
                placement="top"
              >
                <span
                  class="perm-members__item"
                  :class="{ 'is-removable': isRemovableMember(member) }"
                  @click="handleRemoveMember(member)"
                >
                  <el-avatar
                    :size="26"
                    :src="member.memberAvatar || undefined"
                    :style="{ backgroundColor: avatarColor(memberLabel(member)) }"
                    class="perm-members__avatar"
                  >
                    {{ memberInitial(member) }}
                  </el-avatar>
                </span>
              </el-tooltip>

              <el-tooltip
                :content="isSystemRole(selectedRole) ? '添加成员（系统角色）' : '添加成员'"
                placement="top"
              >
                <span class="perm-members__add" @click="openAddMember">
                  <el-icon><Plus /></el-icon>
                </span>
              </el-tooltip>

              <span v-if="!selectedRole.members?.length" class="perm-members__hint">暂无成员</span>
            </div>

            <!-- 进入该角色的成员维护页（增删成员对全局生效） -->
            <button class="perm-members__manage" @click="openMemberPage">
              成员管理
              <el-icon><ArrowRight /></el-icon>
            </button>
          </template>
          <span v-else class="perm-members__hint">请选择角色</span>
        </div>

        <div class="perm-panel__toolbar">
          <span>全部设置为：</span>
          <el-select v-model="bulkLevel" size="small" style="width: 120px" @change="handleBulkSet">
            <el-option label="请选择" value="" />
            <el-option label="完全权限" value="full" />
            <el-option label="可编辑" value="edit" />
            <el-option label="可查看" value="view" />
            <el-option label="无权限" value="none" />
          </el-select>
        </div>

        <div class="perm-panel__search">
          <el-input
            v-model="searchKeyword"
            size="small"
            placeholder="搜索数据表"
            clearable
            :prefix-icon="Search"
          />
        </div>

        <div class="perm-tree">
          <div
            v-for="group in filteredGroups"
            :key="group.name"
            class="perm-tree__group"
          >
            <div class="perm-tree__group-header" @click="toggleGroup(group.name)">
              <el-icon class="perm-tree__chevron" :class="{ collapsed: collapsedGroups.has(group.name) }">
                <ArrowDown />
              </el-icon>
              <el-icon class="perm-tree__folder"><Folder /></el-icon>
              <span>{{ group.name }}</span>
            </div>
            <div v-show="!collapsedGroups.has(group.name)" class="perm-tree__children">
              <div
                v-for="table in group.tables"
                :key="table.id"
                class="perm-tree__table"
                :class="{ selected: selectedTable?.id === table.id }"
                @click="selectTable(table)"
              >
                <el-icon class="perm-tree__table-icon"><Document /></el-icon>
                <span class="perm-tree__table-name">{{ table.name }}</span>
                <span
                  class="perm-tree__badge"
                  :class="`perm-tree__badge--${getTablePermission(table.id)}`"
                >
                  {{ permLabel(getTablePermission(table.id)) }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </main>

      <!-- 右侧：权限设置 -->
      <aside class="perm-settings">
        <template v-if="selectedRole && selectedTable">
          <div class="perm-settings__title">数据表权限</div>
          <div class="perm-settings__subtitle">
            {{ selectedRole.name }} · {{ selectedTable.name }}
          </div>

          <div
            v-for="level in permissionLevels"
            :key="level.value"
            class="perm-option"
            :class="{ selected: currentPermission === level.value }"
            @click="setPermission(level.value)"
          >
            <div class="perm-option__radio">
              <div v-if="currentPermission === level.value" class="perm-option__radio-dot" />
            </div>
            <div class="perm-option__content">
              <div class="perm-option__name">{{ level.label }}</div>
              <div class="perm-option__desc">{{ level.desc }}</div>
            </div>
          </div>

          <!-- 字段权限：在表权限之下再收窄到单个字段（仅表权限不为「无权限」时可用） -->
          <template v-if="currentPermission !== 'none'">
            <div class="perm-settings__divider" />
            <div class="perm-settings__title perm-settings__title--sub">字段权限</div>
            <div v-if="fieldPermLoading" class="perm-fields__loading">
              <el-icon class="is-loading"><Loading /></el-icon>
            </div>
            <div v-else-if="!fieldList.length" class="perm-fields__empty">该数据表暂无字段</div>
            <div v-else class="perm-fields">
              <div v-for="field in fieldList" :key="field.id" class="perm-field-row">
                <span class="perm-field-row__name" :title="field.name">{{ field.name }}</span>
                <el-radio-group
                  :model-value="getFieldPermission(field.id)"
                  size="small"
                  @update:model-value="(v: any) => setFieldPermission(field.id, v as FieldPermissionLevel)"
                >
                  <el-radio-button value="editable">可编辑</el-radio-button>
                  <el-radio-button value="readonly">只读</el-radio-button>
                  <el-radio-button value="hidden">隐藏</el-radio-button>
                </el-radio-group>
              </div>
            </div>
          </template>
        </template>

        <div v-else class="perm-settings__empty">
          <el-icon :size="48" color="#CBD5E1"><Document /></el-icon>
          <span>请选择数据表</span>
        </div>
      </aside>
      </template>
    </div>

    <!-- 底部操作栏 -->
    <template #footer>
      <div class="perm-footer">
        <span v-if="hasChanges" class="perm-footer__hint">
          <el-icon><InfoFilled /></el-icon>
          有未保存的修改
        </span>
        <div class="perm-footer__spacer" />
        <el-button @click="handleCancel">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="!hasChanges" @click="handleSave">
          保存
        </el-button>
      </div>
    </template>

    <!-- 添加角色弹窗 -->
    <el-dialog v-model="showAddRole" title="添加自定义角色" width="360px" append-to-body>
      <el-input v-model="newRoleName" placeholder="请输入角色名称" maxlength="20" />
      <template #footer>
        <el-button size="small" @click="showAddRole = false">取消</el-button>
        <el-button size="small" type="primary" @click="handleAddRole">确定</el-button>
      </template>
    </el-dialog>

    <!-- 添加成员弹窗 -->
    <el-dialog v-model="showAddMember" title="添加角色成员" width="420px" append-to-body>
      <div class="member-add-dialog">
        <div class="member-add-dialog__role">
          角色：<strong>{{ selectedRole?.name }}</strong>
        </div>
        <el-input
          v-model="pickKeyword"
          size="small"
          placeholder="搜索用户"
          clearable
          :prefix-icon="Search"
        />
        <!-- 用内联勾选列表而不是下拉：下拉浮层会盖住底部「确定」，
             误点会把选中项又切掉；勾选列表也更贴合"勾选多个用户"的交互 -->
        <div v-loading="memberUsersLoading" class="member-add-dialog__list">
          <el-checkbox
            v-for="user in filteredAvailableUsers"
            :key="user.id"
            class="member-pick"
            :class="{ 'is-checked': newMemberUserIds.includes(user.id) }"
            :model-value="newMemberUserIds.includes(user.id)"
            @change="toggleMemberPick(user.id)"
          >
            <el-avatar
              :size="24"
              :src="user.avatar || undefined"
              :style="{ backgroundColor: avatarColor(user.realName || user.username) }"
            >
              {{ (user.realName || user.username || '?').charAt(0) }}
            </el-avatar>
            <span class="member-pick__name">{{ user.realName || user.username }}</span>
            <span class="member-pick__username">{{ user.username }}</span>
            <!-- 系统角色一人只有一个：标出用户当前角色，避免误以为能并列加入 -->
            <span v-if="memberRoleByUserId.get(user.id)" class="member-pick__role">
              当前：{{ memberRoleByUserId.get(user.id)!.label }}
            </span>
          </el-checkbox>
          <div
            v-if="!memberUsersLoading && !filteredAvailableUsers.length"
            class="member-add-dialog__empty"
          >
            {{ pickKeyword.trim() ? '没有匹配的用户' : '没有可添加的用户' }}
          </div>
        </div>
        <div v-if="selectedSystemRoleCode" class="member-add-dialog__note">
          系统角色一人只有一个。勾选的用户若已有角色，会被从原角色
          <strong>调整为「{{ selectedRole?.name }}」</strong>（可一次勾选多人）。
          <template v-if="selectedSystemRoleCode === 'owner'">
            <br />
            <strong>注意：</strong>设为所有者将转移所有权，当前所有者会被降级为管理员，因此一次只能选一名用户。
          </template>
        </div>
      </div>
      <template #footer>
        <el-button size="small" @click="showAddMember = false">取消</el-button>
        <el-button
          size="small"
          type="primary"
          :loading="addingMember"
          :disabled="!newMemberUserIds.length"
          @click="handleAddMember"
        >确定</el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import {
  Plus,
  User,
  Search,
  ArrowDown,
  ArrowLeft,
  ArrowRight,
  Folder,
  Document,
  Delete,
  Loading,
  Lock,
  EditPen,
  View,
  CircleClose,
  InfoFilled,
  MoreFilled,
} from '@element-plus/icons-vue'
import {
  listBaseRoles,
  createCustomRole,
  updateCustomRole,
  deleteCustomRole,
  addRoleMember,
  removeRoleMember,
  batchSaveRolePermissions,
  listFieldPermissions,
  batchSaveFieldPermissions,
  listFields,
  addBaseMember,
  removeBaseMember,
} from '@/api/modules/bitable'
import { getFilterUsers } from '@/api/modules/user'
import type {
  BitableBaseRoleVO,
  BitableBaseRoleMember,
  BitableBaseRolePermissionDTO,
  BitableField,
  BitableTable,
  BitableTableGroup,
  FieldPermissionChange,
  FieldPermissionLevel,
  MemberRole,
  PermissionType,
  PermissionLevel,
} from '@/types/bitable'
import { ElMessage, ElMessageBox } from 'element-plus'
import { resolveErrorMessage } from '@/utils/error'

const props = defineProps<{
  modelValue: boolean
  baseId: number
  tables: BitableTable[]
  tableGroups: BitableTableGroup[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const loading = ref(false)
const saving = ref(false)
// 目前只剩「数据权限」一种类型（「自动化权限」tab 已移除）。
// 保留 ref 是为了统一各处取 permissionType 的口径，将来要加新类型时不必改调用点。
const activeTab = ref<PermissionType>('data')
const roles = ref<BitableBaseRoleVO[]>([])
const selectedRole = ref<BitableBaseRoleVO | null>(null)
const selectedTable = ref<BitableTable | null>(null)
const searchKeyword = ref('')
const collapsedGroups = ref<Set<string>>(new Set())
const bulkLevel = ref('')
const showAddRole = ref(false)
const newRoleName = ref('')

// ========== 角色成员 ==========
// 中间面板两种形态：'permission' = 角色 × 数据表的权限设计；'members' = 该角色的成员维护页
const panelMode = ref<'permission' | 'members'>('permission')
const memberSearch = ref('')
const showAddMember = ref(false)
const addingMember = ref(false)
// 一次可勾选多个用户加入同一角色（系统角色一人只有一个，勾选多人 = 逐个调整过去）
const newMemberUserIds = ref<number[]>([])
// 添加成员弹窗里的搜索关键词
const pickKeyword = ref('')
const memberUsers = ref<Array<{ id: number; username: string; realName: string; avatar?: string | null }>>([])
const memberUsersLoaded = ref(false)
const memberUsersLoading = ref(false)

// ========== 本地缓存未保存的改动 ==========
// key = `${roleType}:${systemRoleCode||customRoleId}:${tableId}:${permissionType}`
const pendingChanges = ref<Map<string, PermissionLevel>>(new Map())

// ========== 字段级权限 ==========
// 当前选中表的字段列表
const fieldList = ref<BitableField[]>([])
const fieldPermLoading = ref(false)
// 服务端已有的字段权限：key = `${roleKey}:${fieldId}`
const fieldPermBase = ref<Map<string, FieldPermissionLevel>>(new Map())
// 本次未保存的字段权限改动：key = `${roleKey}:${fieldId}`，值可为 'editable'（表示恢复默认）
const fieldPermPending = ref<Map<string, FieldPermissionLevel>>(new Map())

const systemRoles = computed(() => roles.value.filter(r => r.roleType === 'system'))
const customRoles = computed(() => roles.value.filter(r => r.roleType === 'custom'))

const hasChanges = computed(() => pendingChanges.value.size > 0 || fieldPermPending.value.size > 0)

/** 系统角色（所有者/管理员/编辑者/评论者/只读）与自定义角色都能在本弹窗直接维护成员 */
function isSystemRole(role: BitableBaseRoleVO | null) {
  return !!role && role.roleType === 'system' && !!role.systemRoleCode
}

const canManageMembers = computed(() => !!selectedRole.value)

/** 当前选中的系统角色 code；自定义角色为 null */
const selectedSystemRoleCode = computed(() =>
  isSystemRole(selectedRole.value) ? selectedRole.value!.systemRoleCode! : null,
)

/**
 * 系统角色成员存在 bitable_base_members，**一个人只能有一个系统角色**。
 * 所以"往管理员里加人"本质是把他的系统角色改成管理员（不是并列新增）。
 * 这里建 userId → 当前系统角色的映射，用于在选人时提示"将从 X 调整为 Y"。
 */
const memberRoleByUserId = computed(() => {
  const map = new Map<number, { code: string; label: string }>()
  for (const role of roles.value) {
    if (role.roleType !== 'system' || !role.systemRoleCode) continue
    for (const m of role.members || []) {
      if (m.memberType === 'user' && m.memberId != null) {
        map.set(m.memberId, { code: role.systemRoleCode, label: role.name })
      }
    }
  }
  return map
})

/**
 * userId → 该用户所属的全部角色（系统角色至多 1 个 + 自定义角色可多个）。
 * 用于成员维护页展示「属于N个角色」以及悬停时的角色清单。
 */
const rolesByUserId = computed(() => {
  const map = new Map<number, Array<{ key: string; label: string; type: 'system' | 'custom' }>>()
  for (const role of roles.value) {
    const key = `${role.roleType}:${role.systemRoleCode ?? role.customRoleId}`
    for (const m of role.members || []) {
      if (m.memberType !== 'user' || m.memberId == null) continue
      const list = map.get(m.memberId) || []
      list.push({ key, label: role.name, type: role.roleType })
      map.set(m.memberId, list)
    }
  }
  return map
})

/** 某个成员所属的角色清单（部门成员没有 user 身份，返回空数组） */
function rolesOfMember(member: BitableBaseRoleMember) {
  if (member.memberType !== 'user' || member.memberId == null) return []
  return rolesByUserId.value.get(member.memberId) || []
}

function roleCountOf(member: BitableBaseRoleMember) {
  return rolesOfMember(member).length
}

/** 成员维护页的搜索过滤 */
const filteredMembers = computed(() => {
  const kw = memberSearch.value.trim().toLowerCase()
  const list = selectedRole.value?.members || []
  if (!kw) return list
  return list.filter(m => memberLabel(m).toLowerCase().includes(kw))
})

function openMemberPage() {
  if (!selectedRole.value) return
  memberSearch.value = ''
  panelMode.value = 'members'
}

function closeMemberPage() {
  memberSearch.value = ''
  panelMode.value = 'permission'
}

/** 候选用户：排除已在该角色中的成员，避免重复添加 */
const availableMemberUsers = computed(() => {
  const existing = new Set((selectedRole.value?.members || []).map(m => m.memberId))
  return memberUsers.value.filter(u => !existing.has(u.id))
})

/** 候选用户再按搜索词过滤（添加成员弹窗的内联勾选列表） */
const filteredAvailableUsers = computed(() => {
  const kw = pickKeyword.value.trim().toLowerCase()
  if (!kw) return availableMemberUsers.value
  return availableMemberUsers.value.filter(u =>
    `${u.realName || ''}${u.username || ''}`.toLowerCase().includes(kw),
  )
})

/** 勾选 / 取消勾选一个候选用户 */
function toggleMemberPick(userId: number) {
  const idx = newMemberUserIds.value.indexOf(userId)
  if (idx >= 0) newMemberUserIds.value.splice(idx, 1)
  else newMemberUserIds.value.push(userId)
}

const filteredGroups = computed(() => {
  const kw = searchKeyword.value.trim().toLowerCase()
  const tableList = props.tables

  const groupMap = new Map<string, BitableTable[]>()
  const tablesByGroup = new Map<number | null, BitableTable[]>()
  for (const t of tableList) {
    const gid = t.groupId ?? null
    if (!tablesByGroup.has(gid)) tablesByGroup.set(gid, [])
    tablesByGroup.get(gid)!.push(t)
  }

  const build = (groups: BitableTableGroup[], parentName?: string) => {
    for (const g of groups) {
      const name = parentName ? `${parentName} / ${g.name}` : g.name
      const tables = tablesByGroup.get(g.id) || []
      if (g.children?.length) {
        build(g.children, name)
      }
      if (tables.length || !g.children?.length) {
        groupMap.set(name, tables)
      }
    }
  }

  const rootGroups = props.tableGroups.filter(g => g.parentId == null)
  build(rootGroups)

  const ungrouped = tablesByGroup.get(null) || []
  if (ungrouped.length) {
    groupMap.set('未分组', ungrouped)
  }

  const result: { name: string; tables: BitableTable[] }[] = []
  for (const [name, tables] of groupMap) {
    const filtered = kw
      ? tables.filter(t => t.name.toLowerCase().includes(kw))
      : tables
    if (!kw || filtered.length > 0 || name.toLowerCase().includes(kw)) {
      result.push({ name, tables: filtered })
    }
  }
  return result
})

const permissionLevels = [
  { value: 'full' as PermissionLevel, label: '完全权限', desc: '可修改表结构、配置字段，可编辑记录' },
  { value: 'edit' as PermissionLevel, label: '编辑', desc: '可添加、编辑记录，可查看' },
  { value: 'view' as PermissionLevel, label: '可查看', desc: '仅可查看' },
  { value: 'none' as PermissionLevel, label: '无权限', desc: '无任何权限' },
].map(l => ({ ...l, label: l.label === '编辑' ? '可编辑' : l.label }))

function makeChangeKey(role: BitableBaseRoleVO, tableId: number, permType: PermissionType): string {
  const roleId = role.roleType === 'system' ? role.systemRoleCode : String(role.customRoleId)
  return `${role.roleType}:${roleId}:${tableId}:${permType}`
}

function getRoleIcon(code: string) {
  switch (code) {
    case 'owner': return Lock
    case 'admin': return Lock
    case 'editor': return EditPen
    case 'commenter': return View
    case 'viewer': return View
    default: return User
  }
}

function permLabel(level: string) {
  const map: Record<string, string> = { full: '完全权限', edit: '可编辑', view: '可查看', none: '无权限' }
  return map[level] || level
}

/* ---------------- 成员展示 ---------------- */

function memberKey(member: BitableBaseRoleMember) {
  return `${member.memberType}:${member.memberId}`
}

/** 成员显示名：后端已解析；兜底用「用户 #id」避免出现空白 */
function memberLabel(member: BitableBaseRoleMember) {
  if (member.memberName) return member.memberName
  return member.memberType === 'dept' ? `部门 #${member.memberId}` : `用户 #${member.memberId}`
}

/** 头像占位：取姓名首字（中文取第一个字，英文取首字母大写） */
function memberInitial(member: BitableBaseRoleMember) {
  const name = memberLabel(member).trim()
  return name ? name.charAt(0).toUpperCase() : '?'
}

const AVATAR_COLORS = ['#2563EB', '#7C3AED', '#DB2777', '#EA580C', '#0891B2', '#059669', '#4F46E5', '#B45309']

/** 依据名称生成稳定的头像底色，避免一片灰 */
function avatarColor(name: string) {
  let hash = 0
  for (let i = 0; i < name.length; i++) {
    hash = (hash * 31 + name.charCodeAt(i)) % 1000000
  }
  return AVATAR_COLORS[hash % AVATAR_COLORS.length]
}

/**
 * 成员能否被移除。
 * 所有者不能被移除（后端 removeMember 会拒绝），需先把他人设为所有者来转移所有权。
 */
function isRemovableMember(member: BitableBaseRoleMember) {
  if (!canManageMembers.value || member.memberId == null) return false
  if (isSystemRole(selectedRole.value) && selectedRole.value!.systemRoleCode === 'owner') {
    return false
  }
  return true
}

function isActiveRole(role: BitableBaseRoleVO) {
  if (!selectedRole.value) return false
  if (role.roleType === 'system') {
    return selectedRole.value.roleType === 'system' && selectedRole.value.systemRoleCode === role.systemRoleCode
  }
  return selectedRole.value.roleType === 'custom' && selectedRole.value.customRoleId === role.customRoleId
}

/** 获取某张表的当前权限（优先看 pendingChanges，再看原始 roles） */
function getTablePermission(tableId: number): PermissionLevel {
  if (!selectedRole.value) return 'none'
  const key = makeChangeKey(selectedRole.value, tableId, activeTab.value)
  if (pendingChanges.value.has(key)) {
    return pendingChanges.value.get(key)!
  }
  const perm = selectedRole.value.permissions?.find(
    p => p.tableId === tableId && p.permissionType === activeTab.value
  )
  return (perm?.permissionLevel || 'none') as PermissionLevel
}

const currentPermission = computed(() => {
  if (!selectedTable.value) return 'none'
  return getTablePermission(selectedTable.value.id)
})

async function loadRoles() {
  loading.value = true
  try {
    roles.value = await listBaseRoles(props.baseId, activeTab.value)
  } catch (e) {
    ElMessage.error('加载权限配置失败')
  } finally {
    loading.value = false
  }
}

/**
 * 重新拉取角色后，把 selectedRole 指向新列表中的同一角色。
 * <p>否则 selectedRole 仍引用旧对象，成员列表与权限徽标都不会刷新。
 */
function reselectRole(prev: BitableBaseRoleVO | null) {
  if (!prev) {
    selectedRole.value = null
    return
  }
  const match = roles.value.find(r =>
    prev.roleType === 'system'
      ? r.roleType === 'system' && r.systemRoleCode === prev.systemRoleCode
      : r.roleType === 'custom' && r.customRoleId === prev.customRoleId,
  )
  selectedRole.value = match ?? null
}

/** 重新加载角色并保持当前选中项 */
async function refreshRoles() {
  const prev = selectedRole.value
  await loadRoles()
  reselectRole(prev)
}

function selectRole(role: BitableBaseRoleVO) {
  selectedRole.value = role
  selectedTable.value = null
  // 切换角色时保留已加载的字段列表（同一张表复用），但丢弃上一角色的未保存字段改动
  fieldPermPending.value = new Map()
}

function selectTable(table: BitableTable) {
  selectedTable.value = table
  void loadFieldPermissions(table.id)
}

// ========== 字段级权限：读取 / 本地改动 / 提交 ==========

/** 字段权限的缓存键：角色 + 字段 */
function fieldPermKey(role: BitableBaseRoleVO, fieldId: number): string {
  const roleId = role.roleType === 'system' ? role.systemRoleCode : String(role.customRoleId)
  return `${role.roleType}:${roleId}:${fieldId}`
}

/** 加载所选数据表的字段列表与整个 Base 的字段权限配置 */
async function loadFieldPermissions(tableId: number) {
  fieldPermLoading.value = true
  try {
    const [fields, permissions] = await Promise.all([
      listFields(tableId),
      listFieldPermissions(props.baseId, tableId),
    ])
    fieldList.value = fields
    const base = new Map<string, FieldPermissionLevel>()
    for (const item of permissions || []) {
      const roleId = item.roleType === 'system' ? item.systemRoleCode : String(item.customRoleId)
      base.set(`${item.roleType}:${roleId}:${item.fieldId}`, item.permissionLevel)
    }
    fieldPermBase.value = base
    // 切表后丢弃上一张表的未保存字段改动，避免把 A 表的改动带到 B 表
    fieldPermPending.value = new Map()
  } catch (e) {
    fieldList.value = []
    fieldPermBase.value = new Map()
    ElMessage.error('加载字段权限失败')
  } finally {
    fieldPermLoading.value = false
  }
}

/** 某字段对当前选中角色的权限（优先未保存改动，其次服务端配置，默认可编辑） */
function getFieldPermission(fieldId: number): FieldPermissionLevel {
  if (!selectedRole.value) return 'editable'
  const key = fieldPermKey(selectedRole.value, fieldId)
  if (fieldPermPending.value.has(key)) {
    return fieldPermPending.value.get(key)!
  }
  return fieldPermBase.value.get(key) || 'editable'
}

/** 修改某字段的权限 —— 只写本地缓存，随「保存」一起提交 */
function setFieldPermission(fieldId: number, level: FieldPermissionLevel) {
  if (!selectedRole.value) return
  const key = fieldPermKey(selectedRole.value, fieldId)
  const next = new Map(fieldPermPending.value)
  // 改回服务端原值（无配置即 editable）时视为取消改动
  const original = fieldPermBase.value.get(key) || 'editable'
  if (level === original) {
    next.delete(key)
  } else {
    next.set(key, level)
  }
  fieldPermPending.value = next
}

function toggleGroup(name: string) {
  const next = new Set(collapsedGroups.value)
  if (next.has(name)) next.delete(name)
  else next.add(name)
  collapsedGroups.value = next
}

/** 选择权限级别 —— 只写本地缓存，不调 API */
function setPermission(level: PermissionLevel) {
  if (!selectedRole.value || !selectedTable.value) return
  const key = makeChangeKey(selectedRole.value, selectedTable.value.id, activeTab.value)
  const next = new Map(pendingChanges.value)
  // 如果选的和原始值一样，视为取消改动
  const original = selectedRole.value.permissions?.find(
    p => p.tableId === selectedTable.value!.id && p.permissionType === activeTab.value
  )
  const originalLevel = (original?.permissionLevel || 'none') as PermissionLevel
  if (level === originalLevel) {
    next.delete(key)
  } else {
    next.set(key, level)
  }
  pendingChanges.value = next
}

/** 批量设置 —— 同样只写本地缓存 */
function handleBulkSet(level: string) {
  if (!level || !selectedRole.value) {
    bulkLevel.value = ''
    return
  }
  const next = new Map(pendingChanges.value)
  for (const table of props.tables) {
    const key = makeChangeKey(selectedRole.value, table.id, activeTab.value)
    const original = selectedRole.value.permissions?.find(
      p => p.tableId === table.id && p.permissionType === activeTab.value
    )
    const originalLevel = (original?.permissionLevel || 'none') as PermissionLevel
    if (level === originalLevel) {
      next.delete(key)
    } else {
      next.set(key, level as PermissionLevel)
    }
  }
  pendingChanges.value = next
  bulkLevel.value = ''
  ElMessage.success('已批量设置，记得点击保存')
}

/** 保存 —— 一次性提交所有 pendingChanges（表权限 + 字段权限） */
async function handleSave() {
  if (!pendingChanges.value.size && !fieldPermPending.value.size) return
  saving.value = true
  try {
    if (pendingChanges.value.size) {
      const changes: BitableBaseRolePermissionDTO[] = []
      for (const [key, level] of pendingChanges.value) {
        const [roleType, roleId, tableIdStr, permType] = key.split(':')
        changes.push({
          baseId: props.baseId,
          roleType: roleType as 'system' | 'custom',
          systemRoleCode: roleType === 'system' ? roleId : undefined,
          customRoleId: roleType === 'custom' ? Number(roleId) : undefined,
          tableId: Number(tableIdStr),
          permissionType: permType as PermissionType,
          permissionLevel: level,
        })
      }
      await batchSaveRolePermissions(props.baseId, changes)
    }

    // 字段权限：只提交本次改动过的条目（editable 表示恢复默认）
    if (fieldPermPending.value.size && selectedTable.value) {
      const fieldChanges: FieldPermissionChange[] = []
      for (const [key, level] of fieldPermPending.value) {
        const [roleType, roleId, fieldIdStr] = key.split(':')
        fieldChanges.push({
          baseId: props.baseId,
          tableId: selectedTable.value.id,
          fieldId: Number(fieldIdStr),
          roleType: roleType as 'system' | 'custom',
          systemRoleCode: roleType === 'system' ? roleId : undefined,
          customRoleId: roleType === 'custom' ? Number(roleId) : undefined,
          permissionLevel: level,
        })
      }
      await batchSaveFieldPermissions(props.baseId, fieldChanges)
    }

    pendingChanges.value = new Map()
    fieldPermPending.value = new Map()
    ElMessage.success('权限保存成功')
    await refreshRoles()
    if (selectedTable.value) {
      await loadFieldPermissions(selectedTable.value.id)
    }
  } catch (e) {
    ElMessage.error('保存失败，请重试')
  } finally {
    saving.value = false
  }
}

/** 取消 / 关闭 */
async function handleCancel() {
  if (hasChanges.value) {
    try {
      await ElMessageBox.confirm('有未保存的权限修改，确定要放弃吗？', '提示', {
        confirmButtonText: '放弃',
        cancelButtonText: '继续编辑',
        type: 'warning',
      })
    } catch {
      return // 用户点了取消，留在当前页面
    }
  }
  visible.value = false
}

function handleDialogClose() {
  pendingChanges.value = new Map()
  fieldPermPending.value = new Map()
  showAddMember.value = false
}

async function handleAddRole() {
  const name = newRoleName.value.trim()
  if (!name) return
  try {
    await createCustomRole(props.baseId, { baseId: props.baseId, name })
    ElMessage.success('角色创建成功')
    showAddRole.value = false
    newRoleName.value = ''
    await loadRoles()
  } catch (e) {
    ElMessage.error('创建失败')
  }
}

/* ---------------- 自定义角色：重命名 / 删除 ---------------- */

function handleRoleCommand(command: string, role: BitableBaseRoleVO) {
  if (command === 'rename') handleRenameRole(role)
  else if (command === 'delete') handleDeleteRole(role)
}

async function handleRenameRole(role: BitableBaseRoleVO) {
  if (role.customRoleId == null) return
  try {
    const { value } = await ElMessageBox.prompt('', '重命名角色', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputValue: role.name,
      inputValidator: (v: string) => (v && v.trim() ? true : '角色名称不能为空'),
    })
    const name = (value || '').trim()
    if (!name || name === role.name) return
    await updateCustomRole(role.customRoleId, { name })
    ElMessage.success('角色已重命名')
    await refreshRoles()
  } catch (e: any) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error(resolveErrorMessage(e, '重命名失败'))
    }
  }
}

async function handleDeleteRole(role: BitableBaseRoleVO) {
  if (role.customRoleId == null) return
  try {
    await ElMessageBox.confirm(
      `确定删除角色「${role.name}」吗？该角色的权限配置与成员将一并移除。`,
      '删除确认',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await deleteCustomRole(role.customRoleId)
    ElMessage.success('角色已删除')
    const wasSelected = isActiveRole(role)
    await loadRoles()
    if (wasSelected) {
      selectedRole.value = null
      selectedTable.value = null
    } else {
      reselectRole(selectedRole.value)
    }
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '删除失败'))
  }
}

/* ---------------- 角色成员 ---------------- */

async function openAddMember() {
  if (!canManageMembers.value) return
  newMemberUserIds.value = []
  pickKeyword.value = ''
  showAddMember.value = true
  if (memberUsersLoaded.value) return
  memberUsersLoading.value = true
  try {
    const res: any = await getFilterUsers()
    const list = Array.isArray(res) ? res : (res?.data ?? [])
    memberUsers.value = Array.isArray(list) ? list : []
    memberUsersLoaded.value = true
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '加载用户列表失败'))
  } finally {
    memberUsersLoading.value = false
  }
}

/** 从已加载的用户列表里取显示名，用于成功提示 */
function memberNameOf(userId: number) {
  const u = memberUsers.value.find(x => x.id === userId)
  return u ? u.realName || u.username : `用户${userId}`
}

async function handleAddMember() {
  const role = selectedRole.value
  const userIds = [...newMemberUserIds.value]
  if (!role || !userIds.length) return

  // 系统角色：改的是 bitable_base_members 上的角色，一个人只能有一个，所以是"调整"而非并列新增
  if (isSystemRole(role)) {
    // 授予所有者 = 所有权转移（后端会把其他 owner 降级），一次只能选一个
    if (role.systemRoleCode === 'owner' && userIds.length > 1) {
      ElMessage.warning('所有者属于所有权转移，一次只能选择一名用户')
      return
    }
    addingMember.value = true
    try {
      let added = 0
      const adjusted: string[] = []
      for (const userId of userIds) {
        const prev = memberRoleByUserId.value.get(userId)
        if (prev && prev.code === role.systemRoleCode) continue
        await addBaseMember(props.baseId, { userId, role: role.systemRoleCode as MemberRole })
        if (prev) adjusted.push(`${memberNameOf(userId)}（原「${prev.label}」）`)
        else added++
      }
      const parts: string[] = []
      if (added) parts.push(`已加入 ${added} 人`)
      if (adjusted.length) parts.push(`已调整 ${adjusted.length} 人：${adjusted.join('、')}`)
      ElMessage.success(parts.length ? parts.join('；') : `所选用户已在「${role.name}」中`)
      showAddMember.value = false
      newMemberUserIds.value = []
      await refreshRoles()
    } catch (e: any) {
      ElMessage.error(resolveErrorMessage(e, '添加成员失败'))
    } finally {
      addingMember.value = false
    }
    return
  }

  // 自定义角色：独立成员表，同一个用户可以同时属于多个自定义角色
  if (role.customRoleId == null) return
  addingMember.value = true
  try {
    for (const userId of userIds) {
      await addRoleMember(role.customRoleId, {
        roleId: role.customRoleId,
        memberType: 'user',
        memberId: userId,
      })
    }
    ElMessage.success(userIds.length > 1 ? `已添加 ${userIds.length} 名成员` : '成员已添加')
    showAddMember.value = false
    newMemberUserIds.value = []
    await refreshRoles()
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '添加成员失败'))
  } finally {
    addingMember.value = false
  }
}

async function handleRemoveMember(member: BitableBaseRoleMember) {
  const role = selectedRole.value
  if (!canManageMembers.value || !role) return

  // 系统角色：移出 = 删掉 bitable_base_members 那一行，会失去该 Base 的全部访问权限
  if (isSystemRole(role)) {
    if (role.systemRoleCode === 'owner') {
      ElMessage.warning('不能移除所有者，请先把其他人设为所有者以转移所有权')
      return
    }
    try {
      await ElMessageBox.confirm(
        `「${memberLabel(member)}」将失去该多维表格的全部访问权限，确定移除吗？`,
        '移除成员',
        { confirmButtonText: '移除', cancelButtonText: '取消', type: 'warning' },
      )
    } catch {
      return
    }
    try {
      await removeBaseMember(props.baseId, member.memberId)
      ElMessage.success('已移除成员')
      await refreshRoles()
    } catch (e: any) {
      ElMessage.error(resolveErrorMessage(e, '移除成员失败'))
    }
    return
  }

  if (role.customRoleId == null) return
  try {
    await ElMessageBox.confirm(`确定把「${memberLabel(member)}」移出角色「${role.name}」吗？`, '移除确认', {
      confirmButtonText: '移除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await removeRoleMember(role.customRoleId, member.memberType, member.memberId)
    ElMessage.success('已移除成员')
    await refreshRoles()
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '移除成员失败'))
  }
}

watch(() => props.modelValue, (v) => {
  if (v) {
    loadRoles()
    selectedRole.value = null
    selectedTable.value = null
    pendingChanges.value = new Map()
    memberUsersLoaded.value = false
    memberUsers.value = []
    // 每次打开都回到权限设计视图，避免上次停在成员页
    panelMode.value = 'permission'
    memberSearch.value = ''
  }
})
</script>

<style scoped lang="scss">
.perm-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 60px;
  color: var(--color-text-secondary);
}

.perm-body {
  display: flex;
  height: 520px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  overflow: hidden;
}

.perm-sidebar {
  width: 190px;
  background: var(--color-surface);
  border-right: 1px solid var(--color-border);
  overflow-y: auto;
  flex-shrink: 0;

  &__section {
    margin-bottom: 8px;
  }

  &__label {
    font-size: 12px;
    font-weight: 600;
    color: var(--el-color-primary);
    padding: 12px 12px 6px;
    letter-spacing: 0.5px;
  }
}

.perm-role-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 8px 8px 12px;
  cursor: pointer;
  border-left: 3px solid transparent;
  font-size: 13px;
  transition: background 0.15s;

  &:hover {
    background: var(--color-surface-alt);
  }

  &.active {
    background: var(--el-color-primary-light-9);
    border-left-color: var(--el-color-primary);
    color: var(--el-color-primary);
    font-weight: 500;
  }

  &__icon {
    font-size: 14px;
    color: var(--color-text-secondary);
    flex-shrink: 0;
  }

  &.active &__icon {
    color: var(--el-color-primary);
  }

  &__name {
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__badge {
    font-size: 11px;
    color: var(--color-text-secondary);
    background: var(--color-surface-alt);
    padding: 1px 6px;
    border-radius: 10px;
    flex-shrink: 0;
  }

  /* 自定义角色的「更多操作」入口，悬浮时出现并让位给徽标 */
  &__more {
    display: none;
    flex-shrink: 0;
  }

  &--custom:hover &__badge {
    display: none;
  }

  &--custom:hover &__more {
    display: flex;
  }

  &__more-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 18px;
    height: 18px;
    border-radius: 3px;
    color: var(--color-text-secondary);
    cursor: pointer;

    &:hover {
      background: rgba(15, 23, 42, 0.09);
      color: var(--color-text-primary);
    }
  }
}

.perm-role-add {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  font-size: 13px;
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: color 0.15s;

  &:hover {
    color: var(--el-color-primary);
  }
}

.perm-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: var(--color-surface);
  overflow: hidden;
  min-width: 0;

  &__toolbar {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 12px;
    font-size: 13px;
    color: var(--color-text-secondary);
    border-bottom: 1px solid var(--color-border-light);
  }

  &__search {
    padding: 8px 12px;
    border-bottom: 1px solid var(--color-border-light);
  }
}

/* 角色成员条：头像缩略图（有头像用头像，否则用姓名首字） */
.perm-members {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 40px;
  padding: 6px 12px;
  border-bottom: 1px solid var(--color-border-light);
  flex-wrap: wrap;

  &__label {
    font-size: 12px;
    color: var(--color-text-secondary);
    flex-shrink: 0;
  }

  &__list {
    display: flex;
    align-items: center;
    gap: 4px;
    flex-wrap: wrap;
    min-width: 0;
  }

  &__item {
    display: inline-flex;
    line-height: 0;
    border-radius: 50%;
    transition: transform 0.12s;

    &.is-removable {
      cursor: pointer;

      &:hover {
        transform: translateY(-1px);
        box-shadow: 0 0 0 2px var(--el-color-danger-light-5);
      }
    }
  }

  &__avatar {
    font-size: 12px;
    color: #fff;
    border: 1px solid rgba(255, 255, 255, 0.7);
  }

  &__add {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 26px;
    height: 26px;
    border: 1px dashed var(--color-border);
    border-radius: 50%;
    color: var(--color-text-secondary);
    cursor: pointer;
    transition: all 0.15s;

    &:hover {
      border-color: var(--el-color-primary);
      color: var(--el-color-primary);
      background: var(--el-color-primary-light-9);
    }
  }

  &__hint {
    font-size: 12px;
    color: var(--color-text-placeholder, var(--color-text-secondary));
  }

  &__manage {
    display: inline-flex;
    align-items: center;
    gap: 2px;
    margin-left: auto;
    padding: 2px 6px;
    border: none;
    background: none;
    border-radius: 4px;
    font-size: 12px;
    color: var(--color-text-secondary);
    cursor: pointer;
    flex-shrink: 0;
    transition: all 0.15s;

    &:hover {
      color: var(--el-color-primary);
      background: var(--el-color-primary-light-9);
    }
  }
}

.perm-tree {
  flex: 1;
  overflow-y: auto;
  padding: 4px 0;

  &__group-header {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 6px 12px;
    cursor: pointer;
    font-size: 13px;
    font-weight: 500;
    user-select: none;

    &:hover {
      background: var(--color-surface-alt);
    }
  }

  &__chevron {
    font-size: 12px;
    color: var(--color-text-secondary);
    transition: transform 0.2s;

    &.collapsed {
      transform: rotate(-90deg);
    }
  }

  &__folder {
    font-size: 14px;
    color: var(--el-color-warning);
  }

  &__table {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 6px 12px 6px 32px;
    cursor: pointer;
    font-size: 13px;
    transition: background 0.15s;

    &:hover {
      background: var(--color-surface-alt);
    }

    &.selected {
      background: var(--el-color-primary-light-9);
      color: var(--el-color-primary);
    }
  }

  &__table-icon {
    font-size: 14px;
    color: var(--color-text-secondary);
    flex-shrink: 0;
  }

  &__table-name {
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__badge {
    font-size: 11px;
    padding: 1px 6px;
    border-radius: 3px;
    font-weight: 500;
    white-space: nowrap;

    &--full {
      color: #92400E;
      background: #FEF3C7;
    }

    &--edit {
      color: #065F46;
      background: #D1FAE5;
    }

    &--view {
      color: #1E40AF;
      background: #DBEAFE;
    }

    &--none {
      color: var(--color-text-secondary);
      background: var(--color-surface-alt);
    }
  }
}

.perm-settings {
  width: 260px;
  background: var(--color-surface);
  border-left: 1px solid var(--color-border);
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow-y: auto;

  &__title {
    font-size: 15px;
    font-weight: 600;
  }

  &__subtitle {
    font-size: 12px;
    color: var(--color-text-secondary);
    margin-top: -8px;
  }

  &__empty {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    color: var(--color-text-secondary);
    font-size: 13px;
    gap: 12px;
    text-align: center;
  }
}

.perm-option {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s;

  &:hover {
    border-color: var(--el-color-primary);
    background: var(--el-color-primary-light-9);
  }

  &.selected {
    border-color: var(--el-color-primary);
    background: var(--el-color-primary-light-9);
    box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.08);
  }

  &__radio {
    width: 16px;
    height: 16px;
    border: 2px solid var(--color-border);
    border-radius: 50%;
    flex-shrink: 0;
    margin-top: 2px;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: all 0.15s;
  }

  &.selected &__radio {
    border-color: var(--el-color-primary);
    background: var(--el-color-primary);
  }

  &__radio-dot {
    width: 6px;
    height: 6px;
    background: white;
    border-radius: 50%;
  }

  &__content {
    flex: 1;
  }

  &__name {
    font-size: 13px;
    font-weight: 600;
    margin-bottom: 2px;
  }

  &__desc {
    font-size: 12px;
    color: var(--color-text-secondary);
    line-height: 1.4;
  }
}

.perm-footer {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;

  &__hint {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 13px;
    color: var(--el-color-warning);
  }

  &__spacer {
    flex: 1;
  }
}

/* ===== 字段权限 ===== */
.perm-settings__divider {
  height: 1px;
  margin: 16px 0 12px;
  background: var(--color-border);
}

.perm-settings__title--sub {
  font-size: 13px;
  margin-bottom: 10px;
}

.perm-fields {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 260px;
  overflow-y: auto;
  padding-right: 2px;
}

.perm-field-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;

  &__name {
    flex: 1;
    min-width: 0;
    font-size: 13px;
    color: var(--color-text-primary);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.perm-fields__loading,
.perm-fields__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 12px 0;
  font-size: 12px;
  color: var(--color-text-secondary);
}

/* 添加成员弹窗 */
.member-add-dialog {
  display: flex;
  flex-direction: column;
  gap: 10px;

  &__role {
    font-size: 13px;
    color: var(--color-text-secondary);
  }

  /* 内联勾选列表：自带滚动，不产生浮层，因此不会盖住底部按钮 */
  &__list {
    min-height: 88px;
    max-height: 260px;
    overflow-y: auto;
    padding: 4px;
    border: 1px solid var(--color-border-light);
    border-radius: 6px;
  }

  &__empty {
    font-size: 12px;
    color: var(--color-text-secondary);
  }

  /* 系统角色：说明"一人只有一个角色"，改的是角色归属而非并列新增 */
  &__note {
    padding: 8px 10px;
    border-radius: 6px;
    background: var(--el-color-warning-light-9);
    border: 1px solid var(--el-color-warning-light-7);
    font-size: 12px;
    line-height: 1.6;
    color: var(--color-text-secondary);
  }
}

/* 添加成员弹窗里的候选行：整行可点，勾选状态由 newMemberUserIds 决定 */
.member-pick {
  display: flex;
  align-items: center;
  width: 100%;
  height: auto;
  margin-right: 0;
  padding: 5px 6px;
  border-radius: 4px;

  &:hover {
    background: var(--color-surface-alt, rgba(15, 23, 42, 0.04));
  }

  &.is-checked {
    background: var(--el-color-primary-light-9);
  }

  /* 让默认插槽里的内容铺满整行 */
  :deep(.el-checkbox__label) {
    display: flex;
    align-items: center;
    gap: 8px;
    flex: 1;
    min-width: 0;
  }

  &__name {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    color: var(--color-text-primary);
  }

  &__username {
    flex-shrink: 0;
    font-size: 12px;
    color: var(--color-text-secondary);
  }

  /* 用户当前的系统角色，提示"加入即调整" */
  &__role {
    flex-shrink: 0;
    padding: 0 6px;
    border-radius: 8px;
    font-size: 11px;
    line-height: 16px;
    color: var(--color-text-secondary);
    background: var(--color-surface-alt, rgba(15, 23, 42, 0.06));
  }
}

/* ========== 单个角色的成员维护页（参考设计稿「X包含的成员」） ========== */
.perm-member-page {
  &__header {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 14px;
    border-bottom: 1px solid var(--color-border-light);
  }

  &__back {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 24px;
    height: 24px;
    flex-shrink: 0;
    border: none;
    background: none;
    border-radius: 4px;
    color: var(--color-text-secondary);
    cursor: pointer;
    transition: all 0.15s;

    &:hover {
      color: var(--el-color-primary);
      background: var(--el-color-primary-light-9);
    }
  }

  &__title {
    flex: 1;
    min-width: 0;
    font-size: 13px;
    font-weight: 600;
    color: var(--color-text-primary);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__search {
    width: 220px;
    flex-shrink: 0;
  }

  &__add {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 26px;
    height: 26px;
    flex-shrink: 0;
    border: 1px dashed var(--color-border);
    border-radius: 50%;
    background: none;
    color: var(--color-text-secondary);
    cursor: pointer;
    transition: all 0.15s;

    &:hover {
      border-color: var(--el-color-primary);
      color: var(--el-color-primary);
      background: var(--el-color-primary-light-9);
    }
  }

  &__list {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    padding: 4px 0;
  }

  &__empty {
    padding: 32px 0;
    text-align: center;
    font-size: 12px;
    color: var(--color-text-placeholder, var(--color-text-secondary));
  }
}

.perm-member-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 14px;
  transition: background 0.12s;

  &:hover {
    background: var(--color-surface-alt, rgba(15, 23, 42, 0.03));
  }

  &__name {
    flex: 1;
    min-width: 0;
    font-size: 13px;
    color: var(--color-text-primary);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__roles {
    flex-shrink: 0;
    font-size: 12px;
    color: var(--color-text-secondary);
    border-bottom: 1px dashed transparent;
    cursor: default;

    &:hover {
      border-bottom-color: var(--color-text-secondary);
    }
  }

  &__remove {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 26px;
    height: 26px;
    flex-shrink: 0;
    border: none;
    background: none;
    border-radius: 4px;
    color: var(--color-text-secondary);
    cursor: pointer;
    transition: all 0.15s;

    &:hover:not(:disabled) {
      color: var(--el-color-danger);
      background: var(--el-color-danger-light-9);
    }

    &.is-disabled,
    &:disabled {
      color: var(--color-text-placeholder, var(--color-border));
      cursor: not-allowed;
    }
  }
}

/* 悬停浮层渲染在 body 上，scoped 选不到，必须用 :global */
:global(.perm-member-roles-popover) {
  padding: 8px 10px;

  .member-roles-pop__title {
    margin-bottom: 4px;
    font-size: 12px;
    color: var(--color-text-secondary);
  }

  .member-roles-pop__item {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 12px;
    line-height: 20px;
    color: var(--color-text-primary);
  }

  .member-roles-pop__dot {
    width: 6px;
    height: 6px;
    flex-shrink: 0;
    border-radius: 50%;
    background: var(--el-color-primary);

    &.is-custom {
      background: var(--el-color-warning);
    }
  }
}
</style>
