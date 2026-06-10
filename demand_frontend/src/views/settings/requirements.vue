<template>
  <div class="config-container">
    <div class="config-header">
      <h2>需求基本配置</h2>
      <p class="config-desc">管理系统中需求类型和优先级的配置</p>
    </div>

    <el-tabs v-model="activeTab" class="config-tabs">
      <el-tab-pane label="需求类型" name="types">
        <div class="tab-content">
          <div class="tab-header">
            <AppButton type="primary" permission="button:requirement-config:create" @click="openTypeDialog()">
              <el-icon><Plus /></el-icon>
              新增类型
            </AppButton>
          </div>

          <el-table ref="typeTableRef" :data="types" border style="width: 100%" row-key="id">
            <el-table-column width="60" align="center">
              <template #default>
                <el-icon class="drag-handle" :size="18">
                  <Operation />
                </el-icon>
              </template>
            </el-table-column>
            <el-table-column prop="name" label="名称" min-width="120" />
            <el-table-column prop="code" label="编码" min-width="100" />
            <el-table-column prop="color" label="颜色" min-width="100">
              <template #default="{ row }">
                <div class="color-cell">
                  <span class="color-dot" :style="{ backgroundColor: row.color }"></span>
                  <span>{{ row.color }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
            <el-table-column prop="isDefault" label="默认" width="80" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.isDefault" type="success" size="small">是</el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <AppButton link type="primary" permission="button:requirement-config:update" @click="openTypeDialog(row)"><el-icon><EditPen /></el-icon></AppButton>
                <AppButton link type="danger" permission="button:requirement-config:delete" @click="deleteType(row.id!)"><el-icon><Delete /></el-icon></AppButton>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <el-tab-pane label="优先级" name="priorities">
        <div class="tab-content">
          <div class="tab-header">
            <AppButton type="primary" permission="button:requirement-config:create" @click="openPriorityDialog()">
              <el-icon><Plus /></el-icon>
              新增优先级
            </AppButton>
          </div>

          <el-table ref="priorityTableRef" :data="priorities" border style="width: 100%" row-key="id">
            <el-table-column width="60" align="center">
              <template #default>
                <el-icon class="drag-handle" :size="18">
                  <Operation />
                </el-icon>
              </template>
            </el-table-column>
            <el-table-column prop="name" label="名称" min-width="120" />
            <el-table-column prop="code" label="编码" min-width="100" />
            <el-table-column prop="level" label="级别" width="80" align="center" />
            <el-table-column prop="color" label="颜色" min-width="100">
              <template #default="{ row }">
                <div class="color-cell">
                  <span class="color-dot" :style="{ backgroundColor: row.color }"></span>
                  <span>{{ row.color }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
            <el-table-column prop="isDefault" label="默认" width="80" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.isDefault" type="success" size="small">是</el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <AppButton link type="primary" permission="button:requirement-config:update" @click="openPriorityDialog(row)"><el-icon><EditPen /></el-icon></AppButton>
                <AppButton link type="danger" permission="button:requirement-config:delete" @click="deletePriority(row.id!)"><el-icon><Delete /></el-icon></AppButton>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <el-tab-pane label="节点状态" name="nodeStatuses">
        <div class="tab-content">
          <div class="tab-header">
            <AppButton type="primary" permission="button:requirement-config:create" @click="openNodeStatusDialog()">
              <el-icon><Plus /></el-icon>
              新增节点状态
            </AppButton>
          </div>

          <el-table ref="nodeStatusTableRef" :data="nodeStatuses" border style="width: 100%" row-key="id">
            <el-table-column width="60" align="center">
              <template #default>
                <el-icon class="drag-handle" :size="18">
                  <Operation />
                </el-icon>
              </template>
            </el-table-column>
            <el-table-column prop="name" label="状态名称" min-width="120" />
            <el-table-column prop="code" label="编码" min-width="150" />
            <el-table-column prop="color" label="颜色" min-width="100">
              <template #default="{ row }">
                <div class="color-cell">
                  <span class="color-dot" :style="{ backgroundColor: row.color }"></span>
                  <span>{{ row.color }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="类型标记" min-width="160">
              <template #default="{ row }">
                <el-tag v-if="row.isStart" type="success" size="small" style="margin-right:4px">开始</el-tag>
                <el-tag v-if="row.isEnd" type="info" size="small" style="margin-right:4px">结束</el-tag>
                <el-tag v-if="row.isCancel" type="danger" size="small">取消</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <AppButton link type="primary" permission="button:requirement-config:update" @click="openNodeStatusDialog(row)"><el-icon><EditPen /></el-icon></AppButton>
                <AppButton link type="danger" permission="button:requirement-config:delete" @click="deleteNodeStatus(row.id)"><el-icon><Delete /></el-icon></AppButton>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 类型对话框 -->
    <el-dialog v-model="typeDialogVisible" :title="editingType ? '编辑需求类型' : '新增需求类型'" width="500px" class="settings-form-dialog">
      <el-form ref="typeFormRef" :model="typeForm" :rules="typeRules" label-width="100px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="typeForm.name" placeholder="请输入类型名称" />
        </el-form-item>
        <el-form-item label="编码" prop="code">
          <el-input v-model="typeForm.code" placeholder="如: FEATURE" />
        </el-form-item>
        <el-form-item label="颜色" prop="color">
          <el-color-picker v-model="typeForm.color" show-alpha />
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="typeForm.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="默认">
          <el-switch v-model="typeForm.isDefault" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveType">保存</el-button>
      </template>
    </el-dialog>

    <!-- 优先级对话框 -->
    <el-dialog v-model="priorityDialogVisible" :title="editingPriority ? '编辑优先级' : '新增优先级'" width="500px" class="settings-form-dialog">
      <el-form ref="priorityFormRef" :model="priorityForm" :rules="priorityRules" label-width="100px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="priorityForm.name" placeholder="如: P0-紧急" />
        </el-form-item>
        <el-form-item label="编码" prop="code">
          <el-input v-model="priorityForm.code" placeholder="如: P0" />
        </el-form-item>
        <el-form-item label="级别" prop="level">
          <el-input-number v-model="priorityForm.level" :min="0" />
          <span class="form-tip">数字越小优先级越高</span>
        </el-form-item>
        <el-form-item label="颜色" prop="color">
          <el-color-picker v-model="priorityForm.color" show-alpha />
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="priorityForm.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="默认">
          <el-switch v-model="priorityForm.isDefault" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="priorityDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="savePriority">保存</el-button>
      </template>
    </el-dialog>

    <!-- 节点状态对话框 -->
    <el-dialog v-model="nodeStatusDialogVisible" :title="editingNodeStatus ? '编辑节点状态' : '新增节点状态'" width="500px" class="settings-form-dialog">
      <el-form ref="nodeStatusFormRef" :model="nodeStatusForm" :rules="nodeStatusRules" label-width="100px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="nodeStatusForm.name" placeholder="如: 待评审" />
        </el-form-item>
        <el-form-item label="编码" prop="code">
          <el-input v-model="nodeStatusForm.code" placeholder="如: PENDING_REVIEW" />
        </el-form-item>
        <el-form-item label="颜色">
          <el-color-picker v-model="nodeStatusForm.color" show-alpha />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="nodeStatusForm.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="特殊标记">
          <el-checkbox v-model="nodeStatusForm.isStart">开始状态</el-checkbox>
          <el-checkbox v-model="nodeStatusForm.isEnd" style="margin-left: 16px">结束状态</el-checkbox>
          <el-checkbox v-model="nodeStatusForm.isCancel" style="margin-left: 16px">取消状态</el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="nodeStatusDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveNodeStatus">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, Rank, Operation, EditPen, Delete } from '@element-plus/icons-vue'
import { requirementConfigApi, type RequirementType, type Priority, type SortItem } from '@/api/modules/requirementConfig'
import { nodeStatusApi, type NodeStatus, type SortItem as NodeStatusSortItem } from '@/api/modules/workflow-engine'
import { normalizeText } from '@/utils/format'
import Sortable, { type SortableEvent } from 'sortablejs'
import AppButton from '@/components/common/AppButton.vue'

const activeTab = ref('types')
const types = ref<RequirementType[]>([])
const priorities = ref<Priority[]>([])

// 表格ref
const typeTableRef = ref()
const priorityTableRef = ref()
const nodeStatusTableRef = ref()

// 类型对话框
const typeDialogVisible = ref(false)
const typeFormRef = ref<FormInstance>()
const editingType = ref<RequirementType | null>(null)

const typeForm = ref({
  name: '',
  code: '',
  color: '#409EFF',
  sortOrder: 0,
  isDefault: false
})

const typeRules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入编码', trigger: 'blur' }]
}

// 优先级对话框
const priorityDialogVisible = ref(false)
const priorityFormRef = ref<FormInstance>()
const editingPriority = ref<Priority | null>(null)

const priorityForm = ref({
  name: '',
  code: '',
  level: 2,
  color: '#409EFF',
  sortOrder: 0,
  isDefault: false
})

const priorityRules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入编码', trigger: 'blur' }]
}

