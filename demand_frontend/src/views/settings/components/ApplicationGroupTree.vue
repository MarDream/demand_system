<template>
  <div class="agtree">
    <!-- 头部：新建分组 -->
    <div class="agtree-header">
      <span class="agtree-header__title">功能点分组</span>
      <el-button link class="agtree-header__btn" title="新建分组" @click="startCreate(null)">
        <el-icon><FolderAdd /></el-icon>
      </el-button>
    </div>

    <!-- 搜索 -->
    <div class="agtree-search">
      <el-input
        v-model="keyword"
        size="small"
        placeholder="搜索功能点或分组"
        clearable
        :prefix-icon="Search"
      />
    </div>

    <!-- 树 -->
    <div class="agtree-list" @dragover.prevent @drop.prevent="handleRootDrop">
      <template v-for="node in nodes" :key="node.key">
        <!-- 行内新建分组输入 -->
        <div v-if="node.kind === 'editor'" class="agtree-editor" :style="{ paddingLeft: `${6 + node.depth * 14}px` }">
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
          class="agtree-node"
          :class="[
            `agtree-node--${node.kind}`,
            {
              'is-active': node.isActive,
              'is-drop': dropKey === node.key,
              'is-reject': rejectKey === node.key,
              'is-dragging': dragKey === node.key,
            },
          ]"
          :style="{ paddingLeft: `${6 + node.depth * 14}px` }"
          :data-key="node.key"
          :draggable="node.draggable"
          @click="handleClick($event, node)"
          @dragstart="handleDragStart($event, node)"
          @dragend="handleDragEnd"
          @dragover="handleDragOver($event, node)"
          @dragleave="handleDragLeave(node)"
          @drop="handleDrop($event, node)"
        >
          <!-- 展开箭头 -->
          <span
            class="agtree-node__chev"
            :class="{ 'is-open': node.expanded, 'is-leaf': !node.hasChildren }"
            @click.stop="toggleExpand(node)"
          >
            <el-icon><ArrowRight /></el-icon>
          </span>

          <!-- 图标 -->
          <span class="agtree-node__icon" :class="`agtree-node__icon--${node.kind}`">
            <el-icon>
              <Grid v-if="node.kind === 'all'" />
              <FolderOpened v-else-if="node.kind === 'group' && node.expanded" />
              <Folder v-else-if="node.kind === 'group'" />
              <Folder v-else-if="node.kind === 'ungrouped'" />
              <Cpu v-else />
            </el-icon>
          </span>

          <!-- 名称（搜索时高亮命中片段） -->
          <span class="agtree-node__name" :title="node.name">
            <template v-for="(seg, i) in highlightSegments(node.name)" :key="i">
              <em v-if="seg.hit">{{ seg.text }}</em>
              <template v-else>{{ seg.text }}</template>
            </template>
          </span>

          <!-- 计数 -->
          <span v-if="node.kind !== 'application'" class="agtree-node__count">{{ node.count }}</span>

          <!-- 悬浮工具 -->
          <span v-if="node.quickAdd || node.menu.length" class="agtree-node__tools">
            <el-icon v-if="node.quickAdd" class="agtree-node__tool" title="新建子分组" @click.stop="startCreate(node.id)">
              <FolderAdd />
            </el-icon>
            <el-dropdown
              v-if="node.menu.length"
              trigger="click"
              placement="bottom-end"
              @command="(cmd: string) => handleCommand(cmd, node)"
            >
              <span class="agtree-node__tool" title="更多操作" @click.stop>
                <el-icon><MoreFilled /></el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item
                    v-for="item in node.menu"
                    :key="item.command"
                    :command="item.command"
                    :divided="item.divided"
                  >
                    {{ item.label }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </span>
        </div>
      </template>

      <div v-if="!nodes.length" class="agtree-empty">
        <span v-if="keyword">没有匹配的功能点或分组</span>
        <span v-else>暂无功能点</span>
      </div>
    </div>

    <!-- 移动功能点到分组 -->
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
        <el-button type="primary" @click="confirmMoveApplication">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowRight,
  Cpu,
  Folder,
  FolderAdd,
  FolderOpened,
  Grid,
  MoreFilled,
  Search,
} from '@element-plus/icons-vue'
import type { LlmApplication, LlmApplicationGroup } from '@/api/modules/llmProvider'
import type { ApplicationTreeSelection } from './application-tree.types'

