<template>
  <PageContainer title="多维表格" class="bitable-list">
    <div class="bitable-layout">
      <!-- 左侧：Base 分组目录树（与模型应用设置页一致的管理方式） -->
      <aside class="bitable-sidebar" v-loading="groupsLoading">
        <BaseGroupTree
          :groups="baseGroups"
          :bases="bases"
          :selection="treeSelection"
          :can-manage="canManageGroups"
          @select="handleTreeSelect"
          @create-group="handleCreateGroup"
          @rename-group="handleRenameGroup"
          @delete-group="handleDeleteGroup"
          @move-group="handleMoveGroup"
          @move-base="handleMoveBase"
          @rename-base="handleRenameBase"
          @open-base="goToEditor"
          @open-dashboard="goToDashboard"
          @delete-base="handleDeleteBase"
          @create-base="handleCreateInGroup"
        />
      </aside>

      <!-- 右侧：当前选中范围下的 Base 卡片 -->
      <section class="bitable-main" v-loading="loading">
        <div class="bitable-list__header">
          <div class="bitable-list__scope">
            <span class="bitable-list__scope-title">{{ scopeTitle }}</span>
            <el-tag type="info" size="small">共 {{ scopedBases.length }} 个</el-tag>
          </div>
          <div class="bitable-list__actions">
            <el-button type="primary" @click="handleCreate()">
              <el-icon><Plus /></el-icon> 新建多维表格
            </el-button>
            <el-button @click="showTemplateGallery = true">
              <el-icon><Collection /></el-icon> 从模板创建
            </el-button>
          </div>
        </div>

        <!-- Base 卡片列表 -->
        <div v-if="scopedBases.length" class="bitable-list__grid">
          <el-card
            v-for="base in scopedBases"
            :key="base.id"
            class="base-card"
            shadow="hover"
            @click="goToEditor(base.id)"
          >
            <div class="base-card__header">
              <span class="base-card__name">{{ base.name }}</span>
              <el-dropdown trigger="click" @command="handleAction($event, base)">
                <el-button link @click.stop><el-icon><MoreFilled /></el-icon></el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="edit">重命名</el-dropdown-item>
                    <el-dropdown-item command="move" divided>移动到分组…</el-dropdown-item>
                    <el-dropdown-item command="dashboard">仪表盘</el-dropdown-item>
                    <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
            <p class="base-card__desc">{{ base.description || '暂无描述' }}</p>
            <div class="base-card__footer">
              <span>{{ base.tableCount || 0 }} 个数据表</span>
              <span>{{ base.creatorName }}</span>
              <span>{{ formatDate(base.createdAt) }}</span>
            </div>
          </el-card>
        </div>
        <el-empty
          v-else
          :description="bases.length ? '当前范围下暂无多维表格' : '暂无多维表格，点击上方按钮创建'"
        />
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
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, MoreFilled, Collection } from '@element-plus/icons-vue'
import { resolveErrorMessage } from '@/utils/error'
import PageContainer from '@/components/common/PageContainer.vue'
import { usePermission } from '@/composables/usePermission'
import {
  listBases,
  listBaseGroups,
  createBase,
  createBaseGroup,
  renameBaseGroup,
  moveBaseGroup,
  deleteBaseGroup,
  moveBaseToGroup,
  updateBase,
  deleteBase,
} from '@/api/modules/bitable'
import type { BitableBase, BitableBaseGroup, BitableBaseCreateDTO } from '@/types/bitable'
import TemplateGallery from './components/TemplateGallery.vue'
import BaseGroupTree from './components/BaseGroupTree.vue'

const router = useRouter()
const { hasRole } = usePermission()

const bases = ref<BitableBase[]>([])
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

async function loadBases() {
  loading.value = true
  try {
    const res = await listBases()
    bases.value = Array.isArray(res) ? res : (res as any).data || []
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '加载失败'))
  } finally {
    loading.value = false
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

/** 右侧展示的 Base：由左侧树的选中范围决定 */
const scopedBases = computed<BitableBase[]>(() => {
  const selection = treeSelection.value
  if (selection.type === 'ungrouped') {
    const target = selection.groupId ?? null
    return bases.value.filter((item) => (item.groupId ?? null) === target)
  }
  if (selection.type === 'group' && selection.groupId != null) {
    const ids = collectGroupIds(selection.groupId)
    return bases.value.filter((item) => item.groupId != null && ids.includes(item.groupId))
  }
  return bases.value
})

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
  treeSelection.value = payload
}

/* ---------------- 分组管理 ---------------- */

async function handleCreateGroup(payload: { name: string; parentId: number | null }) {
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

/* ---------------- Base CRUD ---------------- */

function goToEditor(id: number) {
  router.push({ name: 'BitableEditor', params: { baseId: id } })
}

function goToDashboard(id: number) {
  router.push({ name: 'BitableDashboard', params: { baseId: id } })
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

function handleAction(command: string, base: BitableBase) {
  if (command === 'edit') {
    editingBase.value = base
    form.value = { name: base.name, description: base.description || '' }
    showDialog.value = true
  } else if (command === 'dashboard') {
    goToDashboard(base.id)
  } else if (command === 'move') {
    handleMoveBasePrompt(base)
  } else if (command === 'delete') {
    handleDeleteBase(base)
  }
}

function handleMoveBasePrompt(base: BitableBase) {
  moveBaseId.value = base.id
  moveTargetGroupId.value = base.groupId ?? null
  moveDialogVisible.value = true
}

async function confirmMoveBase() {
  if (moveBaseId.value == null) return
  await handleMoveBase({ baseId: moveBaseId.value, groupId: moveTargetGroupId.value ?? null })
  moveDialogVisible.value = false
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
  if (!form.value.name.trim()) {
    ElMessage.warning('请输入名称')
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
  max-height: calc(100vh - 220px);
  position: sticky;
  top: var(--spacing-md);
}

.bitable-main {
  min-width: 0;
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

.bitable-list__actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  flex: none;
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
</style>
