<template>
  <div class="condition-config">
    <!-- 匹配逻辑：紧凑内联 -->
    <div class="logic-row">
      <span class="logic-label">匹配逻辑</span>
      <el-radio-group v-model="logic" :disabled="disabled" size="small">
        <el-radio-button value="AND">全部满足</el-radio-button>
        <el-radio-button value="OR">任一满足</el-radio-button>
      </el-radio-group>
    </div>

    <!-- 条件规则行 -->
    <div v-for="(rule, index) in rules" :key="index" class="rule-card">
      <span class="rule-index">{{ index + 1 }}</span>

      <el-select
        v-model="rule.field"
        placeholder="字段"
        class="field-select"
        :disabled="disabled"
        @change="onFieldChangeAndSync(rule)"
      >
        <el-option label="需求类型" value="type" />
        <el-option label="优先级" value="priority" />
        <el-option label="项目" value="projectId" />
        <el-option label="状态" value="status" />
      </el-select>

      <el-select
        v-model="rule.operator"
        placeholder="运算符"
        class="operator-select"
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

      <!-- in / notIn + 字典字段 → 多选下拉 -->
      <template v-if="(rule.operator === 'in' || rule.operator === 'notIn') && isDictField(rule.field)">
        <el-select
          :model-value="asArray(rule.value)"
          multiple
          filterable
          allow-create
          default-first-option
          class="value-select"
          :placeholder="'选择' + fieldLabel(rule.field)"
          :disabled="disabled"
          @change="(val: string[] | number[]) => onMultiSelectChange(rule, val as string[])"
        >
          <el-option
            v-for="opt in fieldValueOptions(rule.field)"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </template>

      <!-- in / notIn + 非字典字段 → 多值标签输入 -->
      <template v-else-if="rule.operator === 'in' || rule.operator === 'notIn'">
        <div class="tag-input-wrapper">
          <el-tag
            v-for="(tag, ti) in asArray(rule.value)"
            :key="ti"
            closable
            :disable-transitions="false"
            size="small"
            effect="light"
            type="primary"
            @close="removeTag(rule, ti)"
          >
            {{ tag }}
          </el-tag>
          <el-input
            v-model="tagInputMap[index]"
            size="small"
            class="tag-input"
            placeholder="+ 添加"
            :disabled="disabled"
            @keyup.enter="addTag(rule, index)"
          />
        </div>
      </template>

      <!-- isEmpty / notEmpty: 无需值输入 -->
      <template v-else-if="rule.operator === 'isEmpty' || rule.operator === 'notEmpty'">
        <span class="no-value-hint">{{ rule.operator === 'isEmpty' ? '为空判断，无需填值' : '非空判断，无需填值' }}</span>
      </template>

      <!-- matches: 正则输入 -->
      <template v-else-if="rule.operator === 'matches'">
        <el-input
          v-model="rule.value"
          placeholder="如 ^P[01]$"
          class="value-input regex-input"
          :disabled="disabled"
          :class="{ 'is-error': regexError(index) }"
          @input="sync"
        />
        <span v-if="regexError(index)" class="inline-error">{{ regexError(index) }}</span>
        <span v-else-if="rule.value" class="inline-hint">正则</span>
      </template>

      <!-- eq / ne + 字典字段 → 下拉选择 -->
      <template v-else-if="isDictField(rule.field)">
        <el-select
          v-model="rule.value"
          filterable
          clearable
          class="value-select"
          :placeholder="'选择' + fieldLabel(rule.field)"
          :disabled="disabled"
          :loading="loadingDict"
          @change="sync"
        >
          <el-option
            v-for="opt in fieldValueOptions(rule.field)"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </template>

      <!-- eq / ne + 非字典字段 → 兜底文本 -->
      <template v-else>
        <el-input
          v-model="rule.value"
          placeholder="值"
          class="value-input"
          :disabled="disabled"
          @input="sync"
        />
      </template>

      <el-tooltip content="删除此条件" placement="top" :show-after="400">
        <button class="btn-remove" :disabled="disabled" @click="removeRule(index)">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
        </button>
      </el-tooltip>
    </div>

    <!-- 添加条件按钮：虚线样式 -->
    <button class="btn-add-rule" :disabled="disabled" @click="addRule">
      <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
      添加条件
    </button>

    <!-- 表达式预览 -->
    <div v-if="exprPreview" class="expr-preview">
      <code>{{ exprPreview }}</code>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { requirementConfigApi, type RequirementType, type Priority } from '@/api/modules/requirementConfig'
import { getProjectList } from '@/api/modules/project'
import type { Project } from '@/types/project'

/** 需求状态选项 */
const STATUS_OPTIONS = [
  { label: '新建', value: '新建' },
  { label: '待分析', value: '待分析' },
  { label: '待确认', value: '待确认' },
  { label: '待评审', value: '待评审' },
  { label: '评审中', value: '评审中' },
  { label: '已通过', value: '已通过' },
  { label: '开发中', value: '开发中' },
  { label: '测试中', value: '测试中' },
  { label: '已上线', value: '已上线' },
  { label: '已验收', value: '已验收' },
  { label: '已取消', value: '已取消' },
  { label: '已拒绝', value: '已拒绝' },
  { label: '打回', value: '打回' },
]

export interface DictOption {
  label: string
  value: string
}

const DICT_FIELDS = ['type', 'priority', 'projectId', 'status'] as const

function isDictField(field: string): boolean {
  return DICT_FIELDS.includes(field as typeof DICT_FIELDS[number])
}

function fieldLabel(field?: string): string {
  switch (field) {
    case 'type': return '需求类型'
    case 'priority': return '优先级'
    case 'projectId': return '项目'
    case 'status': return '状态'
    default: return '值'
  }
}

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

