<template>
  <PageContainer title="多维表格" class="bitable-list">
    <div class="bitable-layout" :class="{ 'is-sidebar-collapsed': treeCollapsed }">
      <!-- 左侧：Base 分组目录树（与模型应用设置页一致的管理方式） -->
      <aside class="bitable-sidebar" :class="{ 'is-collapsed': treeCollapsed }" v-loading="groupsLoading">
        <BaseGroupTree
          :groups="baseGroups"
          :bases="bases"
          :dashboards="baseDashboards"
          :selection="treeSelection"
          :can-manage="canManageGroups"
          @select="handleTreeSelect"
          @create-group="handleCreateGroup"
          @rename-group="handleRenameGroup"
          @delete-group="handleDeleteGroup"
          @move-group="handleMoveGroup"
          @reorder-groups="handleReorderGroups"
          @reorder-bases="handleReorderBases"
          @reorder-leaves="handleReorderLeaves"
          @move-leaf="handleMoveLeaf"
          @move-base="handleMoveBase"
          @rename-base="handleRenameBase"
          @open-base="goToEditor"
          @open-dashboard="goToDashboard"
          @create-dashboard="quickCreateDashboard"
          @rename-dashboard="handleRenameDashboard"
          @delete-dashboard="handleDeleteDashboard"
          :tables="baseTables"
          @open-table="goToTable"
          @create-table="openCreateTable"
          @rename-table="handleRenameTable"
          @delete-table="handleDeleteTable"
          @convert-to-table="handleConvertToTable"
          @import-excel="openImportDialog"
          @create-dashboard-in-group="quickCreateDashboardInGroup"
          @collapse="treeCollapsed = true"
          @delete-base="handleDeleteBase"
          @create-base="handleCreateInGroup"
          @create-template="showTemplateGallery = true"
        />
      </aside>

      <el-tooltip v-if="treeCollapsed" content="展开目录" placement="right">
        <button type="button" class="bitable-sidebar__expand" @click="treeCollapsed = false">
          <el-icon><DArrowRight /></el-icon>
        </button>
      </el-tooltip>

      <!-- 右侧：数据表工作台（点树上的表内嵌显示）或 Base 卡片 -->
      <section class="bitable-main" v-loading="loading">
        <BitableEditor
          v-if="activeTable"
          :key="`${activeTable.baseId}-${activeTable.tableId}`"
          :base-id="activeTable.baseId"
          :table-id="activeTable.tableId"
          embedded
          class="bitable-embedded"
        />
        <DashboardEditor
          v-else-if="activeDashboard"
          :key="`dash-${activeDashboard.baseId}-${activeDashboard.dashboardId ?? 'new'}`"
          :base-id="activeDashboard.baseId"
          :dashboard-id="activeDashboard.dashboardId"
          :start-in-create-mode="quickCreateLanding"
          embedded
          class="bitable-embedded"
          @changed="loadBaseDashboards"
        />
        <div v-else>
        <div class="bitable-list__header">
          <div class="bitable-list__scope">
            <span class="bitable-list__scope-title">{{ scopeTitle }}</span>
            <el-tag type="info" size="small">共 {{ scopedLeaves.length }} 个</el-tag>
          </div>
        </div>

        <!-- 叶子卡片：数据表 + 仪表盘混排，与左侧目录树同构（同一范围、同一排序口径） -->
        <div v-if="scopedLeaves.length" class="bitable-list__grid">
          <el-card
            v-for="leaf in scopedLeaves"
            :key="leaf.key"
            class="leaf-card"
            shadow="hover"
            @click="openLeaf(leaf)"
          >
            <div class="leaf-card__header">
              <span class="leaf-card__icon" :class="{ 'leaf-card__icon--dashboard': leaf.kind === 'dashboard' }">
                <i v-if="leaf.kind === 'table' && leaf.icon === 'file'" class="ri-file-text-line" />
                <i v-else-if="leaf.kind === 'table'" class="ri-database-2-line" />
                <el-icon v-else><DataAnalysis /></el-icon>
              </span>
              <!-- 重命名不弹框：名称就地变输入框 -->
              <input
                v-if="renamingLeaf?.key === leaf.key"
                v-model="renamingLeafName"
                class="leaf-card__name-input"
                @click.stop
                @keyup.enter="commitLeafRename(leaf)"
                @keyup.esc="cancelLeafRename"
                @blur="commitLeafRename(leaf)"
              />
              <span v-else class="leaf-card__name">{{ leaf.name }}</span>
              <el-dropdown trigger="click" @command="(cmd: string) => handleLeafAction(cmd, leaf)">
                <el-button link @click.stop><el-icon><MoreFilled /></el-icon></el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="rename">重命名</el-dropdown-item>
                    <el-dropdown-item command="move" divided>移动到分组…</el-dropdown-item>
                    <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
            <p class="leaf-card__desc">{{ leaf.description || (leaf.kind === 'dashboard' ? '数据仪表盘' : '暂无描述') }}</p>
            <div class="leaf-card__footer">
              <span class="leaf-card__kind" :class="{ 'leaf-card__kind--dashboard': leaf.kind === 'dashboard' }">
                {{ leaf.kind === 'dashboard' ? '仪表盘' : '数据表' }}
              </span>
              <span>{{ leaf.groupName }}</span>
              <span>{{ leaf.updatedAt ? formatDate(leaf.updatedAt) : '—' }}</span>
            </div>
          </el-card>
        </div>
        <el-empty
          v-else
          :description="bases.length ? '当前范围下暂无数据表或仪表盘' : '暂无数据，点击上方按钮创建'"
        />
        </div>
      </section>
    </div>

    <!-- 移动到分组弹窗 -->
    <el-dialog v-model="moveDialogVisible" title="移动到分组" width="420px">
      <el-tree-select
        v-model="moveTargetGroupId"
        :data="moveOptions"
        :props="{ label: 'label', children: 'children' }"
        node-key="value"
        check-strictly
        default-expand-all
        clearable
        placeholder="选择目标分组"
        style="width: 100%"
      />
      <template #footer>
        <el-button @click="moveDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmMoveBase">确定</el-button>
      </template>
    </el-dialog>

    <!-- 模板库弹窗 -->
    <TemplateGallery
      :visible="showTemplateGallery"
      @create="handleTemplateCreate"
      @close="showTemplateGallery = false"
    />

    <!-- 创建/编辑弹窗 -->
    <el-dialog v-model="showCreateTableDialog" title="新建数据表" width="440px">
    <el-form label-width="86px" @submit.prevent>
      <el-form-item label="数据表名" required>
        <el-input v-model="tableForm.name" placeholder="默认：新建数据表" maxlength="60" clearable @keyup.enter="submitCreateTable" />
      </el-form-item>
      <p class="bitable-dialog__hint">新表会创建为独立的多维表格；未选择分组时放在一级目录。</p>
    </el-form>
    <template #footer>
      <el-button @click="showCreateTableDialog = false">取消</el-button>
      <el-button type="primary" :loading="creatingTable" @click="submitCreateTable">创建</el-button>
    </template>
  </el-dialog>
  <el-dialog v-model="showImportDialog" title="导入 Excel / CSV" width="480px">
    <el-form label-width="86px" @submit.prevent>
      <el-form-item label="数据表名" required>
        <el-input v-model="importFileForm.name" placeholder="默认：导入数据表" maxlength="60" />
      </el-form-item>
      <el-form-item label="所属分组">
        <el-tree-select
          v-model="importFileForm.groupId"
          :data="groupOptions"
          :props="{ label: 'label', children: 'children' }"
          node-key="value"
          check-strictly
          default-expand-all
          clearable
          placeholder="默认放入「未分组」"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="文件" required>
        <el-upload
          :auto-upload="false"
          :limit="1"
          :accept="importFileForm.format === 'excel' ? '.xlsx' : '.csv'"
          :on-change="(f: any) => (importFileForm.file = f.raw)"
          :on-remove="() => (importFileForm.file = null)"
          drag
        >
          <el-icon><UploadFilled /></el-icon>
          <div class="el-upload__text">拖拽文件到此处，或点击上传</div>
          <template #tip>
            <div class="el-upload__tip">仅支持 {{ importFileForm.format === 'excel' ? '.xlsx' : '.csv' }} 格式，第一行为字段名（不存在的字段将自动创建为文本字段）</div>
          </template>
        </el-upload>
      </el-form-item>
      <el-form-item label="格式">
        <el-radio-group v-model="importFileForm.format">
          <el-radio label="excel">Excel (.xlsx)</el-radio>
          <el-radio label="csv">CSV (.csv)</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="showImportDialog = false">取消</el-button>
      <el-button type="primary" :loading="importingFile" @click="submitImportFile">导入</el-button>
    </template>
  </el-dialog>
  <el-dialog v-model="showDialog" :title="editingBase ? '编辑多维表格' : '新建多维表格'" width="500px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="输入多维表格名称" maxlength="200" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" placeholder="输入描述" maxlength="500" />
        </el-form-item>
        <el-form-item v-if="!editingBase" label="所属分组">
          <el-tree-select
            v-model="form.groupId"
            :data="groupOptions"
            :props="{ label: 'label', children: 'children' }"
            node-key="value"
            check-strictly
            default-expand-all
            clearable
            placeholder="默认放入「未分组」"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </PageContainer>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MoreFilled, DArrowRight, UploadFilled, DataAnalysis } from '@element-plus/icons-vue'