type NodeKind = 'all' | 'ungrouped' | 'group' | 'application' | 'editor'

interface MenuItem {
  command: string
  label: string
  divided?: boolean
}

interface FlatNode {
  key: string
  kind: NodeKind
  /** 分组ID（「全部应用」为 null） */
  id: number | null
  /** 功能点编码，仅 application 节点有值 */
  code?: string
  /** 功能点所属分组（用于拖动/归组），未分组为 null */
  parentGroupId: number | null
  name: string
  depth: number
  count?: number
  hasChildren: boolean
  expanded: boolean
  isActive: boolean
  draggable: boolean
  quickAdd?: boolean
  menu: MenuItem[]
}

const props = defineProps<{
  groups: LlmApplicationGroup[]
  applications: LlmApplication[]
  selection: ApplicationTreeSelection
}>()

const emit = defineEmits<{
  select: [payload: ApplicationTreeSelection]
  'create-group': [payload: { name: string; parentId: number | null }]
  'rename-group': [payload: { id: number; name: string }]
  'delete-group': [id: number]
  'move-group': [payload: { id: number; parentId: number | null }]
  'move-application': [payload: { code: string; groupId: number | null }]
}>()

/**
 * 折叠状态，key 形如 all / g:12 / ug:12 / ug:root；「全部应用」是平铺汇总视图，默认折叠。
 * 持久化到 localStorage，下次进入保持上次的展开形态。
 */
const COLLAPSED_STORAGE_KEY = 'llm.applicationGroupTree.collapsed'

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
const creating = ref<{ parentId: number | null } | null>(null)
const editorValue = ref('')

const dragKey = ref<string | null>(null)
const dropKey = ref<string | null>(null)
const rejectKey = ref<string | null>(null)
let dragging: FlatNode | null = null

const moveDialogVisible = ref(false)
const moveApplicationCode = ref<string | null>(null)
const moveTargetGroupId = ref<number | null>(null)

/* ---------------- 基础查询 ---------------- */

/**
 * 接口返回的是嵌套树，这里拍平成列表，
 * 后续按 parentId 查找子分组、做拖拽防环校验都基于扁平结构
 */
