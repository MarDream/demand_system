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
            <el-button type="primary" @click="openTypeDialog()">
              <el-icon><Plus /></el-icon>
              新增类型
            </el-button>
          </div>

          <el-table :data="types" border style="width: 100%">
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
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openTypeDialog(row)">编辑</el-button>
                <el-button link type="danger" @click="deleteType(row.id!)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <el-tab-pane label="优先级" name="priorities">
        <div class="tab-content">
          <div class="tab-header">
            <el-button type="primary" @click="openPriorityDialog()">
              <el-icon><Plus /></el-icon>
              新增优先级
            </el-button>
          </div>

          <el-table :data="priorities" border style="width: 100%">
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
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openPriorityDialog(row)">编辑</el-button>
                <el-button link type="danger" @click="deletePriority(row.id!)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 类型对话框 -->
    <el-dialog v-model="typeDialogVisible" :title="editingType ? '编辑需求类型' : '新增需求类型'" width="500px">
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
    <el-dialog v-model="priorityDialogVisible" :title="editingPriority ? '编辑优先级' : '新增优先级'" width="500px">
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
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { requirementConfigApi, type RequirementType, type Priority } from '@/api/modules/requirementConfig'

const activeTab = ref('types')
const types = ref<RequirementType[]>([])
const priorities = ref<Priority[]>([])

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
    const res = await requirementConfigApi.listTypes()
    types.value = res.data || []
  } catch (error) {
    console.error('加载需求类型失败', error)
  }
}

const loadPriorities = async () => {
  try {
    const res = await requirementConfigApi.listPriorities()
    priorities.value = res.data || []
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

onMounted(() => {
  loadTypes()
  loadPriorities()
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
</style>