<template>
  <el-form
    v-if="visibleFields.length"
    ref="formRef"
    :model="valueMap"
    :rules="fieldRules"
    label-position="top"
    class="dynamic-fields-form"
    @submit.prevent
  >
    <el-form-item
      v-for="field in visibleFields"
      :key="field.fieldCode"
      :prop="field.fieldCode"
      class="dynamic-field-item"
    >
      <template #label>
        <span class="dynamic-field-label">
          <span>{{ field.name }}</span>
          <el-tooltip v-if="!field.editable" content="当前流程节点下该字段为只读" placement="top">
            <el-tag size="small" type="info" effect="plain" round>只读</el-tag>
          </el-tooltip>
          <el-tag v-else-if="field.required" size="small" type="danger" effect="plain" round>必填</el-tag>
        </span>
      </template>

      <!-- 只读展示 -->
      <div v-if="!field.editable" class="dynamic-field-readonly">{{ displayText(field) || '—' }}</div>

      <!-- 文本 -->
      <el-input
        v-else-if="field.fieldType === 'TEXT'"
        v-model="entry(field.fieldCode).value"
        type="textarea"
        :rows="2"
        :placeholder="`请输入${field.name}`"
        maxlength="2000"
        show-word-limit
      />

      <!-- 链接 -->
      <el-input
        v-else-if="field.fieldType === 'URL'"
        v-model="entry(field.fieldCode).value"
        placeholder="https://"
      >
        <template v-if="isValidUrl(entry(field.fieldCode).value)" #append>
          <el-button :icon="Link" @click="openUrl(entry(field.fieldCode).value)" />
        </template>
      </el-input>

      <!-- 数值 -->
      <el-input-number
        v-else-if="field.fieldType === 'NUMBER'"
        v-model="entry(field.fieldCode).valueNumber"
        :precision="2"
        :step="1"
        :controls="false"
        style="width: 100%"
        :placeholder="`请输入${field.name}`"
      />

      <!-- 日期 -->
      <el-date-picker
        v-else-if="field.fieldType === 'DATE'"
        v-model="entry(field.fieldCode).valueDate"
        type="date"
        value-format="YYYY-MM-DD"
        :placeholder="`请选择${field.name}`"
        style="width: 100%"
      />

      <!-- 布尔 -->
      <el-switch
        v-else-if="field.fieldType === 'BOOLEAN'"
        v-model="entry(field.fieldCode).valueBoolean"
        active-text="是"
        inactive-text="否"
      />

      <!-- 单选 -->
      <el-select
        v-else-if="field.fieldType === 'SELECT'"
        v-model="entry(field.fieldCode).value"
        :placeholder="`请选择${field.name}`"
        clearable
        style="width: 100%"
      >
        <el-option v-for="opt in optionsOf(field)" :key="opt.key" :label="opt.label" :value="opt.key" />
      </el-select>

      <!-- 多选 -->
      <el-select
        v-else-if="field.fieldType === 'MULTI_SELECT'"
        v-model="entry(field.fieldCode).values"
        :placeholder="`请选择${field.name}`"
        multiple
        collapse-tags
        collapse-tags-tooltip
        clearable
        style="width: 100%"
      >
        <el-option v-for="opt in optionsOf(field)" :key="opt.key" :label="opt.label" :value="opt.key" />
      </el-select>

      <!-- 人员 -->
      <el-select
        v-else-if="field.fieldType === 'USER'"
        v-model="entry(field.fieldCode).valueUserId"
        :placeholder="`请选择${field.name}`"
        clearable
        filterable
        style="width: 100%"
      >
        <el-option v-for="u in users" :key="u.id" :label="userLabel(u)" :value="u.id" />
      </el-select>

      <!-- 多人员 -->
      <el-select
        v-else-if="field.fieldType === 'MULTI_USER'"
        v-model="entry(field.fieldCode).values"
        :placeholder="`请选择${field.name}`"
        multiple
        collapse-tags
        collapse-tags-tooltip
        clearable
        filterable
        style="width: 100%"
      >
        <el-option v-for="u in users" :key="u.id" :label="userLabel(u)" :value="String(u.id)" />
      </el-select>

      <!-- 附件 -->
      <div v-else-if="field.fieldType === 'FILE'" class="dynamic-field-file">
        <div v-if="fileList(field).length" class="file-tag-list">
          <el-tag
            v-for="url in fileList(field)"
            :key="url"
            closable
            :disable-transitions="false"
            @close="removeFile(field, url)"
            @click="openUrl(url)"
          >
            {{ fileNameOf(url) }}
          </el-tag>
        </div>
        <el-button :icon="Upload" :loading="uploading[field.fieldCode]" size="small" @click="pickFile(field)">
          上传文件
        </el-button>
        <input
          :ref="(el) => setInputRef(field.fieldCode, el)"
          type="file"
          class="hidden-file-input"
          @change="(e) => onFilePicked(field, e)"
        />
      </div>
    </el-form-item>
  </el-form>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Link, Upload } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { uploadRequirementAttachment } from '@/api/modules/file'
