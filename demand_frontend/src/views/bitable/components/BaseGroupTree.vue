<template>
  <div class="bgtree">
    <!-- 头部：数据 + [搜索][＋][收起] -->
    <div class="bgtree-header">
      <span class="bgtree-header__title">数据</span>
      <div class="bgtree-header__actions">
        <el-button v-if="!searchOpen" link class="bgtree-header__btn" title="搜索" @click="openSearch">
          <el-icon><Search /></el-icon>
        </el-button>
        <el-dropdown trigger="click" @command="(cmd: string) => onHeaderCommand(cmd)">
          <el-button link class="bgtree-header__btn" title="新建">
            <el-icon><Plus /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="import-excel">
                <i class="bgtree-menu__icon ri-file-excel-2-line" :style="{ color: 'var(--color-success)' }" />导入 Excel
              </el-dropdown-item>
              <el-dropdown-item command="create-table">
                <i class="bgtree-menu__icon ri-database-2-line" :style="{ color: 'var(--color-primary)' }" />新建数据表
              </el-dropdown-item>
              <el-dropdown-item command="create-dashboard">
                <el-icon class="bgtree-menu__icon" :style="{ color: 'var(--color-type-dashboard)' }"><DataAnalysis /></el-icon>新建仪表盘
              </el-dropdown-item>
              <el-dropdown-item command="create-group" divided>
                <el-icon class="bgtree-menu__icon" :style="{ color: 'var(--color-warning)' }"><FolderAdd /></el-icon>新建分组
              </el-dropdown-item>
              <el-dropdown-item command="create-template" divided>
                <el-icon class="bgtree-menu__icon"><Collection /></el-icon>从模板创建
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-button link class="bgtree-header__btn" title="收起目录" @click="emit('collapse')">
          <el-icon><DArrowLeft /></el-icon>
        </el-button>
      </div>
    </div>

    <!-- 搜索：点击放大镜图标展开 -->
    <div class="bgtree-search">
      <el-input
        v-if="searchOpen"
        ref="searchInputRef"
        v-model="keyword"
        size="small"
        placeholder="搜索数据表或分组"
        clearable
        :prefix-icon="Search"
        @blur="onSearchBlur"
        @keydown.escape="closeSearch"
      />
    </div>

    <!-- 树 -->
    <div ref="listEl" class="bgtree-list" :class="{ 'is-root-drop': dropState?.key === '__root__' }">
      <template v-for="node in nodes" :key="node.key">
        <!-- 行内新建分组输入 -->
        <div v-if="node.kind === 'editor'" class="bgtree-editor" :style="{ paddingLeft: `${6 + node.depth * 14}px` }">
          <el-input
            v-model="editorValue"
            size="small"
            placeholder="输入分组名称"
            @keyup.enter="commitCreate"
            @keyup.esc="cancelCreate"
            @blur="commitCreate"
          />
        </div>

        <div
          v-else
          class="bgtree-node"
          :class="[
            `bgtree-node--${node.kind}`,
            {
              'is-active': node.isActive,
              'is-drop-into': dropState?.key === node.key && dropState.zone === 'in',
              'is-drop-before': dropState?.key === node.key && dropState.zone === 'before',
              'is-drop-after': dropState?.key === node.key && dropState.zone === 'after',
              'is-reject': rejectKey === node.key,
              'is-dragging': dragKey === node.key,
              'is-pressing': pressKey === node.key,
              'is-draggable-ready': node.draggable,
            },
          ]"
          :style="{ paddingLeft: `${6 + node.depth * 14}px` }"
          :data-key="node.key"
          @pointerdown="handlePointerDown($event, node)"
          @click="handleClick($event, node)"
        >
          <!-- 展开箭头 -->
          <span
            class="bgtree-node__chev"
            :class="{ 'is-open': node.expanded, 'is-leaf': !node.hasChildren }"
            @click.stop="toggleExpand(node)"
          >
            <el-icon><ArrowRight /></el-icon>
          </span>

          <!-- 图标 -->
          <span class="bgtree-node__icon" :class="`bgtree-node__icon--${node.kind}`">
            <el-icon>
              <Grid v-if="node.kind === 'all'" />
              <FolderOpened v-else-if="node.kind === 'group' && node.expanded" />
              <Folder v-else-if="node.kind === 'group'" />
              <Folder v-else-if="node.kind === 'ungrouped'" />
              <DataAnalysis v-else-if="node.kind === 'dashboard'" />
              <i v-else-if="node.kind === 'table'" class="bgtree-node__remix" :class="tableIconClass(node)" />
              <Document v-else />
            </el-icon>
          </span>

          <!-- 名称（重命名时行内编辑；搜索时高亮命中片段） -->
          <span v-if="renaming?.key === node.key" class="bgtree-node__rename" @click.stop>
            <el-input
              ref="renameInputRef"
              v-model="renameValue"
              size="small"
              @keyup.enter="commitRename"
              @keyup.esc="cancelRename"
              @blur="commitRename"
            />
          </span>
          <span v-else class="bgtree-node__name" :title="node.name">
            <template v-for="(seg, i) in highlightSegments(node.name)" :key="i">
              <em v-if="seg.hit">{{ seg.text }}</em>
              <template v-else>{{ seg.text }}</template>
            </template>
          </span>

          <!-- 计数 -->
          <span
            v-if="node.kind !== 'base' && node.kind !== 'dashboard' && node.kind !== 'table' && renaming?.key !== node.key"
            class="bgtree-node__count"
          >{{ node.count }}</span>

          <!-- 悬浮工具 -->
          <span v-if="node.menu.length && renaming?.key !== node.key" class="bgtree-node__tools">
            <el-icon
              v-if="node.kind === 'group' && canManage"
              class="bgtree-node__tool"
              title="新建子分组"
              @click.stop="startCreate(node.id)"
            >
              <FolderAdd />
            </el-icon>
            <!-- 叶子节点（数据表/仪表盘）：图标操作，删除走行内红色确认（无弹框） -->
            <template v-if="isInlineActionNode(node)">
              <button
                v-if="confirmDeleteKey === node.key"
                type="button"
                class="bgtree-node__confirm"
                title="确认删除"
                @click.stop="confirmInlineDelete(node)"
              >
                确认
              </button>
              <template v-else>
                <el-icon class="bgtree-node__tool" title="重命名" @click.stop="beginInlineRename(node)">
                  <EditPen />
                </el-icon>
                <i
                  v-if="isFileTable(node)"
                  class="bgtree-node__tool bgtree-node__remix ri-database-2-line"
                  :style="{ color: 'var(--color-primary)' }"
                  title="转为数据表"
                  @click.stop="convertToTable(node)"
                />
                <el-icon class="bgtree-node__tool bgtree-node__tool--danger" title="删除" @click.stop="confirmDeleteKey = node.key">
                  <Delete />
                </el-icon>
              </template>
            </template>
            <el-dropdown
              v-else
              trigger="click"
              placement="bottom-end"
              @command="(cmd: string) => handleCommand(cmd, node)"
            >
              <span class="bgtree-node__tool" title="更多操作" @click.stop>
                <el-icon><MoreFilled /></el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item
                    v-for="item in node.menu"
                    :key="item.command"
                    :command="item.command"
                    :divided="item.divided"
                    :class="{ 'bgtree-menu__item--danger': item.danger }"
                  >
                    <el-icon
                      v-if="item.iconComponent"
                      class="bgtree-menu__icon"
                      :style="item.iconColor ? { color: item.iconColor } : undefined"
                    >
                      <component :is="item.iconComponent" />
                    </el-icon>
                    <i
                      v-else-if="item.icon"
                      class="bgtree-menu__icon"
                      :class="item.icon"
                      :style="item.iconColor ? { color: item.iconColor } : undefined"
                    />
                    {{ item.label }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </span>
        </div>
      </template>

      <div v-if="!nodes.length" class="bgtree-empty">
        <span v-if="keyword">没有匹配的多维表格或分组</span>
        <span v-else>暂无多维表格，点上方 + 新建</span>
      </div>
    </div>

    <!-- 移动多维表格到分组 -->
    <el-dialog v-model="moveDialogVisible" title="移动到分组" width="420px" append-to-body>
      <el-tree-select
        v-model="moveTargetGroupId"
        :data="moveOptions"
        :props="{ label: 'label', children: 'children' }"
        node-key="value"
        check-strictly
        default-expand-all
        placeholder="选择目标分组"
        style="width: 100%"
      />
      <template #footer>
        <el-button @click="moveDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmMoveBase">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch, type Component } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowRight,
  Collection,
  DataAnalysis,
  Delete,
  Document,
  EditPen,
  Folder,
  DArrowLeft,
  FolderAdd,
  FolderOpened,
  Grid,
  MoreFilled,
  Plus,
  Search,
} from '@element-plus/icons-vue'
import type { BitableBase, BitableBaseGroup } from '@/types/bitable'

