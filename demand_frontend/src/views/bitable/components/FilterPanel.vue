<template>
  <el-dialog
    :model-value="modelValue"
    title="筛选"
    width="640px"
    append-to-body
    @update:model-value="emit('update:modelValue', $event)"
    @closed="handleClosed"
  >
    <div class="filter-panel">
      <!-- AI 智能筛选：自然语言生成条件 -->
      <div class="filter-panel__ai">
        <el-input
          v-model="aiText"
          size="small"
          :disabled="aiGenerating"
          maxlength="200"
          placeholder="用一句自然语言描述筛选需求，如「状态为进行中且优先级是高」"
          @keyup.enter="applyAiFilter"
        >
          <template #prefix><i class="ri-sparkling-2-line" /></template>
        </el-input>
        <el-button
          type="primary"
          size="small"
          :loading="aiGenerating"
          @click="applyAiFilter"
        >
          AI 生成
        </el-button>
      </div>

      <!-- 顶层逻辑切换 -->
      <div class="filter-panel__logic">
        <span class="filter-panel__logic-label">条件关系</span>
        <el-radio-group v-model="rootLogic" size="small">
          <el-radio-button value="and">满足所有条件 (AND)</el-radio-button>
          <el-radio-button value="or">满足任一条件 (OR)</el-radio-button>
        </el-radio-group>
      </div>

      <!-- 条件列表 -->
      <div class="filter-panel__rules">
        <div
          v-for="(rule, index) in rules"
          :key="index"
          class="filter-panel__rule"
        >
          <el-select
            v-model="rule.fieldId"
            placeholder="选择字段"
            size="small"
            style="width: 160px"
            filterable
            @change="onFieldChange(rule)"
          >
            <el-option
              v-for="field in filterableFields"
              :key="field.id"
              :label="field.name"
              :value="field.id"
            />
          </el-select>

          <el-select
            v-model="rule.operator"
            placeholder="操作符"
            size="small"
            style="width: 130px"
          >
            <el-option
              v-for="op in operatorsFor(rule)"
              :key="op.value"
              :label="op.label"
              :value="op.value"
            />
          </el-select>

          <!-- 值输入：between 用两个输入框，其他用一个；
               文本/选择类字段的等值与包含类运算符用「已有取值」可搜索下拉（仍可手输新值） -->
          <template v-if="rule.operator === 'between'">
            <el-input
              v-model="rule.valueMin"
              :placeholder="minPlaceholder(rule)"
              size="small"
              maxlength="512"
              show-word-limit
              style="width: 100px"
            />
            <span class="filter-panel__between-sep">至</span>
            <el-input
              v-model="rule.valueMax"
              :placeholder="maxPlaceholder(rule)"
              size="small"
              maxlength="512"
              show-word-limit
              style="width: 100px"
            />
          </template>
          <template v-else-if="rule.operator !== 'is_empty' && rule.operator !== 'is_not_empty'">
            <el-select
              v-if="useValueSelect(rule)"
              v-model="rule.valueText"
              placeholder="搜索或选择，可输入新值"
              size="small"
              style="width: 190px"
              filterable
              allow-create
              default-first-option
              clearable
              :loading="isLoadingOptions(rule)"
              @visible-change="(v: boolean) => v && ensureValueOptions(rule.fieldId)"
              @clear="rule.valueText = ''"
            >
              <el-option v-for="opt in optionsFor(rule)" :key="opt" :label="opt" :value="opt" />
            </el-select>
            <el-input
              v-else
              v-model="rule.valueText"
              :placeholder="valuePlaceholder(rule)"
              size="small"
              maxlength="512"
              show-word-limit
              style="width: 140px"
            />
          </template>

          <el-button
            link
            type="danger"
            :disabled="rules.length === 1"
            @click="removeRule(index)"
          >
            <el-icon><Delete /></el-icon>
          </el-button>
        </div>

        <el-button
          link
          type="primary"
          size="small"
          class="filter-panel__add"
          @click="addRule"
        >
          <el-icon><Plus /></el-icon> 添加条件
        </el-button>
      </div>
    </div>

    <template #footer>
      <el-button size="small" @click="handleClear">清空</el-button>
      <el-button size="small" @click="emit('update:modelValue', false)">取消</el-button>
      <el-button size="small" type="primary" @click="handleApply">应用</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Delete, Plus } from '@element-plus/icons-vue'
import { listFieldDistinctValues, aiGenerateFilter } from '@/api/modules/bitable'
import type {
  BitableField,
  FieldType,
  FilterGroup,
  FilterItem,
  FilterOperator,
} from '@/types/bitable'

