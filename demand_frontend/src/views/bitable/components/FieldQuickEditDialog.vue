<template>
  <teleport to="body">
    <transition name="el-fade-in-linear">
      <div v-if="visible" class="bqf-overlay" @click.self="handleCancel">
        <div class="bqf" :style="{ width: width + 'px' }">
          <!-- 弹层标题（走查 P2-1：与「添加字段」弹窗语境对齐） -->
          <div class="bqf__header">
            <span class="bqf__header-title">编辑字段</span>
            <!-- 右上角关闭按钮（走查 P1-1：与 ESC 等效的显式关闭点） -->
            <button class="bqf__close" type="button" title="关闭 (Esc)" @click="handleCancel">
              <i class="ri-close-line" />
            </button>
          </div>
          <!-- 中部滚动区：头部与底部操作固定，仅表单内容随滚动条滚动 -->
          <div class="bqf__body">
            <!-- 字段名（钉钉风格：大号无边框输入，聚焦时高亮） -->
            <el-input
              v-model="form.name"
              class="bqf__name"
              placeholder="字段名称"
              maxlength="50"
              @keyup.enter="handleConfirm"
            />

            <!-- 字段类型选择器（下拉含搜索 + 分组字段列表，可切换类型；钉钉风格） -->
            <el-select
              v-model="form.fieldType"
              class="bqf__type"
              filterable
              :filter-method="filterType"
              placeholder="搜索"
              popper-class="bqf-type-popper"
              @visible-change="onTypeDropdownToggle"
            >
              <template #label>
                <span class="bqf__type-label">
                  <i :class="currentTypeMeta.icon" :style="{ color: currentTypeMeta.color }" />
                  <span>{{ currentTypeMeta.label }}</span>
                </span>
              </template>
              <el-option-group v-for="group in visibleTypeGroups" :key="group.label" :label="group.label">
                <el-option
                  v-for="opt in group.options"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                >
                  <span class="bqf__type-option">
                    <i :class="typeMeta(opt.value).icon" :style="{ color: typeMeta(opt.value).color }" />
                    <span>{{ opt.label }}</span>
                    <i v-if="opt.value === form.fieldType" class="ri-check-line bqf__type-check" />
                  </span>
                </el-option>
              </el-option-group>
            </el-select>

            <!-- 类型专属属性（数字格式 / 选项 / 日期……复用现有属性面板） -->
            <div v-if="hasExtraAttrs" class="bqf__attrs">
              <FieldAttributeForm
                :key="form.fieldType"
                :field-type="form.fieldType"
                :config="form.config"
                :base="form.base"
                :fields="fields"
                :tables="tables"
                :active-table-id="activeTableId"
                :editing-field-id="editingFieldId"
              />
            </div>

            <!-- 添加字段描述 -->
            <div class="bqf__desc-toggle" @click="showDesc = !showDesc">
              <i class="ri-add-line" />
              <span>添加字段描述</span>
            </div>
            <el-input
              v-if="showDesc"
              v-model="form.base.description"
              type="textarea"
              :rows="2"
              maxlength="500"
              placeholder="输入字段描述"
              class="bqf__desc-input"
            />
          </div>

          <!-- 底部操作 -->
          <div class="bqf__footer">
            <el-button size="small" @click="handleCancel">取消</el-button>
            <el-button size="small" type="primary" :loading="saving" @click="handleConfirm">确定</el-button>
          </div>
        </div>
      </div>
    </transition>
  </teleport>
</template>

<script setup lang="ts">
/**
 * 钉钉多维表格风格的「编辑字段」弹层。
 *
 * 触发：表头右键菜单 →「编辑字段」（GridView emit edit-field）。
 * 能力：
 *  1. 修改字段名称；
 *  2. 调整字段类型（切换后属性区按新类型重置，保存时后端同步 field_type）；
 *  3. 编辑类型专属属性（复用 FieldAttributeForm，与新增字段共用一套配置模型）；
 *  4. 字段描述。
 * 保存走 editor.vue 的统一链路（updateField），本组件只负责收集与校验。
 */
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import FieldAttributeForm from './FieldAttributeForm.vue'
import { listFieldDistinctValues } from '@/api/modules/bitable'
import { OPTION_COLORS, createDefaultFieldConfig, normalizeFieldConfig, sanitizeFieldConfig } from '@/utils/bitableFieldConfig'
import type { BitableField, BitableTable, FieldConfig } from '@/types/bitable'

