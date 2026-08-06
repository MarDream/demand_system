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

        <div class="form-view__actions">
          <el-button @click="reset">重置</el-button>
          <el-button type="primary" :loading="loading" @click="submit">
            提交记录
          </el-button>
        </div>
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
// ===== 多维表格 FormView 激进风格精修（2026-08-03）=====
// 设计目标：
// 1. 字段按类型分组章节
// 2. 提交按钮 sticky 底部
// 3. 必填星号视觉强化
// 4. 行内 hint 替代 tooltip
// 5. label 加大 + 描述行间距加大

.form-view {
  flex: 1;
  overflow: auto;
  padding: 32px 20px 80px;
  background: var(--color-background, #f8fafc);
}

.form-view__card {
  max-width: 760px;
  margin: 0 auto;
  border-radius: var(--radius-card-xl, 18px) !important;
  box-shadow: var(--shadow-md, 0 4px 6px -1px rgba(15, 23, 42, 0.08)) !important;
  border: 0.5px solid var(--color-border, #e2e8f0) !important;
  overflow: hidden;
}

.form-view__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 4px;

  h3 {
    margin: 0 0 6px;
    font-size: 18px;
    font-weight: 700;
    color: var(--color-text-primary, #0f172a);
    letter-spacing: -0.01em;
  }

  p {
    margin: 0;
    color: var(--color-text-secondary, #475569);
    font-size: 13px;
    line-height: 1.6;
  }
}

.form-view__form {
  padding-top: 12px;
}

.form-view__form :deep(.el-form-item) {
  margin-bottom: 22px;
}

.form-view__form :deep(.el-form-item__label) {
  font-weight: 600;
  color: var(--color-text-primary, #0f172a);
  font-size: 13px;
  padding-right: 16px;
  line-height: 1.5;
}

.form-view__form :deep(.el-form-item.is-required:not(.is-no-asterisk) .el-form-item__label-wrap > .el-form-item__label::before) {
  content: '*';
  color: var(--color-danger, #ef4444);
  margin-right: 4px;
  font-weight: 700;
}

.form-view__hint {
  margin-left: 6px;
  color: var(--color-text-secondary, #475569);
  font-size: 12px;
  font-weight: 400;
  margin-top: 4px;
  display: block;
  line-height: 1.5;
}

// 提交操作区
.form-view__actions {
  position: sticky;
  bottom: 0;
  background: linear-gradient(180deg, transparent, var(--color-surface, #fff) 30%);
  padding: 16px 0 0;
  margin-top: 8px;
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}
</style>
