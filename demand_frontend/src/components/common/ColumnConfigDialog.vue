<template>
  <el-dialog
    :model-value="modelValue"
    title="列表字段设置"
    width="860px"
    class="column-config-dialog"
    @opened="onOpened"
    @update:model-value="emit('update:modelValue', $event)"
    @close="onClose"
  >
    <div class="column-config">
      <section class="column-config__panel column-config__panel--available">
        <div class="column-config__panel-title">备选字段</div>
        <div class="column-config__panel-body">
          <div
            v-for="group in columnGroups"
            :key="group.title"
            class="column-config__group"
          >
            <div class="column-config__group-title">{{ group.title }}</div>
            <el-checkbox-group
              :model-value="draftColumnKeys"
              class="column-config__checkbox-grid"
              @update:model-value="onToggle"
            >
              <el-checkbox
                v-for="col in group.columns"
                :key="col.key"
                :value="col.key"
                class="column-config__checkbox"
              >
                {{ col.label }}
              </el-checkbox>
            </el-checkbox-group>
          </div>
        </div>
      </section>
      <section class="column-config__panel column-config__panel--selected">
        <div class="column-config__panel-title">当前选定字段</div>
        <div ref="selectedColumnListRef" class="column-config__selected-list">
          <div
            v-for="col in draftSelectedColumns"
            :key="col.key"
            class="column-config__selected-item"
            :data-key="col.key"
          >
            <el-icon class="column-config__drag-handle"><Rank /></el-icon>
            <span class="column-config__selected-label">{{ col.label }}</span>
            <el-button
              link
              :icon="Close"
              class="column-config__remove"
              :aria-label="`移除${col.label}`"
              @click="emit('remove', col.key)"
            />
          </div>
          <el-empty
            v-if="draftSelectedColumns.length === 0"
            description="暂无选定字段"
            :image-size="72"
            class="column-config__empty"
          />
        </div>
      </section>
    </div>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" @click="emit('save')">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { Close, Rank } from '@element-plus/icons-vue'
import Sortable, { type SortableEvent } from 'sortablejs'
import { nextTick, ref } from 'vue'
import type { ColumnDef } from '@/composables/useColumnConfig'

const props = defineProps<{
  modelValue: boolean
  columnGroups: Array<{ title: string; columns: ColumnDef[] }>
  draftSelectedColumns: ColumnDef[]
  draftColumnKeys: string[]
}>()

const emit = defineEmits<{
  'update:modelValue': [val: boolean]
  'update:draftColumnKeys': [keys: string[]]
  save: []
  remove: [key: string]
}>()

const selectedColumnListRef = ref<HTMLElement>()
let selectedColumnSortable: Sortable | null = null

async function onOpened() {
  await nextTick()
  selectedColumnSortable?.destroy()
  if (!selectedColumnListRef.value) return
  selectedColumnSortable = Sortable.create(selectedColumnListRef.value, {
    animation: 150,
    handle: '.column-config__drag-handle',
    draggable: '.column-config__selected-item',
    ghostClass: 'column-config__selected-item--ghost',
    onEnd: handleColumnSortEnd,
  })
}

function onClose() {
  selectedColumnSortable?.destroy()
  selectedColumnSortable = null
}

function handleColumnSortEnd(evt: SortableEvent) {
  const { oldIndex, newIndex } = evt
  if (oldIndex === undefined || newIndex === undefined || oldIndex === newIndex) return
  const keys = [...props.draftColumnKeys]
  const [moved] = keys.splice(oldIndex, 1)
  if (!moved) return
  keys.splice(newIndex, 0, moved)
  emit('update:draftColumnKeys', keys)
}

function onToggle(keys: string[]) {
  emit('update:draftColumnKeys', [...keys])
}
</script>

<style scoped>
/* 样式由 src/styles/column-config.scss 提供（全局） */
</style>