const loadTypes = async () => {
  try {
    const res = await requirementConfigApi.listTypes() as any
    const list = Array.isArray(res) ? res : res?.data || []
    types.value = list.map((t: RequirementType) => ({ ...t, name: normalizeText(t.name) }))
  } catch (error) {
    console.error('加载需求类型失败', error)
  }
}

const loadPriorities = async () => {
  try {
    const res = await requirementConfigApi.listPriorities() as any
    const list = Array.isArray(res) ? res : res?.data || []
    priorities.value = list.map((p: Priority) => ({ ...p, name: normalizeText(p.name) }))
  } catch (error) {
    console.error('加载优先级失败', error)
  }
}

const openTypeDialog = (type?: RequirementType) => {
  editingType.value = type || null
  if (type) {
    typeForm.value = {
      name: type.name,
      code: type.code,
      color: type.color || '#409EFF',
      sortOrder: type.sortOrder || 0,
      isDefault: type.isDefault || false
    }
  } else {
    typeForm.value = {
      name: '',
      code: '',
      color: '#409EFF',
      sortOrder: 0,
      isDefault: false
    }
  }
  typeDialogVisible.value = true
}

const saveType = async () => {
  if (!typeFormRef.value) return
  await typeFormRef.value.validate(async (valid) => {
    if (valid) {
      try {
        if (editingType.value?.id) {
          await requirementConfigApi.updateType(editingType.value.id, typeForm.value as RequirementType)
          ElMessage.success('更新成功')
        } else {
          await requirementConfigApi.createType(typeForm.value as RequirementType)
          ElMessage.success('创建成功')
        }
        typeDialogVisible.value = false
        loadTypes()
      } catch (error) {
        ElMessage.error('保存失败')
      }
    }
  })
}

