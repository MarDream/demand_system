<template>
  <div class="condition-branch-config">
    <el-form-item label="条件说明">
      <el-input
        :model-value="conditionDesc"
        type="textarea"
        :rows="2"
        placeholder="请描述分支条件用途，如：根据优先级分流到不同审批通道"
        :disabled="disabled"
        @update:model-value="emit('update:conditionDesc', $event)"
      />
    </el-form-item>

    <el-divider content-position="left">分支条件</el-divider>
    <p class="hint">为每条出边配置匹配条件，需求满足条件时走对应分支。无条件的边为默认分支。</p>

    <div v-for="(branch, index) in branches" :key="branch.edgeId" class="branch-item">
      <el-collapse v-model="expandedBranches" class="branch-collapse">
        <el-collapse-item :name="branch.edgeId">
          <template #title>
            <div class="branch-title-row">
              <span class="branch-arrow">→</span>
              <span class="branch-target">{{ branch.targetNodeName || branch.targetNodeId }}</span>
              <el-tag v-if="!hasCondition(branch)" type="info" size="small" class="default-tag">默认</el-tag>
              <el-tag v-else type="success" size="small">已配置</el-tag>
              <el-input
                v-model="branch.label"
                placeholder="边标签"
                size="small"
                style="width: 100px; margin-left: 8px"
                :disabled="disabled"
                @input="emitBranches"
              />
            </div>
          </template>
          <div v-if="hasCondition(branch)" class="branch-condition">
            <ConditionConfig
              :model-value="branch.condition"
              :disabled="disabled"
              @update:model-value="onConditionUpdate(index, $event)"
            />
            <div class="branch-actions">
              <el-button type="danger" text size="small" :disabled="disabled" @click="clearCondition(index)">
                清除条件（改为默认分支）
              </el-button>
            </div>
          </div>
          <div v-else class="branch-empty">
            <p>此分支无条件，需求将在此路径无匹配时走此分支。</p>
            <el-button type="primary" text size="small" :disabled="disabled" @click="addCondition(index)">
              添加条件
            </el-button>
          </div>
        </el-collapse-item>
      </el-collapse>
    </div>

    <div v-if="!branches?.length" class="no-branches">
      <p>暂无出边，请先在画布上从此条件节点连线到目标节点。</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import ConditionConfig from './ConditionConfig.vue'
import type { ConditionBranch, ConditionConfig as ConditionConfigType } from '@/types/workflow-visual'

const props = defineProps<{
  conditionDesc?: string
  branches?: ConditionBranch[]
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:conditionDesc': [value: string]
  'update:branches': [value: ConditionBranch[]]
}>()

// 折叠面板展开状态：默认全部展开
const expandedBranches = ref<string[]>([])

watch(
  () => props.branches,
  (val) => {
    if (val?.length) {
      expandedBranches.value = val.map(b => b.edgeId)
    }
  },
  { immediate: true }
)

function hasCondition(branch: ConditionBranch): boolean {
  const cond = branch.condition
  return cond != null && Array.isArray(cond.rules) && cond.rules.length > 0
}

function addCondition(index: number) {
  const branches = [...(props.branches || [])]
  branches[index] = {
    ...branches[index],
    condition: { logic: 'AND', rules: [{ field: 'type', operator: 'eq', value: '' }] },
  }
  emit('update:branches', branches)
}

function clearCondition(index: number) {
  const branches = [...(props.branches || [])]
  branches[index] = {
    ...branches[index],
    condition: { logic: 'AND', rules: [] },
  }
  emit('update:branches', branches)
}

function onConditionUpdate(index: number, value: ConditionConfigType) {
  const branches = [...(props.branches || [])]
  const normalizedCondition: ConditionConfigType = {
    logic: value.logic === 'OR' ? 'OR' : 'AND',
    rules: value.rules,
    expr: value.expr
  }
  branches[index] = { ...branches[index], condition: normalizedCondition }
  emit('update:branches', branches)
}

function emitBranches() {
  emit('update:branches', [...(props.branches || [])])
}
</script>

<style scoped>
.hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin: 0 0 12px;
  line-height: 1.5;
}
.branch-item {
  margin-bottom: 8px;
}
.branch-collapse :deep(.el-collapse-item__header) {
  padding: 0 12px;
  height: auto;
  min-height: 40px;
  line-height: 1.5;
}
.branch-title-row {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.branch-arrow {
  color: var(--el-color-primary);
  font-weight: bold;
}
.branch-target {
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.default-tag {
  margin-left: 4px;
}
.branch-condition {
  padding: 4px 0;
}
.branch-empty {
  padding: 8px 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.branch-empty p {
  margin: 0 0 8px;
}
.branch-actions {
  margin-top: 4px;
  display: flex;
  justify-content: flex-end;
}
.no-branches {
  padding: 12px 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  text-align: center;
}
</style>
