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

      <el-form
        v-if="editableFields.length"
        ref="formRef"
        :model="formModel"
        :rules="rules"
        label-width="120px"
        class="form-view__form"
      >
        <el-form-item
          v-for="field in editableFields"
          :key="field.id"
          :label="field.name"
          :prop="String(field.id)"
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
            :maxlength="Number(field.config?.maxLength) || undefined"
            :disabled="isFieldReadonly(field)"
            :placeholder="fieldPlaceholder(field)"
          />

          <!-- 富文本：表单内完整编辑 -->
          <RichTextEditor
            v-else-if="field.fieldType === 'rich_text'"
            v-model="formModel[field.id]"
            :placeholder="fieldPlaceholder(field)"
            min-height="140px"
            :editor-key="String(field.id)"
          />

          <el-input-number
            v-else-if="field.fieldType === 'number' || field.fieldType === 'currency' || field.fieldType === 'progress' || field.fieldType === 'rating'"
            v-model="formModel[field.id]"
            :disabled="isFieldReadonly(field)"
            :precision="field.fieldType === 'progress' || field.fieldType === 'rating' ? 0 : (Number(field.config?.precision) || 0)"
            :min="numberMin(field)"
            :max="numberMax(field)"
            style="width: 100%;"
          />

          <el-date-picker
            v-else-if="field.fieldType === 'date' || field.fieldType === 'date_range'"
            v-model="formModel[field.id]"
            :type="field.fieldType === 'date_range' ? 'daterange' : (field.config?.withTime ? 'datetime' : 'date')"
            :value-format="field.config?.withTime ? 'YYYY-MM-DD HH:mm:ss' : 'YYYY-MM-DD'"
            :disabled="isFieldReadonly(field)"
            style="width: 100%;"
          />

          <el-select
            v-else-if="field.fieldType === 'single_select' || field.fieldType === 'process'"
            v-model="formModel[field.id]"
            :disabled="isFieldReadonly(field)"
            :allow-create="Boolean(field.config?.allowAddOption)"
            :placeholder="fieldPlaceholder(field)"
            style="width: 100%;"
          >
            <el-option v-for="opt in fieldOptions(field)" :key="opt.label" :label="opt.label" :value="opt.label" />
          </el-select>

          <el-select
            v-else-if="field.fieldType === 'multi_select'"
            v-model="formModel[field.id]"
            multiple
            :disabled="isFieldReadonly(field)"
            :allow-create="Boolean(field.config?.allowAddOption)"
            :multiple-limit="Number(field.config?.maxSelect) || 0"
            :placeholder="fieldPlaceholder(field)"
            style="width: 100%;"
          >
            <el-option v-for="opt in fieldOptions(field)" :key="opt.label" :label="opt.label" :value="opt.label" />
          </el-select>

          <el-switch
            v-else-if="field.fieldType === 'checkbox' || field.fieldType === 'check'"
            v-model="formModel[field.id]"
            :disabled="isFieldReadonly(field)"
          />

          <!-- 关联字段：弹窗选择目标表记录 -->
          <div v-else-if="field.fieldType === 'link' || field.fieldType === 'bidirectional_link'" class="form-view__link-field">
            <el-button size="small" :disabled="isFieldReadonly(field)" @click="openLinkSelector(field)">
              <el-icon><Link /></el-icon> 选择关联记录
            </el-button>
            <span class="form-view__link-count">
              {{ (formModel[field.id] as number[] | undefined)?.length || 0 }} 条已选
            </span>
          </div>

          <el-input
            v-else-if="field.fieldType === 'location'"
            v-model="formModel[field.id]"
            :disabled="isFieldReadonly(field)"
            :placeholder="fieldPlaceholder(field, '地址或经纬度，例如：上海市浦东新区 / 31.2304,121.4737')"
          />

          <!-- 人员：可选项 = 系统用户列表（按字段属性 userScope 过滤） -->
          <el-select
            v-else-if="field.fieldType === 'user'"
            v-model="formModel[field.id]"
            :multiple="field.config?.userMode === 'multiple'"
            :disabled="isFieldReadonly(field)"
            clearable
            filterable
            collapse-tags
            collapse-tags-tooltip
            :placeholder="fieldPlaceholder(field, '选择人员')"
            style="width: 100%;"
          >
            <el-option v-for="u in scopedUserOptions(field)" :key="u.id" :label="u.realName" :value="u.id">
              <div class="form-view__user-option">
                <el-avatar :size="20" :src="u.avatar || undefined">{{ u.realName.slice(0, 1) }}</el-avatar>
                <span>{{ u.realName }}</span>
                <span class="form-view__user-option__username">{{ u.username }}</span>
              </div>
            </el-option>
          </el-select>

          <!-- 群组：暂保留文本输入（群组数据源未接入） -->
          <el-input
            v-else-if="field.fieldType === 'group'"
            v-model="formModel[field.id]"
            :disabled="isFieldReadonly(field)"
            :placeholder="fieldPlaceholder(field, '请输入群组')"
          />

          <el-input
            v-else
            v-model="formModel[field.id]"
            :disabled="isFieldReadonly(field)"
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

    <!-- 关联记录选择弹窗 -->
    <LinkFieldSelector
      :visible="linkSelectorVisible"
      :target-table-id="linkFieldTargetTableId"
      :selected-ids="linkSelectedIds"
      @confirm="handleLinkConfirm"
      @close="linkSelectorVisible = false"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { QuestionFilled, Link } from '@element-plus/icons-vue'