type NodeKind = 'all' | 'ungrouped' | 'group' | 'base' | 'table' | 'dashboard' | 'editor'

interface MenuItem {
  command: string
  label: string
  divided?: boolean
  /** remixicon 图标类名，更多操作菜单统一图标化 */
  icon?: string
  /** element-plus 图标组件（与目录树节点/悬浮工具同形，优先于 icon） */
  iconComponent?: Component
  /** 图标点缀色（与树上该类型节点的颜色族一致） */
  iconColor?: string
  /** 危险操作（删除类）标红 */
  danger?: boolean
}

interface FlatNode {
  key: string
  kind: NodeKind
  /** 分组ID 或 Base ID；「全部」「未分组」为 null 或所属分组ID */
  id: number | null
  /** Base 所属分组（用于拖动/归组），未分组为 null */
  parentGroupId: number | null
  name: string
  depth: number
  count?: number
  hasChildren: boolean
  expanded: boolean
  isActive: boolean
  draggable: boolean
  menu: MenuItem[]
  /** 表/仪表盘叶子所属 Base 的分组（DnD 移动/排序用） */
  baseGroupId?: number | null
}

const props = defineProps<{
  groups: BitableBaseGroup[]
  bases: BitableBase[]
  /** 各 Base 下的仪表盘（外层树节点展示用） */
  dashboards?: Array<{ id: number; baseId: number; name: string; baseGroupId?: number | null }>
  /** 各 Base 下的数据表（外层树节点展示用） */
  tables?: Array<{ id: number; baseId: number; name: string; baseGroupId?: number | null }>
  /** 当前选中范围（用于右侧卡片过滤） */
  selection: BaseTreeSelection
  /** 是否可管理分组（管理员） */
  canManage?: boolean
}>()

const emit = defineEmits<{
  select: [payload: BaseTreeSelection]
  'create-group': [payload: { name: string; parentId: number | null }]
  'rename-group': [payload: { id: number; name: string }]
  'delete-group': [id: number]
  'move-group': [payload: { id: number; parentId: number | null }]
  'move-base': [payload: { baseId: number; groupId: number | null }]
  /** 分组同级排序：orderedGroupIds 为某父级下按目标顺序排列的全部分组ID */
  'reorder-groups': [orderedGroupIds: number[]]
  /** Base 同级排序：orderedBaseIds 为同一分组下按目标顺序排列的全部 Base ID */
  'reorder-bases': [orderedBaseIds: number[]]
  /**
   * 叶子同级排序：数据表与仪表盘在树上同层级、共用同一 sort_order 序列，
   * 所以一起发出去（baseId 用于说明「同一层级」是哪个分组）。
   */
  'reorder-leaves': [payload: { baseId: number; leaves: Array<{ kind: 'table' | 'dashboard'; id: number }> }]
  /**
   * 叶子跨层级移动：把单个叶子（数据表 / 仪表盘）移到目标 Base 分组，
   * 只动该叶子自身，不影响同 Base 下的其它叶子。
   */
  'move-leaf': [payload: { kind: 'table' | 'dashboard'; id: number; targetGroupId: number | null }]
  'rename-base': [payload: { id: number; name: string }]
  'open-base': [baseId: number]
  'open-dashboard': [payload: { baseId: number; dashboardId?: number }]
  'create-dashboard': [baseId: number | null]
  'create-table': [groupId: number | null]
  'collapse': [],
  'open-table': [payload: { baseId: number; tableId: number }]
  'rename-table': [payload: { id: number; name: string }]
  'delete-table': [payload: { id: number; baseId: number; name: string }]
  /** 导入表 → 数据表：清除文件标记，树上图标从文件样式转回数据库样式 */
  'convert-to-table': [payload: { id: number; baseId: number; name: string }]
  /** 导入 Excel：携带分组ID（分组节点入口）；null = 顶部 + 入口，不预选分组 */
  'import-excel': [groupId: number | null]
  /** 从模板创建：打开模板画廊 */
  'create-template': []
  /** 分组下新建仪表盘：落在该分组（无多维表格时由父组件先建承载） */
  'create-dashboard-in-group': [groupId: number]
  'rename-dashboard': [payload: { id: number; name: string }]
  'delete-dashboard': [payload: { id: number; baseId: number; name: string }]
  'delete-base': [base: BitableBase]
  'create-base': [groupId: number | null]
}>()

interface BaseTreeSelection {
  type: 'all' | 'group' | 'ungrouped'
  groupId: number | null
}

/**
 * 折叠状态，key 形如 all / g:12 / ug:12 / ug:root；「全部」是平铺汇总视图，默认折叠。
 * 持久化到 localStorage，下次进入保持上次的展开形态。
 */
const COLLAPSED_STORAGE_KEY = 'bitable.baseGroupTree.collapsed'

function loadCollapsedKeys(): Set<string> {
  const fallback = new Set<string>(['all'])
  try {
    const raw = localStorage.getItem(COLLAPSED_STORAGE_KEY)
    if (!raw) return fallback
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? new Set(parsed as string[]) : fallback
  } catch {
    return fallback
  }
}

const collapsedKeys = ref<Set<string>>(loadCollapsedKeys())

watch(
  collapsedKeys,
  (value) => {
    try {
      localStorage.setItem(COLLAPSED_STORAGE_KEY, JSON.stringify([...value]))
    } catch {
      /* 隐私模式等写入失败的场景直接忽略，不影响交互 */
    }
  },
  { deep: true },
)

const keyword = ref('')
const searchOpen = ref(false)
const searchInputRef = ref<any>()

function openSearch() {
  searchOpen.value = true
  nextTick(() => searchInputRef.value?.focus?.())
}

function closeSearch() {
  if (!keyword.value) searchOpen.value = false
}

function onSearchBlur() {
  if (!keyword.value) searchOpen.value = false
}
const creating = ref<{ parentId: number | null } | null>(null)
const editorValue = ref('')

/* ---------------- 行内重命名（不弹框，原名称变输入框） ---------------- */

