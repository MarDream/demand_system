<template>
  <div class="widget-canvas" @click="onCanvasClick">
    <div
      v-for="row in rows"
      :key="row.id"
      class="dash-row"
      :class="{ 'dash-row--section': !!row.title }"
      :data-row-id="row.id"
      @dragover.prevent
      @drop.stop="onDrop(row, $event)"
    >
      <!-- 组合布局标题 -->
      <div v-if="row.title" class="dash-row__header">
        <template v-if="titleEditingId === row.id">
          <el-input
            v-model="titleDraft"
            size="small"
            style="width: 220px"
            @keyup.enter="commitTitle(row)"
            @blur="commitTitle(row)"
          />
        </template>
        <template v-else>
          <span class="dash-row__title" @click="editing && startTitleEdit(row)">{{ row.title }}</span>
        </template>
      </div>

      <!-- 行工具条（编辑态 hover 显示） -->
      <div v-if="editing" class="dash-row__actions">
        <el-tooltip :content="row.title ? '取消组合布局' : '设为组合布局'" placement="top">
          <button type="button" class="row-action" @click="emit('toggle-section', row.id)">
            <i :class="row.title ? 'ri-layout-masonry-line' : 'ri-layout-column-line'" />
          </button>
        </el-tooltip>
        <el-tooltip content="删除该行（组件移至上一行）" placement="top">
          <button type="button" class="row-action" @click="emit('remove-row', row.id)">
            <i class="ri-delete-bin-line" />
          </button>
        </el-tooltip>
      </div>

      <!-- 组件网格：12 列栅格 -->
      <div class="dash-row__body">
        <div
          v-for="(widgetId, idx) in row.widgets"
          :key="widgetId"
          class="widget-card"
          :class="{
            'widget-card--selected': selectedId === widgetId,
            'widget-card--editing': editing,
            'widget-card--dragging': draggingId === widgetId,
          }"
          :style="{ gridColumn: `span ${widgetLayout(widgetId).span}`, height: widgetLayout(widgetId).height + 'px' }"
          :draggable="editing"
          @dragstart="onDragStart(widgetId, $event)"
          @dragend="draggingId = null"
          @click.stop="emit('select', widgetId)"
        >
          <div class="widget-card__header">
            <span class="widget-card__title" :title="widgetTitle(widgetId)">{{ widgetTitle(widgetId) }}</span>
            <div class="widget-card__actions" @click.stop>
              <el-tooltip v-if="editing" content="编辑图表" placement="top">
                <button type="button" class="card-action" @click="emit('action', { type: 'edit', widgetId })">
                  <i class="ri-edit-line" />
                </button>
              </el-tooltip>
              <el-dropdown trigger="click" @command="(cmd: string) => emit('action', { type: cmd, widgetId })">
                <button type="button" class="card-action"><i class="ri-more-fill" /></button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-if="editing" command="edit"><i class="ri-settings-3-line menu-ic" />编辑图表</el-dropdown-item>
                    <el-dropdown-item v-if="editing" command="rename" divided><i class="ri-edit-2-line menu-ic" />重命名</el-dropdown-item>
                    <el-dropdown-item v-if="editing" command="copy"><i class="ri-file-copy-line menu-ic" />复制</el-dropdown-item>
                    <el-dropdown-item command="export"><i class="ri-image-line menu-ic" />导出为图片</el-dropdown-item>
                    <el-dropdown-item v-if="editing" command="delete" divided><i class="ri-delete-bin-line menu-ic" />删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </div>
          <div class="widget-card__body">
            <WidgetRenderer :widget="widgetById(widgetId)!" :data="dataMap[String(widgetId)]" />
          </div>

          <!-- 尺寸调整手柄（编辑态） -->
          <template v-if="editing">
            <span class="widget-card__resize-x" @mousedown.stop.prevent="(e) => startResize(e, widgetId, 'span')" />
            <span class="widget-card__resize-xy" @mousedown.stop.prevent="(e) => startResize(e, widgetId, 'both')" />
            <span class="widget-card__resize-y" @mousedown.stop.prevent="(e) => startResize(e, widgetId, 'height')" />
          </template>
          <span class="widget-card__index-tag" v-if="editing">{{ idx + 1 }}</span>
        </div>

        <div v-if="editing && !row.widgets.length" class="dash-row__placeholder">
          <i class="ri-drag-drop-line" />拖拽组件到此处
        </div>
      </div>

      <!-- 移入图表 -->
      <div v-if="editing" class="dash-row__add">
        <WidgetPalette @select="(type: string) => emit('add-widget', { rowId: row.id, type })">
          <button type="button" class="dash-row__add-btn" @click.stop>
            <i class="ri-add-line" /> 移入图表
          </button>
        </WidgetPalette>
      </div>
    </div>

    <div v-if="editing" class="canvas-footer">
      <el-button plain size="small" @click="emit('add-row')">
        <el-icon><Plus /></el-icon>&nbsp;添加组合布局
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import WidgetRenderer from './WidgetRenderer.vue'
import WidgetPalette from './WidgetPalette.vue'
import type { DashboardRow, WidgetInstance } from './widgetDefs'
import { getWidgetDef } from './widgetDefs'