const props = defineProps<{
  modelValue: boolean
  fields: BitableField[]
  filterConfig: FilterGroup | FilterItem[] | null
  /** 右键「按字段筛选」预置的字段 ID：打开时若指定则预置一条该字段的条件 */
  presetFieldId?: number | null
  /** 当前数据表 ID：取字段已有值列表用 */
  tableId?: number | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  apply: [config: FilterGroup | null]
}>()

interface DraftRule {
  fieldId: number | null
  operator: FilterOperator
  valueText: string
  valueMin: string
  valueMax: string
}

const MAX_RULES = 20
const MAX_VALUE_LENGTH = 512

const rootLogic = ref<'and' | 'or'>('and')
const rules = ref<DraftRule[]>([])

// 可筛选字段：排除公式/查找/汇总/关联/日期范围等复杂类型（后端仍支持，但 UI 暂不强制）
const filterableFields = computed(() =>
  props.fields.filter((f) => !['formula', 'lookup', 'rollup', 'attachment', 'button', 'date_range'].includes(f.fieldType)),
)

// 打开时根据现有配置初始化；有预置字段且当前无该字段条件时，追加一条预置规则
watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      // 每次打开重新拉取已有取值，避免使用上次打开后的陈旧数据
      valueOptionsByField.value.clear()
      loadingValueFields.value.clear()
      initFromConfig()
      if (props.presetFieldId != null) {
        const field = props.fields.find((f) => f.id === props.presetFieldId)
        if (field) {
          const already = rules.value.some((r) => r.fieldId === props.presetFieldId)
          if (!already) {
            // 空白占位规则（未选字段）直接替换；否则追加
            const blankIdx = rules.value.findIndex((r) => r.fieldId == null)
            const draft: DraftRule = {
              fieldId: field.id,
              operator: defaultOperator(field.fieldType),
              valueText: '',
              valueMin: '',
              valueMax: '',
            }
            if (blankIdx >= 0) {
              rules.value.splice(blankIdx, 1, draft)
            } else {
              rules.value.push(draft)
            }
          }
        }
      }
      // 预取已配置规则字段的取值列表
      for (const fieldId of new Set(rules.value.map((r) => r.fieldId))) {
        void ensureValueOptions(fieldId)
      }
    }
  },
)

function initFromConfig() {
  const config = props.filterConfig
  rootLogic.value = 'and'
  rules.value = [{ fieldId: null, operator: 'eq', valueText: '', valueMin: '', valueMax: '' }]

  if (!config) return
  const group: FilterGroup | null =
    Array.isArray(config) ? { logic: 'and', rules: config as FilterItem[] } : config
  if (!group) return
  rootLogic.value = group.logic

  // 展平嵌套组，丢失 OR 嵌套结构并给出提示（BLOCKING 修复）
  const flatRules = flattenRules(group.rules)
  if (flatRules.wasNested) {
    ElMessage.warning('当前筛选包含嵌套条件组合，已展平处理；如需恢复嵌套逻辑请重新配置。')
  }

  rules.value = flatRules.items
    .map((r) => {
      const value = Array.isArray(r.value)
        ? r.value
        : r.value && typeof r.value === 'object' && 'min' in (r.value as any) && 'max' in (r.value as any)
          ? [String((r.value as any).min), String((r.value as any).max)]
          : [String(r.value ?? ''), '']
      return {
        fieldId: r.fieldId,
        operator: r.operator,
        valueText: value[0] ?? '',
        valueMin: value[0] ?? '',
        valueMax: value[1] ?? '',
      }
    })
  if (!rules.value.length) {
    rules.value = [{ fieldId: null, operator: 'eq', valueText: '', valueMin: '', valueMax: '' }]
  }
}

function flattenRules(rules: Array<FilterItem | FilterGroup>): { items: FilterItem[]; wasNested: boolean } {
  const items: FilterItem[] = []
  let wasNested = false
  for (const rule of rules) {
    if ('rules' in rule) {
      // 嵌套组：展平子规则，记录发生过嵌套（BLOCKING 修复）
      wasNested = true
      const child = flattenRules(rule.rules)
      if (child.wasNested) wasNested = true
      items.push(...child.items)
    } else {
      items.push(rule)
    }
  }
  return { items, wasNested }
}

function onFieldChange(rule: DraftRule) {
  const field = props.fields.find((f) => f.id === rule.fieldId)
  rule.operator = defaultOperator(field?.fieldType)
  rule.valueText = ''
  rule.valueMin = ''
  rule.valueMax = ''
  // 换字段后预取已有取值，下拉展开时即可用
  void ensureValueOptions(rule.fieldId)
}

// ===== 值下拉（已有取值 + 模糊搜索）：文本/选择类字段 + 等值与包含类运算符 =====
const VALUE_SELECT_TYPES: string[] = ['text', 'rich_text', 'url', 'email', 'phone', 'single_select', 'multi_select', 'ai_text', 'ai_select']
const VALUE_SELECT_OPERATORS: FilterOperator[] = ['eq', 'ne', 'contains', 'not_contains']

