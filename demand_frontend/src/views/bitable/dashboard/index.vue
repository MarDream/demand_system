<template>
  <div ref="pageRef" class="dash-editor" :class="{ 'dash-editor--fullscreen': isFullscreen }">
    <!-- 顶部工具条 -->
    <header class="dash-editor__header">
      <el-button v-if="!embedded" link @click="router.push('/bitable')">
        <el-icon><ArrowLeft /></el-icon>
      </el-button>

      <!-- 嵌入多维表格时左侧目录树已承担仪表盘切换，选择器仅在独立路由页保留 -->
      <el-select
        v-if="!embedded"
        v-model="activeDashboardId"
        placeholder="选择仪表盘"
        style="width: 200px"
        @change="onSelectDashboard"
      >
        <el-option v-for="d in dashboards" :key="d.id" :label="d.name" :value="d.id" />
      </el-select>

      <div class="dash-editor__spacer" />

      <span v-if="editing" class="dash-editor__save-state" :class="{ 'is-dirty': dirty }">
        {{ saving ? '保存中…' : dirty ? '未保存更改' : '已保存' }}
      </span>

      <WidgetPalette v-if="editing" @select="(t) => addWidget(t)">
        <el-button type="primary" plain size="small">
          <el-icon><Plus /></el-icon>&nbsp;添加图表
        </el-button>
      </WidgetPalette>

      <el-tooltip :content="editing ? '完成编辑' : '编辑大屏'" placement="top">
        <el-button size="small" circle :type="editing ? 'primary' : 'default'" @click="toggleEditing">
          <el-icon><Check v-if="editing" /><Edit v-else /></el-icon>
        </el-button>
      </el-tooltip>

      <el-tooltip content="全屏展示" placement="top">
        <el-button size="small" circle @click="toggleFullscreen">
          <el-icon><FullScreen /></el-icon>
        </el-button>
      </el-tooltip>

      <el-tooltip content="刷新数据" placement="top">
        <el-button size="small" circle @click="refreshData">
          <el-icon><Refresh /></el-icon>
        </el-button>
      </el-tooltip>

      <!-- 高级权限：与刷新数据同排，配置当前多维表格的查看/编辑权限 -->
      <el-tooltip content="高级权限" placement="top">
        <el-button size="small" circle class="dash-editor__perm" @click="openPermissionDialog">
          <el-icon><Lock /></el-icon>
        </el-button>
      </el-tooltip>
    </header>

    <!-- 主体：创建界面（AI 一键生成 / 手动搭建） 或 画布 + 属性面板 -->
    <div class="dash-editor__body">
      <!-- 搭建你的仪表盘 -->
      <div v-if="showCreateHero" class="dash-create">
        <div class="dash-create__panel">
          <h2 class="dash-create__title">搭建你的 <span>仪表盘</span></h2>
          <div class="dash-create__cards">
            <div class="dash-create__card dash-create__card--ai">
              <div class="dash-create__card-title">
                <i class="ri-sparkling-2-line" />AI 一键生成
              </div>
              <p class="dash-create__card-desc">
                基于当前多维表格的数据表结构，智能挑选统计数字与图表，并完成分区布局。
              </p>
              <el-input
                v-model="aiDescription"
                type="textarea"
                :rows="2"
                maxlength="200"
                show-word-limit
                placeholder="可选：描述关注重点，如「任务完成情况、负责人排行与优先级分布」"
              />
              <el-button type="primary" :loading="aiGenerating" @click="handleAiGenerate">
                <el-icon><MagicStick /></el-icon>&nbsp;一键生成仪表盘
              </el-button>
            </div>
            <div class="dash-create__card">
              <div class="dash-create__card-title">
                <i class="ri-drag-drop-line" />手动搭建
              </div>
              <p class="dash-create__card-desc">
                从空白开始，通过组件面板自由选择统计数字、图表、排行榜等组件搭建布局。
              </p>
              <el-button @click="handleManualCreate">手动搭建</el-button>
            </div>
          </div>
          <el-button
            v-if="activeDashboardId && widgets.length > 0"
            link
            class="dash-create__back"
            @click="createMode = false"
          >
            <el-icon><ArrowLeft /></el-icon>返回当前仪表盘
          </el-button>
        </div>
      </div>

      <template v-else>
        <div class="dash-editor__scroll">
          <WidgetCanvas
            :rows="rows"
            :widgets="widgets"
            :data-map="dataMap"
            :editing="editing"
            :selected-id="selectedId"
            @select="onSelect"
            @action="onAction"
            @move-widget="moveWidget"
            @add-widget="addWidgetToRow"
            @update-row-title="updateRowTitle"
            @toggle-section="toggleSection"
            @remove-row="removeRow"
            @add-row="addRow"
            @update-layout="updateLayout"
          />
        </div>

        <WidgetPropertyPanel
          v-if="editing && selectedWidget"
          :key="String(selectedWidget.id)"
          :widget="selectedWidget"
          :tables="tables"
          :fields-by-table="fieldsByTable"
          @close="selectedId = null"
          @change="markDirty"
          @ensure-fields="ensureFields"
          @delete="deleteWidget(selectedWidget!.id)"
        />
      </template>
    </div>

    <!-- 高级权限：配置当前多维表格的查看/编辑/字段级权限 -->
    <PermissionManageDialog
      v-model="showPermissionDialog"
      :base-id="baseId"
      :tables="tables"
      :table-groups="tableGroups"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Check, Edit, FullScreen, Lock, MagicStick, Plus, Refresh } from '@element-plus/icons-vue'