import { resolveErrorMessage } from '@/utils/error'
import PageContainer from '@/components/common/PageContainer.vue'
import { importExcel, importCsv } from '@/api/modules/bitableTemplate'

import { usePermission } from '@/composables/usePermission'
import {
  listBases,
  listDashboardsBatch,
  createDashboard,
  listTables,
  createTable,
  updateTable,
  deleteTable,
  renameDashboard,
  deleteDashboard,
  listBaseGroups,
  createBase,
  createBaseGroup,
  renameBaseGroup,
  moveBaseGroup,
  sortBaseGroups,
  sortBases,
  sortLeaves,
  moveLeaf,
  deleteBaseGroup,
  moveBaseToGroup,
  updateBase,
  deleteBase,
} from '@/api/modules/bitable'
import type { BitableBase, BitableBaseGroup, BitableBaseCreateDTO } from '@/types/bitable'
import TemplateGallery from './components/TemplateGallery.vue'
import BaseGroupTree from './components/BaseGroupTree.vue'
import BitableEditor from './editor.vue'
import DashboardEditor from './dashboard/index.vue'

const router = useRouter()
const { hasRole } = usePermission()

const bases = ref<BitableBase[]>([])
/** 目录树叶子：仪表盘与数据表在树上同层级，共用同一 sortOrder 序列；baseGroupId=独立分组归属 */
const baseDashboards = ref<Array<{ id: number; baseId: number; name: string; sortOrder?: number; baseGroupId?: number | null; updatedAt?: string }>>([])
const creatingDashboard = ref(false)
/** 快捷新建落地标记：让内嵌仪表盘以「搭建你的仪表盘」首页开场（仅挂载时读取） */
const quickCreateLanding = ref(false)
const baseTables = ref<Array<{ id: number; baseId: number; name: string; icon?: string; description?: string; sortOrder?: number; baseGroupId?: number | null; updatedAt?: string }>>([])
const showCreateTableDialog = ref(false)
const creatingTable = ref(false)
const tableForm = ref<{ groupId: number | null; name: string }>({ groupId: null, name: '' })
const showImportDialog = ref(false)
const importingFile = ref(false)
const importFileForm = ref<{ name: string; format: 'excel' | 'csv'; file: File | null; groupId: number | null }>({ name: '', format: 'excel', file: null, groupId: null })
/** 内嵌工作台：当前树上选中的数据表 */
const activeTable = ref<{ baseId: number; tableId: number } | null>(null)
/** 内嵌工作台：当前树上选中的仪表盘 */
const activeDashboard = ref<{ baseId: number; dashboardId?: number } | null>(null)
/** 左侧目录收起 */
const treeCollapsed = ref(false)
const baseGroups = ref<BitableBaseGroup[]>([])
const groupsLoading = ref(false)
const loading = ref(false)
const showDialog = ref(false)
const showTemplateGallery = ref(false)
const editingBase = ref<BitableBase | null>(null)
const saving = ref(false)
const form = ref<BitableBaseCreateDTO & { groupId?: number | null }>({
  name: '',
  description: '',
})