const renaming = ref<{ key: string } | null>(null)
const renameValue = ref('')
const renameInputRef = ref<any>()

function startRename(node: FlatNode) {
  renaming.value = { key: node.key }
  renameValue.value = node.name
  nextTick(() => {
    renameInputRef.value?.focus?.()
    renameInputRef.value?.select?.()
  })
}

function cancelRename() {
  renaming.value = null
  renameValue.value = ''
}

function commitRename() {
  if (!renaming.value) return
  const key = renaming.value.key
  const node = nodes.value.find((n) => n.key === key)
  const name = renameValue.value.trim()
  cancelRename()
  if (!node) return
  if (!name) {
    ElMessage.warning('名称不能为空')
    return
  }
  if (name === node.name) return
  const dupMessage = checkDuplicateName(node, name)
  if (dupMessage) {
    ElMessage.error(dupMessage)
    return
  }
  if (node.kind === 'group' && node.id != null) {
    emit('rename-group', { id: node.id, name })
  } else if (node.kind === 'table' && node.id != null) {
    emit('rename-table', { id: node.id, name })
  } else if (node.kind === 'dashboard' && node.id != null) {
    emit('rename-dashboard', { id: node.id, name })
  } else if (node.kind === 'base' && node.id != null) {
    emit('rename-base', { id: node.id, name })
  }
}

/**
 * 同名检测：同类型文件在同一展示层级内名称必须唯一
 * （数据表/仪表盘按其 Base 的分组为作用域，分组按兄弟层级）
 */
function checkDuplicateName(node: FlatNode, name: string): string | null {
  const lower = name.toLowerCase()
  const conflict = (list: Array<{ id: number | null; name: string }>, selfId: number | null) =>
    list.some((item) => item.id !== selfId && item.name.toLowerCase() === lower)

  if (node.kind === 'group') {
    const siblings = childGroups(node.parentGroupId)
    return conflict(siblings, node.id) ? `同级已存在同名分组「${name}」` : null
  }
  if (node.kind === 'table') {
    const siblings = (props.tables || []).filter(
      (t) => leafGroupIdOf(t.baseId, t.baseGroupId) === (node.baseGroupId ?? null),
    )
    return conflict(siblings, node.id) ? `同级已存在同名数据表「${name}」` : null
  }
  if (node.kind === 'dashboard') {
    const siblings = (props.dashboards || []).filter(
      (d) => leafGroupIdOf(d.baseId, d.baseGroupId) === (node.baseGroupId ?? null),
    )
    return conflict(siblings, node.id) ? `同级已存在同名仪表盘「${name}」` : null
  }
  if (node.kind === 'base') {
    const siblings = basesOfGroup(node.parentGroupId)
    return conflict(siblings, node.id) ? `同级已存在同名多维表格「${name}」` : null
  }
  return null
}

const moveDialogVisible = ref(false)
const moveBaseId = ref<number | null>(null)
const moveTargetGroupId = ref<number | null>(null)

/* ---------------- 基础查询 ---------------- */

/**
 * 接口返回的是嵌套树，这里拍平成列表，
 * 后续按 parentId 查找子分组、做拖拽防环校验都基于扁平结构
 */
const allGroups = computed<BitableBaseGroup[]>(() => {
  const out: BitableBaseGroup[] = []
  const walk = (list?: BitableBaseGroup[]) => {
    ;(list || []).forEach((g) => {
      out.push(g)
      walk(g.children)
    })
  }
  walk(props.groups)
  return out
})

function ugKey(parentGroupId: number | null) {
  return parentGroupId == null ? 'ug:root' : `ug:${parentGroupId}`
}

function isExpanded(key: string) {
  return !collapsedKeys.value.has(key)
}

function childGroups(parentId: number | null): BitableBaseGroup[] {
  return allGroups.value
    .filter((g) => (g.parentId ?? null) === parentId)
    .slice()
    .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0) || a.id - b.id)
}

function basesOfGroup(groupId: number | null): BitableBase[] {
  return props.bases.filter((b) => (b.groupId ?? null) === groupId)
}

function descendantGroupIds(groupId: number): number[] {
  const out: number[] = []
  const walk = (pid: number) => {
    childGroups(pid).forEach((g) => {
      out.push(g.id)
      walk(g.id)
    })
  }
  walk(groupId)
  return out
}

function isGroupDescendant(candidate: number, ancestor: number) {
  return descendantGroupIds(ancestor).includes(candidate)
}

function keywordLower() {
  return keyword.value.trim().toLowerCase()
}

/** 把名称按关键词切成片段，命中片段用于高亮（不依赖 v-html，避免 XSS） */
function highlightSegments(name: string): { text: string; hit: boolean }[] {
  const kw = keywordLower()
  if (!kw || !name) return [{ text: name, hit: false }]
  const lower = name.toLowerCase()
  const segments: { text: string; hit: boolean }[] = []
  let cursor = 0
  while (cursor < name.length) {
    const idx = lower.indexOf(kw, cursor)
    if (idx < 0) {
      segments.push({ text: name.slice(cursor), hit: false })
      break
    }
    if (idx > cursor) segments.push({ text: name.slice(cursor, idx), hit: false })
    segments.push({ text: name.slice(idx, idx + kw.length), hit: true })
    cursor = idx + kw.length
  }
  return segments.length ? segments : [{ text: name, hit: false }]
}

function baseMatches(base: BitableBase) {
  const kw = keywordLower()
  if (!kw) return true
  return (
    base.name.toLowerCase().includes(kw) ||
    (base.description || '').toLowerCase().includes(kw)
  )
}

function groupMatches(group: BitableBaseGroup): boolean {
  const kw = keywordLower()
  if (!kw) return true
  if (group.name.toLowerCase().includes(kw)) return true
  const ids = [group.id, ...descendantGroupIds(group.id)]
  return props.bases.some((b) => ids.includes(b.groupId ?? -1) && baseMatches(b))
}

/** 当前 nodes 计算属性不产出 base 节点，此菜单暂无调用方（保留以便 base 节点回归时复用） */
function baseMenu(): MenuItem[] {
  return [
    { command: 'rename', label: '重命名', iconComponent: EditPen, divided: true },
    { command: 'dashboard', label: '打开仪表盘', iconComponent: DataAnalysis, iconColor: 'var(--color-type-dashboard)' },
    { command: 'create-dashboard', label: '新建仪表盘', iconComponent: DataAnalysis, iconColor: 'var(--color-type-dashboard)' },
    { command: 'create-table', label: '新建数据表', icon: 'ri-database-2-line', iconColor: 'var(--color-primary)' },
    { command: 'move', label: '移动到分组…', icon: 'ri-drag-move-2-line' },
    { command: 'delete', label: '删除', iconComponent: Delete, danger: true, divided: true },
  ]
}

// 「打开」已从节点菜单移除：点节点行本身就是打开（handleCommand 的 open 分支与
// handleClick 走的是同一个 emit），菜单里再放一项纯属重复入口。
function tableMenu(): MenuItem[] {
  return [
    { command: 'rename', label: '重命名', iconComponent: EditPen },
    { command: 'delete', label: '删除', iconComponent: Delete, danger: true, divided: true },
  ]
}

function dashboardMenu(): MenuItem[] {
  return [
    { command: 'rename', label: '重命名', iconComponent: EditPen },
    { command: 'delete', label: '删除', iconComponent: Delete, danger: true, divided: true },
  ]
}

