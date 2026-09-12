<template>
  <el-dialog
    :model-value="visible"
    title="编辑记录"
    width="560px"
    @close="handleClose"
  >
    <el-form label-width="110px" v-if="editableFields.length">
      <el-form-item v-for="field in editableFields" :key="field.id" :label="field.name">
        <!-- 文本类 -->
        <el-input
          v-if="isTextFieldType(field.fieldType)"
          v-model="formValues[field.id]"
          :placeholder="`请输入${field.name}`"
        />
        <!-- 数字类 -->
        <el-input-number
          v-else-if="field.fieldType === 'number' || field.fieldType === 'currency' || field.fieldType === 'progress' || field.fieldType === 'rating'"
          v-model="numberValues[field.id]"
          :precision="field.fieldType === 'progress' || field.fieldType === 'rating' ? 0 : 2"
          :min="field.fieldType === 'progress' ? 0 : field.fieldType === 'rating' ? 0 : undefined"
          :max="field.fieldType === 'progress' ? 100 : field.fieldType === 'rating' ? (Number(field.config?.maxRating) || 5) : undefined"
          style="width: 100%"
        />
        <!-- 日期 -->
        <el-date-picker
          v-else-if="field.fieldType === 'date'"
          v-model="dateValues[field.id]"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="选择日期"
          style="width: 100%"
        />
        <!-- 单选 -->
        <el-select
          v-else-if="field.fieldType === 'single_select' || field.fieldType === 'process'"
          v-model="formValues[field.id]"
          clearable
          placeholder="请选择"
          style="width: 100%"
        >
          <el-option
            v-for="opt in (field.config?.options || [])"
            :key="opt.label"
            :label="opt.label"
            :value="opt.label"
          />
        </el-select>
        <!-- 复选框 -->
        <el-checkbox
          v-else-if="field.fieldType === 'check' || field.fieldType === 'checkbox'"
          v-model="checkValues[field.id]"
        />
      </el-form-item>
    </el-form>
    <el-empty v-else description="没有可编辑的字段" />

    <div v-if="readonlyPreview.length" class="record-edit-dialog__readonly">
      <div class="record-edit-dialog__readonly-title">其他字段（只读）</div>
      <div v-for="item in readonlyPreview" :key="item.name" class="record-edit-dialog__readonly-row">
        <span class="record-edit-dialog__readonly-label">{{ item.name }}</span>
        <span class="record-edit-dialog__readonly-value">{{ item.value }}</span>
      </div>
    </div>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import type { BitableField, BitableRecord } from '@/types/bitable'

const props = defineProps<{
  visible: boolean
  record: BitableRecord | null
  fields: BitableField[]
}>()

const emit = defineEmits<{
  close: []
  save: [data: { recordId: number; version: number; cells: Record<number, { valueText?: string; valueNumber?: number; valueDate?: string }> }]
}>()

const saving = ref(false)
const formValues = reactive<Record<number, string>>({})
const numberValues = reactive<Record<number, number | null>>({})
const dateValues = reactive<Record<number, string | null>>({})
const checkValues = reactive<Record<number, boolean>>({})

const readonlyFieldTypes = new Set(['auto_number', 'created_time', 'modified_time', 'last_modified_time', 'created_user', 'modified_user', 'created_by', 'modified_by', 'formula', 'lookup', 'rollup', 'button'])

// 本对话框支持的简单可编辑类型；link/multi_select 等复杂类型仍回网格编辑
const supported = (f: BitableField) => {
  if (readonlyFieldTypes.has(f.fieldType)) return false
  return ['text', 'url', 'email', 'phone', 'number', 'currency', 'progress', 'rating', 'date', 'single_select', 'process', 'check', 'checkbox'].includes(f.fieldType)
}

const editableFields = computed(() => props.fields.filter(supported))

const readonlyPreview = computed(() => {
  if (!props.record) return []
  return props.fields
    .filter((f) => !supported(f) && !readonlyFieldTypes.has(f.fieldType))
    .slice(0, 6)
    .map((f) => {
      const cell = props.record?.cells?.[f.id]
      let value: unknown = cell?.displayText ?? cell?.valueText ?? cell?.valueNumber ?? cell?.valueDate ?? cell?.valueJson
      if (value == null || value === '') value = '-'
      if (typeof value === 'object') value = JSON.stringify(value)
      return { name: f.name, value: String(value) }
    })
})

watch(() => props.visible, (v) => {
  if (!v || !props.record) return
  // 初始化表单值
  Object.keys(formValues).forEach((k) => delete formValues[Number(k)])
  Object.keys(numberValues).forEach((k) => delete numberValues[Number(k)])
  Object.keys(dateValues).forEach((k) => delete dateValues[Number(k)])
  Object.keys(checkValues).forEach((k) => delete checkValues[Number(k)])

  for (const field of editableFields.value) {
    const cell = props.record.cells?.[field.id]
    const raw = cell?.displayText ?? cell?.valueText ?? cell?.valueNumber ?? cell?.valueDate
    switch (field.fieldType) {
      case 'number':
      case 'currency':
      case 'progress':
      case 'rating': {
        const n = Number(raw)
        numberValues[field.id] = Number.isFinite(n) && raw !== '' && raw != null ? n : null
        break
      }
      case 'date':
        dateValues[field.id] = (cell?.valueDate ?? (typeof raw === 'string' ? raw : '')) || null
        break
      case 'check':
      case 'checkbox':
        checkValues[field.id] = String(raw) === 'true' || raw === 'True' || String(raw) === '1'
        break
      default:
        formValues[field.id] = raw != null ? String(raw) : ''
    }
  }
})

function isTextFieldType(type: string) {
  return ['text', 'url', 'email', 'phone'].includes(type)
}

function handleClose() {
  emit('close')
}

async function handleSave() {
  if (!props.record) return
  saving.value = true
  try {
    const cells: Record<number, { valueText?: string; valueNumber?: number; valueDate?: string }> = {}
    for (const field of editableFields.value) {
      switch (field.fieldType) {
        case 'number':
        case 'currency':
        case 'progress':
        case 'rating': {
          const n = numberValues[field.id]
          if (n != null) cells[field.id] = { valueNumber: n }
          break
        }
        case 'date': {
          const d = dateValues[field.id]
          if (d) cells[field.id] = { valueDate: d }
          break
        }
        case 'check':
        case 'checkbox':
          cells[field.id] = { valueText: checkValues[field.id] ? 'true' : 'false' }
          break
        default: {
          const t = formValues[field.id]
          if (t != null) cells[field.id] = { valueText: t }
        }
      }
    }
    emit('save', { recordId: props.record.id, version: props.record.version, cells })
  } finally {
    saving.value = false
  }
}
</script>

<style scoped lang="scss">
.record-edit-dialog__readonly {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--color-border, #e2e8f0);
}
.record-edit-dialog__readonly-title {
  font-size: 12px;
  color: var(--color-text-secondary, #64748b);
  margin-bottom: 8px;
}
.record-edit-dialog__readonly-row {
  display: flex;
  gap: 12px;
  font-size: 13px;
  padding: 3px 0;
}
.record-edit-dialog__readonly-label {
  flex-shrink: 0;
  width: 98px;
  color: var(--color-text-secondary, #64748b);
  text-align: right;
}
.record-edit-dialog__readonly-value {
  color: var(--color-text-primary, #0f172a);
  word-break: break-all;
}
</style>
