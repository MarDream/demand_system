<template>
  <el-dialog
    :model-value="visible"
    title="编辑记录"
    width="560px"
    @close="handleClose"
  >
    <el-form ref="formRef" :model="formValues" :rules="rules" label-width="110px" v-if="editableFields.length">
      <el-form-item v-for="field in editableFields" :key="field.id" :label="field.name" :prop="String(field.id)">
        <!-- 文本类（含 maxLength 限制） -->
        <el-input
          v-if="isTextFieldType(field.fieldType)"
          v-model="formValues[field.id]"
          :maxlength="Number(field.config?.maxLength) || undefined"
          :placeholder="fieldPlaceholder(field)"
        />
        <!-- 富文本（详情弹窗内完整编辑） -->
        <RichTextEditor
          v-else-if="field.fieldType === 'rich_text'"
          v-model="richTextValues[field.id]"
          :placeholder="fieldPlaceholder(field)"
          min-height="140px"
          :editor-key="`${props.record?.id}-${field.id}-${props.visible}`"
        />
        <!-- 数字类 -->
        <el-input-number
          v-else-if="field.fieldType === 'number' || field.fieldType === 'currency' || field.fieldType === 'progress' || field.fieldType === 'rating'"
          v-model="numberValues[field.id]"
          :precision="numberPrecision(field)"
          :min="numberMin(field)"
          :max="numberMax(field)"
          :placeholder="fieldPlaceholder(field)"
          style="width: 100%"
        />
        <!-- 日期（是否包含时间跟随字段属性） -->
        <el-date-picker
          v-else-if="field.fieldType === 'date'"
          v-model="dateValues[field.id]"
          :type="field.config?.withTime ? 'datetime' : 'date'"
          :value-format="field.config?.withTime ? 'YYYY-MM-DD HH:mm:ss' : 'YYYY-MM-DD'"
          :placeholder="fieldPlaceholder(field)"
          style="width: 100%"
        />
        <!-- 单选 -->
        <el-select
          v-else-if="field.fieldType === 'single_select' || field.fieldType === 'process'"
          v-model="formValues[field.id]"
          clearable
          :placeholder="fieldPlaceholder(field)"
          style="width: 100%"
        >
          <el-option
            v-for="opt in (field.config?.options || [])"
            :key="opt.label"
            :label="opt.label"
            :value="opt.label"
          />
        </el-select>
        <!-- 多选 -->
        <el-select
          v-else-if="field.fieldType === 'multi_select'"
          v-model="listValues[field.id]"
          multiple
          clearable
          collapse-tags
          collapse-tags-tooltip
          :placeholder="fieldPlaceholder(field)"
          :multiple-limit="Number(field.config?.maxSelect) || 0"
          style="width: 100%"
        >
          <el-option
            v-for="opt in (field.config?.options || [])"
            :key="opt.label"
            :label="opt.label"
            :value="opt.label"
          />
        </el-select>
        <!-- 部门（单选 / 多选跟随字段属性） -->
        <el-tree-select
          v-else-if="field.fieldType === 'department'"
          v-model="deptValues[field.id]"
          :data="orgTree"
          :multiple="field.config?.departmentMode === 'multiple'"
          :show-checkbox="field.config?.departmentMode === 'multiple'"
          check-strictly
          clearable
          node-key="id"
          :props="{ label: 'name', children: 'children' }"
          style="width: 100%"
        />
        <!-- 人员：可选项 = 系统用户列表（按字段属性 userScope 过滤） -->
        <el-select
          v-else-if="field.fieldType === 'user'"
          v-model="userValues[field.id]"
          :multiple="field.config?.userMode === 'multiple'"
          clearable
          filterable
          collapse-tags
          collapse-tags-tooltip
          :placeholder="fieldPlaceholder(field)"
          style="width: 100%"
        >
          <el-option v-for="u in scopedUserOptions(field)" :key="u.id" :label="u.realName" :value="u.id">
            <div class="record-edit-dialog__user-option">
              <el-avatar :size="20" :src="u.avatar || undefined">{{ u.realName.slice(0, 1) }}</el-avatar>
              <span>{{ u.realName }}</span>
              <span class="record-edit-dialog__user-option__username">{{ u.username }}</span>
            </div>
          </el-option>
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
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { getOrgTree } from '@/api/modules/user'
import { useUserStore } from '@/stores/modules/user'
import { allUsers, ensureUsersLoaded, filterUsersByScope, type UserOption } from '@/composables/useUserOptions'
import RichTextEditor from '@/components/rich/RichTextEditor.vue'
import type { BitableField, BitableRecord } from '@/types/bitable'
import {
  buildFieldRules,
  formatCellDisplay,
  isFieldHidden,
  isFieldReadonly,
  resolveFieldDefault,
} from '@/utils/bitableFieldConfig'