import {
  listDashboards, createDashboard,
  listDashboardWidgets, saveDashboardWidgets, getDashboardData, aiGenerateDashboard,
  listTables, listFields, listTableGroups,
} from '@/api/modules/bitable'
import PermissionManageDialog from '../components/PermissionManageDialog.vue'
import WidgetCanvas from './WidgetCanvas.vue'
import WidgetPalette from './WidgetPalette.vue'
import WidgetPropertyPanel from './WidgetPropertyPanel.vue'
import type { DashboardRow, WidgetInstance } from './widgetDefs'
import { createWidget, getWidgetDef, newRowId } from './widgetDefs'
import { chartRegistry } from './chartRegistry'
import { cssVar } from './chartOptions'

const props = defineProps<{ baseId?: number; dashboardId?: number; embedded?: boolean; startInCreateMode?: boolean }>()
const emit = defineEmits<{ changed: [] }>()
const route = useRoute()
const router = useRouter()
const baseId = props.baseId ?? Number(route.params.baseId)

const loading = ref(false)
const dashboards = ref<any[]>([])
const activeDashboardId = ref<number | null>(null)
const tables = ref<any[]>([])
const fieldsByTable = ref<Record<number, any[]>>({})

const widgets = ref<WidgetInstance[]>([])
const rows = ref<DashboardRow[]>([])
const dataMap = ref<Record<string, any>>({})

const editing = ref(false)
const selectedId = ref<number | string | null>(null)
const saving = ref(false)
const dirty = ref(false)

const selectedWidget = computed(() => widgets.value.find((w) => w.id === selectedId.value) || null)

// ==================== 新建仪表盘界面（AI 一键生成 / 手动搭建） ====================

/** 由「+菜单直接自动新建」落地：仪表盘已建好（空白），手动搭建不再二次创建 */
const landedFromQuickCreate = Boolean(props.startInCreateMode)
const createMode = ref(landedFromQuickCreate || String(route.query.create || '') === '1')
const aiDescription = ref('')
const aiGenerating = ref(false)
/** 落地/新建模式显示搭建首页；已有仪表盘但还没有任何组件时（空白仪表盘）查看态同样显示，避免渲染成空白画布 */
const showCreateHero = computed(() =>
  createMode.value
  || (!activeDashboardId.value && !loading.value)
  || (!loading.value && !editing.value && widgets.value.length === 0),
)