/**
 * 分组更多操作：对齐钉钉多维表格分组菜单（导入 Excel / 数据表 / 仪表盘 / 文件夹）。
 * 图标与目录树同形同族色：仪表盘 DataAnalysis、子分组 FolderAdd（琥珀=分组色）、
 * 数据表 ri-database-2-line（同树节点）。颜色全部走语义 token，随外观/主题色联动。
 */
function groupMenu(): MenuItem[] {
  return [
    { command: 'import-excel', label: '导入 Excel', icon: 'ri-file-excel-2-line', iconColor: 'var(--color-success)' },
    { command: 'create-table', label: '新建数据表', icon: 'ri-database-2-line', iconColor: 'var(--color-primary)' },
    { command: 'create-dashboard', label: '新建仪表盘', iconComponent: DataAnalysis, iconColor: 'var(--color-type-dashboard)' },
    { command: 'create-subgroup', label: '新建子分组', iconComponent: FolderAdd, iconColor: 'var(--color-warning)', divided: true },
    { command: 'rename', label: '重命名', iconComponent: EditPen },
    { command: 'delete', label: '删除分组', iconComponent: Delete, danger: true },
  ]
}

/* ---------------- 选中态 ---------------- */

function isGroupActive(groupId: number) {
  return props.selection.type === 'group' && props.selection.groupId === groupId
}

function isUngroupedActive(parentGroupId: number | null) {
  return (
    props.selection.type === 'ungrouped' &&
    (props.selection.groupId ?? null) === (parentGroupId ?? null)
  )
}

/* ---------------- 扁平化树 ---------------- */

/** 表/仪表盘叶子节点：优先自己的独立分组归属，为空时跟随所属 Base 的分组 */
function leafGroupIdOf(leafBaseId: number, leafGroupId?: number | null): number | null {
  if (leafGroupId != null) return leafGroupId
  return props.bases.find((b) => b.id === leafBaseId)?.groupId ?? null
}

function toTableNode(t: { id: number; baseId: number; name: string; baseGroupId?: number | null }, depth: number): FlatNode {
  return {
    key: `t:${t.id}`,
    kind: 'table',
    id: t.id,
    parentGroupId: t.baseId,
    baseGroupId: leafGroupIdOf(t.baseId, t.baseGroupId),
    name: t.name,
    depth,
    hasChildren: false,
    expanded: false,
    isActive: false,
    draggable: true,
    menu: tableMenu(),
  }
}

function toDashboardNode(d: { id: number; baseId: number; name: string; baseGroupId?: number | null }, depth: number): FlatNode {
  return {
    key: `d:${d.id}`,
    kind: 'dashboard',
    id: d.id,
    parentGroupId: d.baseId,
    baseGroupId: leafGroupIdOf(d.baseId, d.baseGroupId),
    name: d.name,
    depth,
    hasChildren: false,
    expanded: false,
    isActive: false,
    draggable: true,
    menu: dashboardMenu(),
  }
}

/** 数据表树图标：文件导入的表显示文件图标，其余显示数据库图标 */
function tableIconClass(node: FlatNode): string {
  const t = (props.tables || []).find((item) => item.id === node.id)
  return t && (t as any).icon === 'file' ? 'ri-file-text-line' : 'ri-database-2-line'
}

/** 是否为导入形成的文件标记表（树上可一键转为数据表） */
function isFileTable(node: FlatNode): boolean {
  return tableIconClass(node) === 'ri-file-text-line'
}

function convertToTable(node: FlatNode) {
  if (node.id == null || node.parentGroupId == null) return
  emit('convert-to-table', { id: node.id, baseId: node.parentGroupId, name: node.name })
}

function tablesOfGroup(groupId: number | null) {
  const kw = keywordLower()
  const list = (props.tables || []).filter((t) => leafGroupIdOf(t.baseId, t.baseGroupId) === groupId)
  return kw ? list.filter((t) => t.name.toLowerCase().includes(kw)) : list
}

function dashboardsOfGroup(groupId: number | null) {
  const kw = keywordLower()
  const list = (props.dashboards || []).filter((d) => leafGroupIdOf(d.baseId, d.baseGroupId) === groupId)
  return kw ? list.filter((d) => d.name.toLowerCase().includes(kw)) : list
}

function allTables() {
  const kw = keywordLower()
  const list = props.tables || []
  return kw ? list.filter((t) => t.name.toLowerCase().includes(kw)) : list
}

function allDashboards() {
  const kw = keywordLower()
  const list = props.dashboards || []
  return kw ? list.filter((d) => d.name.toLowerCase().includes(kw)) : list
}

/**
 * 某分组（或根层级）下的叶子：数据表与仪表盘按 sort_order **混排**。
 *
 * 两者在树上就是同层级的兄弟节点，共用同一排序序列（后端 PUT /leaves/sort 按下标回写）。
 * 同序号时数据表在前、再按 id —— 这条兜底保证历史数据（sort_order 全 0）的渲染顺序不变。
 */
function pushGroupLeaves(groupId: number | null, depth: number, out: FlatNode[]) {
  const leaves: Array<{ sortOrder: number; kindRank: number; id: number; node: FlatNode }> = []
  tablesOfGroup(groupId).forEach((t) =>
    leaves.push({
      sortOrder: (t as { sortOrder?: number }).sortOrder ?? 0,
      kindRank: 0,
      id: t.id,
      node: toTableNode(t, depth),
    }),
  )
  dashboardsOfGroup(groupId).forEach((d) =>
    leaves.push({
      sortOrder: (d as { sortOrder?: number }).sortOrder ?? 0,
      kindRank: 1,
      id: d.id,
      node: toDashboardNode(d, depth),
    }),
  )
  leaves
    .sort((a, b) => a.sortOrder - b.sortOrder || a.kindRank - b.kindRank || a.id - b.id)
    .forEach((leaf) => out.push(leaf.node))
}

function walkGroups(parentId: number | null, depth: number, out: FlatNode[]) {
  childGroups(parentId).forEach((group) => {
    if (!groupMatches(group)) return

    const subGroups = childGroups(group.id)
    const leafCount = tablesOfGroup(group.id).length + dashboardsOfGroup(group.id).length
    const key = `g:${group.id}`
    const expanded = !!keywordLower() || isExpanded(key)

    out.push({
      key,
      kind: 'group',
      id: group.id,
      parentGroupId: group.parentId ?? null,
      name: group.name,
      depth,
      count: leafCount,
      hasChildren: subGroups.length > 0 || leafCount > 0,
      expanded,
      isActive: isGroupActive(group.id),
      draggable: !!props.canManage,
      menu: groupMenu(),
    })

    if (!expanded) return

    if (subGroups.length) walkGroups(group.id, depth + 1, out)
    pushGroupLeaves(group.id, depth + 1, out)
  })
}