/** 卡片「移动到分组」弹窗 */
const moveDialogVisible = ref(false)
const moveBaseId = ref<number | null>(null)
const moveTargetGroupId = ref<number | null>(null)

/** 左侧树的选中范围：全部 / 分组 / 未分组 */
const treeSelection = ref<{ type: 'all' | 'group' | 'ungrouped'; groupId: number | null }>({
  type: 'all',
  groupId: null,
})

/** 分组管理为管理员能力，与后端权限一致 */
const canManageGroups = computed(() => hasRole('admin'))

/* ---------------- 同名检测辅助 ---------------- */

/** 同名检测（不区分大小写）：名称是否已被同级同类文件占用 */
function isNameTaken(name: string, existingNames: string[]): boolean {
  const lower = name.toLowerCase()
  return existingNames.some((n) => (n || '').toLowerCase() === lower)
}

/** 默认名去重：「默认名」「默认名 2」「默认名 3」…（用户未改名时避免同名冲突） */
function uniquifyName(defaultName: string, existingNames: string[]): string {
  if (!isNameTaken(defaultName, existingNames)) return defaultName
  let i = 2
  while (isNameTaken(`${defaultName} ${i}`, existingNames)) i += 1
  return `${defaultName} ${i}`
}

/** 某分组（含未分组）展示范围内全部数据表名（数据表按其 Base 的分组挂载） */
function scopedTableNames(groupId: number | null): string[] {
  const baseIds = new Set(bases.value.filter((b) => (b.groupId ?? null) === groupId).map((b) => b.id))
  return baseTables.value.filter((t) => baseIds.has(t.baseId)).map((t) => t.name)
}

/** 某分组（含未分组）展示范围内全部仪表盘名 */
function scopedDashboardNames(groupId: number | null): string[] {
  const baseIds = new Set(bases.value.filter((b) => (b.groupId ?? null) === groupId).map((b) => b.id))
  return baseDashboards.value.filter((d) => baseIds.has(d.baseId)).map((d) => d.name)
}

async function loadBases() {
  loading.value = true
  try {
    const res = await listBases()
    bases.value = Array.isArray(res) ? res : (res as any).data || []
    await Promise.all([loadBaseDashboards(), loadBaseTables()])
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '加载失败'))
  } finally {
    loading.value = false
  }
}


/** 外层目录树的数据表节点（逐 Base 拉取，失败静默） */
async function loadBaseTables() {
  if (!bases.value.length) {
    baseTables.value = []
    return
  }
  const results = await Promise.all(
    bases.value.map(async (b) => {
      try {
        const list = await listTables(b.id)
        return (Array.isArray(list) ? list : []).map((t: any) => ({
          id: t.id,
          baseId: b.id,
          name: t.name,
          icon: t.icon,
          // 目录树要按它和数据表/仪表盘混排，漏了会全部退化成 0 而丢掉顺序
          sortOrder: t.sortOrder ?? 0,
          // 独立分组归属（null=跟随所属 Base 分组），拖拽移动单个叶子时由后端维护
          baseGroupId: t.baseGroupId ?? null,
        }))
      } catch {
        return []
      }
    }),
  )
  baseTables.value = results.flat()
}

function goToTable(payload: { baseId: number; tableId: number }) {
  activeDashboard.value = null
  activeTable.value = { baseId: payload.baseId, tableId: payload.tableId }
}

/** 外层新建数据表：独立建一个多维表格承载，分组节点入口时挂到该分组 */
function openCreateTable(groupId: number | null) {
  tableForm.value = { groupId, name: '' }
  showCreateTableDialog.value = true
}

async function submitCreateTable() {
  const groupId = tableForm.value.groupId ?? null
  const existing = scopedTableNames(groupId)
  const typed = tableForm.value.name.trim()
  // 用户输入了名称则做同名检测；留空走默认名并自动去重
  if (typed && isNameTaken(typed, existing)) {
    ElMessage.error(`同级已存在同名数据表「${typed}」`)
    return
  }
  const name = typed || uniquifyName('新建数据表', existing)
  creatingTable.value = true
  try {
    const basePayload: BitableBaseCreateDTO & { groupId?: number | null } = { name, groupId }
    // request 拦截器已解包 response.data，后端 POST /bases 的 data 就是新 Base ID（数字）
    const baseId = Number(await createBase(basePayload))
    const tableId = Number(await createTable(baseId, { name }))
    ElMessage.success('数据表已创建')
    showCreateTableDialog.value = false
    await Promise.all([loadBases(), loadGroups(), loadBaseTables()])
    activeTable.value = { baseId, tableId }
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '创建失败'))
  } finally {
    creatingTable.value = false
  }
}