const props = defineProps<{
  visible: boolean
  record: BitableRecord | null
  fields: BitableField[]
}>()

const emit = defineEmits<{
  close: []
  save: [data: { recordId: number; version: number; cells: Record<number, { valueText?: string; valueNumber?: number; valueDate?: string; valueJson?: unknown }> }]
}>()

const saving = ref(false)
const formRef = ref<FormInstance>()
const formValues = reactive<Record<number, string>>({})
/** 富文本字段：字段 id → HTML */
const richTextValues = reactive<Record<number, string>>({})
const numberValues = reactive<Record<number, number | null>>({})
const dateValues = reactive<Record<number, string | null>>({})
const checkValues = reactive<Record<number, boolean>>({})
const listValues = reactive<Record<number, string[]>>({})
/** 部门字段：单部门存 id，多部门存 id 数组 */
const deptValues = reactive<Record<number, number | number[] | null>>({})
/** 人员字段：单人存用户 id，多人存 id 数组 */
const userValues = reactive<Record<number, number | number[] | null>>({})

// 本对话框支持的简单可编辑类型；link/群组等复杂类型仍回网格编辑
const supportedTypes = ['text', 'rich_text', 'url', 'email', 'phone', 'number', 'currency', 'progress', 'rating', 'date', 'single_select', 'multi_select', 'process', 'check', 'checkbox', 'department', 'user']

// ==================== 人员字段可选项（系统用户列表） ====================
const userStore = useUserStore()

/** 按字段属性 userScope 过滤后的可选项 */
function scopedUserOptions(field: BitableField): UserOption[] {
  return filterUsersByScope(allUsers.value, field, userStore.userInfo?.id ?? null)
}

/** 部门选择器数据源（组织树） */
const orgTree = ref<{ id: number; name: string; children?: unknown[] }[]>([])
/** 组织树拍平成 id → 名称，保存时把选中的 id 一并落成可读文本 */
const deptNameMap = computed(() => {
  const map = new Map<number, string>()
  const walk = (nodes: unknown[]) => {
    for (const node of nodes) {
      const rec = node as { id?: unknown; name?: unknown; children?: unknown[] }
      if (rec?.id != null) map.set(Number(rec.id), String(rec.name ?? ''))
      if (Array.isArray(rec?.children)) walk(rec.children)
    }
  }
  walk(orgTree.value)
  return map
})

const supported = (f: BitableField) => {
  if (isFieldReadonly(f)) return false
  return supportedTypes.includes(f.fieldType)
}

/** 可编辑字段：排除只读字段（类型只读 + 字段级权限 readonly/hidden） */
const editableFields = computed(() => props.fields.filter(supported))

/** 只读区展示：类型只读字段 + 字段级权限只读字段；隐藏字段完全不展示 */
const readonlyPreview = computed(() => {
  if (!props.record) return []
  return props.fields
    .filter((f) => !isFieldHidden(f) && !supported(f))
    .slice(0, 8)
    .map((f) => {
      // 统一走展示口径：附件/关联/人员等结构化值不会再被 JSON.stringify 成裸串
      const text = formatCellDisplay(f, props.record?.cells?.[f.id])
      return { name: f.name, value: text || '-' }
    })
})

/** 按字段属性生成校验规则（必填 / 长度 / 正则 / 数值区间 / 格式） */
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