import type { BitableField, BitableTable } from '@/types/bitable'
import {
  buildFieldRules,
  isFieldHidden,
  isFieldReadonly,
  resolveFieldDefault,
} from '@/utils/bitableFieldConfig'
import LinkFieldSelector from './LinkFieldSelector.vue'
import RichTextEditor from '@/components/rich/RichTextEditor.vue'
import { useUserStore } from '@/stores/modules/user'
import { allUsers, ensureUsersLoaded, filterUsersByScope, type UserOption } from '@/composables/useUserOptions'

const props = defineProps<{
  table: BitableTable | null
  fields: BitableField[]
  loading?: boolean
}>()

const emit = defineEmits<{
  submit: [cells: Record<number, { valueText?: string; valueNumber?: number; valueDate?: string; valueJson?: unknown }>]
}>()

const formRef = ref<FormInstance>()
const formModel = reactive<Record<number, any>>({})

// ==================== 人员字段可选项（系统用户列表） ====================
const userStore = useUserStore()

/** 按字段属性 userScope 过滤后的可选项 */
function scopedUserOptions(field: BitableField): UserOption[] {
  return filterUsersByScope(allUsers.value, field, userStore.userInfo?.id ?? null)
}

// 有人员字段才拉取用户列表（共享缓存，多入口只发一次请求）
watch(
  () => props.fields,
  (fields) => {
    if (fields.some((f) => f.fieldType === 'user')) {
      ensureUsersLoaded()
    }
  },
  { immediate: true },
)

// 关联字段选择器状态
const linkSelectorVisible = ref(false)
const linkEditingFieldId = ref<number | null>(null)
const linkFieldTargetTableId = computed<number | null>(() => {
  const field = props.fields.find((f) => f.id === linkEditingFieldId.value)
  const target = field?.config?.linkTargetTableId
  return typeof target === 'number' ? target : null
})
const linkSelectedIds = computed<number[]>(() => {
  const v = linkEditingFieldId.value != null ? formModel[linkEditingFieldId.value] : null
  return Array.isArray(v) ? v : []
})

function openLinkSelector(field: BitableField) {
  const target = field.config?.linkTargetTableId
  if (!target) {
    ElMessage.warning('该关联字段未配置目标表')
    return
  }
  linkEditingFieldId.value = field.id
  linkSelectorVisible.value = true
}

function handleLinkConfirm(ids: number[]) {
  if (linkEditingFieldId.value != null) {
    formModel[linkEditingFieldId.value] = ids
  }
  linkSelectorVisible.value = false
}

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

const editableFields = computed(() =>
  props.fields.filter(
    (field) =>
      !readonlyTypes.has(field.fieldType) &&
      !field.config?.formHidden &&
      // 字段级权限为「隐藏」的字段不出现在表单里
      !isFieldHidden(field),
  ),
)

/** 按字段属性生成校验规则（必填 / 长度 / 正则 / 数值区间 / 格式 / 多选上限） */
const rules = computed<FormRules>(() => {
  const result: FormRules = {}
  for (const field of editableFields.value) {
    const fieldRules = buildFieldRules(field)
    if (fieldRules.length) {
      result[String(field.id)] = fieldRules
    }
  }
  return result
})

/** 数字类字段的取值范围 */
function numberMin(field: BitableField) {
  if (field.fieldType === 'progress' || field.fieldType === 'rating') return 0
  return field.config?.min != null ? Number(field.config.min) : undefined
}