async function handleAiGenerate() {
  aiGenerating.value = true
  try {
    const id = await aiGenerateDashboard(baseId, aiDescription.value.trim())
    ElMessage.success('AI 仪表盘已生成')
    dashboards.value = await listDashboards(baseId)
    emit('changed')
    createMode.value = false
    aiDescription.value = ''
    clearCreateQuery()
    activeDashboardId.value = id
    await loadDashboard(id)
  } catch (e: any) {
    ElMessage.error(e?.message || 'AI 生成失败，请重试')
  } finally {
    aiGenerating.value = false
  }
}

async function handleManualCreate() {
  if (landedFromQuickCreate) {
    // 自动新建的空白仪表盘已就位：直接进入编辑模式，不再弹框新建
    createMode.value = false
    clearCreateQuery()
    editing.value = true
    return
  }
  await handleCreateDashboard()
  if (activeDashboardId.value) {
    createMode.value = false
    clearCreateQuery()
    editing.value = true
  }
}

function onSelectDashboard(id: number) {
  createMode.value = false
  clearCreateQuery()
  loadDashboard(id)
}

function clearCreateQuery() {
  if (route.query.create) {
    router.replace({ query: { ...route.query, create: undefined } })
  }
}

// ==================== 加载 ====================

onMounted(async () => {
  loading.value = true
  try {
    const [dashList, tableList] = await Promise.all([listDashboards(baseId), listTables(baseId)])
    dashboards.value = dashList
    tables.value = tableList
    const target = (props.dashboardId ?? Number(route.query.dashboardId)) || dashList[0]?.id
    if (target) {
      activeDashboardId.value = target
      await loadDashboard(target)
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '加载仪表盘失败')
  } finally {
    loading.value = false
  }
})

async function loadDashboard(id: number) {
  try {
    const [widgetList, data] = await Promise.all([
      listDashboardWidgets(id),
      getDashboardData(id),
    ])
    widgets.value = widgetList.map(serializeWidget)
    dataMap.value = Object.fromEntries((data || []).map((item: any) => [String(item.widgetId), item.data]))
    const dashRecord = dashboards.value.find((d) => d.id === id)
    rows.value = parseRows(dashRecord?.layoutConfig)
    normalizeLayout()
    // 预取组件引用的数据表字段（含多数据源模式的各源表）
    const tableIds = new Set<number>()
    widgets.value.forEach((w) => {
      if (w.dataSourceConfig?.tableId) tableIds.add(w.dataSourceConfig.tableId)
      w.dataSourceConfig?.sources?.forEach((s) => {
        if (s.tableId) tableIds.add(s.tableId)
      })
    })
    tableIds.forEach((tid) => ensureFields(tid))
    selectedId.value = null
  } catch (e: any) {
    ElMessage.error(e?.message || '加载仪表盘数据失败')
  }
}

function serializeWidget(w: any): WidgetInstance {
  const parse = (raw: any) => {
    if (!raw) return {}
    if (typeof raw === 'string') {
      try { return JSON.parse(raw) } catch { return {} }
    }
    return raw
  }
  const dataSourceConfig = parse(w.dataSourceConfig)
  const def = getWidgetDef(w.type)
  return {
    id: w.id,
    type: w.type,
    title: w.title || '',
    dataSourceConfig,
    displayConfig: parse(w.displayConfig),
    layoutConfig: { rowId: undefined, span: def.defaultSpan, height: def.defaultHeight, ...parse(w.layoutConfig) },
    sortNo: w.sortNo,
  }
}

function parseRows(layoutConfig: string | null | undefined): DashboardRow[] {
  if (!layoutConfig) return []
  try {
    const parsed = typeof layoutConfig === 'string' ? JSON.parse(layoutConfig) : layoutConfig
    if (Array.isArray(parsed?.rows)) {
      return parsed.rows
        .filter((r: any) => r && typeof r.id === 'string' && Array.isArray(r.widgets))
        .map((r: any) => ({ id: r.id, title: r.title ?? null, widgets: [...r.widgets] }))
    }
  } catch { /* 忽略损坏的布局，走兜底 */ }
  return []
}

