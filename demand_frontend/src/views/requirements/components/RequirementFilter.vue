<template>
  <el-form :model="internalValue" inline class="requirement-filter">
    <el-form-item label="需求类型">
      <el-select
        v-model="internalValue.type"
        placeholder="全部"
        clearable
        style="width: 140px"
        @change="emitChange"
      >
        <el-option
          v-for="type in typeOptions"
          :key="type.code"
          :label="type.name"
          :value="type.code"
        />
      </el-select>
    </el-form-item>

    <el-form-item label="优先级">
      <el-select
        v-model="internalValue.priority"
        placeholder="全部"
        clearable
        style="width: 100px"
        @change="emitChange"
      >
        <el-option
          v-for="priority in priorityOptions"
          :key="priority.code"
          :label="priority.name"
          :value="priority.code"
        />
      </el-select>
    </el-form-item>

    <el-form-item label="状态">
      <el-select
        v-model="internalValue.status"
        placeholder="全部"
        clearable
        style="width: 120px"
        @change="emitChange"
      >
        <el-option label="新建" value="新建" />
        <el-option label="待评审" value="待评审" />
        <el-option label="评审中" value="评审中" />
        <el-option label="已通过" value="已通过" />
        <el-option label="开发中" value="开发中" />
        <el-option label="测试中" value="测试中" />
        <el-option label="已上线" value="已上线" />
        <el-option label="已验收" value="已验收" />
      </el-select>
    </el-form-item>

    <el-form-item label="关键词">
      <el-input
        v-model="internalValue.keyword"
        placeholder="请输入关键词"
        clearable
        style="width: 200px"
        @keyup.enter="handleSearch"
      />
    </el-form-item>

    <el-form-item>
      <el-button type="primary" @click="handleSearch">搜索</el-button>
      <el-button @click="handleReset">重置</el-button>
    </el-form-item>
  </el-form>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import {
  requirementConfigApi,
  type Priority,
  type RequirementType,
} from '@/api/modules/requirementConfig'
import { normalizeText, stripPriorityPrefix } from '@/utils/format'

interface FilterValue {
  type?: string
  priority?: string
  status?: string
  keyword?: string
}

const props = defineProps<{
  modelValue?: FilterValue
}>()

const emit = defineEmits<{
  'update:modelValue': [value: FilterValue]
  search: []
  reset: []
}>()

const typeOptions = ref<RequirementType[]>([])
const priorityOptions = ref<Priority[]>([])

const internalValue = reactive<FilterValue>({
  type: props.modelValue?.type || '',
  priority: props.modelValue?.priority || '',
  status: props.modelValue?.status || '',
  keyword: props.modelValue?.keyword || '',
})

watch(
  () => props.modelValue,
  (val) => {
    if (val) {
      internalValue.type = val.type || ''
      internalValue.priority = val.priority || ''
      internalValue.status = val.status || ''
      internalValue.keyword = val.keyword || ''
    }
  },
  { deep: true },
)

function emitChange() {
  emit('update:modelValue', { ...internalValue })
}

function handleSearch() {
  emitChange()
  emit('search')
}

function handleReset() {
  internalValue.type = ''
  internalValue.priority = ''
  internalValue.status = ''
  internalValue.keyword = ''
  emit('update:modelValue', { ...internalValue })
  emit('reset')
}

async function loadConfigOptions() {
  try {
    const [typesRes, prioritiesRes] = await Promise.all([
      requirementConfigApi.listTypes(),
      requirementConfigApi.listPriorities(),
    ])
    const typeList = Array.isArray(typesRes) ? typesRes : (typesRes as any)?.data || []
    const priorityList = Array.isArray(prioritiesRes) ? prioritiesRes : (prioritiesRes as any)?.data || []
    typeOptions.value = typeList.map((type: RequirementType) => ({
      ...type,
      name: normalizeText(type.name),
    }))
    priorityOptions.value = priorityList.map((priority: Priority) => ({
      ...priority,
      name: stripPriorityPrefix(normalizeText(priority.name)),
    }))
  } catch (error) {
  }
}

onMounted(() => {
  loadConfigOptions()
})
</script>

<style scoped>
.requirement-filter {
  margin: 0;
}
</style>
