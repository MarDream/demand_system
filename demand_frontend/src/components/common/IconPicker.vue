<template>
  <div ref="rootRef" class="icon-picker-wrapper">
    <el-popover
      v-model:visible="visible"
      :width="520"
      trigger="manual"
      placement="bottom-start"
      :teleported="false"
      :persistent="false"
    >
      <template #reference>
        <el-input
          :model-value="modelValue"
          placeholder="选择图标"
          readonly
          style="cursor: pointer"
          @click="visible = !visible"
        >
        <template #prefix>
          <template v-if="modelValue">
            <i v-if="isRemixIcon(modelValue)" :class="modelValue" style="margin-right: 4px; font-size: 16px;" />
            <el-icon v-else style="margin-right: 4px">
              <component :is="iconComponent" />
            </el-icon>
          </template>
        </template>
      </el-input>
    </template>

    <div class="icon-picker">
      <el-input v-model="keyword" placeholder="搜索图标..." clearable size="small" class="icon-picker-search" />

      <div class="icon-picker-tabs">
        <span
          class="icon-picker-tab"
          :class="{ active: activeSource === 'element' }"
          @click="activeSource = 'element'"
        >Element Plus</span>
        <span
          class="icon-picker-tab"
          :class="{ active: activeSource === 'remix' }"
          @click="activeSource = 'remix'"
        >Remix Icon</span>
      </div>

      <div v-if="activeSource === 'element'" class="icon-picker-tabs icon-picker-sub-tabs">
        <span
          v-for="cat in elementCategories"
          :key="cat.label"
          class="icon-picker-tab"
          :class="{ active: activeCategory === cat.label }"
          @click="activeCategory = cat.label"
        >{{ cat.label }}</span>
      </div>

      <div v-if="activeSource === 'remix'" class="icon-picker-tabs icon-picker-sub-tabs">
        <span
          v-for="cat in remixCategories"
          :key="cat.label"
          class="icon-picker-tab"
          :class="{ active: activeRemixCategory === cat.label }"
          @click="activeRemixCategory = cat.label"
        >{{ cat.label }}</span>
      </div>

      <div class="icon-picker-grid" :class="{ 'icon-picker-grid--remix': activeSource === 'remix' }">
        <template v-if="activeSource === 'element'">
          <div
            v-for="name in filteredElementIcons"
            :key="name"
            class="icon-picker-item"
            :class="{ selected: name === modelValue }"
            :title="name"
            @click="handleSelect(name)"
          >
            <el-icon :size="20"><component :is="elementIconMap[name]" /></el-icon>
            <span class="icon-picker-item-name">{{ name }}</span>
          </div>
        </template>
        <template v-else>
          <div
            v-for="name in filteredRemixIcons"
            :key="name"
            class="icon-picker-item"
            :class="{ selected: name === modelValue }"
            :title="name"
            @click="handleSelect(name)"
          >
            <i :class="name" style="font-size: 20px;" />
            <span class="icon-picker-item-name">{{ name.replace('ri-', '').replace(/-line$/, '').replace(/-fill$/, '') }}</span>
          </div>
        </template>
        <el-empty v-if="currentFilteredIcons.length === 0" description="无匹配图标" :image-size="60" />
      </div>

      <div class="icon-picker-footer">
        <span class="icon-picker-count">共 {{ currentFilteredIcons.length }} 个图标</span>
        <el-button v-if="modelValue" size="small" text type="danger" @click="handleClear">清除</el-button>
      </div>
    </div>
  </el-popover>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, type Component } from 'vue'
import * as ElementPlusIcons from '@element-plus/icons-vue'
import { iconCategories } from './IconPickerData'
import { remixIconCategories, isRemixIcon } from './RemixIconData'

const props = defineProps<{
  modelValue: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const visible = ref(false)
const rootRef = ref<HTMLElement>()

function handleOutsideClick(e: MouseEvent) {
  if (!visible.value) return
  const el = rootRef.value
  if (el && !el.contains(e.target as Node)) {
    visible.value = false
  }
}

onMounted(() => {
  document.addEventListener('mousedown', handleOutsideClick)
})

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', handleOutsideClick)
})
const keyword = ref('')
const activeSource = ref<'element' | 'remix'>('element')
const activeCategory = ref('全部')
const activeRemixCategory = ref('全部')

const elementIconMap: Record<string, Component> = {}
for (const [name, comp] of Object.entries(ElementPlusIcons)) {
  elementIconMap[name] = comp as Component
}

const elementCategories = computed(() => [
  { label: '全部', icons: Object.keys(elementIconMap).sort() },
  ...iconCategories,
])

const remixCategories = computed(() => [
  { label: '全部', icons: getAllRemixNames() },
  ...remixIconCategories,
])

function getAllRemixNames(): string[] {
  const names: string[] = []
  remixIconCategories.forEach(c => c.icons.forEach(i => names.push(i)))
  return names
}

const iconComponent = computed(() => elementIconMap[props.modelValue] || null)

const filteredElementIcons = computed(() => {
  const cat = elementCategories.value.find(c => c.label === activeCategory.value)
  if (!cat) return []
  let icons = cat.icons
  if (keyword.value.trim()) {
    const lower = keyword.value.toLowerCase()
    icons = icons.filter(n => n.toLowerCase().includes(lower))
  }
  return icons
})

const filteredRemixIcons = computed(() => {
  const cat = remixCategories.value.find(c => c.label === activeRemixCategory.value)
  if (!cat) return []
  let icons = cat.icons
  if (keyword.value.trim()) {
    const lower = keyword.value.toLowerCase()
    icons = icons.filter(n => n.toLowerCase().includes(lower))
  }
  return icons
})

const currentFilteredIcons = computed(() =>
  activeSource.value === 'element' ? filteredElementIcons.value : filteredRemixIcons.value
)

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
.icon-picker-wrapper {
  width: 100%;
}
.icon-picker {
  max-height: 440px;
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
  margin-bottom: 4px;
  padding-bottom: 4px;
  border-bottom: 1px solid var(--color-border);
}
.icon-picker-sub-tabs {
  border-bottom: none;
  margin-bottom: 8px;
}
.icon-picker-tab {
  padding: 2px 8px;
  font-size: 12px;
  border-radius: 4px;
  cursor: pointer;
  color: var(--color-text-secondary);
  white-space: nowrap;
  transition: all 0.15s;
}
.icon-picker-tab:hover {
  background: var(--color-surface-alt);
}
.icon-picker-tab.active {
  background: var(--color-accent);
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
.icon-picker-grid--remix {
  grid-template-columns: repeat(7, 1fr);
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
  background: var(--color-info-light);
}
.icon-picker-item.selected {
  background: var(--color-accent);
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
  border-top: 1px solid var(--color-border);
}
.icon-picker-count {
  font-size: 12px;
  color: var(--color-muted-text);
}
</style>