async function handleRenameTable(payload: { id: number; name: string }) {
  try {
    await updateTable(payload.id, { name: payload.name })
    await loadBaseTables()
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '重命名失败'))
  }
}

async function handleDeleteTable(payload: { id: number; baseId: number; name: string }) {
  try {
    await deleteTable(payload.id)
    ElMessage.success('已删除')
    await loadBaseTables()
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '删除失败'))
  }
}

/** 导入表 → 数据表：清除文件标记，树上恢复数据库图标（数据本身不变） */
async function handleConvertToTable(payload: { id: number; baseId: number; name: string }) {
  try {
    await updateTable(payload.id, { icon: 'database' })
    ElMessage.success(`已将「${payload.name}」转为数据表`)
    await loadBaseTables()
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '转换失败'))
  }
}

/** 导入 Excel：一键创建多维表格+数据表并导入记录，表标记为文件图标；分组节点入口时预选分组 */
function openImportDialog(groupId: number | null = null) {
  importFileForm.value = { name: '', format: 'excel', file: null, groupId }
  showImportDialog.value = true
}

async function submitImportFile() {
  if (!importFileForm.value.file) {
    ElMessage.warning('请先选择文件')
    return
  }
  const groupId = importFileForm.value.groupId ?? null
  const existing = scopedTableNames(groupId)
  const typed = importFileForm.value.name.trim()
  if (typed && isNameTaken(typed, existing)) {
    ElMessage.error(`同级已存在同名数据表「${typed}」`)
    return
  }
  const name = typed || uniquifyName('导入数据表', existing)
  importingFile.value = true
  try {
    const basePayload: BitableBaseCreateDTO & { groupId?: number | null } = { name, groupId }
    const baseId = Number(await createBase(basePayload))
    const tableId = Number(await createTable(baseId, { name }))
    const recordIds = importFileForm.value.format === 'excel'
      ? await importExcel(tableId, importFileForm.value.file)
      : await importCsv(tableId, importFileForm.value.file)
    await updateTable(tableId, { icon: 'file' })
    ElMessage.success(`已导入 ${recordIds.length} 条记录`)
    showImportDialog.value = false
    await Promise.all([loadBases(), loadGroups(), loadBaseTables()])
    activeTable.value = { baseId, tableId }
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '导入失败'))
  } finally {
    importingFile.value = false
  }
}

/** 外层目录树的仪表盘节点（按 Base 批量拉取，失败静默不影响主列表） */
async function loadBaseDashboards() {
  const ids = bases.value.map((b) => b.id)
  if (!ids.length) {
    baseDashboards.value = []
    return
  }
  try {
    const list = await listDashboardsBatch(ids)
    baseDashboards.value = Array.isArray(list) ? list : []
  } catch {
    baseDashboards.value = []
  }
}

async function loadGroups() {
  groupsLoading.value = true
  try {
    const res = await listBaseGroups()
    baseGroups.value = Array.isArray(res) ? res : (res as any).data || []
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '加载分组失败'))
    baseGroups.value = []
  } finally {
    groupsLoading.value = false
  }
  resetSelectionIfMissing()
}

/** 拍平嵌套分组，便于按 parentId 查找子分组与子孙分组 */
const flatGroups = computed<BitableBaseGroup[]>(() => {
  const out: BitableBaseGroup[] = []
  const walk = (list?: BitableBaseGroup[]) => {
    ;(list || []).forEach((g) => {
      out.push(g)
      walk(g.children)
    })
  }
  walk(baseGroups.value)
  return out
})

/** 某分组及其所有子孙分组的ID集合（用于按分组范围过滤 Base） */
function collectGroupIds(groupId: number): number[] {
  const childrenMap = new Map<number, number[]>()
  for (const group of flatGroups.value) {
    if (group.parentId != null) {
      const list = childrenMap.get(group.parentId) ?? []
      list.push(group.id)
      childrenMap.set(group.parentId, list)
    }
  }
  const ids: number[] = []
  const queue: number[] = [groupId]
  while (queue.length) {
    const current = queue.shift() as number
    ids.push(current)
    for (const child of childrenMap.get(current) ?? []) queue.push(child)
  }
  return ids
}

/** 叶子分组的归属：独立分组优先，为空时跟随 Base 的分组（与目录树口径一致） */
function leafGroupId(baseId: number, baseGroupId?: number | null): number | null {
  if (baseGroupId != null) return baseGroupId
  return bases.value.find((b) => b.id === baseId)?.groupId ?? null
}

function leafGroupName(baseId: number, baseGroupId?: number | null): string {
  const gid = leafGroupId(baseId, baseGroupId)
  if (gid == null) return '未分组'
  return flatGroups.value.find((g) => g.id === gid)?.name ?? '未命名分组'
}

interface LeafCardVM {
  key: string
  kind: 'table' | 'dashboard'
  id: number
  baseId: number
  name: string
  description: string
  icon?: string
  groupName: string
  updatedAt?: string
  sortOrder: number
}

