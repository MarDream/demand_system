<template>
  <div class="form-view">
    <el-card class="form-view__card" shadow="never">
      <template #header>
        <div class="form-view__header">
          <div>
            <h3>{{ table?.name || '表单视图' }}</h3>
            <p>按字段配置录入一条新记录，适用于信息收集、报名登记、客户反馈等场景。</p>
          </div>
          <el-tag type="success">Form</el-tag>
        </div>
      </template>

      <el-form v-if="editableFields.length" :model="formModel" label-width="120px" class="form-view__form">
        <el-form-item
          v-for="field in editableFields"
          :key="field.id"
          :label="field.name"
          :required="Boolean(field.required)"
        >
          <template #label>
            <span>{{ field.name }}</span>
            <el-tooltip v-if="field.description" :content="field.description" placement="top">
              <el-icon class="form-view__hint"><QuestionFilled /></el-icon>
            </el-tooltip>
          </template>

          <el-input
            v-if="field.fieldType === 'text' || field.fieldType === 'url' || field.fieldType === 'email' || field.fieldType === 'phone' || field.fieldType === 'barcode'"
            v-model="formModel[field.id]"
            :type="field.fieldType === 'text' ? 'textarea' : 'text'"
            :rows="field.fieldType === 'text' ? 3 : undefined"
            :placeholder="fieldPlaceholder(field)"
          />

          <el-input-number
            v-else-if="field.fieldType === 'number' || field.fieldType === 'currency' || field.fieldType === 'progress' || field.fieldType === 'rating'"
            v-model="formModel[field.id]"
            :min="field.fieldType === 'progress' ? 0 : undefined"
            :max="field.fieldType === 'progress' ? 100 : (field.fieldType === 'rating' ? (field.config?.maxRating || 10) : undefined)"
            style="width: 100%;"
          />

          <el-date-picker
            v-else-if="field.fieldType === 'date' || field.fieldType === 'date_range'"
            v-model="formModel[field.id]"
            :type="field.fieldType === 'date_range' ? 'daterange' : 'date'"
            value-format="YYYY-MM-DD"
            style="width: 100%;"
          />

          <el-select
            v-else-if="field.fieldType === 'single_select' || field.fieldType === 'process'"
            v-model="formModel[field.id]"
            :placeholder="fieldPlaceholder(field)"
            style="width: 100%;"
          >
            <el-option v-for="opt in fieldOptions(field)" :key="opt.label" :label="opt.label" :value="opt.label" />
          </el-select>

          <el-select
            v-else-if="field.fieldType === 'multi_select'"
            v-model="formModel[field.id]"
            multiple
            :placeholder="fieldPlaceholder(field)"
            style="width: 100%;"
          >
            <el-option v-for="opt in fieldOptions(field)" :key="opt.label" :label="opt.label" :value="opt.label" />
          </el-select>

          <el-switch v-else-if="field.fieldType === 'checkbox' || field.fieldType === 'check'" v-model="formModel[field.id]" />

          <el-input
            v-else-if="field.fieldType === 'location'"
            v-model="formModel[field.id]"
            :placeholder="fieldPlaceholder(field, '地址或经纬度，例如：上海市浦东新区 / 31.2304,121.4737')"
          />

          <el-input
            v-else-if="field.fieldType === 'user' || field.fieldType === 'group'"
            v-model="formModel[field.id]"
            :placeholder="fieldPlaceholder(field, `请输入${fieldTypeLabel(field.fieldType)}`)"
          />

          <el-input
            v-else
            v-model="formModel[field.id]"
            :placeholder="fieldPlaceholder(field)"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="loading" @click="submit">提交记录</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-empty v-else description="暂无可填写字段，请先添加字段" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { QuestionFilled } from '@element-plus/icons-vue'
import type { BitableField, BitableTable } from '@/types/bitable'

const props = defineProps<{
  table: BitableTable | null
  fields: BitableField[]
  loading?: boolean
}>()

const emit = defineEmits<{
  submit: [cells: Record<number, { valueText?: string; valueNumber?: number; valueDate?: string; valueJson?: unknown }>]
}>()

const formModel = reactive<Record<number, any>>({})

const readonlyTypes = new Set([
  'auto_number',
  'created_time',
  'modified_time',
  'last_modified_time',
  'created_user',
  'modified_user',
  'created_by',
  'modified_by',
  'formula',
  'lookup',
  'rollup',
  'button',
])

const editableFields = computed(() => props.fields.filter((field) => !readonlyTypes.has(field.fieldType) && !field.config?.formHidden))

function fieldOptions(field: BitableField) {
  return field.config?.options || field.config?.processNodes || []
}

function fieldPlaceholder(field: BitableField, fallback?: string) {
  return field.config?.formPlaceholder || field.description || fallback || `请输入${field.name}`
}

function fieldTypeLabel(type: string) {
  const map: Record<string, string> = { user: '人员', group: '群组' }
  return map[type] || '内容'
}

function validateRequired() {
  for (const field of editableFields.value) {
    const value = formModel[field.id]
    if (!field.required) continue
    if (value === undefined || value === null || value === '' || (Array.isArray(value) && value.length === 0)) {
      ElMessage.warning(`请填写必填字段：${field.name}`)
      return false
    }
  }
  return true
}

function buildCells() {
  const cells: Record<number, { valueText?: string; valueNumber?: number; valueDate?: string; valueJson?: unknown }> = {}
  for (const field of editableFields.value) {
    const value = formModel[field.id]
    if (value === undefined || value === null || value === '') continue
    if (['number', 'currency', 'progress', 'rating'].includes(field.fieldType)) {
      cells[field.id] = { valueNumber: Number(value) || 0 }
    } else if (field.fieldType === 'date') {
      cells[field.id] = { valueDate: String(value) }
    } else if (field.fieldType === 'date_range' || field.fieldType === 'multi_select' || field.fieldType === 'attachment' || field.fieldType === 'location') {
      cells[field.id] = { valueJson: value }
    } else if (field.fieldType === 'link' || field.fieldType === 'bidirectional_link') {
      const ids = Array.isArray(value)
        ? value
        : String(value).split(',').map((item) => Number(item.trim())).filter((id) => Number.isFinite(id))
      cells[field.id] = { valueJson: ids }
    } else if (field.fieldType === 'checkbox' || field.fieldType === 'check') {
      cells[field.id] = { valueText: String(Boolean(value)) }
    } else {
      cells[field.id] = { valueText: String(value) }
    }
  }
  return cells
}

function submit() {
  if (!validateRequired()) return
  emit('submit', buildCells())
}

function reset() {
  for (const key of Object.keys(formModel)) {
    delete formModel[Number(key)]
  }
}

defineExpose({ reset })
</script>

<style scoped lang="scss">
.form-view {
  flex: 1;
  overflow: auto;
  padding: 24px;
  background: var(--color-background);
}

.form-view__card {
  max-width: 760px;
  margin: 0 auto;
}

.form-view__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;

  h3 {
    margin: 0 0 4px;
    font-size: 18px;
  }

  p {
    margin: 0;
    color: var(--color-text-secondary);
    font-size: 13px;
  }
}

.form-view__form {
  padding-top: 8px;
}

.form-view__hint {
  margin-left: 4px;
  color: var(--color-text-secondary);
  vertical-align: -2px;
}
</style>