watch(() => props.visible, (v) => {
  if (!v || !props.record) return
  // 初始化表单值
  Object.keys(formValues).forEach((k) => delete formValues[Number(k)])
  Object.keys(richTextValues).forEach((k) => delete richTextValues[Number(k)])
  Object.keys(numberValues).forEach((k) => delete numberValues[Number(k)])
  Object.keys(dateValues).forEach((k) => delete dateValues[Number(k)])
  Object.keys(checkValues).forEach((k) => delete checkValues[Number(k)])
  Object.keys(listValues).forEach((k) => delete listValues[Number(k)])
  Object.keys(deptValues).forEach((k) => delete deptValues[Number(k)])
  Object.keys(userValues).forEach((k) => delete userValues[Number(k)])

  for (const field of editableFields.value) {
    const cell = props.record.cells?.[field.id]
    const raw = cell?.displayText ?? cell?.valueText ?? cell?.valueNumber ?? cell?.valueDate
    switch (field.fieldType) {
      case 'rich_text':
        richTextValues[field.id] = cell?.valueText || ''
        break
      case 'number':
      case 'currency':
      case 'progress':
      case 'rating': {
        const n = Number(raw)
        const fallback = resolveFieldDefault(field)
        numberValues[field.id] = Number.isFinite(n) && raw !== '' && raw != null
          ? n
          : typeof fallback === 'number' ? fallback : null
        break
      }
      case 'date':
        dateValues[field.id] = (cell?.valueDate ?? (typeof raw === 'string' ? raw : '')) || null
        break
      case 'check':
      case 'checkbox':
        checkValues[field.id] = String(raw) === 'true' || raw === 'True' || String(raw) === '1'
        break
      case 'multi_select': {
        // valueJson 可能是数组、JSON 字符串，或退化为逗号分隔的 valueText
        const json = cell?.valueJson
        if (Array.isArray(json)) {
          listValues[field.id] = json.map((v) => String(v))
        } else if (typeof json === 'string' && json.trim()) {
          try {
            const parsed = JSON.parse(json)
            listValues[field.id] = Array.isArray(parsed) ? parsed.map((v) => String(v)) : [json]
          } catch {
            listValues[field.id] = json.split(',').map((s) => s.trim()).filter(Boolean)
          }
        } else {
          listValues[field.id] = String(raw || '')
            .split(',')
            .map((s) => s.trim())
            .filter(Boolean)
        }
        break
      }
      case 'department': {
        // valueJson 里存的是部门 id（数字，或 [{id,name}] 形态）
        const json = cell?.valueJson
        const ids = Array.isArray(json)
          ? json
              .map((v) => Number(v && typeof v === 'object' ? (v as { id?: unknown }).id : v))
              .filter((n) => Number.isFinite(n))
          : []
        deptValues[field.id] =
          field.config?.departmentMode === 'multiple' ? ids : (ids[0] ?? null)
        break
      }
      case 'user': {
        // valueJson 里存的是用户 id（数字）或 [{id, name}] 形态；历史纯文本按姓名反查
        const json = cell?.valueJson
        const ids = Array.isArray(json)
          ? json
              .map((v) => Number(v && typeof v === 'object' ? (v as { id?: unknown }).id : v))
              .filter((n) => Number.isFinite(n))
          : []
        if (!ids.length && typeof raw === 'string' && raw.trim()) {
          const names = raw.split(',').map((s) => s.trim()).filter(Boolean)
          for (const name of names) {
            const matched = allUsers.value.find((u) => u.realName === name || u.username === name)
            if (matched) ids.push(matched.id)
          }
        }
        userValues[field.id] =
          field.config?.userMode === 'multiple' ? ids : (ids[0] ?? null)
        break
      }
      default:
        formValues[field.id] = raw != null ? String(raw) : ''
    }
  }
})

// 打开弹框时按需拉取组织树（表里没有部门字段就不发请求）
watch(
  () => props.visible,
  async (visible) => {
    if (!visible || orgTree.value.length) return
    if (!props.fields.some((f) => f.fieldType === 'department')) return
    try {
      const tree = await getOrgTree()
      orgTree.value = Array.isArray(tree) ? (tree as typeof orgTree.value) : ((tree as any)?.data ?? [])
    } catch {
      orgTree.value = []
    }
  },
)