/** 右侧展示的叶子（数据表 + 仪表盘混排）：范围与排序口径均与左侧目录树一致 */
const scopedLeaves = computed<LeafCardVM[]>(() => {
  const selection = treeSelection.value
  const inScope = (gid: number | null) => {
    if (selection.type === 'all') return true
    if (selection.type === 'ungrouped') return (gid ?? null) === (selection.groupId ?? null)
    if (selection.type === 'group' && selection.groupId != null) {
      return collectGroupIds(selection.groupId).includes(gid ?? -1)
    }
    return true
  }
  const leaves: LeafCardVM[] = []
  for (const t of baseTables.value) {
    const gid = leafGroupId(t.baseId, t.baseGroupId)
    if (!inScope(gid)) continue
    leaves.push({
      key: `t:${t.id}`,
      kind: 'table',
      id: t.id,
      baseId: t.baseId,
      name: t.name,
      description: t.description || '',
      icon: t.icon,
      groupName: leafGroupName(t.baseId, t.baseGroupId),
      updatedAt: t.updatedAt,
      sortOrder: t.sortOrder ?? 0,
    })
  }
  for (const d of baseDashboards.value) {
    const gid = leafGroupId(d.baseId, d.baseGroupId)
    if (!inScope(gid)) continue
    leaves.push({
      key: `d:${d.id}`,
      kind: 'dashboard',
      id: d.id,
      baseId: d.baseId,
      name: d.name,
      description: '',
      groupName: leafGroupName(d.baseId, d.baseGroupId),
      updatedAt: d.updatedAt,
      sortOrder: d.sortOrder ?? 0,
    })
  }
  // 与树同口径：sort_order 混排，同序号数据表在前、再按 id
  return leaves.sort(
    (a, b) =>
      a.sortOrder - b.sortOrder ||
      (a.kind === 'table' ? 0 : 1) - (b.kind === 'table' ? 0 : 1) ||
      a.id - b.id,
  )
})

function openLeaf(leaf: LeafCardVM) {
  if (leaf.kind === 'table') {
    goToTable({ baseId: leaf.baseId, tableId: leaf.id })
  } else {
    goToDashboard({ baseId: leaf.baseId, dashboardId: leaf.id })
  }
}

/** 右侧标题：全部 / 分组名 / 未分组 */
const scopeTitle = computed(() => {
  const selection = treeSelection.value
  if (selection.type === 'group' && selection.groupId != null) {
    return flatGroups.value.find((group) => group.id === selection.groupId)?.name ?? '分组'
  }
  if (selection.type === 'ungrouped') {
    const parent = selection.groupId == null
      ? null
      : flatGroups.value.find((group) => group.id === selection.groupId)
    return parent ? `${parent.name} / 未分组` : '未分组'
  }
  return '全部'
})

/** 选中分组被删除后回退到「全部」，避免右侧停留在空范围 */
function resetSelectionIfMissing() {
  const selection = treeSelection.value
  if (selection.type !== 'group' || selection.groupId == null) return
  if (!flatGroups.value.some((group) => group.id === selection.groupId)) {
    treeSelection.value = { type: 'all', groupId: null }
  }
}

function handleTreeSelect(payload: { type: 'all' | 'group' | 'ungrouped'; groupId: number | null }) {
  activeTable.value = null
  activeDashboard.value = null
  treeSelection.value = payload
}

/* ---------------- 分组管理 ---------------- */

async function handleCreateGroup(payload: { name: string; parentId: number | null }) {
  // 同名检测：同级分组名不能重复
  const duplicate = flatGroups.value.some(
    (g) =>
      (g.parentId ?? null) === (payload.parentId ?? null) &&
      g.name.toLowerCase() === payload.name.toLowerCase(),
  )
  if (duplicate) {
    ElMessage.error(`同级已存在同名分组「${payload.name}」`)
    return
  }
  try {
    await createBaseGroup({ name: payload.name, parentId: payload.parentId })
    ElMessage.success('分组已创建')
    await loadGroups()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '创建分组失败'))
  }
}

async function handleRenameGroup(payload: { id: number; name: string }) {
  try {
    await renameBaseGroup(payload.id, payload.name)
    ElMessage.success('分组已重命名')
    await loadGroups()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '重命名分组失败'))
  }
}

async function handleRenameBase(payload: { id: number; name: string }) {
  try {
    await updateBase(payload.id, { name: payload.name })
    ElMessage.success('多维表格已重命名')
    await loadBases()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '重命名多维表格失败'))
  }
}

async function handleDeleteGroup(id: number) {
  try {
    await deleteBaseGroup(id)
    ElMessage.success('分组已删除')
    // 分组删除后其子分组与 Base 会一并上移到父级，两处一起刷新
    await Promise.all([loadGroups(), loadBases()])
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '删除分组失败'))
  }
}

async function handleMoveGroup(payload: { id: number; parentId: number | null }) {
  try {
    await moveBaseGroup(payload.id, { parentId: payload.parentId })
    ElMessage.success('分组已移动')
    await loadGroups()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '移动分组失败'))
  }
}

/** 分组同级排序（长按拖拽）：全量回写某父级下的分组顺序 */
async function handleReorderGroups(orderedIds: number[]) {
  // 先乐观更新本地树，避免等接口返回时顺序跳动
  applyGroupOrder(baseGroups.value, orderedIds)
  try {
    await sortBaseGroups(orderedIds)
    await loadGroups()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '分组排序失败'))
    await loadGroups()
  }
}

/** Base 同级排序（长按拖拽）：全量回写某分组下的 Base 顺序 */
async function handleReorderBases(orderedIds: number[]) {
  const rank = new Map(orderedIds.map((id, idx) => [id, idx]))
  bases.value = [...bases.value].sort(
    (a, b) => (rank.get(a.id) ?? a.sortOrder) - (rank.get(b.id) ?? b.sortOrder),
  )
  try {
    await sortBases(orderedIds)
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '多维表格排序失败'))
    await loadBases()
  }
}

/**
 * 叶子节点（数据表 / 仪表盘）同级排序：目录树里两者是兄弟节点，共用一条 sort_order 序列。
 * 不做本地数组重排 —— 树的顺序由 sortOrder 计算属性推导，写回新序号即可触发重渲染。
 */