const deleteType = async (id: number) => {
  await ElMessageBox.confirm('确定要删除该类型吗？', '提示', { type: 'warning' })
  try {
    await requirementConfigApi.deleteType(id)
    ElMessage.success('删除成功')
    loadTypes()
  } catch (error) {
    ElMessage.error('删除失败')
  }
}

const openPriorityDialog = (priority?: Priority) => {
  editingPriority.value = priority || null
  if (priority) {
    priorityForm.value = {
      name: priority.name,
      code: priority.code,
      level: priority.level || 2,
      color: priority.color || '#409EFF',
      sortOrder: priority.sortOrder || 0,
      isDefault: priority.isDefault || false
    }
  } else {
    priorityForm.value = {
      name: '',
      code: '',
      level: 2,
      color: '#409EFF',
      sortOrder: 0,
      isDefault: false
    }
  }
  priorityDialogVisible.value = true
}

const savePriority = async () => {
  if (!priorityFormRef.value) return
  await priorityFormRef.value.validate(async (valid) => {
    if (valid) {
      try {
        if (editingPriority.value?.id) {
          await requirementConfigApi.updatePriority(editingPriority.value.id, priorityForm.value as Priority)
          ElMessage.success('更新成功')
        } else {
          await requirementConfigApi.createPriority(priorityForm.value as Priority)
          ElMessage.success('创建成功')
        }
        priorityDialogVisible.value = false
        loadPriorities()
      } catch (error) {
        ElMessage.error('保存失败')
      }
    }
  })
}

