<template>
  <div class="condition-config">
    <el-form-item label="匹配逻辑">
      <el-radio-group v-model="logic" :disabled="disabled">
        <el-radio value="AND">全部满足</el-radio>
        <el-radio value="OR">任一满足</el-radio>
      </el-radio-group>
    </el-form-item>
    <div v-for="(rule, index) in rules" :key="index" class="condition-rule">
      <el-select
        v-model="rule.field"
        placeholder="字段"
        style="width: 120px"
        :disabled="disabled"
        @change="sync"
      >
        <el-option label="需求类型" value="type" />
        <el-option label="优先级" value="priority" />
        <el-option label="项目" value="projectId" />
        <el-option label="状态" value="status" />
      </el-select>
      <el-select
        v-model="rule.operator"
        placeholder="运算符"
        style="width: 110px"
        :disabled="disabled"
        @change="onOperatorChange(rule)"
      >
        <el-option label="等于" value="eq" />
        <el-option label="不等于" value="ne" />
        <el-option label="包含于" value="in" />
        <el-option label="不包含于" value="notIn" />
        <el-option label="为空" value="isEmpty" />
        <el-option label="不为空" value="notEmpty" />
        <el-option label="匹配正则" value="matches" />
      </el-select>

      <!-- in / notIn: 多值标签输入 -->
      <template v-if="rule.operator === 'in' || rule.operator === 'notIn'">
        <div class="tag-input-wrapper">
          <el-tag
            v-for="(tag, ti) in asArray(rule.value)"
            :key="ti"
            closable
            :disable-transitions="false"
            size="small"
            @close="removeTag(rule, ti)"
          >
            {{ tag }}
          </el-tag>
          <el-input
            v-model="tagInputMap[index]"
            size="small"
            style="width: 100px"
            placeholder="回车添加"
            :disabled="disabled"
            @keyup.enter="addTag(rule, index)"
          />
        </div>
      </template>

      <!-- isEmpty / notEmpty: 无需值输入 -->
      <template v-else-if="rule.operator === 'isEmpty' || rule.operator === 'notEmpty'">
        <!-- 无值输入 -->
      </template>

      <!-- matches: 正则输入 + 校验提示 -->
      <template v-else-if="rule.operator === 'matches'">
        <div class="regex-input-wrapper">
          <el-input
            v-model="rule.value"
            placeholder="如 ^P[01]$"
            style="flex: 1"
            :disabled="disabled"
            :class="{ 'is-error': regexError(index) }"
            @input="sync"
          />
          <div v-if="regexError(index)" class="regex-error">{{ regexError(index) }}</div>
          <div v-else-if="rule.value" class="regex-hint">正则，最大128字符</div>
        </div>
      </template>

      <!-- eq / ne: 普通单值输入 -->
      <template v-else>
        <el-input
          v-model="rule.value"
          placeholder="值"
          style="flex: 1"
          :disabled="disabled"
          @input="sync"
        />
      </template>

      <el-button type="danger" text :disabled="disabled" @click="removeRule(index)">删除</el-button>
    </div>
    <el-button :disabled="disabled" @click="addRule">添加条件</el-button>

    <!-- 表达式预览 -->
    <div v-if="exprPreview" class="expr-preview">
      <span class="expr-label">预览:</span>
      <code>{{ exprPreview }}</code>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive } from 'vue'

export interface ConditionRule {
  field: string
  operator: 'eq' | 'ne' | 'in' | 'notIn' | 'isEmpty' | 'notEmpty' | 'matches'
  value: string | string[]
}

const props = defineProps<{
  modelValue?: {
    logic?: string
    rules?: ConditionRule[]
  } | null
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: { logic: 'AND' | 'OR'; rules: ConditionRule[]; expr: string }]
}>()

const tagInputMap = reactive<Record<number, string>>({})

const logic = computed({
  get: () => props.modelValue?.logic || 'AND',
  set: (val) => sync({ logic: val, rules: rules.value }),
})

const rules = computed<ConditionRule[]>({
  get: () => props.modelValue?.rules?.length
    ? [...props.modelValue.rules] as ConditionRule[]
    : [{ field: 'type', operator: 'eq' as const, value: '' }],
  set: (val) => sync({ logic: logic.value, rules: val }),
})

function asArray(val: string | string[]): string[] {
  if (Array.isArray(val)) return val
  if (val === '' || val == null) return []
  return [val]
}