async function handleReorderLeaves(payload: {
  baseId: number
  leaves: Array<{ kind: 'table' | 'dashboard'; id: number }>
}) {
  const rank = new Map(payload.leaves.map((leaf, idx) => [`${leaf.kind}:${leaf.id}`, idx]))
  baseTables.value = baseTables.value.map((table) => {
    const next = rank.get(`table:${table.id}`)
    return next === undefined ? table : { ...table, sortOrder: next }
  })
  baseDashboards.value = baseDashboards.value.map((dashboard) => {
    const next = rank.get(`dashboard:${dashboard.id}`)
    return next === undefined ? dashboard : { ...dashboard, sortOrder: next }
  })
  try {
    await sortLeaves(payload.baseId, payload.leaves)
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '排序失败'))
    await Promise.all([loadBaseTables(), loadBaseDashboards()])
  }
}

/** 把接口返回的嵌套分组树按 orderedIds 就地重排（递归各层） */
function applyGroupOrder(groups: BitableBaseGroup[], orderedIds: number[]) {
  const rank = new Map(orderedIds.map((id, idx) => [id, idx]))
  const sortLevel = (list: BitableBaseGroup[]) => {
    list.sort((a, b) => (rank.get(a.id) ?? a.sortOrder) - (rank.get(b.id) ?? b.sortOrder))
    list.forEach((g) => {
      if (g.children?.length) sortLevel(g.children)
    })
  }
  sortLevel(groups)
}

async function handleMoveBase(payload: { baseId: number; groupId: number | null }) {
  try {
    await moveBaseToGroup(payload.baseId, payload.groupId)
    ElMessage.success('已移动到目标分组')
    // 分组计数依赖 Base 归属，两处一起刷新
    await Promise.all([loadGroups(), loadBases()])
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '移动多维表格失败'))
  }
}

/**
 * 叶子跨层级移动：拖拽单个数据表/仪表盘到别的分组，只动该叶子自身。
 * 后端把它挂到目标层级末尾；这里刷新叶子与分组计数即可，Base 归属不变。
 */
async function handleMoveLeaf(payload: { kind: 'table' | 'dashboard'; id: number; targetGroupId: number | null }) {
  try {
    await moveLeaf(payload.kind, payload.id, payload.targetGroupId)
    ElMessage.success('已移动到目标分组')
    // 叶子归属变了，目标/源分组的计数与列表都要刷新；Base 归属不变
    await Promise.all([loadBaseTables(), loadBaseDashboards(), loadGroups()])
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '移动失败'))
  }
}

/* ---------------- Base CRUD ---------------- */

function goToEditor(id: number) {
  router.push({ name: 'BitableEditor', params: { baseId: id } })
}

/** 仪表盘与数据表一致：直接在右侧工作台内嵌打开，不跳转路由 */
function goToDashboard(payload: { baseId: number; dashboardId?: number }) {
  activeTable.value = null
  activeDashboard.value = { baseId: payload.baseId, dashboardId: payload.dashboardId }
}

/** 点击「仪表盘」直接自动创建：无需选库弹框，命名「仪表盘」并自动去重，落地到空白搭建首页 */
async function quickCreateDashboard(baseId: number | null) {
  const targetBaseId = baseId
    ?? activeDashboard.value?.baseId
    ?? activeTable.value?.baseId
    ?? bases.value[0]?.id
  if (!targetBaseId) {
    ElMessage.warning('请先创建一个多维表格')
    return
  }
  const groupScope = bases.value.find((b) => b.id === targetBaseId)?.groupId ?? null
  const name = uniquifyName('仪表盘', scopedDashboardNames(groupScope))
  creatingDashboard.value = true
  try {
    const id = await createDashboard(targetBaseId, name)
    ElMessage.success(`已创建空白仪表盘「${name}」`)
    await loadBaseDashboards()
    // 落地到「搭建你的仪表盘」首页；prop 仅在挂载时读取，渲染完成后复位
    quickCreateLanding.value = true
    goToDashboard({ baseId: targetBaseId, dashboardId: id })
    nextTick(() => { quickCreateLanding.value = false })
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '创建失败'))
  } finally {
    creatingDashboard.value = false
  }
}

/** 分组节点「新建仪表盘」：优先落分组内第一个多维表格；空分组先建一个承载再建仪表盘 */
async function quickCreateDashboardInGroup(groupId: number) {
  const basesInGroup = bases.value.filter((b) => (b.groupId ?? null) === groupId)
  let targetBaseId = basesInGroup[0]?.id ?? null
  try {
    if (!targetBaseId) {
      const name = uniquifyName('新建多维表格', scopedTableNames(groupId))
      const base = await createBase({ name, groupId } as BitableBaseCreateDTO & { groupId?: number | null })
      targetBaseId = Number(base)
      ElMessage.success(`已在分组下创建多维表格「${name}」`)
      await loadBases()
    }
    await quickCreateDashboard(targetBaseId)
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '创建失败'))
  }
}

async function handleRenameDashboard(payload: { id: number; name: string }) {
  try {
    await renameDashboard(payload.id, payload.name)
    await loadBaseDashboards()
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '重命名失败'))
  }
}

async function handleDeleteDashboard(payload: { id: number; baseId: number; name: string }) {
  try {
    await deleteDashboard(payload.id)
    ElMessage.success('已删除')
    if (activeDashboard.value?.dashboardId === payload.id) {
      activeDashboard.value = null
    }
    await loadBaseDashboards()
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '删除失败'))
  }
}

function handleTemplateCreate(baseId: number) {
  showTemplateGallery.value = false
  loadBases()
  goToEditor(baseId)
}

/** 新建：可带默认分组（树上的「新建多维表格」/ 右上角按钮） */
function handleCreate(groupId: number | null = null) {
  editingBase.value = null
  form.value = { name: '', description: '', groupId: groupId ?? (treeSelection.value.type === 'group' ? treeSelection.value.groupId : null) }
  showDialog.value = true
}

/** 树节点上的「新建多维表格」：固定放入对应分组（null = 未分组） */
function handleCreateInGroup(groupId: number | null) {
  editingBase.value = null
  form.value = { name: '', description: '', groupId }
  showDialog.value = true
}

