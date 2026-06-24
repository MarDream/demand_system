<template>
  <div class="condition-branch-config">
    <!-- 条件说明 -->
    <div class="section-block">
      <label class="section-label">条件说明</label>
      <el-input
        :model-value="conditionDesc"
        type="textarea"
        :rows="2"
        placeholder="请描述分支条件用途，如：根据优先级分流到不同审批通道"
        :disabled="disabled"
        @update:model-value="emit('update:conditionDesc', $event)"
      />
    </div>

    <!-- 分支标题 -->
    <div class="section-header">
      <span class="section-title">分支条件</span>
      <span class="section-hint">为每条出边配置匹配条件，需求满足条件时走对应分支。无条件的边为默认分支。</span>
    </div>

    <!-- 分支卡片列表 -->
    <div v-for="(branch, index) in branches" :key="branch.edgeId" class="branch-card">
      <!-- 卡片头部 -->
      <div class="card-header">
        <div class="header-left">
          <span class="arrow">→</span>
          <span class="target-name">{{ branch.targetNodeName || branch.targetNodeId }}</span>
          <el-tag v-if="!hasCondition(branch)" type="info" size="small" effect="light" round>默认</el-tag>
          <el-tag v-else type="success" size="small" effect="light" round>已配置</el-tag>
        </div>
        <el-input
          v-model="branch.label"
          placeholder="边标签"
          size="small"
          class="edge-label-input"
          :disabled="disabled"
          @input="emitBranches"
        />
      </div>

      <!-- 卡片内容 -->
      <div v-if="hasCondition(branch)" class="card-body">
        <ConditionConfig
          :model-value="branch.condition"
          :disabled="disabled"
          @update:model-value="onConditionUpdate(index, $event)"
        />
        <div class="card-footer">
          <button
            class="btn-clear"
            :disabled="disabled"
            @click="clearCondition(index)"
          >
            清除条件（改为默认分支）
          </button>
        </div>
      </div>

      <div v-else class="card-body card-empty">
        <p class="empty-text">此分支无条件，需求将在此路径无匹配时走此分支。</p>
        <button class="btn-add-cond" :disabled="disabled" @click="addCondition(index)">
          <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
          添加条件
        </button>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-if="!branches?.length" class="empty-state">
      暂无出边，请先在画布上从此条件节点连线到目标节点。
    </div>
  </div>
</template>

<script setup lang="ts">
import { watch } from 'vue'
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
    expr: value.expr,
  }
  branches[index] = { ...branches[index], condition: normalizedCondition }
  emit('update:branches', branches)
}

function emitBranches() {
  emit('update:branches', [...(props.branches || [])])
}
</script>

<style scoped>
/* ===== 区块通用样式 ===== */
.section-block {
  margin-bottom: 20px;
}
.section-label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-primary, #303133);
  margin-bottom: 8px;
}

/* ===== 分支标题行 ===== */
.section-header {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 14px;
}
.section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
}
.section-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
}

/* ===== 分支卡片 ===== */
.branch-card {
  background: #fff;
  border: 1px solid var(--el-border-color-lighter, #e4e7ed);
  border-radius: var(--el-border-radius-round, 10px);
  overflow: hidden;
  margin-bottom: 14px;
  transition: border-color 0.2s;
}
.branch-card:hover {
  border-color: var(--el-border-color, #dcdfe6);
}

/* 卡片头部 */
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 11px 16px;
  background: var(--el-fill-color-extra-light, #fafbfc);
  border-bottom: 1px solid var(--el-border-color-extra-light, #f0f1f3);
}
.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}
.arrow {
  color: var(--el-color-primary, #409eff);
  font-weight: 700;
  font-size: 15px;
}
.target-name {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
}
.edge-label-input {
  width: 100px !important;
}

/* 卡片内容区 */
.card-body {
  padding: 16px;
}
.card-empty {
  padding: 18px 16px;
  text-align: center;
}
.empty-text {
  margin: 0 0 10px;
  font-size: 13px;
  color: var(--el-text-color-secondary, #909399);
}
.btn-add-cond {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: none;
  border-radius: 6px;
  background: var(--el-color-primary-light-9, #ecf5ff);
  color: var(--el-color-primary, #409eff);
  font-size: 12.5px;
  cursor: pointer;
  padding: 5px 14px;
  transition: all 0.15s;
}
.btn-add-cond:hover:not(:disabled) {
  background: var(--el-color-primary-light-8, #d9ecff);
}
.btn-add-cond:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

/* 底部操作栏 */
.card-footer {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--el-border-color-extra-light, #f0f1f3);
  display: flex;
  justify-content: flex-end;
}
.btn-clear {
  border: none;
  background: none;
  color: var(--el-text-color-placeholder, #c0c4cc);
  font-size: 12px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
  transition: all 0.15s;
}
.btn-clear:not(:disabled):hover {
  color: var(--el-text-color-regular, #606266);
  background: var(--el-fill-color-light, #f5f7fa);
}
.btn-clear:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

/* 空状态 */
.empty-state {
  padding: 28px 16px;
  text-align: center;
  font-size: 13px;
  color: var(--el-text-color-secondary, #909399);
  background: var(--el-fill-color-lighter, #f7f8fa);
  border-radius: 8px;
}
</style>