const nodes = computed<FlatNode[]>(() => {
  const out: FlatNode[] = []

  // 1. 全部（平铺视图，默认折叠）
  const totalLeaves = (props.tables?.length ?? 0) + (props.dashboards?.length ?? 0)
  const allExpanded = isExpanded('all')
  out.push({
    key: 'all',
    kind: 'all',
    id: null,
    parentGroupId: null,
    name: '全部',
    depth: 0,
    count: totalLeaves,
    hasChildren: totalLeaves > 0,
    expanded: allExpanded,
    isActive: props.selection.type === 'all',
    draggable: false,
    menu: [],
  })
  if (allExpanded) {
    // 「全部」平铺视图与下方分组会渲染同一批叶子，key 必须带作用域后缀避免重复键
    allTables().forEach((t) => out.push({ ...toTableNode(t, 1), key: `t:${t.id}@all` }))
    allDashboards().forEach((d) => out.push({ ...toDashboardNode(d, 1), key: `d:${d.id}@all` }))
  }

  // 2. 根层级叶子：未分组的数据表/仪表盘直接放一级目录（无「未分组」文件夹）
  pushGroupLeaves(null, 0, out)

  // 3. 分组树
  walkGroups(null, 0, out)

  // 4. 新建分组的行内输入框
  if (creating.value) {
    const parentId = creating.value.parentId
    const editorNode: FlatNode = {
      key: 'editor',
      kind: 'editor',
      id: null,
      parentGroupId: parentId,
      name: '',
      depth: 0,
      hasChildren: false,
      expanded: false,
      isActive: false,
      draggable: false,
      menu: [],
    }
    if (parentId == null) {
      const firstRootGroup = out.findIndex((n) => n.kind === 'group' && n.depth === 0)
      editorNode.depth = 0
      out.splice(firstRootGroup < 0 ? out.length : firstRootGroup, 0, editorNode)
    } else {
      const index = out.findIndex((n) => n.kind === 'group' && n.id === parentId)
      if (index < 0) {
        out.push(editorNode)
      } else {
        editorNode.depth = out[index].depth + 1
        out.splice(index + 1, 0, editorNode)
      }
    }
  }

  return out
})

const moveOptions = computed(() => {
  const build = (parentId: number | null, depth: number): unknown[] =>
    childGroups(parentId).map((g) => {
      const children = build(g.id, depth + 1)
      return {
        value: g.id,
        label: `${'　'.repeat(depth)}${g.name}`,
        children: children.length ? children : undefined,
      }
    })
  return [{ value: -1, label: '未分组（根层级）', children: build(null, 1) }]
})

/** 展开当前选中项的所有祖先分组，保证选中项可见 */
function revealSelection() {
  const next = new Set(collapsedKeys.value)
  if (props.selection.type === 'group' && props.selection.groupId != null) {
    let current: number | null = props.selection.groupId
    let guard = 0
    while (current != null && guard++ < 50) {
      next.delete(`g:${current}`)
      const group: BitableBaseGroup | undefined = allGroups.value.find((g) => g.id === current)
      current = group?.parentId ?? null
    }
  } else if (props.selection.type === 'ungrouped' && props.selection.groupId != null) {
    next.delete(ugKey(props.selection.groupId))
  }
  if (next.size !== collapsedKeys.value.size) {
    collapsedKeys.value = next
  }
}

watch(
  () => [props.selection.type, props.selection.groupId],
  () => revealSelection(),
  { immediate: true },
)

/* ---------------- 交互 ---------------- */

function toggleExpand(node: FlatNode) {
  if (!node.hasChildren) return
  const key = node.kind === 'group' ? `g:${node.id}` : node.key
  const next = new Set(collapsedKeys.value)
  if (next.has(key)) {
    next.delete(key)
  } else {
    next.add(key)
  }
  collapsedKeys.value = next
}

function handleClick(event: MouseEvent, node: FlatNode) {
  if ((event.target as HTMLElement).closest('.bgtree-node__tools')) return
  if ((event.target as HTMLElement).closest('.bgtree-node__rename')) return
  if (renaming.value) return
  if (Date.now() - dragCommittedAt < 300) return

  if (node.kind === 'base') {
    if (node.id != null) emit('open-base', node.id)
    return
  }
  if (node.kind === 'dashboard') {
    if (node.id != null && node.parentGroupId != null) {
      emit('open-dashboard', { baseId: node.parentGroupId, dashboardId: node.id })
    }
    return
  }
  if (node.kind === 'table') {
    if (node.id != null && node.parentGroupId != null) {
      emit('open-table', { baseId: node.parentGroupId, tableId: node.id })
    }
    return
  }
  if (node.kind === 'all') {
    emit('select', { type: 'all', groupId: null })
  } else if (node.kind === 'ungrouped') {
    emit('select', { type: 'ungrouped', groupId: node.parentGroupId })
  } else if (node.kind === 'group') {
    emit('select', { type: 'group', groupId: node.id })
  }
  toggleExpand(node)
}

function handleCommand(command: string, node: FlatNode) {
  if (node.kind === 'base') {
    if (command === 'open' && node.id != null) {
      emit('open-base', node.id)
    } else if (command === 'rename' && node.id != null) {
      startRename(node)
    } else if (command === 'dashboard' && node.id != null) {
      emit('open-dashboard', { baseId: node.id })
    } else if (command === 'create-dashboard' && node.id != null) {
      emit('create-dashboard', node.id)
    } else if (command === 'create-table' && node.id != null) {
      emit('create-table', node.id)
    } else if (command === 'move' && node.id != null) {
      openMoveDialog(node.id, node.parentGroupId)
    } else if (command === 'delete' && node.id != null) {
      const base = props.bases.find((b) => b.id === node.id)
      if (base) emit('delete-base', base)
    }
    return
  }
  if (node.kind === 'dashboard') {
    if (command === 'open' && node.id != null && node.parentGroupId != null) {
      emit('open-dashboard', { baseId: node.parentGroupId, dashboardId: node.id })
    } else if (command === 'rename' && node.id != null) {
      startRename(node)
    } else if (command === 'delete' && node.id != null && node.parentGroupId != null) {
      confirmDeleteDashboard(node.id, node.parentGroupId, node.name)
    }
    return
  }
  if (node.kind === 'table') {
    if (command === 'open' && node.id != null && node.parentGroupId != null) {
      emit('open-table', { baseId: node.parentGroupId, tableId: node.id })
    } else if (command === 'rename' && node.id != null) {
      startRename(node)
    } else if (command === 'delete' && node.id != null && node.parentGroupId != null) {
      confirmDeleteTable(node.id, node.parentGroupId, node.name)
    }
    return
  }
  if (command === 'import-excel' || command === 'create-table') {
    // 分组节点：导入 Excel / 新建数据表都落在该分组（全部/未分组节点 id 为 null = 一级目录）
    if (command === 'import-excel') emit('import-excel', node.id)
    else emit('create-table', node.id)
  } else if (command === 'create-dashboard') {
    // 分组下新建仪表盘：落在分组内第一个多维表格（父组件决定承载方式）
    if (node.id != null) emit('create-dashboard-in-group', node.id)
  } else if (command === 'create-subgroup') {
    if (node.id != null) startCreate(node.id)
  } else if (command === 'create-base') {
    emit('create-base', node.id)
  } else if (command === 'rename' && node.id != null) {
    startRename(node)
  } else if (command === 'delete' && node.id != null) {
    confirmDeleteGroup(node.id, node.name, node.count ?? 0)
  }
}

/* ---------------- 新建 / 重命名 / 删除 ---------------- */

function startCreate(parentId: number | null) {
  if (parentId != null) {
    const next = new Set(collapsedKeys.value)
    next.delete(`g:${parentId}`)
    collapsedKeys.value = next
  }
  creating.value = { parentId }
  editorValue.value = ''
  nextTick(() => {
    const input = document.querySelector<HTMLInputElement>('.bgtree-editor input')
    input?.focus()
  })
}

function cancelCreate() {
  creating.value = null
  editorValue.value = ''
}