const props = defineProps<{
  visible: boolean
  /** 正在编辑的字段（打开时快照进表单） */
  field: BitableField | null
  fields: BitableField[]
  tables: BitableTable[]
  activeTableId: number | null
  saving?: boolean
}>()

const emit = defineEmits<{
  close: []
  /** payload 为整理后的编辑结果，由父级调 updateField 落库 */
  confirm: [data: {
    fieldId: number
    name: string
    fieldType: string
    description: string
    config: FieldConfig
  }]
}>()

interface FieldBaseModel {
  name: string
  description: string
  width: number
  required: boolean
  aiPrompt: string
}

/** 字段类型分组下拉（钉钉「编辑字段」风格：AI字段 / 常规字段 / 高级字段） */
const TYPE_GROUPS: { label: string; options: { label: string; value: string }[] }[] = [
  {
    label: 'AI 字段',
    options: [
      { label: 'AI 文本', value: 'ai_text' },
      { label: 'AI 选择', value: 'ai_select' },
    ],
  },
  {
    label: '常规字段',
    options: [
      { label: '文本', value: 'text' },
      { label: '单选', value: 'single_select' },
      { label: '多选', value: 'multi_select' },
      { label: '日期', value: 'date' },
      { label: '数字', value: 'number' },
      { label: '货币', value: 'currency' },
      { label: '人员', value: 'user' },
      { label: '图片和附件', value: 'attachment' },
      { label: '进度', value: 'progress' },
      { label: '链接', value: 'url' },
      { label: '电话', value: 'phone' },
      { label: '邮箱', value: 'email' },
      { label: '评分', value: 'rating' },
      { label: '复选框', value: 'checkbox' },
    ],
  },
  {
    label: '高级字段',
    options: [
      { label: '流程', value: 'process' },
      { label: '部门', value: 'department' },
      { label: '群组', value: 'group' },
      { label: '地理位置', value: 'location' },
      { label: '单向关联', value: 'link' },
      { label: '双向关联', value: 'bidirectional_link' },
      { label: '汇总', value: 'rollup' },
      { label: '查找引用', value: 'lookup' },
      { label: '公式', value: 'formula' },
      { label: '自动编号', value: 'auto_number' },
      { label: '条码', value: 'barcode' },
      { label: '按钮', value: 'button' },
    ],
  },
]