const valueOptionsByField = ref<Map<number, string[]>>(new Map())
const loadingValueFields = ref<Set<number>>(new Set())

function useValueSelect(rule: DraftRule): boolean {
  const field = props.fields.find((f) => f.id === rule.fieldId)
  if (!field) return false
  return VALUE_SELECT_TYPES.includes(field.fieldType) && VALUE_SELECT_OPERATORS.includes(rule.operator)
}

function optionsFor(rule: DraftRule): string[] {
  return rule.fieldId != null ? valueOptionsByField.value.get(rule.fieldId) ?? [] : []
}

function isLoadingOptions(rule: DraftRule): boolean {
  return rule.fieldId != null ? loadingValueFields.value.has(rule.fieldId) : false
}

async function ensureValueOptions(fieldId: number | null) {
  if (fieldId == null || props.tableId == null) return
  if (valueOptionsByField.value.has(fieldId) || loadingValueFields.value.has(fieldId)) return
  loadingValueFields.value.add(fieldId)
  try {
    const values = await listFieldDistinctValues(props.tableId, fieldId)
    valueOptionsByField.value.set(fieldId, Array.isArray(values) ? values : [])
  } catch {
    // 拉取失败：留空列表，仍可手输新值
    valueOptionsByField.value.set(fieldId, [])
  } finally {
    loadingValueFields.value.delete(fieldId)
  }
}

function defaultOperator(type?: FieldType): FilterOperator {
  switch (type) {
    case 'number': case 'currency': case 'progress': case 'rating':
      return 'gt'
    case 'date': case 'date_range': case 'created_time': case 'modified_time':
    case 'last_modified_time':
      return 'gte'
    case 'single_select':
      return 'eq'
    case 'multi_select':
      return 'contains'
    case 'checkbox':
      return 'eq'
    default:
      return 'contains'
  }
}

interface OperatorOption {
  value: FilterOperator
  label: string
}

function operatorsFor(rule: DraftRule): OperatorOption[] {
  const field = props.fields.find((f) => f.id === rule.fieldId)
  const type = field?.fieldType
  const numeric = type === 'number' || type === 'currency' || type === 'progress' || type === 'rating'
  const dateLike = type === 'date' || type === 'date_range' || type === 'created_time'
    || type === 'modified_time' || type === 'last_modified_time'

  if (numeric || dateLike) {
    return [
      { value: 'eq', label: '等于' },
      { value: 'ne', label: '不等于' },
      { value: 'gt', label: '大于' },
      { value: 'gte', label: '大于等于' },
      { value: 'lt', label: '小于' },
      { value: 'lte', label: '小于等于' },
      { value: 'between', label: '区间内' },
      { value: 'is_empty', label: '为空' },
      { value: 'is_not_empty', label: '不为空' },
    ]
  }
  if (type === 'single_select') {
    return [
      { value: 'eq', label: '等于' },
      { value: 'ne', label: '不等于' },
      { value: 'is_empty', label: '为空' },
      { value: 'is_not_empty', label: '不为空' },
    ]
  }
  if (type === 'multi_select') {
    return [
      { value: 'contains', label: '包含' },
      { value: 'not_contains', label: '不包含' },
      { value: 'is_empty', label: '为空' },
      { value: 'is_not_empty', label: '不为空' },
    ]
  }
  // 默认文本类型
  return [
    { value: 'eq', label: '等于' },
    { value: 'ne', label: '不等于' },
    { value: 'contains', label: '包含' },
    { value: 'not_contains', label: '不包含' },
    { value: 'is_empty', label: '为空' },
    { value: 'is_not_empty', label: '不为空' },
  ]
}

function valuePlaceholder(rule: DraftRule): string {
  const field = props.fields.find((f) => f.id === rule.fieldId)
  const type = field?.fieldType
  if (type === 'number' || type === 'currency' || type === 'progress' || type === 'rating') return '输入数字'
  if (type === 'date' || type === 'date_range') return '选择或输入日期'
  if (type === 'created_time' || type === 'modified_time' || type === 'last_modified_time') return '选择或输入日期'
  return '输入内容'
}

function minPlaceholder(rule: DraftRule): string {
  return valuePlaceholder(rule) === '输入数字' ? '最小值' : '开始'
}

function maxPlaceholder(rule: DraftRule): string {
  return valuePlaceholder(rule) === '输入数字' ? '最大值' : '结束'
}

function addRule() {
  if (rules.value.length >= MAX_RULES) {
    ElMessage.warning(`最多添加 ${MAX_RULES} 条筛选条件`)
    return
  }
  rules.value.push({ fieldId: null, operator: 'contains', valueText: '', valueMin: '', valueMax: '' })
}

