<template>
  <div class="gallery-view">
    <div class="gallery-header">
      <span class="gallery-header__label">图片/附件字段：</span>
      <el-select
        v-model="imageFieldId"
        placeholder="选择字段"
        style="width: 200px;"
        size="small"
        clearable
        @change="handleFieldChange"
      >
        <el-option
          v-for="f in mediaFields"
          :key="f.id"
          :label="f.name"
          :value="f.id"
        />
      </el-select>
    </div>

    <div v-if="records.length === 0" class="gallery-empty">
      <div class="gallery-empty__inner">
        <div class="gallery-empty__icon">
          <i class="ri-image-line" />
        </div>
        <p class="gallery-empty__text">画廊里空空如也</p>
        <p class="gallery-empty__hint">先到表格视图录入一些记录，画廊会自动生成</p>
      </div>
    </div>

    <div v-else-if="loading" class="gallery-loading">
      <el-icon class="is-loading" :size="32"><Loading /></el-icon>
    </div>

    <div v-else class="gallery-grid">
      <div
        v-for="record in records"
        :key="record.id"
        class="gallery-card"
        @click="handleCardClick(record)"
      >
        <div class="gallery-card-image" :class="{ 'is-placeholder': !getCardImage(record) }">
          <img
            v-if="getCardImage(record)"
            :src="getCardImage(record)"
            alt="cover"
          />
          <span v-else class="gallery-card-placeholder">{{ getRecordInitial(record) }}</span>
        </div>
        <div class="gallery-card-body">
          <div class="gallery-card-title">{{ getRecordTitle(record) }}</div>
          <div class="gallery-card-fields">
            <div
              v-for="field in displayFields.slice(0, 3)"
              :key="field.id"
              class="gallery-card-field"
            >
              <span class="field-label">{{ field.name }}</span>
              <span class="field-value">{{ formatFieldValue(record, field) }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { Picture, Loading } from '@element-plus/icons-vue'
import type { BitableField, BitableRecord, BitableTable } from '@/types/bitable'

const props = defineProps<{
  table: BitableTable | null
  fields: BitableField[]
  records: BitableRecord[]
  loading: boolean
}>()

const emit = defineEmits<{
  cardClick: [record: BitableRecord]
}>()

const imageFieldId = ref<number | null>(null)

const mediaFields = computed(() =>
  props.fields.filter((f) => f.fieldType === 'attachment')
)

const displayFields = computed(() =>
  props.fields.filter((f) => f.fieldType !== 'attachment').slice(0, 5)
)

function handleFieldChange() {
  // 字段切换无需额外处理
}

function getRecordTitle(record: BitableRecord): string {
  const textFields = props.fields.filter((f) => f.fieldType === 'text')
  if (textFields.length === 0) return `记录 ${record.id}`
  const cell = record.cells?.[textFields[0].id]
  return cell?.valueText || `记录 ${record.id}`
}

function getCardImage(record: BitableRecord): string | undefined {
  if (!imageFieldId.value) return undefined
  const cell = record.cells?.[imageFieldId.value]
  if (!cell || !cell.valueJson) return undefined
  const files = cell.valueJson as unknown
  if (Array.isArray(files) && files.length > 0) {
    const firstFile = files[0] as any
    return firstFile.url || firstFile.path || undefined
  }
  return undefined
}

// 取标题首字符作为占位（中文取首字，英文取首字母）
function getRecordInitial(record: BitableRecord): string {
  const title = getRecordTitle(record)
  if (!title) return '·'
  const first = String(title).trim().charAt(0)
  return first || '·'
}

function formatFieldValue(record: BitableRecord, field: BitableField): string {
  const cell = record.cells?.[field.id]
  if (!cell) return '-'
  if (cell.valueText) return cell.valueText
  if (cell.valueNumber !== null && cell.valueNumber !== undefined) {
    return String(cell.valueNumber)
  }
  if (cell.valueDate) return cell.valueDate
  return '-'
}

function handleCardClick(record: BitableRecord) {
  emit('cardClick', record)
}
</script>

<style scoped lang="scss">
// ===== 多维表格 GalleryView 激进风格精修（2026-08-03）=====
// 设计目标：
// 1. 卡片 14px 圆角、3:2 比例封面、hover 阴影抬起
// 2. 缺图占位：品牌渐变 + 标题首字符大写
// 3. 自定义空态：图标 + 文案 + 引导

.gallery-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.gallery-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 20px;
  border-bottom: 1px solid var(--color-border, #e2e8f0);
  background: var(--color-surface, #fff);
  flex-shrink: 0;

  .gallery-header__label {
    font-size: 13px;
    font-weight: 500;
    color: var(--color-text-secondary, #475569);
  }
}

.gallery-empty,
.gallery-loading {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

// 自定义空态：图标 + 文案 + 引导
.gallery-empty__inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 40px;
}

.gallery-empty__icon {
  width: 72px;
  height: 72px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--color-primary-subtle, #eff6ff) 0%, var(--color-accent-light, #e0e7ff) 100%);
  border-radius: 50%;
  color: var(--color-primary, #2563eb);
  font-size: 36px;
  margin-bottom: 8px;
}

.gallery-empty__text {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary, #0f172a);
  margin: 0;
}

.gallery-empty__hint {
  font-size: 12px;
  color: var(--color-text-secondary, #475569);
  margin: 0;
}

.gallery-grid {
  flex: 1;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 18px;
  padding: 20px;
  overflow-y: auto;
  align-content: start;
}

// 卡片：14px 圆角 + 3:2 封面 + hover 抬起
.gallery-card {
  background: var(--color-surface, #fff);
  border: 0.5px solid var(--color-border, #e2e8f0);
  border-radius: var(--radius-card-lg, 14px);
  overflow: hidden;
  cursor: pointer;
  transition: all 250ms var(--ease-decelerate, cubic-bezier(0, 0, 0.2, 1));

  &:hover {
    transform: translateY(-3px);
    box-shadow: var(--shadow-card-lift, 0 12px 32px -4px rgba(15, 23, 42, 0.16));
    border-color: var(--color-border-hover, #cbd5e1);
  }
}

.gallery-card-image {
  aspect-ratio: 3 / 2;
  background: var(--color-background, #f8fafc);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  position: relative;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: transform 400ms var(--ease-decelerate, cubic-bezier(0, 0, 0.2, 1));
  }

  .gallery-card:hover & img {
    transform: scale(1.04);
  }

  // 缺图占位：品牌渐变 + 首字符
  &.is-placeholder {
    background: linear-gradient(135deg, var(--color-gallery-placeholder-from, #dbeafe) 0%, var(--color-gallery-placeholder-to, #e0e7ff) 100%);
  }
}

.gallery-card-placeholder {
  font-size: 56px;
  font-weight: 700;
  color: var(--color-primary, #2563eb);
  text-shadow: 0 2px 8px rgba(37, 99, 235, 0.15);
  font-family: var(--font-family-base, 'Inter', sans-serif);
}

.gallery-card-body {
  padding: 14px 16px 16px;
}

.gallery-card-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary, #0f172a);
  margin-bottom: 10px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  letter-spacing: -0.01em;
}

.gallery-card-fields {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.gallery-card-field {
  font-size: 12px;
  display: flex;
  align-items: baseline;
  gap: 6px;

  .field-label {
    color: var(--color-text-placeholder, #94a3b8);
    font-weight: 500;
    flex-shrink: 0;
    min-width: 50px;
  }

  .field-value {
    color: var(--color-text-primary, #0f172a);
    font-weight: 500;
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}
</style>