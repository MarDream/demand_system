<template>
  <el-popover :width="480" trigger="click" :visible="visible" @update:visible="$emit('update:visible', $event)">
    <template #reference>
      <el-input
        :model-value="modelValue"
        placeholder="选择图标"
        readonly
        style="cursor: pointer"
        @click="visible = true"
      >
        <template #prefix>
          <el-icon v-if="modelValue" style="margin-right: 4px">
            <component :is="iconComponent" />
          </el-icon>
        </template>
      </el-input>
    </template>

    <div class="icon-picker">
      <el-input v-model="keyword" placeholder="搜索图标..." clearable size="small" class="icon-picker-search" />

      <div class="icon-picker-tabs">
        <span
          v-for="cat in categories"
          :key="cat.label"
          class="icon-picker-tab"
          :class="{ active: activeCategory === cat.label }"
          @click="activeCategory = cat.label"
        >{{ cat.label }}</span>
      </div>

      <div class="icon-picker-grid">
        <div
          v-for="name in filteredIcons"
          :key="name"
          class="icon-picker-item"
          :class="{ selected: name === modelValue }"
          :title="name"
          @click="handleSelect(name)"
        >
          <el-icon :size="20"><component :is="iconMap[name]" /></el-icon>
          <span class="icon-picker-item-name">{{ name }}</span>
        </div>
        <el-empty v-if="filteredIcons.length === 0" description="无匹配图标" :image-size="60" />
      </div>

      <div class="icon-picker-footer">
        <span class="icon-picker-count">共 {{ filteredIcons.length }} 个图标</span>
        <el-button v-if="modelValue" size="small" text type="danger" @click="handleClear">清除</el-button>
      </div>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import { ref, computed, type Component } from 'vue'
import * as ElementPlusIcons from '@element-plus/icons-vue'
import { iconCategories } from './IconPickerData'

const props = defineProps<{
  modelValue: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'update:visible': [value: boolean]
}>()

const visible = ref(false)
const keyword = ref('')
const activeCategory = ref('全部')

const iconMap: Record<string, Component> = {}
for (const [name, comp] of Object.entries(ElementPlusIcons)) {
  iconMap[name] = comp as Component
}

const categories = computed(() => [
  { label: '全部', icons: Object.keys(iconMap).sort() },
  ...iconCategories,
])

const iconComponent = computed(() => iconMap[props.modelValue] || null)

const filteredIcons = computed(() => {
  const cat = categories.value.find(c => c.label === activeCategory.value)
  if (!cat) return []
  let icons = cat.icons
  if (keyword.value.trim()) {
    const lower = keyword.value.toLowerCase()
    icons = icons.filter(n => n.toLowerCase().includes(lower))
  }
  return icons
})

function handleSelect(name: string) {
  emit('update:modelValue', name)
  visible.value = false
}

function handleClear() {
  emit('update:modelValue', '')
  visible.value = false
}
</script>

<style scoped>
.icon-picker {
  max-height: 420px;
  display: flex;
  flex-direction: column;
}
.icon-picker-search {
  margin-bottom: 8px;
}
.icon-picker-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 8px;
  padding-bottom: 8px;
  border-bottom: 1px solid #ebeef5;
}
.icon-picker-tab {
  padding: 2px 8px;
  font-size: 12px;
  border-radius: 4px;
  cursor: pointer;
  color: #606266;
  white-space: nowrap;
}
.icon-picker-tab:hover {
  background: #f0f2f5;
}
.icon-picker-tab.active {
  background: #409eff;
  color: #fff;
}
.icon-picker-grid {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 4px;
  max-height: 280px;
  overflow-y: auto;
  padding: 4px 0;
}
.icon-picker-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 6px 2px;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.15s;
}
.icon-picker-item:hover {
  background: #ecf5ff;
}
.icon-picker-item.selected {
  background: #409eff;
  color: #fff;
}
.icon-picker-item-name {
  font-size: 9px;
  margin-top: 2px;
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  width: 100%;
}
.icon-picker-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 8px;
  border-top: 1px solid #ebeef5;
}
.icon-picker-count {
  font-size: 12px;
  color: #909399;
}
</style>
