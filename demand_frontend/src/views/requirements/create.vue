<template>
  <div class="create-page">
    <div class="page-header">
      <el-breadcrumb separator="/">
        <el-breadcrumb-item :to="{ name: 'Requirements' }">需求管理</el-breadcrumb-item>
        <el-breadcrumb-item v-if="parentId" :to="{ name: 'RequirementDetail', params: { id: parentId } }">父需求</el-breadcrumb-item>
        <el-breadcrumb-item>{{ isEditMode ? '编辑需求' : (parentId ? '拆分子需求' : '新建需求') }}</el-breadcrumb-item>
      </el-breadcrumb>
      <h2 class="page-title">{{ isEditMode ? '编辑需求' : (parentId ? '拆分子需求' : '新建需求') }}</h2>
    </div>

    <div class="form-card">
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="120px"
        style="max-width: 700px"
      >
        <el-form-item label="需求标题" prop="title">
          <el-input v-model="formData.title" placeholder="请输入需求标题" maxlength="200" />
        </el-form-item>

        <el-form-item label="需求描述" prop="description">
          <el-input
            v-model="formData.description"
            type="textarea"
            :rows="4"
            placeholder="请输入需求描述"
            maxlength="2000"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="需求类型" prop="type">
          <el-select v-model="formData.type" placeholder="请选择需求类型" style="width: 100%">
            <el-option label="功能" value="功能" />
            <el-option label="优化" value="优化" />
            <el-option label="Bug" value="Bug" />
            <el-option label="技术债务" value="技术债务" />
            <el-option label="运营" value="运营" />
          </el-select>
        </el-form-item>

        <el-form-item label="优先级" prop="priority">
          <el-select v-model="formData.priority" placeholder="请选择优先级" style="width: 100%">
            <el-option label="P0" value="P0" />
            <el-option label="P1" value="P1" />
            <el-option label="P2" value="P2" />
            <el-option label="P3" value="P3" />
          </el-select>
        </el-form-item>

        <el-form-item label="负责人" prop="assigneeId">
          <el-select v-model="formData.assigneeId" placeholder="请选择负责人" clearable style="width: 100%">
            <el-option label="张三" :value="1" />
            <el-option label="李四" :value="2" />
            <el-option label="王五" :value="3" />
          </el-select>
        </el-form-item>

        <el-form-item label="所属迭代" prop="iterationId">
          <el-select v-model="formData.iterationId" placeholder="请选择迭代" clearable style="width: 100%">
            <el-option label="迭代 V1.0" :value="1" />
            <el-option label="迭代 V1.1" :value="2" />
            <el-option label="迭代 V2.0" :value="3" />
          </el-select>
        </el-form-item>

        <el-form-item label="截止日期" prop="dueDate">
          <el-date-picker
            v-model="formData.dueDate"
            type="date"
            placeholder="请选择截止日期"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="估算工时" prop="estimatedHours">
          <el-input-number
            v-model="formData.estimatedHours"
            :min="0"
            :precision="1"
            :step="0.5"
            placeholder="请输入估算工时"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">
            {{ isEditMode ? '保存' : '创建' }}
          </el-button>
          <el-button @click="handleCancel">取消</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { requirementApi } from '@/api'
import type { RequirementCreate, RequirementUpdate } from '@/types/requirement'

const route = useRoute()
const router = useRouter()

const formRef = ref<FormInstance>()
const submitting = ref(false)

const editId = computed(() => {
  const q = route.query.id
  return q ? Number(q) : 0
})

const parentId = computed(() => {
  const q = route.query.parentId
  return q ? Number(q) : undefined
})

const isEditMode = computed(() => editId.value > 0)

const DEFAULT_PROJECT_ID = 1

const formData = reactive<Partial<RequirementCreate & RequirementUpdate>>({
  projectId: DEFAULT_PROJECT_ID,
  title: '',
  description: '',
  type: '',
  priority: '',
  assigneeId: undefined,
  iterationId: undefined,
  dueDate: undefined,
  estimatedHours: undefined,
})

const formRules: FormRules = {
  title: [{ required: true, message: '请输入需求标题', trigger: 'blur' }],
  description: [{ required: true, message: '请输入需求描述', trigger: 'blur' }],
  type: [{ required: true, message: '请选择需求类型', trigger: 'change' }],
  priority: [{ required: true, message: '请选择优先级', trigger: 'change' }],
}

// Load existing data in edit mode
async function loadEditData() {
  if (!isEditMode.value) return
  try {
    const res = await requirementApi.getRequirementById(editId.value)
    const data = res
    formData.title = data.title
    formData.description = data.description
    formData.type = data.type
    formData.priority = data.priority
    formData.assigneeId = data.assigneeId || undefined
    formData.iterationId = data.iterationId || undefined
    formData.dueDate = data.dueDate || undefined
    formData.estimatedHours = data.estimatedHours || undefined
  } catch {
    ElMessage.error('加载需求数据失败')
  }
}

// Submit
async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (isEditMode.value) {
      const updateData: RequirementUpdate = {
        id: editId.value,
        title: formData.title,
        description: formData.description,
        type: formData.type,
        priority: formData.priority,
        assigneeId: formData.assigneeId,
        iterationId: formData.iterationId,
        dueDate: formData.dueDate,
        estimatedHours: formData.estimatedHours,
      }
      await requirementApi.updateRequirement(editId.value, updateData)
      ElMessage.success('更新成功')
    } else {
      const createData: RequirementCreate = {
        projectId: DEFAULT_PROJECT_ID,
        parentId: parentId.value,
        title: formData.title!,
        description: formData.description!,
        type: formData.type!,
        priority: formData.priority!,
        assigneeId: formData.assigneeId,
        iterationId: formData.iterationId,
        dueDate: formData.dueDate,
        estimatedHours: formData.estimatedHours,
      }
      await requirementApi.createRequirement(createData)
      ElMessage.success('创建成功')
    }
    if (parentId.value) {
      router.push({ name: 'RequirementDetail', params: { id: parentId.value } })
    } else {
      router.push({ name: 'Requirements' })
    }
  } catch {
    ElMessage.error(isEditMode.value ? '更新失败' : '创建失败')
  } finally {
    submitting.value = false
  }
}

function handleCancel() {
  router.push({ name: 'Requirements' })
}

onMounted(() => {
  loadEditData()
})
</script>

<style scoped>
.create-page {
  padding: 20px;
}

.page-header {
  margin-bottom: 20px;
}

.page-title {
  margin: 12px 0 0;
  font-size: 20px;
  font-weight: 600;
}

.form-card {
  background: #fff;
  padding: 24px;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}
</style>