/** 与 GridView 表头一致的类型图标（remixicon + 品牌色），AI/附件等语义对齐钉钉下拉 */
const TYPE_ICONS: Record<string, { icon: string; color: string; label: string }> = {
  text: { icon: 'ri-text', color: 'var(--color-primary)', label: '文本' },
  number: { icon: 'ri-hashtag', color: '#E8A33D', label: '数字' },
  date: { icon: 'ri-calendar-line', color: '#50B987', label: '日期' },
  single_select: { icon: 'ri-radio-button-line', color: '#7A5AF8', label: '单选' },
  multi_select: { icon: 'ri-list-check-2', color: '#7A5AF8', label: '多选' },
  process: { icon: 'ri-flow-chart', color: '#F5A623', label: '流程' },
  user: { icon: 'ri-user-3-line', color: 'var(--color-primary)', label: '人员' },
  group: { icon: 'ri-group-line', color: 'var(--color-primary)', label: '群组' },
  department: { icon: 'ri-organization-chart', color: 'var(--color-primary)', label: '部门' },
  check: { icon: 'ri-checkbox-line', color: '#50B987', label: '复选框' },
  checkbox: { icon: 'ri-checkbox-line', color: '#50B987', label: '复选框' },
  attachment: { icon: 'ri-image-line', color: '#E8A33D', label: '图片和附件' },
  url: { icon: 'ri-external-link-line', color: '#33B8D9', label: '链接' },
  email: { icon: 'ri-mail-line', color: '#E8684A', label: '邮箱' },
  phone: { icon: 'ri-phone-line', color: '#50B987', label: '电话' },
  location: { icon: 'ri-map-pin-line', color: '#F06292', label: '地理位置' },
  currency: { icon: 'ri-currency-line', color: '#E8A33D', label: '货币' },
  progress: { icon: 'ri-dashboard-3-line', color: '#7A5AF8', label: '进度' },
  rating: { icon: 'ri-star-line', color: '#F5A623', label: '评分' },
  link: { icon: 'ri-share-forward-line', color: '#33B8D9', label: '单向关联' },
  bidirectional_link: { icon: 'ri-links-line', color: '#33B8D9', label: '双向关联' },
  rollup: { icon: 'ri-calculator-line', color: '#7A5AF8', label: '汇总' },
  lookup: { icon: 'ri-search-eye-line', color: '#7A5AF8', label: '查找引用' },
  formula: { icon: 'ri-function-line', color: '#7A5AF8', label: '公式' },
  ai_text: { icon: 'ri-magic-line', color: '#E8684A', label: 'AI 文本' },
  ai_select: { icon: 'ri-magic-line', color: '#E8684A', label: 'AI 选择' },
  auto_number: { icon: 'ri-sort-asc', color: 'var(--color-text-tertiary)', label: '自动编号' },
  barcode: { icon: 'ri-qr-code-line', color: 'var(--color-muted-text)', label: '条码' },
  button: { icon: 'ri-cursor-line', color: '#F06291', label: '按钮' },
  created_time: { icon: 'ri-time-line', color: 'var(--color-text-tertiary)', label: '创建时间' },
  modified_time: { icon: 'ri-time-line', color: 'var(--color-text-tertiary)', label: '修改时间' },
  last_modified_time: { icon: 'ri-time-line', color: 'var(--color-text-tertiary)', label: '最后更新时间' },
  created_by: { icon: 'ri-user-add-line', color: 'var(--color-text-tertiary)', label: '创建人' },
  modified_by: { icon: 'ri-user-add-line', color: 'var(--color-text-tertiary)', label: '修改人' },
  date_range: { icon: 'ri-calendar-event-line', color: '#50B987', label: '日期范围' },
}

function typeMeta(type: string) {
  return TYPE_ICONS[type] || { icon: 'ri-text', color: 'var(--color-primary)', label: type }
}

const form = reactive<{
  name: string
  fieldType: string
  base: FieldBaseModel
  config: FieldConfig
}>({
  name: '',
  fieldType: 'text',
  base: { name: '', description: '', width: 200, required: false, aiPrompt: '' },
  config: {},
})

const showDesc = ref(false)
const saving = computed(() => props.saving ?? false)
/** 弹层宽度：数字等简单类型窄一些，带选项配置的类型宽一些 */
const width = computed(() => (hasExtraAttrs.value ? 400 : 360))

const editingFieldId = computed(() => props.field?.id ?? null)

/** 当前类型元信息（选择器 label 展示） */
const currentTypeMeta = computed(() => typeMeta(form.fieldType))

/** 类型下拉是否处于搜索过滤中 */
const typeKeyword = ref('')
/** 按关键词过滤后的分组（空关键词返回全部分组；组内无匹配项则整组隐藏） */
const visibleTypeGroups = computed(() => {
  const kw = typeKeyword.value.toLowerCase()
  if (!kw) return TYPE_GROUPS
  return TYPE_GROUPS.map((group) => ({
    ...group,
    options: group.options.filter(
      (opt) => opt.label.toLowerCase().includes(kw) || opt.value.toLowerCase().includes(kw),
    ),
  })).filter((group) => group.options.length > 0)
})

function filterType(query: string) {
  typeKeyword.value = (query || '').trim()
}
function onTypeDropdownToggle(visible: boolean) {
  if (!visible) typeKeyword.value = ''
}

/** 该类型是否有专属属性可配（选项 / 数字格式 / 日期格式等） */
const hasExtraAttrs = computed(() => {
  const cfg = form.config as Record<string, unknown>
  return Object.keys(cfg).length > 0
})