const props = defineProps<{
  rows: DashboardRow[]
  widgets: WidgetInstance[]
  dataMap: Record<string, any>
  editing: boolean
  selectedId: number | string | null
}>()

const emit = defineEmits<{
  (e: 'select', widgetId: number | string): void
  (e: 'action', payload: { type: string; widgetId: number | string }): void
  (e: 'move-widget', payload: { widgetId: number | string; rowId: string; index: number }): void
  (e: 'add-widget', payload: { rowId: string; type: string }): void
  (e: 'update-row-title', payload: { rowId: string; title: string }): void
  (e: 'toggle-section', rowId: string): void
  (e: 'remove-row', rowId: string): void
  (e: 'add-row'): void
  (e: 'update-layout', payload: { widgetId: number | string; span?: number; height?: number }): void
}>()

function widgetById(id: number | string): WidgetInstance | undefined {
  return props.widgets.find((w) => w.id === id)
}
function widgetLayout(id: number | string) {
  const w = widgetById(id)
  return { span: w?.layoutConfig?.span ?? 3, height: w?.layoutConfig?.height ?? 240 }
}
function widgetTitle(id: number | string): string {
  const w = widgetById(id)
  if (!w) return ''
  return w.title || getWidgetDef(w.type).label
}

// ---- 拖拽移动 ----
const draggingId = ref<number | string | null>(null)
function onDragStart(widgetId: number | string, e: DragEvent) {
  draggingId.value = widgetId
  e.dataTransfer?.setData('text/widget-id', String(widgetId))
  if (e.dataTransfer) e.dataTransfer.effectAllowed = 'move'
}
function onDrop(row: DashboardRow, e: DragEvent) {
  const raw = e.dataTransfer?.getData('text/widget-id')
  if (!raw) return
  draggingId.value = null
  const widgetId: number | string = raw.startsWith('tmp_') ? raw : Number(raw)
  if (!props.widgets.some((w) => w.id === widgetId)) return
  emit('move-widget', { widgetId, rowId: row.id, index: dropIndex(e, row) })
}
/** 根据鼠标位置计算落点：命中卡片左半→插到其前，否则追加到行尾 */
function dropIndex(e: DragEvent, row: DashboardRow): number {
  const rowEl = document.querySelector(`[data-row-id="${row.id}"] .dash-row__body`)
  if (!rowEl) return row.widgets.length
  const cards = Array.from(rowEl.querySelectorAll(':scope > .widget-card')) as HTMLElement[]
  for (let i = 0; i < cards.length; i++) {
    const rect = cards[i].getBoundingClientRect()
    const insideY = e.clientY >= rect.top - 6 && e.clientY <= rect.bottom + 6
    if (insideY && e.clientX < rect.left + rect.width / 2) return i
  }
  return row.widgets.length
}

// ---- 行标题编辑 ----
const titleEditingId = ref<string | null>(null)
const titleDraft = ref('')
function startTitleEdit(row: DashboardRow) {
  titleEditingId.value = row.id
  titleDraft.value = row.title || ''
}
function commitTitle(row: DashboardRow) {
  if (titleEditingId.value !== row.id) return
  titleEditingId.value = null
  const title = titleDraft.value.trim()
  if (title && title !== row.title) emit('update-row-title', { rowId: row.id, title })
}

// ---- 尺寸调整 ----
function startResize(e: MouseEvent, widgetId: number | string, mode: 'span' | 'height' | 'both') {
  const cardEl = (e.target as HTMLElement).closest('.widget-card') as HTMLElement
  const bodyEl = cardEl?.parentElement as HTMLElement
  if (!cardEl || !bodyEl) return
  const colWidth = bodyEl.clientWidth / 12
  const startX = e.clientX
  const startY = e.clientY
  const startSpan = widgetLayout(widgetId).span
  const startHeight = widgetLayout(widgetId).height

  const onMove = (ev: MouseEvent) => {
    const payload: { widgetId: number | string; span?: number; height?: number } = { widgetId }
    if (mode === 'span' || mode === 'both') {
      payload.span = Math.max(1, Math.min(12, Math.round(startSpan + (ev.clientX - startX) / colWidth)))
    }
    if (mode === 'height' || mode === 'both') {
      payload.height = Math.max(80, Math.min(1200, Math.round((startHeight + ev.clientY - startY) / 10) * 10))
    }
    emit('update-layout', payload)
  }
  const onUp = () => {
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
    document.body.style.cursor = ''
    emit('update-layout', { widgetId })
  }
  document.body.style.cursor = 'ew-resize'
  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
}

