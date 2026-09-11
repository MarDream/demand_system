<template>
  <el-dialog
    :model-value="modelValue"
    title="分组"
    width="420px"
    append-to-body
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="group-panel">
      <div class="group-panel__current" v-if="currentFieldName">
        <span class="group-panel__label">当前分组：</span>
        <el-tag size="small">{{ currentFieldName }}</el-tag>
        <el-button link size="small" type="danger" @click="handleClear" style="margin-left: 8px">清除分组</el-button>
      </div>

      <el-divider v-if="currentFieldName" />

      <div class="group-panel__fields">
        <div class="group-panel__section-title">选择分组字段</div>
        <el-select
          v-model="selectedFieldId"
          placeholder="选择分组字段"
          size="default"
          style="width: 100%"
          filterable
          clearable
        >
          <el-option
            v-for="field in groupableFields"
            :key="field.id"
            :label="field.name"
            :value="field.id"
          />
        </el-select>
      </div>
    </div>

    <template #footer>
      <el-button size="small" @click="emit('update:modelValue', false)">取消</el-button>
      <el-button size="small" type="primary" @click="handleApply">应用</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import type { BitableField } from '@/types/bitable'

const props = defineProps<{
  modelValue: boolean
  fields: BitableField[]
  groupFieldId: number | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  apply: [fieldId: number | null]
}>()

const selectedFieldId = ref<number | null>(null)

// 可分组字段：排除附件/按钮等不适合分组的类型
const groupableFields = computed(() =>
  props.fields.filter((f) =>
    !['attachment', 'button', 'formula', 'lookup', 'rollup', 'link', 'bidirectional_link'].includes(f.fieldType),
  ),
)

const currentFieldName = computed(() => {
  if (!props.groupFieldId) return null
  return props.fields.find((f) => f.id === props.groupFieldId)?.name ?? null
})

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      selectedFieldId.value = props.groupFieldId
    }
  },
)

function handleApply() {
  emit('apply', selectedFieldId.value)
  emit('update:modelValue', false)
}

function handleClear() {
  emit('apply', null)
  emit('update:modelValue', false)
}
</script>

<style scoped lang="scss">
.group-panel {
  &__current {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 6px;
  }

  &__label {
    font-size: 13px;
    color: var(--color-text-secondary);
  }

  &__fields {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  &__section-title {
    font-size: 13px;
    color: var(--color-text-secondary);
  }
}
</style>