import {
  fieldOptionLabel,
  parseFieldOptions,
  type CustomFieldOption,
  type CustomFieldValuePayload,
  type DynamicFieldSchema,
} from '@/api/modules/requirementConfig'

interface UserOption {
  id: number
  realName?: string
  username?: string
  nickname?: string
}

const props = withDefaults(
  defineProps<{
    fields: DynamicFieldSchema[]
    modelValue: Record<string, CustomFieldValuePayload>
    users?: UserOption[]
    disabled?: boolean
  }>(),
  { users: () => [], disabled: false },
)

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, CustomFieldValuePayload>]
}>()

const formRef = ref<FormInstance>()
const uploading = reactive<Record<string, boolean>>({})
const fileInputs: Record<string, HTMLInputElement | null> = {}

/** 只渲染后端判定为可见的字段（后端已按流程节点权限过滤） */
const visibleFields = computed(() => props.fields.filter((f) => f.visible !== false))

const valueMap = computed(() => props.modelValue)

function entry(code: string): CustomFieldValuePayload {
  const map = props.modelValue
  if (!map[code]) {
    map[code] = { fieldCode: code }
  }
  return map[code]
}

// ---------------------------------------------------------------- 选项

/** 选项列表：优先后端解析的 optionList，兜底本地解析（兼容历史字符串数组） */
function optionsOf(field: DynamicFieldSchema): CustomFieldOption[] {
  if (field.optionList && field.optionList.length) {
    return field.optionList
  }
  return parseFieldOptions(field.options)
}

function userLabel(u: UserOption): string {
  return u.realName || u.nickname || u.username || String(u.id)
}

// ---------------------------------------------------------------- 展示

function displayText(field: DynamicFieldSchema): string {
  const e = props.modelValue[field.fieldCode]
  if (!e) return ''
  switch (field.fieldType) {
    case 'NUMBER':
      return e.valueNumber == null ? '' : String(e.valueNumber)
    case 'DATE':
      return e.valueDate || ''
    case 'BOOLEAN':
      return e.valueBoolean == null ? '' : e.valueBoolean ? '是' : '否'
    case 'USER': {
      if (e.valueUserId == null) return ''
      const u = props.users.find((x) => x.id === e.valueUserId)
      return u ? userLabel(u) : String(e.valueUserId)
    }
    case 'MULTI_USER': {
      const ids = (e.values || []).map(String)
      return ids
        .map((id) => {
          const u = props.users.find((x) => String(x.id) === id)
          return u ? userLabel(u) : id
        })
        .join('、')
    }
    case 'MULTI_SELECT': {
      const opts = optionsOf(field)
      return (e.values || []).map((v) => fieldOptionLabel(opts, String(v))).join('、')
    }
    case 'FILE':
      return (e.values || []).map((v) => fileNameOf(String(v))).join('、')
    default:
      return field.fieldType === 'SELECT' ? fieldOptionLabel(optionsOf(field), e.value) : e.value || ''
  }
}