// ========== 字典数据 ==========
const typeOptions = ref<RequirementType[]>([])
const priorityOptions = ref<Priority[]>([])
const projectOptions = ref<DictOption[]>([])
const loadingDict = ref(false)

function fieldValueOptions(field?: string): DictOption[] {
  switch (field) {
    case 'type':
      return typeOptions.value.map((t) => ({ label: t.name, value: t.code }))
    case 'priority':
      return priorityOptions.value.map((p) => ({ label: p.name, value: p.code }))
    case 'projectId':
      return projectOptions.value
    case 'status':
      return STATUS_OPTIONS
    default:
      return []
  }
}

async function loadDictionaries() {
  loadingDict.value = true
  try {
    let types: RequirementType[] = []
    let priorities: Priority[] = []
    let projectList: Project[] | null = null
    try { types = (await requirementConfigApi.listTypes()) as unknown as RequirementType[] } catch { /* empty */ }
    try { priorities = (await requirementConfigApi.listPriorities()) as unknown as Priority[] } catch { /* empty */ }
    try {
      const res = await getProjectList({ pageNum: 1, pageSize: 999 })
      projectList = (res as unknown as { list: Project[] }).list || null
    } catch { /* empty */ }

    typeOptions.value = types || []
    priorityOptions.value = priorities || []
    if (projectList) {
      projectOptions.value = projectList.map((p) => ({ label: p.name, value: String(p.id) }))
    }
  } finally {
    loadingDict.value = false
  }
}

onMounted(() => {
  loadDictionaries()
})

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

function onFieldChangeAndSync(rule: ConditionRule) {
  onFieldChange(rule)
  sync({ logic: logic.value, rules: rules.value })
}

function onFieldChange(rule: ConditionRule) {
  if (rule.operator === 'in' || rule.operator === 'notIn') {
    rule.value = []
  } else if (rule.operator !== 'isEmpty' && rule.operator !== 'notEmpty') {
    rule.value = ''
  }
}

function onMultiSelectChange(rule: ConditionRule, val: string[]) {
  rule.value = val
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

function regexError(index: number): string {
  const rule = rules.value[index]
  if (!rule || rule.operator !== 'matches' || !rule.value) return ''
  const val = String(rule.value)
  if (val.length > 128) return `长度 ${val.length} 超限（最大128）`
  try {
    new RegExp(val)
    return ''
  } catch {
    return '语法错误'
  }
}

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
/* ===== 匹配逻辑行 ===== */
.logic-row {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}
.logic-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  font-weight: 500;
  white-space: nowrap;
}

/* ===== 条件规则卡片 ===== */
.rule-card {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  padding: 10px 12px;
  background: var(--el-fill-color-lighter, #f7f8fa);
  border: 1px solid var(--el-border-color-extra-light, #ebeef5);
  border-radius: var(--el-border-radius-base, 8px);
  transition: border-color 0.2s;
}
.rule-card:hover {
  border-color: var(--el-border-color, #dcdfe6);
}

/* 序号圆点 */
.rule-index {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #eaf2ff;
  color: #409eff;
  font-size: 11px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  line-height: 1;
}

/* 字段/运算符下拉 */
.field-select {
  flex: 0 0 100px;
}
.operator-select {
  flex: 0 0 90px;
}

/* 值域控件 */
.value-select {
  flex: 1;
  min-width: 120px;
}
.value-input {
  flex: 1;
  min-width: 100px;
}
.regex-input {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

/* 多值标签区域 */
.tag-input-wrapper {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  min-width: 100px;
}
.tag-input {
  width: 64px !important;
}
:deep(.tag-input .el-input__wrapper) {
  box-shadow: none !important;
  background: transparent !important;
  padding-left: 4px !important;
}

/* 无值提示 */
.no-value-hint {
  flex: 1;
  font-size: 12px;
  color: var(--el-text-color-placeholder, #a8abb2);
}

/* 内联错误/提示 */
.inline-error {
  font-size: 11.5px;
  color: var(--el-color-danger, #f56c6c);
  white-space: nowrap;
}
.inline-hint {
  font-size: 11.5px;
  color: var(--el-text-color-placeholder, #a8abb2);
  white-space: nowrap;
}

/* 删除图标按钮 */
.btn-remove {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border: none;
  border-radius: 6px;
  background: none;
  cursor: pointer;
  color: var(--el-text-color-placeholder, #c0c4cc);
  transition: all 0.15s;
  flex-shrink: 0;
}
.btn-remove:not(:disabled):hover {
  color: var(--el-color-danger, #f56c6c);
  background: var(--el-fill-color-light, #f5f7fa);
}
.btn-remove:disabled {
  cursor: not-allowed;
  opacity: 0.4;
}

/* ===== 添加条件按钮（虚线）===== */
.btn-add-rule {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: 1px dashed var(--el-border-color, #dcdfe6);
  border-radius: 6px;
  background: none;
  color: var(--el-color-primary, #409eff);
  font-size: 12.5px;
  padding: 5px 14px;
  cursor: pointer;
  margin-top: 6px;
  transition: all 0.15s;
}
.btn-add-rule:not(:disabled):hover {
  border-color: var(--el-color-primary-light-3, #79bbff);
  background: var(--el-fill-color-light, #f5f7fa);
}
.btn-add-rule:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

/* ===== 表达式预览 ===== */
.expr-preview {
  margin-top: 14px;
  padding: 8px 14px;
  background: var(--el-fill-color, #fafafa);
  border-left: 3px solid var(--el-color-primary, #409eff);
  border-radius: 0 6px 6px 0;
}
.expr-preview code {
  font-size: 12px;
  color: var(--el-color-primary, #409eff);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  word-break: break-all;
  line-height: 1.5;
}
</style>