function commitCreate() {
  if (!creating.value) return
  const name = editorValue.value.trim()
  const parentId = creating.value.parentId
  creating.value = null
  editorValue.value = ''
  if (!name) return
  // 同名检测：同级分组名不能重复
  const duplicate = childGroups(parentId).some((g) => g.name.toLowerCase() === name.toLowerCase())
  if (duplicate) {
    ElMessage.error(`同级已存在同名分组「${name}」`)
    return
  }
  emit('create-group', { name, parentId })
}

function confirmDeleteTable(id: number, baseId: number, name: string) {
  ElMessageBox.confirm(`确定删除数据表「${name}」及其全部数据吗？`, '删除确认', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => {
      emit('delete-table', { id, baseId, name })
    })
    .catch(() => {})
}

// ==================== 叶子节点行内图标操作（重命名/删除） ====================

/** 数据表/仪表盘节点：⋯ 菜单替换为行内图标；删除时原位出现红色「确认」按钮 */
const confirmDeleteKey = ref<string | null>(null)

function isInlineActionNode(node: FlatNode): boolean {
  return node.kind === 'table' || node.kind === 'dashboard'
}

function beginInlineRename(node: FlatNode) {
  confirmDeleteKey.value = null
  startRename(node)
}

/** 行内「确认」直接删除，不再弹确认框 */
function confirmInlineDelete(node: FlatNode) {
  confirmDeleteKey.value = null
  if (node.id == null || node.parentGroupId == null) return
  if (node.kind === 'table') {
    emit('delete-table', { id: node.id, baseId: node.parentGroupId, name: node.name })
  } else if (node.kind === 'dashboard') {
    emit('delete-dashboard', { id: node.id, baseId: node.parentGroupId, name: node.name })
  }
}

/** 点击页面任意处 / Esc 退出确认态 */
function dismissConfirmDelete() {
  confirmDeleteKey.value = null
}

function confirmDeleteDashboard(id: number, baseId: number, name: string) {
  ElMessageBox.confirm(`确定删除仪表盘「${name}」及其全部组件吗？`, '删除确认', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => {
      emit('delete-dashboard', { id, baseId, name })
    })
    .catch(() => {})
}

function onHeaderCommand(command: string) {
  if (command === 'create-dashboard') {
    emit('create-dashboard', null)
  } else if (command === 'create-table') {
    emit('create-table', null)
  } else if (command === 'create-group') {
    // 根层级行内新建分组（与分组菜单「新建子分组」同一交互）
    startCreate(null)
  } else if (command === 'create-template') {
    emit('create-template')
  } else if (command === 'import-excel') {
    emit('import-excel', null)
  }
}

function confirmDeleteGroup(id: number, name: string, count: number) {
  const extra = count > 0 ? `\n\n该分组下的 ${count} 个多维表格与子分组将上移到父级，不会被删除。` : ''
  ElMessageBox.confirm(`确定删除分组「${name}」吗？${extra}`, '删除确认', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => emit('delete-group', id))
    .catch(() => {})
}

/* ---------------- 移动 Base ---------------- */

function openMoveDialog(baseId: number, currentGroupId: number | null) {
  moveBaseId.value = baseId
  moveTargetGroupId.value = currentGroupId ?? -1
  moveDialogVisible.value = true
}

function confirmMoveBase() {
  if (moveBaseId.value == null) return
  const target = moveTargetGroupId.value
  const groupId = target == null || target === -1 ? null : target
  emit('move-base', { baseId: moveBaseId.value, groupId })
  moveDialogVisible.value = false
}

/* ---------------- 拖拽（长按激活 + 三区落点） ---------------- */

const listEl = ref<HTMLElement | null>(null)
const dragKey = ref<string | null>(null)
const pressKey = ref<string | null>(null)
const rejectKey = ref<string | null>(null)
/** 当前落点：key 为节点 key（'in'/'before'/'after' 三区）或 '__root__'（列表空白处） */
const dropState = ref<{ key: string; zone: 'in' | 'before' | 'after' } | null>(null)

let dragging: FlatNode | null = null
let pressTimer: number | null = null
let pressStart: { x: number; y: number } | null = null
/** 拖拽提交时刻：吞掉抬起后派发的 click，避免拖完误开表/切视图 */
let dragCommittedAt = 0

/** 长按时长：超过该阈值且未明显移动即进入拖拽 */
const PRESS_DELAY_MS = 300
/** 按下后允许的位移抖动，超出视为滑动而非长按 */
const PRESS_MOVE_TOLERANCE = 8

function clearPressTimer() {
  if (pressTimer != null) {
    window.clearTimeout(pressTimer)
    pressTimer = null
  }
}

function resetDragState() {
  if (dragging) dragCommittedAt = Date.now()
  dragging = null
  dragKey.value = null
  pressKey.value = null
  rejectKey.value = null
  dropState.value = null
  document.body.classList.remove('bgtree-dragging-cursor')
}

function handlePointerDown(event: PointerEvent, node: FlatNode) {
  if (event.button !== 0 || !node.draggable) return
  // 行内重命名输入框里要正常选字，不进入长按拖拽识别
  if ((event.target as HTMLElement).closest('.bgtree-node__rename')) return
  // 触摸设备不做长按拖拽（树支持触摸点击即可，拖拽留给桌面端鼠标场景）
  if (event.pointerType !== 'mouse') return
  pressKey.value = node.key
  pressStart = { x: event.clientX, y: event.clientY }

  pressTimer = window.setTimeout(() => {
    dragging = node
    dragKey.value = node.key
    pressKey.value = null
    document.body.classList.add('bgtree-dragging-cursor')
  }, PRESS_DELAY_MS)
}

/** 全局指针移动：未激活时识别滑动取消长按；激活后计算落点 */
function handlePointerMove(event: PointerEvent) {
  if (pressStart != null && dragging == null) {
    const dx = Math.abs(event.clientX - pressStart.x)
    const dy = Math.abs(event.clientY - pressStart.y)
    if (dx > PRESS_MOVE_TOLERANCE || dy > PRESS_MOVE_TOLERANCE) {
      clearPressTimer()
      pressStart = null
      pressKey.value = null
    }
    return
  }
  if (!dragging) return
  event.preventDefault()
  updateDropState(event.clientX, event.clientY)
}

function handlePointerUp() {
  clearPressTimer()
  pressStart = null
  pressKey.value = null
  if (dragging) commitDrop()
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    if (confirmDeleteKey.value) dismissConfirmDelete()
    if (dragging) resetDragState()
  }
}

/** 依据指针位置计算三区落点：上 1/4 = 排到其前，下 1/4 = 排到其后，中间 1/2 = 拖入 */
function updateDropState(clientX: number, clientY: number) {
  const source = dragging
  if (!source || !listEl.value) return

  const listRect = listEl.value.getBoundingClientRect()
  if (
    clientX < listRect.left ||
    clientX > listRect.right ||
    clientY < listRect.top ||
    clientY > listRect.bottom
  ) {
    dropState.value = null
    rejectKey.value = null
    return
  }

  const el = document.elementFromPoint(clientX, clientY)
  const nodeEl = el?.closest('.bgtree-node') as HTMLElement | null
  const key = nodeEl?.dataset.key

  // 指针在行上：按纵向三区判定
  if (nodeEl && key) {
    const rect = nodeEl.getBoundingClientRect()
    const ratio = (clientY - rect.top) / rect.height
    const zone: 'in' | 'before' | 'after' = ratio < 0.25 ? 'before' : ratio > 0.75 ? 'after' : 'in'
    const node = nodes.value.find((n) => n.key === key)
    if (!node) {
      dropState.value = null
      return
    }
    const result = resolveDropTarget(node, zone)
    dropState.value = result.ok ? { key, zone } : null
    rejectKey.value = !result.ok && result.reason ? key : null
    return
  }

  // 指针在列表空白处：分组/数据表可拖到根层级（一级目录）
  if (source.kind === 'group' && source.id != null && (source.parentGroupId ?? null) !== null) {
    dropState.value = { key: '__root__', zone: 'in' }
    rejectKey.value = null
    return
  }
  if (isLeafKind(source.kind) && (source.baseGroupId ?? null) !== null) {
    dropState.value = { key: '__root__', zone: 'in' }
    rejectKey.value = null
  } else {
    dropState.value = null
    rejectKey.value = null
  }
}

