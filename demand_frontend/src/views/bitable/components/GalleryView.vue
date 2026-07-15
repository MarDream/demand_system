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
      <el-empty description="暂无记录" />
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
        <div class="gallery-card-image">
          <img
            v-if="getCardImage(record)"
            :src="getCardImage(record)"
            alt="cover"
          />
          <el-icon v-else class="gallery-card-placeholder"><Picture /></el-icon>
        </div>
        <div class="gallery-card-body">
          <div class="gallery-card-title">{{ getRecordTitle(record) }}</div>
          <div class="gallery-card-fields">
            <div
              v-for="field in displayFields.slice(0, 3)"
              :key="field.id"
              class="gallery-card-field"
            >
              <span class="field-label">{{ field.name }}:</span>
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
.gallery-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.gallery-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-surface);
  flex-shrink: 0;

  .gallery-header__label {
    font-size: var(--font-size-sm);
    color: var(--color-text-secondary);
  }
}

.gallery-empty,
.gallery-loading {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.gallery-grid {
  flex: 1;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
  padding: 16px;
  overflow-y: auto;
  align-content: start;
}

.gallery-card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  transition: box-shadow 0.2s;

  &:hover {
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  }
}

.gallery-card-image {
  height: 160px;
  background: var(--color-background);
  display: flex;
  align-items: center;
  justify-content: center;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  .gallery-card-placeholder {
    font-size: 48px;
    color: var(--color-text-placeholder);
  }
}

.gallery-card-body {
  padding: 12px;
}

.gallery-card-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin-bottom: 8px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.gallery-card-fields {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.gallery-card-field {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);

  .field-label {
    color: var(--color-text-placeholder);
  }

  .field-value {
    margin-left: 4px;
  }
}
</style>