function addTag(rule: ConditionRule, index: number) {
  const input = (tagInputMap[index] || '').trim()
  if (!input) return
  const current = asArray(rule.value)
  rule.value = [...current, input]
  tagInputMap[index] = ''
  sync({ logic: logic.value, rules: rules.value })
}

function removeTag(rule: ConditionRule, tagIndex: number) {
  const current = asArray(rule.value)
  rule.value = current.filter((_, i) => i !== tagIndex)
  sync({ logic: logic.value, rules: rules.value })
}

function onOperatorChange(rule: ConditionRule) {
  // 切换运算符时重置 value 到合适类型
  switch (rule.operator) {
    case 'in':
    case 'notIn':
      rule.value = Array.isArray(rule.value) ? rule.value : []
      break
    case 'isEmpty':
    case 'notEmpty':
      rule.value = ''
      break
    default:
      rule.value = Array.isArray(rule.value) ? rule.value.join(',') : rule.value
      break
  }
  sync({ logic: logic.value, rules: rules.value })
}

function addRule() {
  const newRules = [...rules.value, { field: 'type', operator: 'eq' as const, value: '' }]
  sync({ logic: logic.value, rules: newRules })
}

function removeRule(index: number) {
  const next = rules.value.filter((_, i) => i !== index)
  sync({
    logic: logic.value,
    rules: next.length ? next : [{ field: 'type', operator: 'eq' as const, value: '' }],
  })
}

function sync(payload: { logic: string; rules: ConditionRule[] }) {
  const expr = buildExpr(payload)
  const normalizedLogic: 'AND' | 'OR' = payload.logic === 'OR' ? 'OR' : 'AND'
  emit('update:modelValue', { logic: normalizedLogic, rules: payload.rules, expr })
}

/** 正则校验：返回错误信息或空字符串 */
function regexError(index: number): string {
  const rule = rules.value[index]
  if (!rule || rule.operator !== 'matches' || !rule.value) return ''
  const val = String(rule.value)
  if (val.length > 128) return `正则长度 ${val.length} 超限（最大128）`
  try {
    new RegExp(val)
    return ''
  } catch {
    return '正则语法错误'
  }
}

/** 生成可读预览表达式（只读展示，不作为后端解析依据） */
function buildExpr(payload: { logic: string; rules: ConditionRule[] }): string {
  const parts = payload.rules
    .filter((r) => {
      if (r.operator === 'isEmpty' || r.operator === 'notEmpty') return r.field
      if (r.operator === 'in' || r.operator === 'notIn') return r.field && asArray(r.value).length > 0
      return r.field && r.value
    })
    .map(formatRule)
  if (parts.length === 0) return ''
  return parts.length === 1 ? parts[0] : `(${parts.join(payload.logic === 'OR' ? ' || ' : ' && ')})`
}

function formatRule(r: ConditionRule): string {
  const v = r.value
  switch (r.operator) {
    case 'eq': return `${r.field} == '${v}'`
    case 'ne': return `${r.field} != '${v}'`
    case 'in': return `${r.field} IN [${asArray(v).map(x => `'${x}'`).join(', ')}]`
    case 'notIn': return `${r.field} NOT IN [${asArray(v).map(x => `'${x}'`).join(', ')}]`
    case 'isEmpty': return `${r.field} IS EMPTY`
    case 'notEmpty': return `${r.field} IS NOT EMPTY`
    case 'matches': return `${r.field} MATCHES '${v}'`
    default: return `${r.field} == '${v}'`
  }
}

const exprPreview = computed(() => buildExpr({ logic: logic.value, rules: rules.value }))
</script>

<style scoped>
.condition-rule {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
  align-items: flex-start;
  flex-wrap: wrap;
}
.tag-input-wrapper {
  display: flex;
  gap: 4px;
  align-items: center;
  flex: 1;
  flex-wrap: wrap;
  min-height: 32px;
}
.regex-input-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.regex-error {
  color: var(--el-color-danger);
  font-size: 12px;
  line-height: 1.2;
}
.regex-hint {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
  line-height: 1.2;
}
.expr-preview {
  margin-top: 8px;
  padding: 6px 10px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.expr-label {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.expr-preview code {
  color: var(--el-color-primary);
  font-family: 'Cascadia Code', 'Fira Code', monospace;
}
</style>