/** 叶子节点类型：数据表 / 仪表盘（树上同层级，共用同一 sort_order 序列）——类型守卫，供 emit 收窄用 */
function isLeafKind(kind: NodeKind): kind is 'table' | 'dashboard' {
  return kind === 'table' || kind === 'dashboard'
}

/**
 * 校验落点合法性：
 * - 'in'：与原「拖入分组」一致（拖到分组/未分组/全部上）
 * - 'before'/'after'：同级排序——叶子（数据表/仪表盘）对叶子、分组对分组，且同父级
 */
function resolveDropTarget(node: FlatNode, zone: 'in' | 'before' | 'after'): { ok: boolean; reason?: string } {
  if (!dragging || dragging.key === node.key) return { ok: false }

  if (zone === 'in') {
    if (node.kind !== 'group' && node.kind !== 'ungrouped' && node.kind !== 'all') return { ok: false }

    if (dragging.kind === 'base') {
      if (node.kind === 'all') {
        return { ok: false, reason: '「全部」是平铺视图，不能拖入' }
      }
      const targetGroupId = node.kind === 'ungrouped' ? node.parentGroupId ?? null : node.id
      if ((dragging.parentGroupId ?? null) === targetGroupId) return { ok: false }
      return { ok: true }
    }

    // 叶子（数据表 / 仪表盘）拖入分组：只移动该叶子自身，不动所属 Base
    if (isLeafKind(dragging.kind)) {
      if (node.kind !== 'group') return { ok: false }
      if ((dragging.baseGroupId ?? null) === node.id) return { ok: false }
      return { ok: true }
    }

    if (dragging.kind === 'group' && dragging.id != null) {
      if (node.kind === 'all') {
        return { ok: false, reason: '「全部」是平铺视图，不能拖入' }
      }
      const targetParentId = node.kind === 'ungrouped' ? node.parentGroupId ?? null : node.id
      if (targetParentId === dragging.id) return { ok: false }
      if (targetParentId != null && isGroupDescendant(targetParentId, dragging.id)) {
        return { ok: false, reason: '不能把分组移到它自己的子分组下面' }
      }
      if ((dragging.parentGroupId ?? null) === targetParentId) return { ok: false }
      return { ok: true }
    }

    return { ok: false }
  }

  // 同级排序：叶子节点之间（数据表 / 仪表盘混排），须处于同一分组层级
  if (isLeafKind(dragging.kind)) {
    if (!isLeafKind(node.kind)) return { ok: false }
    return (dragging.baseGroupId ?? null) === (node.baseGroupId ?? null)
      ? { ok: true }
      : { ok: false }
  }
  // 同级排序：目标须是分组或 Base 节点
  if (node.kind !== 'group' && node.kind !== 'base') return { ok: false }
  if (dragging.kind === 'base' && node.kind === 'base') {
    // 同父级（同一分组下的 Base 才能排序；「全部」展开出的平铺 Base 不参与）
    return (dragging.parentGroupId ?? null) === (node.parentGroupId ?? null)
      ? { ok: true }
      : { ok: false }
  }
  if (dragging.kind === 'group' && node.kind === 'group' && dragging.id != null && node.id != null) {
    if (isGroupDescendant(node.id, dragging.id)) {
      return { ok: false, reason: '不能把分组排到它自己的子分组旁边' }
    }
    return (dragging.parentGroupId ?? null) === (node.parentGroupId ?? null)
      ? { ok: true }
      : { ok: false }
  }
  return { ok: false }
}

/** 指针抬起：按落点提交移动或排序 */
function commitDrop() {
  const source = dragging
  const state = dropState.value
  resetDragState()
  if (!source || !state) return

  if (state.key === '__root__') {
    if (source.kind === 'group' && source.id != null && (source.parentGroupId ?? null) !== null) {
      emit('move-group', { id: source.id, parentId: null })
    }
    if (isLeafKind(source.kind) && source.id != null) {
      // 叶子拖到空白处 = 移到根层级（未分组），只动该叶子自身
      emit('move-leaf', { kind: source.kind, id: source.id, targetGroupId: null })
    }
    return
  }

  const node = nodes.value.find((n) => n.key === state.key)
  if (!node) return

  if (state.zone === 'in') {
    if (source.kind === 'base' && source.id != null) {
      const groupId = node.kind === 'ungrouped' ? node.parentGroupId ?? null : node.id
      emit('move-base', { baseId: source.id, groupId })
      return
    }
    if (isLeafKind(source.kind) && source.id != null && node.kind === 'group') {
      // 叶子拖入分组 = 只移动该叶子（数据表/仪表盘），不再连带整个 Base
      const targetGroupId = node.id
      if ((source.baseGroupId ?? null) !== targetGroupId) {
        emit('move-leaf', { kind: source.kind, id: source.id, targetGroupId })
      }
      return
    }
    if (source.kind === 'group' && source.id != null) {
      const parentId = node.kind === 'ungrouped' ? node.parentGroupId ?? null : node.id
      emit('move-group', { id: source.id, parentId })
    }
    return
  }

  // before / after：同级排序（叶子：数据表与仪表盘混排，共用同一序列）
  if (isLeafKind(source.kind) && isLeafKind(node.kind) && source.parentGroupId != null) {
    // 同级 = 同一「有效分组」（叶子独立归属或所属 Base 分组）
    const groupKey = source.baseGroupId ?? null
    const siblings = nodes.value.filter(
      (n) => isLeafKind(n.kind) && (n.baseGroupId ?? null) === groupKey,
    )
    const fromIdx = siblings.findIndex((n) => n.key === source.key)
    if (fromIdx < 0) return
    const moved = siblings.splice(fromIdx, 1)[0]
    // 先删后插：目标下标会前移，必须重新定位；'after' 才是插到目标之后
    const targetIdx = siblings.findIndex((n) => n.key === node.key)
    if (targetIdx < 0) return
    siblings.splice(state.zone === 'after' ? targetIdx + 1 : targetIdx, 0, moved)
    emit('reorder-leaves', {
      baseId: moved.parentGroupId as number,
      leaves: siblings.map((n) => ({
        kind: n.kind === 'dashboard' ? ('dashboard' as const) : ('table' as const),
        id: n.id as number,
      })),
    })
    return
  }
  // before / after：同级排序（分组/Base）
  const isAfter = state.zone === 'after'
  if (source.kind === 'base' && node.kind === 'base' && source.id != null) {
    const siblings = basesOfGroup(node.parentGroupId)
      .map((b) => b.id)
      .filter((id) => id !== source.id)
    const targetIdx = siblings.indexOf(node.id as number)
    siblings.splice(isAfter ? targetIdx + 1 : targetIdx, 0, source.id)
    emit('reorder-bases', siblings)
    return
  }
  if (source.kind === 'group' && node.kind === 'group' && source.id != null && node.id != null) {
    const siblings = childGroups(node.parentGroupId)
      .map((g) => g.id)
      .filter((id) => id !== source.id)
    const targetIdx = siblings.indexOf(node.id)
    siblings.splice(isAfter ? targetIdx + 1 : targetIdx, 0, source.id)
    emit('reorder-groups', siblings)
  }
}