/* ---------------- 叶子卡片：菜单 / 行内重命名 / 移动分组 ---------------- */

function handleLeafAction(command: string, leaf: LeafCardVM) {
  if (command === 'rename') {
    startLeafRename(leaf)
  } else if (command === 'move') {
    handleLeafMovePrompt(leaf)
  } else if (command === 'delete') {
    if (leaf.kind === 'table') {
      handleDeleteTable({ id: leaf.id, baseId: leaf.baseId, name: leaf.name })
    } else {
      handleDeleteDashboard({ id: leaf.id, baseId: leaf.baseId, name: leaf.name })
    }
  }
}

const renamingLeaf = ref<{ key: string } | null>(null)
const renamingLeafName = ref('')

function startLeafRename(leaf: LeafCardVM) {
  renamingLeaf.value = { key: leaf.key }
  renamingLeafName.value = leaf.name
  nextTick(() => {
    const input = document.querySelector<HTMLInputElement>('.leaf-card__name-input')
    input?.focus()
    input?.select()
  })
}

function cancelLeafRename() {
  renamingLeaf.value = null
  renamingLeafName.value = ''
}

function commitLeafRename(leaf: LeafCardVM) {
  if (renamingLeaf.value?.key !== leaf.key) return
  const name = renamingLeafName.value.trim()
  cancelLeafRename()
  if (!name) {
    ElMessage.warning('名称不能为空')
    return
  }
  if (name === leaf.name) return
  if (leaf.kind === 'table') {
    handleRenameTable({ id: leaf.id, name })
  } else {
    handleRenameDashboard({ id: leaf.id, name })
  }
}

const pendingLeafMove = ref<{ kind: 'table' | 'dashboard'; id: number; name: string } | null>(null)

function handleLeafMovePrompt(leaf: LeafCardVM) {
  pendingLeafMove.value = { kind: leaf.kind, id: leaf.id, name: leaf.name }
  moveTargetGroupId.value = null
  moveDialogVisible.value = true
}

async function confirmMoveBase() {
  moveDialogVisible.value = false
  // 叶子（数据表/仪表盘）移动：走叶子归属，Base 归属不变
  if (pendingLeafMove.value) {
    const target = moveTargetGroupId.value != null && moveTargetGroupId.value >= 0 ? moveTargetGroupId.value : null
    const payload = { kind: pendingLeafMove.value.kind, id: pendingLeafMove.value.id, targetGroupId: target }
    pendingLeafMove.value = null
    await handleMoveLeaf(payload)
    return
  }
  if (moveBaseId.value != null) {
    await handleMoveBase({ baseId: moveBaseId.value, groupId: moveTargetGroupId.value ?? null })
  }
}

function handleDeleteBase(base: BitableBase) {
  ElMessageBox.confirm(`确定删除多维表格「${base.name}」吗？此操作不可恢复。`, '删除确认', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(async () => {
    try {
      await deleteBase(base.id)
      ElMessage.success('删除成功')
      await Promise.all([loadGroups(), loadBases()])
    } catch (e: any) {
      ElMessage.error(resolveErrorMessage(e, '删除失败'))
    }
  }).catch(() => {})
}

async function handleSave() {
  const name = form.value.name.trim()
  if (!name) {
    ElMessage.warning('请输入名称')
    return
  }
  // 同名检测：目标分组（含未分组）下不能有同名多维表格
  const scopeGroupId = editingBase.value ? (editingBase.value.groupId ?? null) : (form.value.groupId ?? null)
  const selfId = editingBase.value?.id ?? -1
  if (bases.value.some((b) => b.id !== selfId && (b.groupId ?? null) === scopeGroupId && b.name.toLowerCase() === name.toLowerCase())) {
    ElMessage.error(`同级已存在同名多维表格「${name}」`)
    return
  }
  saving.value = true
  try {
    if (editingBase.value) {
      await updateBase(editingBase.value.id, form.value)
      ElMessage.success('更新成功')
    } else {
      const created = await createBase(form.value)
      ElMessage.success('创建成功')
      // 创建时指定了分组则直接归组
      if (form.value.groupId != null && created?.id) {
        try {
          await moveBaseToGroup(created.id, form.value.groupId)
        } catch { /* 归组失败不阻断创建流程 */ }
      }
    }
    showDialog.value = false
    await Promise.all([loadGroups(), loadBases()])
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '保存失败'))
  } finally {
    saving.value = false
  }
}

function formatDate(dateStr: string): string {
  if (!dateStr) return ''
  try {
    return new Date(dateStr).toLocaleDateString('zh-CN')
  } catch {
    return dateStr
  }
}

/** 新建弹窗的分组选择项（含「未分组」占位） */
const groupOptions = computed(() => {
  const build = (parentId: number | null, depth: number): unknown[] =>
    flatGroups.value
      .filter((g) => (g.parentId ?? null) === parentId)
      .map((g) => {
        const children = build(g.id, depth + 1)
        return {
          value: g.id,
          label: `${'　'.repeat(depth)}${g.name}`,
          children: children.length ? children : undefined,
        }
      })
  return build(null, 0)
})

/** 移动弹窗的分组选择项（-1 = 移出分组/根层级未分组） */
const moveOptions = computed(() => {
  const build = (parentId: number | null, depth: number): unknown[] =>
    flatGroups.value
      .filter((g) => (g.parentId ?? null) === parentId)
      .map((g) => {
        const children = build(g.id, depth + 1)
        return {
          value: g.id,
          label: `${'　'.repeat(depth)}${g.name}`,
          children: children.length ? children : undefined,
        }
      })
  return [{ value: -1, label: '未分组（根层级）', children: build(null, 1) }]
})