// 打开弹框时按需拉取系统用户列表（表里没有人员字段就不发请求；共享缓存）
watch(
  () => props.visible,
  (visible) => {
    if (!visible) return
    if (props.fields.some((f) => f.fieldType === 'user')) {
      ensureUsersLoaded()
    }
  },
)

function isTextFieldType(type: string) {
  return ['text', 'url', 'email', 'phone'].includes(type)
}

/** 占位文案：输入提示 > 字段描述 > 默认「请输入{字段名}」（与 FormView 同口径） */
function fieldPlaceholder(field: BitableField) {
  return field.config?.formPlaceholder || field.description || `请输入${field.name}`
}

/** 数字类字段精度：进度/评分取整，其余按字段配置（默认数字 0 位、货币 2 位）。 */
function numberPrecision(field: BitableField) {
  if (field.fieldType === 'progress' || field.fieldType === 'rating') return 0
  return Number(field.config?.precision) || (field.fieldType === 'currency' ? 2 : 0)
}

function numberMin(field: BitableField) {
  if (field.fieldType === 'progress' || field.fieldType === 'rating') return 0
  return field.config?.min != null ? Number(field.config.min) : undefined
}

function numberMax(field: BitableField) {
  if (field.fieldType === 'progress') return 100
  if (field.fieldType === 'rating') return Number(field.config?.maxRating) || 5
  return field.config?.max != null ? Number(field.config.max) : undefined
}

function handleClose() {
  emit('close')
}

async function handleSave() {
  if (!props.record) return
  // 保存前跑一遍字段属性校验，避免绕过前端把非法值写进库
  const valid = await formRef.value?.validate().catch(() => false)
  if (valid === false) {
    ElMessage.warning('请检查表单填写')
    return
  }
  saving.value = true
  try {
    const cells: Record<number, { valueText?: string; valueNumber?: number; valueDate?: string; valueJson?: unknown }> = {}
    for (const field of editableFields.value) {
      switch (field.fieldType) {
        case 'rich_text':
          cells[field.id] = { valueText: richTextValues[field.id] || '' }
          break
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
        case 'multi_select':
          // 多选以数组形式写入 valueJson，与网格/看板的取值口径一致
          cells[field.id] = { valueJson: listValues[field.id] || [] }
          break
        case 'department': {
          // id 数组写 valueJson；同时落一份名称到 valueText，
          // 让网格/卡片不用再回查组织树就能显示部门名
          const value = deptValues[field.id]
          const ids = (Array.isArray(value) ? value : value != null ? [value] : [])
            .map(Number)
            .filter((n) => Number.isFinite(n))
          cells[field.id] = {
            valueJson: ids,
            valueText: ids.map((id) => deptNameMap.value.get(id) || String(id)).join(', '),
          }
          break
        }
        case 'user': {
          // [{id, name}] 写 valueJson（与展示口径一致），valueText 落可读姓名串
          const value = userValues[field.id]
          const ids = (Array.isArray(value) ? value : value != null ? [value] : [])
            .map(Number)
            .filter((n) => Number.isFinite(n))
          const picked = ids
            .map((id) => allUsers.value.find((u) => u.id === id))
            .filter((u): u is UserOption => !!u)
          cells[field.id] = {
            valueJson: picked.map((u) => ({ id: u.id, name: u.realName })),
            valueText: picked.map((u) => u.realName).join(', '),
          }
          break
        }
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
  border-top: 1px solid var(--color-border, var(--color-border));
}
.record-edit-dialog__readonly-title {
  font-size: 12px;
  color: var(--color-text-secondary, var(--color-muted-text));
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
  color: var(--color-text-secondary, var(--color-muted-text));
  text-align: right;
}
.record-edit-dialog__readonly-value {
  color: var(--color-text-primary, var(--color-text-primary));
  word-break: break-all;
}

// 人员下拉选项：头像 + 姓名 + 用户名
.record-edit-dialog__user-option {
  display: flex;
  align-items: center;
  gap: 8px;

  &__username {
    margin-left: auto;
    font-size: 12px;
    color: var(--color-text-tertiary, var(--color-text-tertiary));
  }
}
</style>