function onCanvasClick() {
  // 点击空白处取消选中（由父组件根据 selectedId 处理）
  emit('select', '')
}
</script>

<style scoped lang="scss">
.widget-canvas {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 4px 4px 24px;
}

.dash-row {
  position: relative;
  border-radius: var(--radius-lg, 14px);
  padding: 8px;

  &--section {
    border: 1px solid var(--color-border);
    background: var(--color-surface);
    padding: 12px;
  }

  &__header {
    display: flex;
    align-items: center;
    margin-bottom: 8px;
  }

  &__title {
    font-size: 14px;
    font-weight: 600;
    color: var(--color-text-primary);
    cursor: default;

    .widget-canvas & { cursor: default; }
  }

  .dash-row--section .dash-row__title { cursor: pointer; }

  &__actions {
    position: absolute;
    top: 10px;
    right: 12px;
    display: none;
    gap: 4px;
    z-index: 3;
  }

  &:hover .dash-row__actions { display: flex; }

  &__body {
    display: grid;
    grid-template-columns: repeat(12, 1fr);
    gap: 12px;
    min-height: 80px;
  }

  &__placeholder {
    grid-column: 1 / -1;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
    height: 96px;
    border: 1px dashed var(--color-border);
    border-radius: var(--radius-lg, 12px);
    color: var(--color-text-tertiary);
    font-size: 13px;
  }

  &__add {
    display: flex;
    justify-content: center;
    padding: 6px 0 2px;
    opacity: 0;
    transition: opacity 0.15s ease;
  }

  &:hover .dash-row__add { opacity: 1; }

  &__add-btn {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    border: none;
    background: var(--color-fill-secondary);
    color: var(--color-text-secondary);
    font-size: 12px;
    padding: 4px 12px;
    border-radius: 999px;
    cursor: pointer;

    &:hover { background: var(--color-fill-tertiary); color: var(--color-primary); }
  }
}

.row-action {
  width: 26px;
  height: 26px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  color: var(--color-text-secondary);
  border-radius: var(--radius-md, 8px);
  cursor: pointer;
  font-size: 14px;

  &:hover { color: var(--color-primary); border-color: var(--color-primary); }
}

.widget-card {
  position: relative;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg, 14px);
  background: var(--color-surface);
  min-width: 0;
  transition: box-shadow 0.15s ease, border-color 0.15s ease;

  &--selected {
    border-color: var(--color-primary);
    box-shadow: 0 0 0 2px color-mix(in srgb, var(--color-primary) 25%, transparent);
  }

  &--dragging { opacity: 0.4; }

  &--editing:hover {
    border-color: var(--color-primary);
  }

  &__header {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 12px 0;
  }

  &__title {
    flex: 1;
    font-size: 13px;
    font-weight: 600;
    color: var(--color-text-primary);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__actions {
    display: none;
    align-items: center;
    gap: 2px;
  }

  &:hover .widget-card__actions { display: inline-flex; }

  &__body {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
  }

  .card-action {
    width: 24px;
    height: 24px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    border: 1px solid var(--color-border);
    background: var(--color-surface);
    color: var(--color-text-secondary);
    border-radius: var(--radius-md, 8px);
    cursor: pointer;
    font-size: 14px;

    &:hover { color: var(--color-primary); border-color: var(--color-primary); }
  }

  &__resize-x {
    position: absolute;
    top: 8px;
    bottom: 8px;
    right: -5px;
    width: 10px;
    cursor: col-resize;
    z-index: 4;
  }

  &__resize-y {
    position: absolute;
    left: 8px;
    right: 8px;
    bottom: -5px;
    height: 10px;
    cursor: row-resize;
    z-index: 4;
  }

  &__resize-xy {
    position: absolute;
    right: -3px;
    bottom: -3px;
    width: 14px;
    height: 14px;
    border-right: 3px solid var(--color-primary);
    border-bottom: 3px solid var(--color-primary);
    border-radius: 0 0 6px 0;
    cursor: nwse-resize;
    opacity: 0;
    z-index: 5;
  }

  &:hover .widget-card__resize-xy { opacity: 0.9; }

  &__index-tag {
    position: absolute;
    left: -7px;
    top: -7px;
    width: 18px;
    height: 18px;
    border-radius: 50%;
    background: var(--color-primary);
    color: #fff;
    font-size: 11px;
    display: none;
    align-items: center;
    justify-content: center;
    z-index: 5;
  }

  &--editing:hover .widget-card__index-tag { display: inline-flex; }
}

.menu-ic {
  margin-right: 6px;
}

.canvas-footer {
  display: flex;
  justify-content: center;
  padding-top: 4px;
}
</style>
