<template>
  <div class="menu-page">
    <PageContainer title="菜单管理">
      <template #headerActions>
        <el-tooltip content="列表字段设置">
          <el-button link :icon="Setting" @click="openColumnConfig" />
        </el-tooltip>
        <AppButton type="primary" permission="button:menu:create" @click="openCreate">新增菜单</AppButton>
      </template>

      <TableCard>
        <template #table>
          <el-table
            ref="menuTableRef"
            v-loading="loading"
            :data="menus"
            row-key="id"
            border
            :row-class-name="menuRowClassName"
            :tree-props="{ children: 'children' }"
            class="menu-table"
          >
            <el-table-column v-if="isColumnVisible('name')" prop="name" label="名称" min-width="220" header-align="center">
              <template #default="{ row }">
                <span
                  class="menu-name-content"
                  :class="`menu-name-content--${row.menuType.toLowerCase()}`"
                  title="拖拽调整同级排序"
                >
                  <template v-if="row.icon && isRemixIcon(row.icon)">
                    <i :class="row.icon" class="menu-remix-icon" />
                  </template>
                  <el-icon v-else-if="row.icon && iconMap[row.icon]" class="menu-icon">
                    <component :is="iconMap[row.icon]" />
                  </el-icon>
                  <span>{{ row.name }}</span>
                </span>
              </template>
            </el-table-column>
            <el-table-column v-if="isColumnVisible('menuType')" prop="menuType" label="类型" width="90" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="menuTypeTagType(row.menuType)">
                  {{ typeLabel(row.menuType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column v-if="isColumnVisible('path')" prop="path" label="路径" min-width="170" show-overflow-tooltip header-align="center">
              <template #default="{ row }">{{ row.path || '-' }}</template>
            </el-table-column>
            <el-table-column v-if="isColumnVisible('permissionCode')" prop="permissionCode" label="权限编码" min-width="190" show-overflow-tooltip header-align="center">
              <template #default="{ row }">{{ row.permissionCode || '-' }}</template>
            </el-table-column>
            <el-table-column v-if="isColumnVisible('sortOrder')" prop="sortOrder" label="排序" width="80" align="center" />
            <el-table-column v-if="isColumnVisible('enabled')" prop="enabled" label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="row.enabled === 1 ? 'success' : 'info'">
                  {{ row.enabled === 1 ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column v-if="isColumnVisible('operations')" label="操作" width="160" align="center" header-align="center" fixed="right">
              <template #default="{ row }">
                <el-tooltip content="编辑">
                  <AppButton type="primary" link size="small" permission="button:menu:update" @click="openEdit(row)"><el-icon><EditPen /></el-icon></AppButton>
                </el-tooltip>
                <el-tooltip v-if="row.menuType !== 'BUTTON'" content="授权">
                  <AppButton
                    type="warning"
                    link
                    size="small"
                    permission="button:menu:grant"
                    @click="openGrantDrawer"
                  ><el-icon><Key /></el-icon></AppButton>
                </el-tooltip>
                <el-tooltip content="删除">
                  <AppButton type="danger" link size="small" permission="button:menu:delete" @click="handleDelete(row)"><el-icon><Delete /></el-icon></AppButton>
                </el-tooltip>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </TableCard>
    </PageContainer>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑菜单' : '新增菜单'" width="640px" class="settings-form-dialog">
      <el-form :model="form" label-width="100px">
        <el-form-item label="上级菜单">
          <el-tree-select
            v-model="form.parentId"
            :data="parentTreeOptions"
            :props="{ label: 'name', value: 'id', children: 'children' }"
            check-strictly
            clearable
            placeholder="无（顶级菜单）"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.menuType" style="width: 100%">
            <el-option label="目录" value="DIRECTORY" />
            <el-option label="菜单" value="MENU" />
            <el-option label="按钮" value="BUTTON" />
          </el-select>
        </el-form-item>
        <el-form-item label="路径">
          <el-input v-model="form.path" />
        </el-form-item>
        <el-form-item label="路由名称">
          <el-input v-model="form.routeName" />
        </el-form-item>
        <el-form-item label="组件">
          <el-input v-model="form.component" />
        </el-form-item>
        <el-form-item label="图标">
          <IconPicker :model-value="form.icon || ''" @update:model-value="form.icon = $event" />
        </el-form-item>
        <el-form-item label="权限编码">
          <el-input v-model="form.permissionCode" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="grantDrawerVisible" title="角色授权" size="500px">
      <el-table :data="roleList" v-loading="roleLoading" border>
        <el-table-column prop="name" label="角色名称" min-width="120" />
        <el-table-column prop="code" label="角色编码" min-width="120" />
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-tooltip content="设置权限">
              <el-button link size="small" type="primary" @click="openRolePermission(row)"><el-icon><Setting /></el-icon></el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="permissionEditing" style="margin-top: 20px;">
        <div style="margin-bottom: 10px; font-weight: bold;">权限选择</div>
        <el-checkbox-group v-model="selectedPermissions" v-loading="permissionLoading">
          <el-checkbox v-for="code in grantablePermissions" :key="code" :value="code">{{ code }}</el-checkbox>
        </el-checkbox-group>
        <div style="margin-top: 16px; text-align: right;">
          <el-button @click="permissionEditing = false">取消</el-button>
          <el-button type="primary" :loading="permissionSaving" @click="handleSavePermission">保存</el-button>
        </div>
      </div>
    </el-drawer>

    <ColumnConfigDialog
      v-model="showColumnConfig"
      :column-groups="columnGroups"
      :draft-selected-columns="draftSelectedColumns"
      :draft-column-keys="draftColumnKeys"
      @update:draft-column-keys="draftColumnKeys = $event"
      @remove="removeDraftColumn"
      @save="saveColumns"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, type Component } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as ElementPlusIcons from '@element-plus/icons-vue'
import { EditPen, Key, Delete, Setting } from '@element-plus/icons-vue'
import Sortable, { type MoveEvent, type SortableEvent } from 'sortablejs'
import { isRemixIcon } from '@/components/common/RemixIconData'
import AppButton from '@/components/common/AppButton.vue'
import ColumnConfigDialog from '@/components/common/ColumnConfigDialog.vue'
import { useColumnConfig, type ColumnDef } from '@/composables/useColumnConfig'
import PageContainer from '@/components/common/PageContainer.vue'
import TableCard from '@/components/common/TableCard.vue'
import IconPicker from '@/components/common/IconPicker.vue'
import { createMenu, deleteMenu, getAllMenus, updateMenu, batchSortMenu, type MenuItem, type MenuPayload, type MenuSortItem, getRoleList, getGrantablePermissions, getRolePermissions, saveRolePermissions, type RoleItem } from '@/api/modules/menu'

const iconMap: Record<string, Component> = {}
for (const [name, comp] of Object.entries(ElementPlusIcons)) {
  iconMap[name] = comp as Component
}

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const menus = ref<MenuItem[]>([])
const menuTableRef = ref()
let menuSortable: Sortable | null = null

const grantDrawerVisible = ref(false)
const roleList = ref<RoleItem[]>([])
const roleLoading = ref(false)
const permissionEditing = ref(false)
const permissionLoading = ref(false)
const permissionSaving = ref(false)
const grantablePermissions = ref<string[]>([])
const selectedPermissions = ref<string[]>([])
const currentRoleItem = ref<RoleItem | null>(null)

const form = reactive<MenuPayload>({
  parentId: 0,
  name: '',
  menuType: 'MENU',
  path: '',
  routeName: '',
  component: '',
  icon: '',
  sortOrder: 0,
  permissionCode: '',
  visible: 1,
  enabled: 1,
  keepAlive: 0,
  remark: '',
})

const parentTreeOptions = computed(() => {
  function build(items: MenuItem[], excludeId?: number): any[] {
    return items
      .filter(item => item.id !== excludeId && item.menuType !== 'BUTTON')
      .map(item => ({
        id: item.id,
        name: item.name,
        children: item.children?.length ? build(item.children, excludeId) : undefined,
      }))
  }
  return [{ id: 0, name: '无（顶级菜单）', children: build(menus.value, editingId.value || undefined) }]
})

function typeLabel(type: string) {
  if (type === 'DIRECTORY') return '目录'
  if (type === 'MENU') return '菜单'
  if (type === 'BUTTON') return '按钮'
  return type
}

function menuTypeTagType(type: string) {
  if (type === 'BUTTON') return 'info'
  if (type === 'DIRECTORY') return 'warning'
  return 'primary'
}

// ── 列表字段设置 ──
const menuAllColumns: ColumnDef[] = [
  { key: 'name', label: '名称', group: '基础字段', minWidth: 220 },
  { key: 'menuType', label: '类型', group: '基础字段', width: 90 },
  { key: 'path', label: '路径', group: '基础字段', minWidth: 170, showOverflowTooltip: true },
  { key: 'permissionCode', label: '权限编码', group: '基础字段', minWidth: 190, showOverflowTooltip: true },
  { key: 'sortOrder', label: '排序', group: '基础字段', width: 80 },
  { key: 'enabled', label: '状态', group: '状态信息', width: 90 },
  { key: 'operations', label: '操作', width: 160 },
]
const menuDefaultKeys = ['name', 'menuType', 'path', 'permissionCode', 'sortOrder', 'enabled', 'operations']

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
  pageKey: 'menu_list',
  columns: menuAllColumns,
  defaultKeys: menuDefaultKeys,
})

function isColumnVisible(key: string) {
  return visibleColumns.value.some((c) => c.key === key)
}

onMounted(() => {
  fetchMenus()
  loadColumnConfig()
})

onBeforeUnmount(() => {
  menuSortable?.destroy()
  menuSortable = null
})

async function fetchMenus() {
  loading.value = true
  try {
    menus.value = await getAllMenus() as any
    await initMenuSortable()
  } finally {
    loading.value = false
  }
}

function menuRowClassName({ row }: { row: MenuItem }) {
  return `menu-row menu-row-${row.id} menu-row--${row.menuType.toLowerCase()}`
}

async function initMenuSortable() {
  await nextTick()
  const tbody = menuTableRef.value?.$el.querySelector('.el-table__body-wrapper tbody') as HTMLElement | null
  if (!tbody) return

  menuSortable?.destroy()
  menuSortable = Sortable.create(tbody, {
    handle: '.menu-name-content',
    animation: 150,
    ghostClass: 'sortable-ghost',
    onMove: (evt: MoveEvent) => {
      const dragged = getMenuFromRow(evt.dragged as HTMLElement)
      const related = getMenuFromRow(evt.related as HTMLElement)
      if (!dragged || !related) return true
      return normalizeParentId(dragged.parentId) === normalizeParentId(related.parentId)
    },
    onEnd: handleSortEnd,
  })
}

async function handleSortEnd(evt: SortableEvent) {
  if (evt.oldIndex === evt.newIndex) return

  const moved = getMenuFromRow(evt.item as HTMLElement)
  if (!moved) {
    await fetchMenus()
    return
  }

  const siblingOrder = getRenderedMenuOrder()
    .map(id => findMenuById(menus.value, id))
    .filter((item): item is MenuItem => item !== null && normalizeParentId(item.parentId) === normalizeParentId(moved.parentId))
  const currentSiblings = getSiblingMenus(moved.parentId)
  const currentSiblingIds = currentSiblings.map(item => item.id).sort((a, b) => a - b)
  const renderedSiblingIds = siblingOrder.map(item => item.id).sort((a, b) => a - b)

  if (
    siblingOrder.length !== currentSiblings.length ||
    currentSiblingIds.some((id, index) => id !== renderedSiblingIds[index])
  ) {
    ElMessage.warning('仅支持同级菜单拖拽排序')
    await fetchMenus()
    return
  }

  const sortItems: MenuSortItem[] = siblingOrder.map((item, index) => ({
    id: item.id,
    parentId: normalizeParentId(item.parentId),
    sortOrder: index + 1,
  }))

  try {
    await batchSortMenu(sortItems)
    ElMessage.success('排序已保存')
    await fetchMenus()
  } catch {
    ElMessage.error('排序保存失败')
    await fetchMenus()
  }
}

function getRenderedMenuOrder() {
  const rows = menuTableRef.value?.$el.querySelectorAll('.el-table__body-wrapper tbody tr.menu-row') as NodeListOf<HTMLElement> | undefined
  return Array.from(rows || [])
    .map(getRowMenuId)
    .filter((id): id is number => id !== null)
}

function getMenuFromRow(row: HTMLElement | null) {
  const id = getRowMenuId(row)
  return id === null ? null : findMenuById(menus.value, id)
}

function getRowMenuId(row: HTMLElement | null) {
  if (!row) return null
  for (const className of Array.from(row.classList)) {
    const matched = /^menu-row-(\d+)$/.exec(className)
    if (matched) return Number(matched[1])
  }
  return null
}

function findMenuById(items: MenuItem[], id: number): MenuItem | null {
  for (const item of items) {
    if (item.id === id) return item
    const child = findMenuById(item.children || [], id)
    if (child) return child
  }
  return null
}

function getSiblingMenus(parentId: number | undefined) {
  const normalizedParentId = normalizeParentId(parentId)
  if (normalizedParentId === 0) return menus.value
  return findMenuById(menus.value, normalizedParentId)?.children || []
}

function normalizeParentId(parentId: number | undefined) {
  return parentId || 0
}

function resetForm() {
  editingId.value = null
  form.parentId = 0
  form.name = ''
  form.menuType = 'MENU'
  form.path = ''
  form.routeName = ''
  form.component = ''
  form.icon = ''
  form.sortOrder = 0
  form.permissionCode = ''
  form.visible = 1
  form.enabled = 1
  form.keepAlive = 0
  form.remark = ''
}

function openCreate() {
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: MenuItem) {
  editingId.value = row.id
  form.parentId = row.parentId
  form.name = row.name
  form.menuType = row.menuType
  form.path = row.path || ''
  form.routeName = row.routeName || ''
  form.component = row.component || ''
  form.icon = row.icon || ''
  form.sortOrder = row.sortOrder || 0
  form.permissionCode = row.permissionCode || ''
  form.visible = row.visible
  form.enabled = row.enabled
  form.keepAlive = row.keepAlive
  form.remark = row.remark || ''
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入菜单名称')
    return
  }
  submitting.value = true
  try {
    if (editingId.value) {
      await updateMenu(editingId.value, form)
    } else {
      await createMenu(form)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    resetForm()
    await fetchMenus()
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row: MenuItem) {
  await ElMessageBox.confirm(`确认删除菜单”${row.name}”吗？`, '提示', { type: 'warning' })
  await deleteMenu(row.id)
  ElMessage.success('删除成功')
  await fetchMenus()
}

async function openGrantDrawer() {
  grantDrawerVisible.value = true
  roleLoading.value = true
  try {
    roleList.value = await getRoleList() as any
  } finally {
    roleLoading.value = false
  }
}

async function openRolePermission(role: RoleItem) {
  currentRoleItem.value = role
  permissionEditing.value = true
  permissionLoading.value = true
  try {
    const grantable = await getGrantablePermissions() as any
    grantablePermissions.value = grantable as string[]
    const rolePerm = await getRolePermissions(role.id) as any
    selectedPermissions.value = (rolePerm as any).permissionCodes || []
  } finally {
    permissionLoading.value = false
  }
}

async function handleSavePermission() {
  if (!currentRoleItem.value) return
  permissionSaving.value = true
  try {
    await saveRolePermissions(currentRoleItem.value.id, selectedPermissions.value)
    ElMessage.success('权限保存成功')
    permissionEditing.value = false
  } finally {
    permissionSaving.value = false
  }
}
</script>

<style scoped>
.menu-page {
  padding: 20px;
}
.menu-table {
  width: 100%;
}

.menu-table :deep(.menu-row--directory) {
  background: #f8fafc;
}

.menu-table :deep(.menu-row--directory .el-table__cell) {
  font-weight: 600;
}

.menu-table :deep(.menu-row--button) {
  background: #fcfcfd;
}

.menu-icon {
  margin-right: 6px;
  vertical-align: -2px;
}

.menu-remix-icon {
  font-size: 16px;
  margin-right: 6px;
  vertical-align: -2px;
}

.menu-name-content {
  display: inline-flex;
  align-items: center;
  max-width: 100%;
  cursor: grab;
  user-select: none;
}

.menu-name-content:active {
  cursor: grabbing;
}

.menu-name-content--directory {
  color: var(--color-text-primary);
}

.menu-name-content--menu {
  color: var(--color-text-primary);
}

.menu-name-content--button {
  color: var(--color-text-secondary);
  font-size: 13px;
}

.menu-name-content--button::before {
  content: '';
  width: 6px;
  height: 6px;
  margin-right: 8px;
  border-radius: 50%;
  background: #c0c4cc;
}

.sortable-ghost {
  opacity: 0.55;
}
</style>