// ==================== AI 智能筛选：自然语言生成条件 ====================

const aiText = ref('')
const aiGenerating = ref(false)

/** 调后端把自然语言解析为条件，填充面板并自动应用查询（面板保持打开可微调） */
async function applyAiFilter() {
  const text = aiText.value.trim()
  if (!text) {
    ElMessage.warning('请先描述筛选需求，如「状态为进行中且优先级是高」')
    return
  }
  if (props.tableId == null) {
    ElMessage.warning('当前无法定位数据表')
    return
  }
  aiGenerating.value = true
  try {
    const result = await aiGenerateFilter(props.tableId, text)
    rootLogic.value = result.logic === 'or' ? 'or' : 'and'
    const drafts: DraftRule[] = []
    for (const r of result.rules || []) {
      const field = props.fields.find((f) => f.id === r.fieldId)
      if (!field) continue
      const isBetween = r.operator === 'between'
      const value = Array.isArray(r.value) ? r.value : [r.value ?? '', '']
      drafts.push({
        fieldId: r.fieldId,
        operator: r.operator as FilterOperator,
        valueText: String(value[0] ?? ''),
        valueMin: isBetween ? String(value[0] ?? '') : '',
        valueMax: isBetween ? String(value[1] ?? '') : '',
      })
    }
    if (!drafts.length) {
      ElMessage.warning('未能生成有效筛选条件，请换个描述或手动配置')
      return
    }
    rules.value = drafts.slice(0, MAX_RULES)
    ElMessage.success(`已生成 ${drafts.length} 条筛选条件并应用`)
    handleApply()
  } catch (e: any) {
    ElMessage.error(e?.message || 'AI 生成筛选失败，请重试')
  } finally {
    aiGenerating.value = false
  }
}

function removeRule(index: number) {
  rules.value.splice(index, 1)
}

function handleClear() {
  rules.value = [{ fieldId: null, operator: 'eq', valueText: '', valueMin: '', valueMax: '' }]
  rootLogic.value = 'and'
  emit('apply', null)
  emit('update:modelValue', false)
}

function handleApply() {
  // 标记未完成规则（MEDIUM 修复：提示用户哪些规则被忽略）
  const incompleteRules = rules.value.filter((r) => {
    if (r.fieldId == null) return false // 未选字段不算 incomplete
    if (r.operator === 'is_empty' || r.operator === 'is_not_empty') return false
    if (r.operator === 'between') {
      return !r.valueMin.trim() || !r.valueMax.trim()
    }
    return !r.valueText.trim()
  })
  if (incompleteRules.length > 0) {
    ElMessage.warning(`有 ${incompleteRules.length} 条条件未填写完整，将被忽略`)
  }

  const validRules = rules.value
    .filter((r) => r.fieldId != null)
    .map((r): FilterItem | null => {
      if (r.operator === 'is_empty' || r.operator === 'is_not_empty') {
        return { fieldId: r.fieldId!, operator: r.operator }
      }
      if (r.operator === 'between') {
        const value = [r.valueMin.trim(), r.valueMax.trim()]
        if (!value[0] || !value[1]) return null
        if (value.some((item) => item.length > MAX_VALUE_LENGTH)) return null
        return { fieldId: r.fieldId!, operator: 'between', value }
      }
      if (!r.valueText.trim() || r.valueText.trim().length > MAX_VALUE_LENGTH) return null
      return { fieldId: r.fieldId!, operator: r.operator, value: r.valueText.trim() }
    })
    .filter((r): r is FilterItem => r !== null)

  if (!validRules.length) {
    // 有未完成规则时不能清空已有筛选；无任何规则时允许清空
    if (rules.value.some((r) => r.fieldId != null)) {
      ElMessage.warning('所有已填条件均不完整，无法应用筛选')
      return
    }
    emit('apply', null)
  } else {
    emit('apply', { logic: rootLogic.value, rules: validRules })
  }
  emit('update:modelValue', false)
}

function handleClosed() {
  // 关闭后重置为空规则，避免下次打开残留
  rules.value = [{ fieldId: null, operator: 'eq', valueText: '', valueMin: '', valueMax: '' }]
  rootLogic.value = 'and'
}
</script>

<style scoped lang="scss">
.filter-panel {
  &__ai {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 12px;
    padding-bottom: 12px;
    border-bottom: 1px dashed var(--color-border);
  }

  &__logic {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 12px;
  }

  &__logic-label {
    font-size: 13px;
    color: var(--color-text-secondary);
  }

  &__rules {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  &__rule {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  &__between-sep {
    color: var(--color-text-secondary);
    font-size: 12px;
    flex-shrink: 0;
  }

  &__add {
    align-self: flex-start;
  }
}
</style>