/** 布局一致性兜底：行引用的组件必须存在；组件必须归属一行；至少一行 */
function normalizeLayout() {
  const ids = new Set(widgets.value.map((w) => w.id))
  rows.value.forEach((row) => {
    row.widgets = row.widgets.filter((id) => ids.has(id))
  })
  rows.value = rows.value.filter((row, idx) => rows.value.findIndex((r) => r.id === row.id) === idx)
  const placed = new Set(rows.value.flatMap((r) => r.widgets))
  const orphans = widgets.value.filter((w) => !placed.has(w.id))
  if (orphans.length) {
    const target = rows.value[rows.value.length - 1]
    if (target) {
      target.widgets.push(...orphans.map((w) => w.id))
    } else {
      rows.value.push({ id: newRowId(), title: null, widgets: orphans.map((w) => w.id) })
    }
  }
  if (!rows.value.length) {
    rows.value.push({ id: newRowId(), title: null, widgets: [] })
  }
  // 同步组件行归属
  rows.value.forEach((row) => {
    row.widgets.forEach((wid) => {
      const w = widgets.value.find((item) => item.id === wid)
      if (w) w.layoutConfig.rowId = row.id
    })
  })
}

async function ensureFields(tableId: number) {
  if (!tableId || fieldsByTable.value[tableId]) return
  fieldsByTable.value[tableId] = []
  try {
    fieldsByTable.value[tableId] = await listFields(tableId)
  } catch { /* 字段加载失败时面板显示空列表 */ }
}

async function refreshData() {
  if (!activeDashboardId.value) return
  try {
    const data = await getDashboardData(activeDashboardId.value)
    dataMap.value = Object.fromEntries((data || []).map((item: any) => [String(item.widgetId), item.data]))
    ElMessage.success('数据已刷新')
  } catch (e: any) {
    ElMessage.error(e?.message || '刷新失败')
  }
}

// ==================== 编辑与保存 ====================

let saveTimer: number | undefined
watch([widgets, rows], () => {
  if (!editing.value) return
  dirty.value = true
  window.clearTimeout(saveTimer)
  saveTimer = window.setTimeout(saveAll, 600)
}, { deep: true })

function markDirty() {
  dirty.value = true
  window.clearTimeout(saveTimer)
  saveTimer = window.setTimeout(saveAll, 600)
}

function toggleEditing() {
  editing.value = !editing.value
  if (!editing.value) {
    selectedId.value = null
    window.clearTimeout(saveTimer)
    if (dirty.value) saveAll()
  }
}

/** 按画布可视顺序生成保存载荷 */
function buildPayload() {
  const orderedIds = rows.value.flatMap((r) => r.widgets)
  const byId = new Map(widgets.value.map((w) => [w.id, w]))
  const ordered: WidgetInstance[] = []
  orderedIds.forEach((id) => {
    const w = byId.get(id)
    if (w) ordered.push(w)
  })
  widgets.value.forEach((w) => {
    if (!orderedIds.includes(w.id)) ordered.push(w)
  })
  return {
    widgets: ordered.map((w, idx) => ({
      id: typeof w.id === 'number' ? w.id : undefined,
      type: w.type,
      title: w.title,
      dataSourceConfig: w.dataSourceConfig,
      displayConfig: w.displayConfig,
      layoutConfig: w.layoutConfig,
      sortNo: idx,
    })),
    layoutConfig: {
      rows: rows.value.map((r) => ({ id: r.id, title: r.title, widgets: r.widgets })),
    },
    order: ordered.map((w) => w.id),
  }
}