const allGroups = computed<LlmApplicationGroup[]>(() => {
  const out: LlmApplicationGroup[] = []
  const walk = (list?: LlmApplicationGroup[]) => {
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

function childGroups(parentId: number | null): LlmApplicationGroup[] {
  return allGroups.value
    .filter((g) => (g.parentId ?? null) === parentId)
    .slice()
    .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0) || a.id - b.id)
}

function applicationsOfGroup(groupId: number | null): LlmApplication[] {
  return props.applications.filter((a) => (a.groupId ?? null) === groupId)
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

function applicationMatches(application: LlmApplication) {
  const kw = keywordLower()
  if (!kw) return true
  return (
    application.name.toLowerCase().includes(kw) ||
    application.code.toLowerCase().includes(kw)
  )
}

function groupMatches(group: LlmApplicationGroup): boolean {
  const kw = keywordLower()
  if (!kw) return true
  if (group.name.toLowerCase().includes(kw)) return true
  const ids = [group.id, ...descendantGroupIds(group.id)]
  return props.applications.some((a) => ids.includes(a.groupId ?? -1) && applicationMatches(a))
}

function applicationMenu(): MenuItem[] {
  return [{ command: 'move', label: '移动到分组…' }]
}

function groupMenu(): MenuItem[] {
  return [
    { command: 'create-sub', label: '新建子分组' },
    { command: 'rename', label: '重命名', divided: true },
    { command: 'delete', label: '删除分组' },
  ]
}

/* ---------------- 选中态 ---------------- */

function isApplicationActive(code: string) {
  return props.selection.type === 'application' && props.selection.code === code
}

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

function toApplicationNode(application: LlmApplication, depth: number): FlatNode {
  return {
    key: `a:${application.code}`,
    kind: 'application',
    id: application.id ?? null,
    code: application.code,
    parentGroupId: application.groupId ?? null,
    name: application.name,
    depth,
    hasChildren: false,
    expanded: false,
    isActive: isApplicationActive(application.code),
    draggable: true,
    menu: applicationMenu(),
  }
}

function toUngroupedNode(
  parentGroupId: number | null,
  depth: number,
  total: number,
  expanded: boolean,
): FlatNode {
  return {
    key: ugKey(parentGroupId),
    kind: 'ungrouped',
    id: parentGroupId,
    parentGroupId,
    name: '未分组',
    depth,
    count: total,
    hasChildren: true,
    expanded,
    isActive: isUngroupedActive(parentGroupId),
    draggable: false,
    menu: [],
  }
}

function walkGroups(parentId: number | null, depth: number, out: FlatNode[]) {
  const kw = keywordLower()
  childGroups(parentId).forEach((group) => {
    if (!groupMatches(group)) return

    const subGroups = childGroups(group.id)
    const directApplications = applicationsOfGroup(group.id)
    const visibleApplications = kw ? directApplications.filter(applicationMatches) : directApplications
    const key = `g:${group.id}`
    const expanded = !!kw || isExpanded(key)

    out.push({
      key,
      kind: 'group',
      id: group.id,
      parentGroupId: group.parentId ?? null,
      name: group.name,
      depth,
      count: group.totalApplicationCount ?? group.applicationCount ?? 0,
      hasChildren: subGroups.length > 0 || directApplications.length > 0,
      expanded,
      isActive: isGroupActive(group.id),
      draggable: true,
      quickAdd: true,
      menu: groupMenu(),
    })

    if (!expanded) return

    if (subGroups.length) {
      // 仅当存在子分组时才用「未分组」把直接挂载的功能点与子分区分开，避免无意义层级
      if (visibleApplications.length) {
        const uKey = ugKey(group.id)
        const uExpanded = !!kw || isExpanded(uKey)
        out.push(toUngroupedNode(group.id, depth + 1, directApplications.length, uExpanded))
        if (uExpanded) {
          visibleApplications.forEach((a) => out.push(toApplicationNode(a, depth + 2)))
        }
      }
      walkGroups(group.id, depth + 1, out)
    } else {
      visibleApplications.forEach((a) => out.push(toApplicationNode(a, depth + 1)))
    }
  })
}

const nodes = computed<FlatNode[]>(() => {
  const kw = keywordLower()
  const out: FlatNode[] = []

  // 1. 全部应用（平铺视图，默认折叠；搜索时不自动展开，避免同一功能点重复出现）
  const allExpanded = isExpanded('all')
  out.push({
    key: 'all',
    kind: 'all',
    id: null,
    parentGroupId: null,
    name: '全部应用',
    depth: 0,
    count: props.applications.length,
    hasChildren: props.applications.length > 0,
    expanded: allExpanded,
    isActive: props.selection.type === 'all',
    draggable: false,
    menu: [],
  })
  if (allExpanded) {
    props.applications.filter(applicationMatches).forEach((a) => out.push(toApplicationNode(a, 1)))
  }

  // 2. 根层级未分组的功能点
  const rootApplications = applicationsOfGroup(null)
  const visibleRootApplications = kw ? rootApplications.filter(applicationMatches) : rootApplications
  if (visibleRootApplications.length) {
    const key = ugKey(null)
    const expanded = !!kw || isExpanded(key)
    out.push(toUngroupedNode(null, 0, rootApplications.length, expanded))
    if (expanded) {
      visibleRootApplications.forEach((a) => out.push(toApplicationNode(a, 1)))
    }
  }

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
  if (props.selection.type === 'application' && props.selection.code) {
    const application = props.applications.find((a) => a.code === props.selection.code)
    let current: number | null = application?.groupId ?? null
    let guard = 0
    while (current != null && guard++ < 50) {
      next.delete(`g:${current}`)
      const group: LlmApplicationGroup | undefined = allGroups.value.find((g) => g.id === current)
      current = group?.parentId ?? null
    }
  } else if (props.selection.type === 'group' && props.selection.groupId != null) {
    let current: number | null = props.selection.groupId
    let guard = 0
    while (current != null && guard++ < 50) {
      next.delete(`g:${current}`)
      const group: LlmApplicationGroup | undefined = allGroups.value.find((g) => g.id === current)
      current = group?.parentId ?? null
    }
  } else if (props.selection.type === 'ungrouped' && props.selection.groupId != null) {
    next.delete(ugKey(props.selection.groupId))
  }
  collapsedKeys.value = next
}

watch(
  () => [props.selection.type, props.selection.groupId, props.selection.code],
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
  if ((event.target as HTMLElement).closest('.agtree-node__tools')) return

  if (node.kind === 'application') {
    emit('select', { type: 'application', groupId: node.parentGroupId, code: node.code ?? null })
    return
  }
  if (node.kind === 'all') {
    emit('select', { type: 'all', groupId: null, code: null })
  } else if (node.kind === 'ungrouped') {
    emit('select', { type: 'ungrouped', groupId: node.parentGroupId, code: null })
  } else if (node.kind === 'group') {
    emit('select', { type: 'group', groupId: node.id, code: null })
  }
  toggleExpand(node)
}

function handleCommand(command: string, node: FlatNode) {
  if (node.kind === 'application') {
    if (command === 'move' && node.code) {
      openMoveDialog(node.code, node.parentGroupId)
    }
    return
  }
  if (command === 'create-sub') {
    startCreate(node.id)
  } else if (command === 'rename' && node.id != null) {
    promptRenameGroup(node.id, node.name)
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
    const input = document.querySelector<HTMLInputElement>('.agtree-editor input')
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
  emit('create-group', { name, parentId })
}

function promptRenameGroup(id: number, currentName: string) {
  ElMessageBox.prompt('', '重命名分组', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputValue: currentName,
    inputValidator: (value: string) => (value && value.trim() ? true : '分组名称不能为空'),
  })
    .then(({ value }) => {
      const name = (value || '').trim()
      if (name && name !== currentName) {
        emit('rename-group', { id, name })
      }
    })
    .catch(() => {})
}

function confirmDeleteGroup(id: number, name: string, count: number) {
  const extra = count > 0 ? `\n\n该分组下的 ${count} 个功能点与子分组将上移到父级，不会被删除。` : ''
  ElMessageBox.confirm(`确定删除分组「${name}」吗？${extra}`, '删除确认', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => emit('delete-group', id))
    .catch(() => {})
}

/* ---------------- 移动功能点 ---------------- */

function openMoveDialog(code: string, currentGroupId: number | null) {
  moveApplicationCode.value = code
  moveTargetGroupId.value = currentGroupId ?? -1
  moveDialogVisible.value = true
}

function confirmMoveApplication() {
  if (!moveApplicationCode.value) return
  const target = moveTargetGroupId.value
  const groupId = target == null || target === -1 ? null : target
  emit('move-application', { code: moveApplicationCode.value, groupId })
  moveDialogVisible.value = false
}

/* ---------------- 拖拽 ---------------- */

function handleDragStart(event: DragEvent, node: FlatNode) {
  if (!node.draggable) return
  dragging = node
  dragKey.value = node.key
  event.dataTransfer?.setData('text/plain', node.key)
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move'
}

function handleDragEnd() {
  dragging = null
  dragKey.value = null
  dropKey.value = null
  rejectKey.value = null
}

function resolveDropTarget(node: FlatNode): { ok: boolean; reason?: string } {
  if (!dragging || dragging.key === node.key) return { ok: false }
  if (node.kind !== 'group' && node.kind !== 'ungrouped' && node.kind !== 'all') return { ok: false }

  if (dragging.kind === 'application') {
    if (node.kind === 'all') {
      return { ok: false, reason: '「全部应用」是平铺视图，不能拖入' }
    }
    const targetGroupId = node.kind === 'ungrouped' ? node.parentGroupId ?? null : node.id
    // 已在目标分组内，无需移动
    if ((dragging.parentGroupId ?? null) === targetGroupId) return { ok: false }
    return { ok: true }
  }

  if (dragging.kind === 'group' && dragging.id != null) {
    if (node.kind === 'all') {
      return { ok: false, reason: '「全部应用」是平铺视图，不能拖入' }
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

function handleDragOver(event: DragEvent, node: FlatNode) {
  if (!dragging) return
  // 必须始终 preventDefault，否则浏览器不触发 drop，拒绝原因也无法提示
  event.preventDefault()
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'move'
  const result = resolveDropTarget(node)
  if (result.ok) {
    rejectKey.value = null
    dropKey.value = node.key
  } else {
    dropKey.value = null
    rejectKey.value = result.reason ? node.key : null
  }
}

function handleDragLeave(node: FlatNode) {
  if (dropKey.value === node.key) dropKey.value = null
  if (rejectKey.value === node.key) rejectKey.value = null
}

function handleDrop(event: DragEvent, node: FlatNode) {
  event.stopPropagation()
  const source = dragging
  const result = resolveDropTarget(node)
  handleDragEnd()
  if (!source || !result.ok) {
    if (result.reason) ElMessage.warning(result.reason)
    return
  }

  if (source.kind === 'application' && source.code) {
    const groupId = node.kind === 'ungrouped' ? node.parentGroupId ?? null : node.id
    emit('move-application', { code: source.code, groupId })
    return
  }
  if (source.kind === 'group' && source.id != null) {
    const parentId = node.kind === 'ungrouped' ? node.parentGroupId ?? null : node.id
    emit('move-group', { id: source.id, parentId })
  }
}

/** 拖到列表空白处 = 把分组移到根层级 */
function handleRootDrop() {
  const source = dragging
  if (!source || source.kind !== 'group' || source.id == null) return
  if ((source.parentGroupId ?? null) === null) return
  emit('move-group', { id: source.id, parentId: null })
  handleDragEnd()
}
</script>

<style scoped lang="scss">
.agtree {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: var(--el-bg-color);
}

.agtree-header {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 4px 10px 8px 16px;

  .agtree-header__title {
    flex: 1;
    font-weight: 600;
    font-size: 13px;
    color: var(--el-text-color-primary);
  }

  .agtree-header__btn {
    padding: 2px;
  }
}

.agtree-search {
  padding: 0 12px 8px;
}

.agtree-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 0 6px 12px;
}

.agtree-editor {
  padding-top: 2px;
  padding-bottom: 4px;
  padding-right: 6px;
}

.agtree-node {
  display: flex;
  align-items: center;
  gap: 5px;
  height: 32px;
  padding-right: 6px;
  border-radius: 6px;
  cursor: pointer;
  user-select: none;
  color: var(--el-text-color-primary);
  transition: background 0.12s ease;

  &:hover {
    background: var(--el-fill-color-light);
  }

  &.is-active {
    background: var(--el-color-primary-light-9);
    color: var(--el-color-primary);
    font-weight: 500;
  }

  &.is-dragging {
    opacity: 0.4;
  }

  &.is-drop {
    background: var(--el-color-primary-light-9);
    box-shadow: inset 0 0 0 1.5px var(--el-color-primary);
  }

  &.is-reject {
    background: var(--el-color-danger-light-9);
    box-shadow: inset 0 0 0 1.5px var(--el-color-danger);
  }

  .agtree-node__chev {
    width: 16px;
    height: 16px;
    flex: none;
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--el-text-color-placeholder);
    border-radius: 3px;
    transition: transform 0.15s ease;

    &.is-open {
      transform: rotate(90deg);
    }

    &.is-leaf {
      visibility: hidden;
    }
  }

  .agtree-node__icon {
    width: 16px;
    height: 16px;
    flex: none;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 14px;

    &--group {
      color: #f59e0b;
    }

    &--ungrouped {
      color: var(--el-text-color-placeholder);
    }

    &--all {
      color: var(--el-color-primary);
    }

    &--application {
      color: var(--el-color-primary);
    }
  }

  .agtree-node__name {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: 13px;

    em {
      font-style: normal;
      background: #fef3c7;
      color: #92400e;
      border-radius: 2px;
      padding: 0 1px;
    }
  }

  .agtree-node__count {
    flex: none;
    font-size: 11px;
    line-height: 16px;
    padding: 0 6px;
    border-radius: 9px;
    color: var(--el-text-color-secondary);
    background: var(--el-fill-color-light);
  }

  .agtree-node__tools {
    display: none;
    align-items: center;
    flex: none;
  }

  &:hover .agtree-node__tools {
    display: flex;
  }

  .agtree-node__tool {
    width: 18px;
    height: 18px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 3px;
    color: var(--el-text-color-secondary);
    cursor: pointer;

    &:hover {
      background: var(--el-fill-color-dark);
      color: var(--el-text-color-primary);
    }
  }
}

.agtree-empty {
  padding: 32px 12px;
  text-align: center;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>