function numberMax(field: BitableField) {
  if (field.fieldType === 'progress') return 100
  if (field.fieldType === 'rating') return Number(field.config?.maxRating) || 5
  return field.config?.max != null ? Number(field.config.max) : undefined
}

/** 套用字段默认值（新建记录时的初始值） */
function applyDefaults() {
  for (const field of editableFields.value) {
    const fallback = resolveFieldDefault(field)
    if (fallback === undefined) continue
    if (field.fieldType === 'date' || field.fieldType === 'date_range') {
      formModel[field.id] = typeof fallback === 'string' ? fallback.slice(0, field.config?.withTime ? 19 : 10).replace('T', ' ') : fallback
    } else if (field.fieldType === 'user' && fallback === 'currentUser') {
      // 人员字段「默认当前用户」：解析为当前登录用户 id；单人多选形态对齐 userMode
      if (userStore.userInfo?.id == null) continue
      formModel[field.id] =
        field.config?.userMode === 'multiple' ? [userStore.userInfo.id] : userStore.userInfo.id
    } else {
      formModel[field.id] = fallback
    }
  }
}

// 字段列表变化时（切换数据表 / 新增字段）重新套用默认值
watch(
  () => props.fields,
  () => {
    reset()
  },
  { immediate: true },
)

function fieldOptions(field: BitableField) {
  return field.config?.options || field.config?.processNodes || []
}

function fieldPlaceholder(field: BitableField, fallback?: string) {
  return field.config?.formPlaceholder || field.description || fallback || `请输入${field.name}`
}

function buildCells() {
  const cells: Record<number, { valueText?: string; valueNumber?: number; valueDate?: string; valueJson?: unknown }> = {}
  for (const field of editableFields.value) {
    // 字段级权限为只读时不提交，避免把未改动的展示值回写成新值
    if (isFieldReadonly(field)) continue
    const value = formModel[field.id]
    if (value === undefined || value === null || value === '') continue
    if (['number', 'currency', 'progress', 'rating'].includes(field.fieldType)) {
      cells[field.id] = { valueNumber: Number(value) || 0 }
    } else if (field.fieldType === 'date') {
      cells[field.id] = { valueDate: String(value) }
    } else if (field.fieldType === 'user') {
      // 人员：valueJson 存 [{id, name}]（与展示口径一致），valueText 落可读姓名串
      const ids = Array.isArray(value) ? value : [value]
      const picked = ids
        .map((id) => allUsers.value.find((u) => u.id === Number(id)))
        .filter((u): u is UserOption => !!u)
      cells[field.id] = {
        valueJson: picked.map((u) => ({ id: u.id, name: u.realName })),
        valueText: picked.map((u) => u.realName).join(', '),
      }
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

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (valid === false) return
  emit('submit', buildCells())
}

function reset() {
  for (const key of Object.keys(formModel)) {
    delete formModel[Number(key)]
  }
  formRef.value?.clearValidate()
  applyDefaults()
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
  background: var(--color-background, var(--color-background));
}

// 人员下拉选项：头像 + 姓名 + 用户名
.form-view__user-option {
  display: flex;
  align-items: center;
  gap: 8px;

  &__username {
    margin-left: auto;
    font-size: 12px;
    color: var(--color-text-tertiary, var(--color-text-tertiary));
  }
}

.form-view__card {
  max-width: 760px;
  margin: 0 auto;
  border-radius: var(--radius-card-xl, 18px) !important;
  box-shadow: var(--shadow-md, 0 4px 6px -1px rgba(15, 23, 42, 0.08)) !important;
  border: 0.5px solid var(--color-border, var(--color-border)) !important;
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
    color: var(--color-text-primary, var(--color-text-primary));
    letter-spacing: -0.01em;
  }

  p {
    margin: 0;
    color: var(--color-text-secondary, var(--color-text-secondary));
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
  color: var(--color-text-primary, var(--color-text-primary));
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
  color: var(--color-text-secondary, var(--color-text-secondary));
  font-size: 12px;
  font-weight: 400;
  margin-top: 4px;
  display: block;
  line-height: 1.5;
}

.form-view__link-field {
  display: flex;
  align-items: center;
  gap: 10px;
}

.form-view__link-count {
  font-size: 12px;
  color: var(--color-text-secondary, var(--color-muted-text));
}

// 提交操作区
.form-view__actions {
  position: sticky;
  bottom: 0;
  background: linear-gradient(180deg, transparent, var(--color-surface, var(--color-surface)) 30%);
  padding: 16px 0 0;
  margin-top: 8px;
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}
</style>