async function saveAll() {
  if (!activeDashboardId.value || saving.value) return
  saving.value = true
  const { widgets: payload, layoutConfig, order } = buildPayload()
  try {
    await saveDashboardWidgets(activeDashboardId.value, { widgets: payload, layoutConfig })
    // 同步服务端生成的组件 id（临时 id → 真实 id），按 sortNo 一一对应
    const fresh = await listDashboardWidgets(activeDashboardId.value)
    const idMap = new Map<number | string, number>()
    order.forEach((oldId, idx) => {
      const f = fresh[idx]
      if (f && f.id !== oldId) idMap.set(oldId, f.id)
    })
    if (idMap.size) {
      widgets.value = widgets.value.map((w) => (idMap.has(w.id) ? { ...w, id: idMap.get(w.id)! } : w))
      rows.value = rows.value.map((row) => ({
        ...row,
        widgets: row.widgets.map((wid) => idMap.get(wid) ?? wid),
      }))
      if (selectedId.value && idMap.has(selectedId.value)) {
        selectedId.value = idMap.get(selectedId.value)!
      }
    }
    // 保存成功后统一刷新聚合数据，使配置变更即时生效
    const data = await getDashboardData(activeDashboardId.value)
    dataMap.value = Object.fromEntries((data || []).map((item: any) => [String(item.widgetId), item.data]))
    dirty.value = false
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

// ==================== 组件操作 ====================

function onSelect(widgetId: number | string) {
  selectedId.value = widgetId || null
}

function addWidget(type: string) {
  const target = rows.value[rows.value.length - 1]
  if (target) addWidgetToRow({ rowId: target.id, type })
  else addWidgetToRow({ rowId: newRowId(), type })
}

function addWidgetToRow(payload: { rowId: string; type: string }) {
  let row = rows.value.find((r) => r.id === payload.rowId)
  if (!row) {
    row = { id: payload.rowId, title: null, widgets: [] }
    rows.value.push(row)
  }
  const widget = createWidget(payload.type, row.id)
  widgets.value.push(widget)
  row.widgets.push(widget.id)
  selectedId.value = widget.id
  if (!editing.value) editing.value = true
}

function deleteWidget(widgetId: number | string) {
  const w = widgets.value.find((item) => item.id === widgetId)
  if (!w) return
  ElMessageBox.confirm(`确定删除组件「${w.title || getWidgetDef(w.type).label}」吗？`, '删除确认', { type: 'warning' })
    .then(() => {
      widgets.value = widgets.value.filter((item) => item.id !== widgetId)
      rows.value.forEach((row) => {
        row.widgets = row.widgets.filter((id) => id !== widgetId)
      })
      chartRegistry.delete(widgetId)
      if (selectedId.value === widgetId) selectedId.value = null
    })
    .catch(() => {})
}

function copyWidget(widgetId: number | string) {
  const source = widgets.value.find((w) => w.id === widgetId)
  if (!source) return
  const clone: WidgetInstance = JSON.parse(JSON.stringify(source))
  clone.id = `tmp_${Date.now()}_copy`
  clone.title = (source.title || getWidgetDef(source.type).label) + ' 副本'
  widgets.value.push(clone)
  const row = rows.value.find((r) => r.widgets.includes(widgetId))
  if (row) {
    const idx = row.widgets.indexOf(widgetId)
    row.widgets.splice(idx + 1, 0, clone.id)
    clone.layoutConfig.rowId = row.id
  }
  selectedId.value = clone.id
}

function renameWidget(widgetId: number | string) {
  const w = widgets.value.find((item) => item.id === widgetId)
  if (!w) return
  ElMessageBox.prompt('', '重命名组件', {
    inputValue: w.title,
    inputPattern: /\S+/,
    inputErrorMessage: '名称不能为空',
  })
    .then(({ value }) => {
      w.title = value.trim()
    })
    .catch(() => {})
}

function exportWidgetImage(widgetId: number | string) {
  const chart = chartRegistry.get(widgetId)
  if (!chart?.getDataURL) {
    ElMessage.warning('该组件暂不支持导出为图片')
    return
  }
  const url = chart.getDataURL({ pixelRatio: 2, backgroundColor: cssVar('--color-surface', '#ffffff') })
  const link = document.createElement('a')
  const w = widgets.value.find((item) => item.id === widgetId)
  link.download = `${w?.title || '图表'}.png`
  link.href = url
  link.click()
}

function onAction(payload: { type: string; widgetId: number | string }) {
  switch (payload.type) {
    case 'edit':
      selectedId.value = payload.widgetId
      break
    case 'rename':
      renameWidget(payload.widgetId)
      break
    case 'copy':
      copyWidget(payload.widgetId)
      break
    case 'export':
      exportWidgetImage(payload.widgetId)
      break
    case 'delete':
      deleteWidget(payload.widgetId)
      break
  }
}

function moveWidget(payload: { widgetId: number | string; rowId: string; index: number }) {
  if (payload.widgetId === '') return
  // 从原行移除
  rows.value.forEach((row) => {
    row.widgets = row.widgets.filter((id) => id !== payload.widgetId)
  })
  const target = rows.value.find((r) => r.id === payload.rowId)
  if (!target) return
  const index = Math.max(0, Math.min(payload.index, target.widgets.length))
  target.widgets.splice(index, 0, payload.widgetId)
  const w = widgets.value.find((item) => item.id === payload.widgetId)
  if (w) w.layoutConfig.rowId = payload.rowId
}

function updateLayout(payload: { widgetId: number | string; span?: number; height?: number }) {
  const w = widgets.value.find((item) => item.id === payload.widgetId)
  if (!w) return
  if (payload.span != null) w.layoutConfig.span = payload.span
  if (payload.height != null) w.layoutConfig.height = payload.height
}

// ==================== 行操作 ====================

function sectionName(): string {
  return `组合布局 ${rows.value.filter((r) => r.title).length + 1}`
}

function addRow() {
  rows.value.push({ id: newRowId(), title: sectionName(), widgets: [] })
}

function updateRowTitle(payload: { rowId: string; title: string }) {
  const row = rows.value.find((r) => r.id === payload.rowId)
  if (row) row.title = payload.title
}

function toggleSection(rowId: string) {
  const row = rows.value.find((r) => r.id === rowId)
  if (!row) return
  row.title = row.title ? null : sectionName()
}

function removeRow(rowId: string) {
  if (rows.value.length <= 1) return
  const idx = rows.value.findIndex((r) => r.id === rowId)
  if (idx < 0) return
  const [removed] = rows.value.splice(idx, 1)
  const fallback = rows.value[Math.max(0, idx - 1)]
  fallback.widgets.push(...removed.widgets)
  fallback.widgets.forEach((wid) => {
    const w = widgets.value.find((item) => item.id === wid)
    if (w) w.layoutConfig.rowId = fallback.id
  })
}

// ==================== 仪表盘管理 ====================

async function handleCreateDashboard() {
  try {
    const { value } = await ElMessageBox.prompt('', '新建仪表盘', {
      inputValue: '新建仪表盘',
      inputPattern: /\S+/,
      inputErrorMessage: '名称不能为空',
    })
    const typed = value.trim()
    const existing = dashboards.value.map((d: any) => d.name as string)
    // 同名检测：同一多维表格下仪表盘名唯一；默认名未改时自动去重
    if (typed !== '新建仪表盘' && existing.some((n) => (n || '').toLowerCase() === typed.toLowerCase())) {
      ElMessage.error(`已存在同名仪表盘「${typed}」`)
      return
    }
    const name = typed === '新建仪表盘' ? uniquifyDashboardName('新建仪表盘', existing) : typed
    const id = await createDashboard(baseId, name)
    dashboards.value = await listDashboards(baseId)
    emit('changed')
    activeDashboardId.value = id
    await loadDashboard(id)
  } catch (e: any) {
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
  }
}

/** 默认名去重：「默认名」「默认名 2」「默认名 3」… */
function uniquifyDashboardName(defaultName: string, existingNames: string[]): string {
  const taken = new Set(existingNames.map((n) => (n || '').toLowerCase()))
  if (!taken.has(defaultName.toLowerCase())) return defaultName
  let i = 2
  while (taken.has(`${defaultName} ${i}`.toLowerCase())) i += 1
  return `${defaultName} ${i}`
}

/* ---------------- 高级权限（重命名/删除已收敛到左侧目录树节点菜单） ---------------- */

const showPermissionDialog = ref(false)
const tableGroups = ref<any[]>([])

async function openPermissionDialog() {
  showPermissionDialog.value = true
  // 分组结构只在首次打开时拉取，供权限弹窗按分组归组数据表
  if (!tableGroups.value.length) {
    try {
      const res = await listTableGroups(baseId)
      tableGroups.value = Array.isArray(res) ? res : (res as any).data || []
    } catch {
      tableGroups.value = []
    }
  }
}

// 字段级权限可能改变可聚合的数据范围，关闭弹窗后刷新一次聚合数据
watch(showPermissionDialog, (open, wasOpen) => {
  if (!open && wasOpen && widgets.value.length) {
    refreshData()
  }
})

// ==================== 全屏 ====================

const pageRef = ref<HTMLElement>()
const isFullscreen = ref(false)

function toggleFullscreen() {
  if (document.fullscreenElement) {
    document.exitFullscreen()
  } else {
    pageRef.value?.requestFullscreen?.()
  }
}
function onFullscreenChange() {
  isFullscreen.value = !!document.fullscreenElement
}
onMounted(() => document.addEventListener('fullscreenchange', onFullscreenChange))
onBeforeUnmount(() => {
  document.removeEventListener('fullscreenchange', onFullscreenChange)
  window.clearTimeout(saveTimer)
})
</script>

<style scoped lang="scss">
.dash-editor {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--color-fill-secondary);
  overflow: hidden;

  &__header {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px 16px;
    background: var(--color-surface);
    border-bottom: 1px solid var(--color-border);
    flex-shrink: 0;
  }

  &__spacer {
    flex: 1;
  }

  &__save-state {
    font-size: 12px;
    color: var(--color-text-tertiary);

    &.is-dirty { color: var(--el-color-warning, #e6a23c); }
  }

  &__perm {
    color: var(--color-text-secondary);

    .el-icon { font-size: 15px; }

    &:hover {
      color: var(--color-primary);
      border-color: var(--color-primary);
    }
  }

  &__body {
    flex: 1;
    min-height: 0;
    display: flex;
  }

  &__scroll {
    flex: 1;
    min-width: 0;
    overflow-y: auto;
    padding: 16px;
  }

  &--fullscreen {
    background: var(--color-fill-secondary);

    .dash-editor__scroll {
      padding: 20px 24px;
    }
  }
}

// ==================== 新建仪表盘界面 ====================
.dash-create {
  flex: 1;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  overflow-y: auto;
  padding: 6vh 24px 24px;

  &__panel {
    width: 100%;
    max-width: 760px;
    display: flex;
    flex-direction: column;
    gap: 24px;
  }

  &__title {
    font-size: 28px;
    font-weight: 700;
    color: var(--color-text-primary);
    text-align: center;

    span { color: var(--color-primary); }
  }

  &__cards {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 16px;
  }

  &__card {
    display: flex;
    flex-direction: column;
    gap: 12px;
    padding: 20px;
    border: 1px solid var(--color-border);
    border-radius: var(--radius-lg, 14px);
    background: var(--color-surface);

    &--ai {
      border-color: color-mix(in srgb, var(--color-primary) 35%, var(--color-border));
      box-shadow: 0 4px 16px color-mix(in srgb, var(--color-primary) 12%, transparent);
    }

    .el-button { align-self: flex-start; }
  }

  &__card-title {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 15px;
    font-weight: 600;
    color: var(--color-text-primary);

    i {
      font-size: 18px;
      color: var(--color-primary);
    }
  }

  &__card-desc {
    margin: 0;
    font-size: 13px;
    line-height: 1.7;
    color: var(--color-text-secondary);
  }

  &__back {
    align-self: center;
  }
}
</style>
