<template>
  <el-form
    ref="formRef"
    :model="internalValue"
    :rules="formRules"
    label-width="120px"
    class="requirement-form"
  >
    <el-form-item label="需求标题" prop="title">
      <el-input v-model="internalValue.title" placeholder="请输入需求标题" maxlength="200" />
    </el-form-item>

    <el-form-item label="需求描述" prop="description">
      <el-input
        v-model="internalValue.description"
        type="textarea"
        :rows="4"
        placeholder="请输入需求描述"
        maxlength="2000"
        show-word-limit
      />
    </el-form-item>

    <el-form-item label="需求类型" prop="type">
      <el-select v-model="internalValue.type" placeholder="请选择需求类型" style="width: 100%">
        <el-option label="功能" value="功能" />
        <el-option label="优化" value="优化" />
        <el-option label="Bug" value="Bug" />
        <el-option label="技术债务" value="技术债务" />
        <el-option label="运营" value="运营" />
      </el-select>
    </el-form-item>

    <el-form-item label="优先级" prop="priority">
      <el-select v-model="internalValue.priority" placeholder="请选择优先级" style="width: 100%">
        <el-option label="P0" value="P0" />
        <el-option label="P1" value="P1" />
        <el-option label="P2" value="P2" />
        <el-option label="P3" value="P3" />
      </el-select>
    </el-form-item>

    <el-form-item label="负责人">
      <el-select
        v-model="internalValue.assigneeId"
        placeholder="请选择负责人"
        clearable
        style="width: 100%"
      >
        <el-option v-for="user in userList" :key="user.id" :label="user.realName || user.username" :value="user.id" />
      </el-select>
    </el-form-item>

    <el-form-item label="所属迭代">
      <el-select
        v-model="internalValue.iterationId"
        placeholder="请选择迭代"
        clearable
        style="width: 100%"
      >
        <el-option v-for="iter in iterationList" :key="iter.id" :label="iter.name" :value="iter.id" />
      </el-select>
    </el-form-item>

    <el-form-item label="截止日期">
      <el-date-picker
        v-model="internalValue.dueDate"
        type="date"
        placeholder="请选择截止日期"
        value-format="YYYY-MM-DD"
        style="width: 100%"
      />
    </el-form-item>

    <el-form-item label="估算工时">
      <el-input-number
        v-model="internalValue.estimatedHours"
        :min="0"
        :precision="1"
        :step="0.5"
        placeholder="请输入估算工时"
        style="width: 100%"
      />
    </el-form-item>

    <el-form-item>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">
        {{ isEdit ? '保存' : '创建' }}
      </el-button>
      <el-button @click="handleCancel">取消</el-button>
    </el-form-item>
  </el-form>
</template>

<script setup lang="ts">
import { reactive, computed, watch, ref, onMounted } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import type { RequirementCreate } from '@/types/requirement'
import type { User } from '@/types/user'
import type { Iteration } from '@/types/iteration'
import { userApi, iterationApi } from '@/api'

const props = defineProps<{
  modelValue: Partial<RequirementCreate>
  isEdit?: boolean
  projectId?: number
}>()

const emit = defineEmits<{
  'update:modelValue': [value: Partial<RequirementCreate>]
  submit: [value: Partial<RequirementCreate>]
  cancel: []
}>()

const formRef = ref<FormInstance>()
const submitting = ref(false)

// 动态加载：用户列表
const userList = ref<User[]>([])

// 动态加载：迭代列表
const iterationList = ref<Iteration[]>([])

async function loadUsers() {
  try {
    const res = await userApi.getUserList({ pageNum: 1, pageSize: 100 }) as any
    userList.value = res.list
  } catch (error) {
    console.error('加载用户列表失败', error)
  }
}

async function loadIterations() {
  if (!props.projectId) return
  try {
    const res = await iterationApi.getIterationList(props.projectId) as any
    iterationList.value = res
  } catch (error) {
    console.error('加载迭代列表失败', error)
  }
}

const internalValue = reactive<Partial<RequirementCreate>>({
  title: props.modelValue.title || '',
  description: props.modelValue.description || '',
  type: props.modelValue.type || '',
  priority: props.modelValue.priority || '',
  assigneeId: props.modelValue.assigneeId,
  iterationId: props.modelValue.iterationId,
  dueDate: props.modelValue.dueDate,
  estimatedHours: props.modelValue.estimatedHours,
  projectId: props.modelValue.projectId || 1,
})

watch(
  () => props.modelValue,
  (val) => {
    internalValue.title = val.title || ''
    internalValue.description = val.description || ''
    internalValue.type = val.type || ''
    internalValue.priority = val.priority || ''
    internalValue.assigneeId = val.assigneeId
    internalValue.iterationId = val.iterationId
    internalValue.dueDate = val.dueDate
    internalValue.estimatedHours = val.estimatedHours
    internalValue.projectId = val.projectId || 1
  },
  { deep: true },
)

// 监听 projectId 变化，加载对应的迭代列表
watch(
  () => props.projectId,
  () => {
    loadIterations()
  },
  { immediate: true },
)

onMounted(() => {
  loadUsers()
  loadIterations()
})

const isEdit = computed(() => props.isEdit)

const formRules: FormRules = {
  title: [{ required: true, message: '请输入需求标题', trigger: 'blur' }],
  description: [{ required: true, message: '请输入需求描述', trigger: 'blur' }],
  type: [{ required: true, message: '请选择需求类型', trigger: 'change' }],
  priority: [{ required: true, message: '请选择优先级', trigger: 'change' }],
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    emit('submit', { ...internalValue })
  } finally {
    submitting.value = false
  }
}

function handleCancel() {
  emit('cancel')
}
</script>

<style scoped>
.requirement-form {
  max-width: 700px;
}
</style>