function isValidUrl(v?: string | null): boolean {
  return !!v && /^https?:\/\//i.test(v)
}

function openUrl(url?: string | null) {
  if (isValidUrl(url)) window.open(url as string, '_blank')
}

// ---------------------------------------------------------------- 附件

function fileList(field: DynamicFieldSchema): string[] {
  return (props.modelValue[field.fieldCode]?.values || []).map(String)
}

function fileNameOf(url: string): string {
  try {
    const last = decodeURIComponent(url.split('?')[0].split('/').pop() || url)
    return last || url
  } catch {
    return url
  }
}

function setInputRef(code: string, el: any) {
  fileInputs[code] = el as HTMLInputElement
}

function pickFile(field: DynamicFieldSchema) {
  fileInputs[field.fieldCode]?.click()
}

async function onFilePicked(field: DynamicFieldSchema, event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  uploading[field.fieldCode] = true
  try {
    const attachment = await uploadRequirementAttachment(file)
    const url = attachment?.url
    if (!url) throw new Error('上传结果缺少文件地址')
    const e = entry(field.fieldCode)
    if (!e.values) e.values = []
    if (!e.values.map(String).includes(url)) {
      e.values.push(url)
      emit('update:modelValue', props.modelValue)
    }
  } catch (err: any) {
    ElMessage.error(err?.message || '文件上传失败')
  } finally {
    uploading[field.fieldCode] = false
    input.value = ''
  }
}

function removeFile(field: DynamicFieldSchema, url: string) {
  const e = entry(field.fieldCode)
  e.values = (e.values || []).filter((v) => String(v) !== url)
  emit('update:modelValue', props.modelValue)
}

// ---------------------------------------------------------------- 校验

function isEmpty(field: DynamicFieldSchema): boolean {
  const e = props.modelValue[field.fieldCode]
  if (!e) return true
  switch (field.fieldType) {
    case 'NUMBER':
      return e.valueNumber == null
    case 'DATE':
      return !e.valueDate
    case 'BOOLEAN':
      return e.valueBoolean == null
    case 'USER':
      return e.valueUserId == null
    case 'MULTI_SELECT':
    case 'MULTI_USER':
    case 'FILE':
      return !e.values || e.values.length === 0
    default:
      return !e.value || !String(e.value).trim()
  }
}

const fieldRules = computed<FormRules>(() => {
  const rules: FormRules = {}
  for (const field of visibleFields.value) {
    rules[field.fieldCode] = [
      {
        validator: (_rule, _value, callback) => {
          // 只读字段不参与必填校验：当前节点无法修改，不应阻塞流转
          if (!field.editable || !field.required) return callback()
          return isEmpty(field) ? callback(new Error(`${field.name} 为必填项`)) : callback()
        },
        trigger: 'change',
      },
    ]
  }
  return rules
})

async function validate(): Promise<boolean> {
  if (!formRef.value) return true
  try {
    return await formRef.value.validate().then(() => true).catch(() => false)
  } catch {
    return false
  }
}

function resetFields() {
  formRef.value?.clearValidate()
}

defineExpose({ validate, resetFields })
</script>

<style scoped lang="scss">
.dynamic-fields-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;

  @media (max-width: 768px) {
    grid-template-columns: 1fr;
  }
}

.dynamic-field-item {
  margin-bottom: 14px;
}

.dynamic-field-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.dynamic-field-readonly {
  width: 100%;
  min-height: 32px;
  padding: 4px 11px;
  border-radius: 4px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
  line-height: 24px;
  word-break: break-all;
  white-space: pre-wrap;
}

.dynamic-field-file {
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}

.file-tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;

  .el-tag {
    cursor: pointer;
  }
}

.hidden-file-input {
  display: none;
}
</style>
