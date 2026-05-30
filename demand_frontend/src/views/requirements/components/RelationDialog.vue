<template>
  <el-dialog
    v-model="dialogVisible"
    title="添加关联需求"
    width="600px"
  >
    <div class="relation-search">
      <el-input
        v-model="localSearchText"
        placeholder="搜索需求标题..."
        clearable
        :prefix-icon="Search"
      />
    </div>
    <el-table
      :data="filteredRequirements"
      size="small"
      @selection-change="handleSelectionChange"
      class="relation-select-table"
    >
      <el-table-column type="selection" width="40" />
      <el-table-column prop="title" label="标题" />
      <el-table-column prop="type" label="类型" width="100">
        <template #default="{ row }">
          <el-tag size="small">{{ row.type }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100" />
    </el-table>
    <template #footer>
      <el-button @click="handleCancel">取消</el-button>
      <el-button type="primary" @click="handleConfirm">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { Search } from '@element-plus/icons-vue'

export interface RequirementOption {
  id: number
  title: string
  type?: string | null
  status?: string | null
}

const props = defineProps<{
  visible: boolean
  requirements: RequirementOption[]
  selected: RequirementOption[]
  searchText: string
  excludeIds: number[]
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  'update:searchText': [value: string]
  'update:selected': [value: RequirementOption[]]
  confirm: []
  cancel: []
}>()

const localSearchText = computed({
  get: () => props.searchText,
  set: (val) => emit('update:searchText', val),
})

const localSelected = ref<RequirementOption[]>([...props.selected])

watch(() => props.selected, (val) => {
  localSelected.value = [...val]
})

const dialogVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val),
})

const filteredRequirements = computed(() => {
  const candidates = props.requirements.filter((req) => {
    if (props.excludeIds.includes(req.id)) {
      return false
    }
    return true
  })
  if (!localSearchText.value) return candidates
  return candidates.filter(r =>
    r.title.toLowerCase().includes(localSearchText.value.toLowerCase())
  )
})

function handleSelectionChange(val: RequirementOption[]) {
  localSelected.value = val
}

function handleConfirm() {
  emit('update:selected', [...localSelected.value])
  emit('confirm')
}

function handleCancel() {
  emit('cancel')
}
</script>

<style scoped>
.relation-search {
  margin-bottom: 16px;
}

.relation-select-table {
  max-height: 400px;
  overflow-y: auto;
}
</style>
