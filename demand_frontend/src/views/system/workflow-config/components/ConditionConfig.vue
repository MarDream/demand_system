<template>
  <div class="condition-config">
    <el-form-item label="条件逻辑">
      <el-radio-group v-model="logic">
        <el-radio value="AND">全部满足</el-radio>
        <el-radio value="OR">任一满足</el-radio>
      </el-radio-group>
    </el-form-item>
    <div v-for="(rule, index) in rules" :key="index" class="condition-rule">
      <el-select v-model="rule.field" placeholder="字段" style="width: 120px">
        <el-option label="需求类型" value="type" />
        <el-option label="优先级" value="priority" />
        <el-option label="项目" value="projectId" />
        <el-option label="状态" value="status" />
      </el-select>
      <el-select v-model="rule.operator" placeholder="运算符" style="width: 100px">
        <el-option label="等于" value="eq" />
        <el-option label="不等于" value="ne" />
        <el-option label="包含" value="in" />
        <el-option label="不包含" value="notIn" />
      </el-select>
      <el-input v-model="rule.value" placeholder="值" style="flex: 1" />
      <el-button type="danger" text @click="removeRule(index)">删除</el-button>
    </div>
    <el-button @click="addRule">添加条件</el-button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

export interface ConditionRule {
  field: string
  operator: string
  value: string
}

const props = defineProps<{
  modelValue?: {
    logic?: string
    rules?: ConditionRule[]
  } | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: { logic: string; rules: ConditionRule[] }]
  'update:expr': [expr: string]
}>()

const logic = computed({
  get: () => props.modelValue?.logic || 'AND',
  set: (val) => sync({ logic: val, rules: rules.value }),
})

const rules = computed({
  get: () => props.modelValue?.rules?.length ? [...props.modelValue.rules] : [{ field: 'type', operator: 'eq', value: '' }],
  set: (val) => sync({ logic: logic.value, rules: val }),
})

function sync(payload: { logic: string; rules: ConditionRule[] }) {
  emit('update:modelValue', payload)
  emit('update:expr', buildExpr(payload))
}

function addRule() {
  sync({ logic: logic.value, rules: [...rules.value, { field: 'type', operator: 'eq', value: '' }] })
}

function removeRule(index: number) {
  const next = rules.value.filter((_, i) => i !== index)
  sync({ logic: logic.value, rules: next.length ? next : [{ field: 'type', operator: 'eq', value: '' }] })
}

function buildExpr(payload: { logic: string; rules: ConditionRule[] }) {
  const parts = payload.rules
    .filter((r) => r.field && r.value)
    .map((r) => `${r.field} ${r.operator === 'ne' ? '!=' : '=='} '${r.value}'`)
  if (parts.length === 0) return ''
  return parts.length === 1 ? parts[0] : `(${parts.join(payload.logic === 'OR' ? ' || ' : ' && ')})`
}
</script>

<style scoped>
.condition-rule {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
  align-items: center;
}
</style>