onMounted(() => {
  window.addEventListener('pointermove', handlePointerMove)
  window.addEventListener('pointerup', handlePointerUp)
  window.addEventListener('keydown', handleKeydown)
  window.addEventListener('click', dismissConfirmDelete)
})

onBeforeUnmount(() => {
  window.removeEventListener('pointermove', handlePointerMove)
  window.removeEventListener('pointerup', handlePointerUp)
  window.removeEventListener('keydown', handleKeydown)
  window.removeEventListener('click', dismissConfirmDelete)
  clearPressTimer()
  document.body.classList.remove('bgtree-dragging-cursor')
})
</script>

<style scoped lang="scss">
.bgtree {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: var(--color-surface);
}

.bgtree-header {
  gap: 6px;

.bgtree-header__actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 2px;
}
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 12px 10px 8px 16px;

  .bgtree-header__title {
    flex: 1;
    font-weight: var(--font-weight-semibold);
    font-size: var(--font-size-sm);
    color: var(--color-text-primary);
  }

  .bgtree-header__btn {
    padding: 2px;
  }
}

.bgtree-search {
  padding: 0 12px 8px;
}

.bgtree-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 0 6px 12px;

  &.is-root-drop {
    box-shadow: inset 0 0 0 1.5px var(--color-primary);
    border-radius: var(--border-radius-md, 6px);
  }
}

/* 长按拖拽中的全局抓手光标。
   注意：这里只保留一条 cursor 声明 —— 之前同时写了 grabbing 和 grab 两条，
   后一条覆盖前一条，导致「正在拖拽」反而显示成张开手（grab），与「可拖拽」的悬停态无法区分。 */
:global(body.bgtree-dragging-cursor),
:global(body.bgtree-dragging-cursor *) {
  cursor: grabbing !important;
  user-select: none;
}

.bgtree-editor {
  padding-top: 2px;
  padding-bottom: 4px;
  padding-right: 6px;
}

/* 行内重命名输入框：替换名称占满一行，需允许选中文字 */
.bgtree-node__rename {
  flex: 1;
  min-width: 0;
  user-select: text;
}

.bgtree-node {
  user-select: none;
  display: flex;
  align-items: center;
  gap: 5px;
  height: 32px;
  padding-right: 6px;
  border-radius: var(--border-radius-md, 6px);
  cursor: pointer;
  user-select: none;
  color: var(--color-text-primary);
  transition: background 0.12s ease;

  /* 可拖拽节点：默认抓手提示 */
  &.is-draggable-ready {
    cursor: grab;
  }

  /* 长按进行中 */
  &.is-pressing {
    cursor: grabbing;
    background: var(--color-surface-alt);
  }

  &:hover {
    background: var(--color-surface-alt);
  }

  &.is-active {
    background: var(--color-primary-bg, rgba(37, 99, 235, 0.1));
    color: var(--color-primary);
    font-weight: var(--font-weight-medium, 500);
  }

  &.is-dragging {
    opacity: 0.4;
  }

  /* 中区：拖入该分组 */
  &.is-drop-into {
    background: var(--color-primary-subtle, var(--color-primary-subtle));
    box-shadow: inset 0 0 0 1.5px var(--color-primary);
  }

  /* 上区：排到该行之前 */
  &.is-drop-before {
    box-shadow: inset 0 2px 0 0 var(--color-primary);
    background: var(--color-primary-subtle, rgba(59, 130, 246, 0.06));
  }

  /* 下区：排到该行之后 */
  &.is-drop-after {
    box-shadow: inset 0 -2px 0 0 var(--color-primary);
    background: var(--color-primary-subtle, rgba(59, 130, 246, 0.06));
  }

  &.is-reject {
    background: var(--color-danger-bg, rgba(239, 68, 68, 0.12));
    box-shadow: inset 0 0 0 1.5px var(--color-danger);
  }

  .bgtree-node__chev {
    width: 16px;
    height: 16px;
    flex: none;
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--color-muted-text);
    border-radius: 3px;
    transition: transform 0.15s ease;

    &.is-open {
      transform: rotate(90deg);
    }

    &.is-leaf {
      visibility: hidden;
    }
  }

  .bgtree-node__icon {
    width: 16px;
    height: 16px;
    flex: none;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 14px;

    /* 类型色与高级权限弹窗树/分组菜单保持同一套；琥珀随暗色 token 提亮、表色随主题色联动 */
    &--table {
      color: var(--color-primary);
    }

    &--dashboard {
      color: var(--color-type-dashboard);
    }

    &--group {
      color: var(--color-warning);
    }

    &--ungrouped {
      color: var(--color-text-placeholder);
    }

    &--all {
      color: var(--color-primary);
    }

    &--base {
      color: var(--color-primary);
    }
  }

  .bgtree-node__name {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: var(--font-size-sm);

    em {
      font-style: normal;
      background: var(--color-warning-bg);
      color: var(--color-warning-text);
      border-radius: 2px;
      padding: 0 1px;
    }
  }

  .bgtree-node__count {
    flex: none;
    font-size: 11px;
    line-height: 16px;
    padding: 0 6px;
    border-radius: 9px;
    color: var(--color-muted-text);
    background: var(--color-surface-alt);
  }

  .bgtree-node__tools {
    display: none;
    align-items: center;
    flex: none;
  }

  &:hover .bgtree-node__tools {
    display: flex;
  }

  .bgtree-node__tool {
    width: 18px;
    height: 18px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 3px;
    color: var(--color-muted-text);
    cursor: pointer;

    &:hover {
      background: rgba(15, 23, 42, 0.09);
      color: var(--color-text-primary);
    }
  }

  /* 删除图标：悬停亮红，提示破坏性操作 */
  .bgtree-node__tool--danger:hover {
    background: var(--el-color-danger-light-9, #fef0f0);
    color: var(--el-color-danger);
  }

  /* 行内删除确认按钮：替换在 ⋯ 原位置 */
  .bgtree-node__confirm {
    padding: 1px 8px;
    border: none;
    border-radius: 4px;
    background: var(--el-color-danger);
    color: #fff;
    font-size: 12px;
    line-height: 18px;
    white-space: nowrap;
    cursor: pointer;

    &:hover {
      background: var(--el-color-danger-light-3, #f78989);
    }
  }
}

.bgtree-empty {
  padding: 32px 12px;
  text-align: center;
  font-size: var(--font-size-sm);
  color: var(--color-muted-text);
}

/* 更多操作菜单：图标 + 文案（钉钉多维表格风格），danger 项标红 */
.bgtree-menu__icon {
  margin-right: 8px;
  font-size: 14px;
  color: var(--color-text-secondary, #909399);
  vertical-align: -2px;
}

.bgtree-menu__item--danger {
  color: var(--el-color-danger);

  .bgtree-menu__icon {
    color: var(--el-color-danger);
  }
}
</style>
