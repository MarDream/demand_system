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
    <div v-for="(branch, index) in branches" :key="index" class="branch-item">
      <el-card shadow="never">
        <div class="branch-header">
          <span>分支 {{ index + 1 }}</span>
          <el-button type="danger" text @click="removeBranch(index)">删除</el-button>
        </div>
        <el-form-item label="分支ID">
          <el-input v-model="branch.branchId" placeholder="如 branch_tech" />
        </el-form-item>
        <el-form-item label="分支名称">
          <el-input v-model="branch.branchName" placeholder="如 技术评审" />
        </el-form-item>
        <el-form-item label="条件字段">
          <el-select v-model="branch.condition.field" clearable placeholder="不限">
            <el-option label="需求类型" value="type" />
            <el-option label="优先级" value="priority" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="branch.condition.field" label="运算符">
          <el-select v-model="branch.condition.operator" style="width: 120px">
            <el-option label="等于" value="eq" />
            <el-option label="包含" value="in" />
          </el-select>
          <el-input v-model="branch.condition.value" placeholder="值" style="margin-top: 8px" />
        </el-form-item>
      </el-card>
    </div>
    <el-button @click="addBranch">添加分支</el-button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

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
  margin-bottom: 8px;
}
</style>