const deletePriority = async (id: number) => {
  await ElMessageBox.confirm('确定要删除该优先级吗？', '提示', { type: 'warning' })
  try {
    await requirementConfigApi.deletePriority(id)
    ElMessage.success('删除成功')
    loadPriorities()
  } catch (error) {
    ElMessage.error('删除失败')
  }
}

// 初始化拖拽排序
const initTypeSortable = () => {
  nextTick(() => {
    const el = typeTableRef.value?.$el.querySelector('.el-table__body-wrapper tbody')
    if (!el) return

    Sortable.create(el, {
      handle: '.drag-handle',
      animation: 150,
      ghostClass: 'sortable-ghost',
      onEnd: async (evt: SortableEvent) => {
        const { oldIndex, newIndex } = evt
        if (oldIndex === newIndex) return

        // 更新本地数据
        const movedItem = types.value.splice(oldIndex!, 1)[0]
        types.value.splice(newIndex!, 0, movedItem)

        // 重新计算sortOrder
        const items: SortItem[] = types.value.map((item, index) => ({
          id: item.id!,
          sortOrder: index
        }))

        try {
          await requirementConfigApi.sortTypes(items)
          ElMessage.success('排序已保存')
          loadTypes()
        } catch (error) {
          ElMessage.error('排序保存失败')
          loadTypes() // 恢复原始顺序
        }
      }
    })
  })
}

const initPrioritySortable = () => {
  nextTick(() => {
    const el = priorityTableRef.value?.$el.querySelector('.el-table__body-wrapper tbody')
    if (!el) return

    Sortable.create(el, {
      handle: '.drag-handle',
      animation: 150,
      ghostClass: 'sortable-ghost',
      onEnd: async (evt: SortableEvent) => {
        const { oldIndex, newIndex } = evt
        if (oldIndex === newIndex) return

        // 更新本地数据
        const movedItem = priorities.value.splice(oldIndex!, 1)[0]
        priorities.value.splice(newIndex!, 0, movedItem)

        // 重新计算sortOrder
        const items: SortItem[] = priorities.value.map((item, index) => ({
          id: item.id!,
          sortOrder: index
        }))

        try {
          await requirementConfigApi.sortPriorities(items)
          ElMessage.success('排序已保存')
          loadPriorities()
        } catch (error) {
          ElMessage.error('排序保存失败')
          loadPriorities() // 恢复原始顺序
        }
      }
    })
  })
}

const initNodeStatusSortable = () => {
  nextTick(() => {
    const el = nodeStatusTableRef.value?.$el.querySelector('.el-table__body-wrapper tbody')
    if (!el) return

    Sortable.create(el, {
      handle: '.drag-handle',
      animation: 150,
      ghostClass: 'sortable-ghost',
      onEnd: async (evt: SortableEvent) => {
        const { oldIndex, newIndex } = evt
        if (oldIndex === newIndex) return

        const movedItem = nodeStatuses.value.splice(oldIndex!, 1)[0]
        nodeStatuses.value.splice(newIndex!, 0, movedItem)

        const items: NodeStatusSortItem[] = nodeStatuses.value.map((item, index) => ({
          id: item.id,
          sortOrder: index
        }))

        try {
          await nodeStatusApi.sort(items)
          ElMessage.success('排序已保存')
          await loadNodeStatuses()
        } catch (error) {
          ElMessage.error('排序保存失败')
          await loadNodeStatuses()
        }
      }
    })
  })
}

// 节点状态管理
const nodeStatuses = ref<NodeStatus[]>([])
const nodeStatusDialogVisible = ref(false)
const nodeStatusFormRef = ref<FormInstance>()
const editingNodeStatus = ref<NodeStatus | null>(null)

