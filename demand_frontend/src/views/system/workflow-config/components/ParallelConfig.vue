<template>
  <div class="parallel-config">
    <el-form-item label="汇聚策略">
      <el-radio-group v-model="parallelType">
        <el-radio value="AND">全部完成 (AND)</el-radio>
        <el-radio value="OR">任一完成 (OR)</el-radio>
      </el-radio-group>
    </el-form-item>

    <el-divider content-position="left">分支配置（可选）</el-divider>
    <p class="hint">不配置时按连线自动识别分支；配置后可按需求属性过滤分支。</p>

    <div v-for="(branch, index) in branches" :key="branchKey(branch, index)" class="branch-item">
      <el-card shadow="never">
        <div class="branch-header">
          <span class="branch-title">分支 {{ index + 1 }}</span>
          <el-button type="danger" text :icon="Delete" @click="removeBranch(index)">删除</el-button>
        </div>
        <el-form-item label="分支ID">
          <el-input v-model="branch.branchId" placeholder="如 branch_tech" />
        </el-form-item>
        <el-form-item label="分支名称">
          <el-input v-model="branch.branchName" placeholder="如 技术评审" />
        </el-form-item>
        <el-form-item label="条件字段">
          <el-select
            v-model="branch.condition.field"
            clearable
            placeholder="不限"
            @change="onFieldChange(branch)"
          >
            <el-option label="需求类型" value="type" />
            <el-option label="优先级" value="priority" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="branch.condition.field" label="运算符">
          <el-select v-model="branch.condition.operator" style="width: 120px">
            <el-option label="等于" value="eq" />
            <el-option label="包含" value="in" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="branch.condition.field" label="值域">
          <el-select
            v-model="branch.condition.value"
            clearable
            filterable
            :loading="loadingDict"
            :placeholder="valuePlaceholder(branch.condition.field)"
          >
            <el-option
              v-for="opt in valueOptions(branch.condition.field)"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
          <span v-if="valueOptions(branch.condition.field).length === 0 && !loadingDict" class="value-hint">
            暂未配置{{ fieldLabel(branch.condition.field) }}字典
          </span>
        </el-form-item>
      </el-card>
    </div>

    <div class="branch-footer">
      <el-button type="primary" plain :icon="Plus" @click="addBranch">添加分支</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Delete, Plus } from '@element-plus/icons-vue'
import { requirementConfigApi, type RequirementType, type Priority } from '@/api/modules/requirementConfig'

interface BranchCondition {
  field?: string
  operator?: string
  value?: string
}

interface ParallelBranchConfig {
  branchId: string
  branchName: string
  condition: BranchCondition
}

const props = defineProps<{
  parallelType?: string
  branches?: ParallelBranchConfig[]
}>()

const emit = defineEmits<{
  'update:parallelType': [value: string]
  'update:branches': [value: ParallelBranchConfig[]]
}>()

const parallelType = computed({
  get: () => props.parallelType || 'AND',
  set: (val) => emit('update:parallelType', val),
})

const branches = computed({
  get: () => props.branches || [],
  set: (val) => emit('update:branches', val),
})

const typeOptions = ref<RequirementType[]>([])
const priorityOptions = ref<Priority[]>([])
const loadingDict = ref(false)

async function loadDictionaries() {
  loadingDict.value = true
  try {
    const [types, priorities] = await Promise.all([
      requirementConfigApi.listTypes().catch(() => [] as RequirementType[]),
      requirementConfigApi.listPriorities().catch(() => [] as Priority[]),
    ])
    typeOptions.value = (types as unknown as RequirementType[]) || []
    priorityOptions.value = (priorities as unknown as Priority[]) || []
  } finally {
    loadingDict.value = false
  }
}

onMounted(() => {
  loadDictionaries()
})

function fieldLabel(field?: string) {
  if (field === 'type') return '需求类型'
  if (field === 'priority') return '优先级'
  return '字段'
}

function valueOptions(field?: string) {
  if (field === 'type') {
    return typeOptions.value.map((t) => ({ label: t.name, value: t.code }))
  }
  if (field === 'priority') {
    return priorityOptions.value.map((p) => ({ label: p.name, value: p.code }))
  }
  return []
}

function valuePlaceholder(field?: string) {
  if (field === 'type') return '请选择需求类型'
  if (field === 'priority') return '请选择优先级'
  return '请选择值'
}

function onFieldChange(branch: ParallelBranchConfig) {
  // 切换字段时清空旧值，避免值域不匹配
  branch.condition.value = undefined
}

function branchKey(branch: ParallelBranchConfig, index: number) {
  return branch.branchId || `branch_${index}`
}

function addBranch() {
  emit('update:branches', [
    ...branches.value,
    { branchId: `branch_${Date.now()}`, branchName: '新分支', condition: {} },
  ])
}

function removeBranch(index: number) {
  emit('update:branches', branches.value.filter((_, i) => i !== index))
}
</script>

<style scoped>
.hint {
  color: var(--color-muted-text);
  font-size: 12px;
  margin: 0 0 12px;
}
.branch-item {
  margin-bottom: 12px;
}
.branch-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.branch-title {
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.branch-footer {
  display: flex;
  justify-content: flex-start;
  padding-top: 4px;
}
.value-hint {
  margin-left: 8px;
  color: var(--color-muted-text);
  font-size: 12px;
}
</style>