/**
 * 打开时快照字段到表单。
 * config 用 normalizeFieldConfig 归一（补齐该类型默认键），属性面板才有完整控件。
 * 注意：先设 skipTypeReset 再赋 fieldType，避免「打开弹窗」被误判为「用户切类型」
 * 而把 config 重置成默认值（丢掉已保存的属性配置）。
 */
let skipTypeReset = false
watch(
  () => [props.visible, props.field?.id],
  () => {
    if (!props.visible || !props.field) return
    const field = props.field
    form.name = field.name
    skipTypeReset = true
    form.fieldType = field.fieldType
    form.base = {
      name: field.name,
      description: field.description || '',
      width: field.width || 200,
      required: Boolean(field.required),
      aiPrompt: field.aiPrompt || '',
    }
    form.config = normalizeFieldConfig(field.fieldType, field.config)
    showDesc.value = Boolean(field.description)
    // 重置类型搜索
    typeKeyword.value = ''
    // flush 掉 fieldType watcher 后再恢复用户切换监听
    nextTick(() => {
      skipTypeReset = false
    })
  },
  { immediate: true },
)

/** 切换类型：config 重置为新类型默认值（与新增字段行为一致），避免残留旧键 */
watch(
  () => form.fieldType,
  (next, prev) => {
    if (next === prev || skipTypeReset) return
    form.config = createDefaultFieldConfig(next)
    // 已有数据的字段切为单选：自动把现有值去重后预填为选项（旧值即选项 label，转换后直接对上）
    if (next === 'single_select' && prev && props.field?.id) {
      void prefillSelectOptionsFromValues(props.field)
    }
  },
)

/**
 * 字段转单选时，按现有单元格值自动生成选项。
 * 单选单元格的展示值就是选项 label，因此选项 = 去重后的现有值时，
 * 历史文本值无需迁移即成为合法选项。
 */
async function prefillSelectOptionsFromValues(field: BitableField) {
  const tableId = field.tableId || props.activeTableId
  if (!tableId || !field.id) return
  try {
    const values = await listFieldDistinctValues(tableId, field.id)
    // 拉取期间用户可能已切到其它类型，放弃回填
    if (form.fieldType !== 'single_select') return
    const labels = [...new Set(values.map((v) => (v ?? '').trim()).filter(Boolean))]
    if (!labels.length) return
    form.config.options = labels.map((label, i) => ({
      label,
      color: OPTION_COLORS[i % OPTION_COLORS.length],
    }))
    ElMessage.success(`已根据现有值自动生成 ${labels.length} 个选项，可调整后保存`)
  } catch {
    // 拉取失败保持空选项，由用户手动添加
  }
}

function handleCancel() {
  emit('close')
}

/**
 * ESC 关闭弹层（走查 P1-1：自定义弹层没有 el-dialog 的内置 ESC 行为，需自行监听）。
 * 仅在弹层可见时响应；卸载时清理监听。
 */
function onGlobalKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && props.visible) {
    e.stopPropagation()
    handleCancel()
  }
}
watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      window.addEventListener('keydown', onGlobalKeydown, true)
    } else {
      window.removeEventListener('keydown', onGlobalKeydown, true)
    }
  },
  { immediate: true },
)
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onGlobalKeydown, true)
})

function handleConfirm() {
  if (!props.field) return
  const name = form.name.trim()
  if (!name) {
    ElMessage.warning('请输入字段名称')
    return
  }
  // 同表其它字段不可重名（后端同样校验，这里即时反馈）
  if (props.fields.some((f) => f.name === name && f.id !== props.field!.id)) {
    ElMessage.warning(`字段「${name}」已存在`)
    return
  }
  // 清洗：只保留新类型允许的键
  const config = sanitizeFieldConfig(form.fieldType, form.config)
  emit('confirm', {
    fieldId: props.field.id,
    name,
    fieldType: form.fieldType,
    description: form.base.description || '',
    config,
  })
}
</script>

