<template>
  <el-form :model="internalValue" inline class="requirement-filter">
    <el-form-item label="需求类型">
      <el-select
        v-model="internalValue.type"
        placeholder="全部"
        clearable
        style="width: 140px"
        @change="emitChange"
      >
        <el-option label="功能" value="功能" />
        <el-option label="优化" value="优化" />
        <el-option label="Bug" value="Bug" />
        <el-option label="技术债务" value="技术债务" />
        <el-option label="运营" value="运营" />
      </el-select>
    </el-form-item>

    <el-form-item label="优先级">
      <el-select
        v-model="internalValue.priority"
        placeholder="全部"
        clearable
        style="width: 100px"
        @change="emitChange"
      >
        <el-option label="P0" value="P0" />
        <el-option label="P1" value="P1" />
        <el-option label="P2" value="P2" />
        <el-option label="P3" value="P3" />
      </el-select>
    </el-form-item>

    <el-form-item label="状态">
      <el-select
        v-model="internalValue.status"
        placeholder="全部"
        clearable
        style="width: 120px"
        @change="emitChange"
      >
        <el-option label="新建" value="新建" />
        <el-option label="待评审" value="待评审" />
        <el-option label="评审中" value="评审中" />
        <el-option label="已通过" value="已通过" />
        <el-option label="开发中" value="开发中" />
        <el-option label="测试中" value="测试中" />
        <el-option label="已上线" value="已上线" />
        <el-option label="已验收" value="已验收" />
      </el-select>
    </el-form-item>

    <el-form-item label="关键词">
      <el-input
        v-model="internalValue.keyword"
        placeholder="请输入关键词"
        clearable
        style="width: 200px"
        @keyup.enter="handleSearch"
      />
    </el-form-item>

    <el-form-item>
      <el-button type="primary" @click="handleSearch">搜索</el-button>
      <el-button @click="handleReset">重置</el-button>
    </el-form-item>
  </el-form>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue'

interface FilterValue {
  type?: string
  priority?: string
  status?: string
  keyword?: string
}

const props = defineProps<{
  modelValue?: FilterValue
}>()

const emit = defineEmits<{
  'update:modelValue': [value: FilterValue]
  search: []
  reset: []
}>()

const internalValue = reactive<FilterValue>({
  type: props.modelValue?.type || '',
  priority: props.modelValue?.priority || '',
  status: props.modelValue?.status || '',
  keyword: props.modelValue?.keyword || '',
})

watch(
  () => props.modelValue,
  (val) => {
    if (val) {
      internalValue.type = val.type || ''
      internalValue.priority = val.priority || ''
      internalValue.status = val.status || ''
      internalValue.keyword = val.keyword || ''
    }
  },
  { deep: true },
)

function emitChange() {
  emit('update:modelValue', { ...internalValue })
}

function handleSearch() {
  emitChange()
  emit('search')
}

function handleReset() {
  internalValue.type = ''
  internalValue.priority = ''
  internalValue.status = ''
  internalValue.keyword = ''
  emit('update:modelValue', { ...internalValue })
  emit('reset')
}
</script>

<style scoped>
.requirement-filter {
  margin: 0;
}
</style>