const nodeStatusForm = ref({
  name: '',
  code: '',
  color: '#409EFF',
  sortOrder: 0,
  isStart: false,
  isEnd: false,
  isCancel: false
})

const nodeStatusRules: FormRules = {
  name: [{ required: true, message: '请输入状态名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入状态编码', trigger: 'blur' }]
}

const loadNodeStatuses = async () => {
  try {
    const res = await nodeStatusApi.list() as any
    nodeStatuses.value = Array.isArray(res) ? res : res?.data || []
  } catch (error) {
    console.error('加载节点状态失败', error)
  }
}

const openNodeStatusDialog = (status?: NodeStatus) => {
  editingNodeStatus.value = status || null
  if (status) {
    nodeStatusForm.value = {
      name: status.name,
      code: status.code,
      color: status.color || '#409EFF',
      sortOrder: status.sortOrder || 0,
      isStart: status.isStart || false,
      isEnd: status.isEnd || false,
      isCancel: status.isCancel || false
    }
  } else {
    nodeStatusForm.value = { name: '', code: '', color: '#409EFF', sortOrder: 0, isStart: false, isEnd: false, isCancel: false }
  }
  nodeStatusDialogVisible.value = true
}

const saveNodeStatus = async () => {
  if (!nodeStatusFormRef.value) return
  await nodeStatusFormRef.value.validate(async (valid) => {
    if (valid) {
      try {
        if (editingNodeStatus.value?.id) {
          await nodeStatusApi.update(editingNodeStatus.value.id, nodeStatusForm.value)
          ElMessage.success('更新成功')
        } else {
          await nodeStatusApi.create(nodeStatusForm.value)
          ElMessage.success('创建成功')
        }
        nodeStatusDialogVisible.value = false
        loadNodeStatuses()
      } catch (error) {
        ElMessage.error('保存失败')
      }
    }
  })
}

const deleteNodeStatus = async (id: number) => {
  await ElMessageBox.confirm('确定要删除该节点状态吗？', '提示', { type: 'warning' })
  try {
    await nodeStatusApi.delete(id)
    ElMessage.success('删除成功')
    loadNodeStatuses()
  } catch (error) {
    ElMessage.error('删除失败')
  }
}

const initializePage = async () => {
  await Promise.all([
    loadTypes(),
    loadPriorities(),
    loadNodeStatuses()
  ])
  initTypeSortable()
  initPrioritySortable()
  initNodeStatusSortable()
}

onMounted(() => {
  void initializePage()
})
</script>

<style lang="scss" scoped>
.config-container {
  padding: 20px;
}

.config-header {
  margin-bottom: 24px;

  h2 {
    margin: 0 0 8px;
    font-size: 22px;
    color: #303133;
  }

  .config-desc {
    margin: 0;
    color: #909399;
    font-size: 14px;
  }
}

.config-tabs {
  :deep(.el-tabs__header) {
    margin-bottom: 20px;
  }
}

.tab-content {
  padding: 0 4px;
}

.tab-header {
  margin-bottom: 16px;
}

.color-cell {
  display: flex;
  align-items: center;
  gap: 8px;

  .color-dot {
    width: 16px;
    height: 16px;
    border-radius: 4px;
  }
}

.form-tip {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}

.drag-handle {
  cursor: grab;
  color: #c0c4cc;
  padding: 6px;
  border-radius: 4px;
  transition: color 0.2s, background-color 0.2s, transform 0.15s;
  user-select: none;

  &:hover {
    color: #409EFF;
    background-color: #ecf5ff;
  }

  &:active {
    cursor: grabbing;
    color: #337ecc;
    background-color: #d9ecff;
    transform: scale(1.1);
  }
}

:deep(.sortable-ghost) {
  opacity: 0.35;
  background: #ecf5ff !important;
  outline: 2px dashed #409EFF;
  outline-offset: -2px;
}

:deep(.sortable-chosen) {
  background: #f5f7fa;
}

:deep(.el-table__body-wrapper tbody) {
  tr {
    transition: transform 0.3s;
  }
}
</style>