<style scoped lang="scss">
.bqf-overlay {
  position: fixed;
  inset: 0;
  /* z-index 必须低于 el-select popper 默认层级区间（2000+），否则下拉面板被遮住（走查 P0-1） */
  z-index: 1980;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding-top: 12vh;
  background: rgba(15, 23, 42, 0.24);
}

.bqf {
  position: relative;
  display: flex;
  flex-direction: column;
  max-height: 72vh;
  overflow: hidden;
  border-radius: var(--radius-lg, 12px);
  background: var(--color-surface, var(--color-surface));
  box-shadow: var(--shadow-2xl, 0 25px 50px -12px rgba(15, 23, 42, 0.25));

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    flex-shrink: 0;
    padding: 16px 16px 0;
    margin-bottom: 10px;
  }

  &__body {
    flex: 1 1 auto;
    min-height: 0;
    overflow-y: auto;
    padding: 0 16px;
  }

  &__header-title {
    font-size: 14px;
    font-weight: 600;
    color: var(--color-text-primary, var(--color-text-primary));
  }

  &__close {
    position: static;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 26px;
    height: 26px;
    border: none;
    border-radius: 6px;
    background: transparent;
    color: var(--color-text-secondary, var(--color-muted-text));
    cursor: pointer;
    transition: background 120ms ease, color 120ms ease;

    i {
      font-size: 17px;
    }

    &:hover {
      background: var(--color-background, var(--color-surface-alt));
      color: var(--color-text-primary, var(--color-text-primary));
    }
  }

  &__name {
    margin-bottom: 10px;

    :deep(.el-input__wrapper) {
      border-radius: var(--radius-md, 8px);
      box-shadow: 0 0 0 1px var(--color-border, #e2e8f0);

      &.is-focus {
        box-shadow: 0 0 0 2px var(--color-primary, #2563eb);
      }
    }

    :deep(.el-input__inner) {
      font-size: 15px;
      font-weight: 600;
    }
  }

  &__type {
    width: 100%;
    margin-bottom: 4px;

    :deep(.el-select__wrapper) {
      border-radius: var(--radius-md, 8px);
    }
  }

  &__type-label,
  &__type-option {
    display: inline-flex;
    align-items: center;
    gap: 8px;

    i {
      font-size: 15px;
    }
  }

  &__type-check {
    margin-left: auto;
    color: var(--color-primary, var(--color-primary));
  }

  &__attrs {
    margin-top: 10px;
    padding: 12px;
    border: 1px solid var(--color-border, var(--color-border));
    border-radius: var(--radius-md, 8px);
    background: var(--color-background, var(--color-background));

    :deep(.el-form-item) {
      margin-bottom: 12px;
    }

    :deep(.el-divider--horizontal) {
      margin: 8px 0 12px;
    }
  }

  &__desc-toggle {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    margin-top: 8px;
    font-size: 13px;
    color: var(--color-text-secondary, var(--color-muted-text));
    cursor: pointer;
    user-select: none;
    transition: color 120ms ease;

    &:hover {
      color: var(--color-primary, var(--color-primary));
    }

    i {
      font-size: 15px;
    }
  }

  &__desc-input {
    margin-top: 8px;
  }

  &__footer {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    flex-shrink: 0;
    margin-top: 14px;
    padding: 0 16px 16px;
  }
}
</style>

<style lang="scss">
/* 类型下拉 popper（teleport 到 body，需全局样式）；钉钉风格：分组标题 + 图标选项 + 选中高亮 */
.bqf-type-popper {
  /* 弹层 overlay z-index 1980，popper 必须压过它（走查 P0-1） */
  z-index: 3100 !important;

  .el-select-dropdown__item {
    height: 34px;
    line-height: 34px;
    padding: 0 12px;

    &.is-selected {
      background: var(--color-primary-subtle, var(--color-primary-subtle));
      font-weight: 500;
    }
  }

  .el-select-group__title {
    font-size: 11px;
    color: var(--color-text-placeholder, var(--color-text-tertiary));
    padding: 8px 12px 2px;
  }

  .el-select-group__wrap:not(:last-of-type)::after {
    display: none;
  }
}
</style>