onMounted(() => {
  loadBases()
  loadGroups()
})
</script>

<style scoped lang="scss">
.bitable-layout {
  position: relative;
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: var(--spacing-md);
  align-items: stretch;
  min-height: 0;
}

.bitable-sidebar {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  overflow: hidden;
  min-height: 480px;
  /* 偏移 = 顶部工具条 + 页面内边距（实测 76px）+ 底部页边距 20px，过大会在页面底部留白 */
  max-height: calc(100vh - 96px);
  position: sticky;
  top: var(--spacing-md);
}

.bitable-main {
  min-width: 0;
}

.bitable-sidebar {
  transition: width 0.2s ease;
}

/* 收起后左侧保留一条窄轨道承载展开按钮：按钮走正常流而非绝对定位，
   否则它会浮在右侧内容左上角，正好压住仪表盘选择器 / 列表标题 */
.bitable-layout.is-sidebar-collapsed {
  grid-template-columns: auto minmax(0, 1fr);
}

.bitable-sidebar.is-collapsed {
  display: none;
}

.bitable-sidebar__expand {
  align-self: start;
  margin-top: 10px;
  z-index: 20;
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  color: var(--color-text-secondary);
  border-radius: 8px;
  padding: 5px 7px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  box-shadow: var(--shadow-sm, 0 1px 4px rgba(15, 23, 42, 0.12));

  &:hover {
    color: var(--color-primary);
    border-color: var(--color-primary);
  }
}

.bitable-embedded {
  height: calc(100vh - 96px);
  min-height: 480px;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  overflow: hidden;
  background: var(--color-surface);
}

.bitable-dialog__hint {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-tertiary);
  line-height: 1.6;
}

.bitable-list__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-md);
}

.bitable-list__scope {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  min-width: 0;

  .bitable-list__scope-title {
    font-size: var(--font-size-md);
    font-weight: var(--font-weight-semibold);
    color: var(--color-text-primary);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.bitable-list__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--spacing-md);
}

.base-card {
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;

  &:hover {
    transform: translateY(-2px);
    box-shadow: var(--shadow-lg);
  }

  .base-card__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--spacing-sm);
    margin-bottom: var(--spacing-sm);
  }

  .base-card__name {
    font-size: var(--font-size-md);
    font-weight: var(--font-weight-semibold);
    color: var(--color-text-primary);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  /* 卡片行内重命名输入框：与名称字号一致 */
  .base-card__name-input {
    flex: 1;
    min-width: 0;
    font-size: var(--font-size-md);
    font-weight: var(--font-weight-semibold);
    color: var(--color-text-primary);
    background: var(--color-surface);
    border: 1px solid var(--color-primary);
    border-radius: var(--border-radius-sm, 4px);
    padding: 2px 6px;
    outline: none;

    &:focus {
      box-shadow: 0 0 0 2px var(--color-primary-bg, rgba(37, 99, 235, 0.15));
    }
  }

  .base-card__desc {
    font-size: var(--font-size-sm);
    color: var(--color-text-secondary);
    margin: var(--spacing-xs) 0;
    overflow: hidden;
    text-overflow: ellipsis;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    min-height: 2.4em;
  }

  .base-card__footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--spacing-xs);
    font-size: var(--font-size-xs);
    color: var(--color-muted-text);
    margin-top: var(--spacing-sm);
    border-top: 1px solid var(--color-border);
    padding-top: var(--spacing-sm);

    span {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }
}

/* 叶子卡片：数据表/仪表盘，与目录树同构 */
.leaf-card {
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;

  &:hover {
    transform: translateY(-2px);
    box-shadow: var(--shadow-lg);

    .leaf-card__name {
      color: var(--color-primary);
    }
  }

  .leaf-card__header {
    display: flex;
    align-items: center;
    gap: var(--spacing-sm);
    margin-bottom: var(--spacing-sm);
  }

  .leaf-card__icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 28px;
    height: 28px;
    flex-shrink: 0;
    border-radius: 8px;
    font-size: 16px;
    color: var(--color-primary);
    background: var(--color-primary-bg, rgba(37, 99, 235, 0.1));

    &--dashboard {
      color: var(--color-type-dashboard, #d97706);
      background: var(--color-warning-bg, rgba(217, 119, 6, 0.12));
    }
  }

  .leaf-card__name {
    flex: 1;
    min-width: 0;
    font-size: var(--font-size-md);
    font-weight: var(--font-weight-semibold);
    color: var(--color-text-primary);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    transition: color 0.15s ease;
  }

  .leaf-card__name-input {
    flex: 1;
    min-width: 0;
    font-size: var(--font-size-md);
    font-weight: var(--font-weight-semibold);
    color: var(--color-text-primary);
    background: var(--color-surface);
    border: 1px solid var(--color-primary);
    border-radius: var(--border-radius-sm, 4px);
    padding: 2px 6px;
    outline: none;

    &:focus {
      box-shadow: 0 0 0 2px var(--color-primary-bg, rgba(37, 99, 235, 0.15));
    }
  }

  .leaf-card__desc {
    font-size: var(--font-size-sm);
    color: var(--color-text-secondary);
    margin: var(--spacing-xs) 0;
    overflow: hidden;
    text-overflow: ellipsis;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    min-height: 2.4em;
  }

  .leaf-card__footer {
    display: flex;
    align-items: center;
    gap: var(--spacing-sm);
    font-size: var(--font-size-xs);
    color: var(--color-muted-text);
    margin-top: var(--spacing-sm);
    border-top: 1px solid var(--color-border);
    padding-top: var(--spacing-sm);

    span {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .leaf-card__kind {
      flex-shrink: 0;
      color: var(--color-primary);

      &--dashboard {
        color: var(--color-type-dashboard, #d97706);
      }
    }
  }
}
</style>
