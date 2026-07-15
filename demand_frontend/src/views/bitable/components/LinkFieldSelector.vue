<template>
  <el-dialog
    v-model="internalVisible"
    title="选择关联记录"
    width="600px"
    @close="handleClose"
  >
    <div class="link-selector">
      <el-input
        v-model="keyword"
        placeholder="搜索记录..."
        clearable
        size="small"
        style="margin-bottom: 12px;"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>

      <el-table
        :data="filteredRecords"
        style="width: 100%"
        max-height="400"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="50" />
        <el-table-column prop="title" label="记录标题" />
      </el-table>
    </div>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" @click="handleConfirm">确认关联</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { Search } from '@element-plus/icons-vue'
import type { BitableRecord } from '@/types/bitable'
import { listRecords } from '@/api/modules/bitable'

const props = defineProps<{
  visible: boolean
  targetTableId: number | null | undefined
  selectedIds: number[]
}>()

const emit = defineEmits<{
  confirm: [ids: number[]]
  close: []
}>()

const internalVisible = ref(props.visible)
const keyword = ref('')
const records = ref<BitableRecord[]>([])
const selectedRows = ref<BitableRecord[]>([])

watch(() => props.visible, (v) => {
  internalVisible.value = v
  if (v && props.targetTableId) {
    loadRecords()
  }
})

async function loadRecords() {
  if (!props.targetTableId) return
  try {
    const result = await listRecords(props.targetTableId, { pageNum: 1, pageSize: 100 })
    records.value = result.list
  } catch {
    records.value = []
  }
}

const filteredRecords = computed(() => {
  if (!keyword.value) return records.value
  const kw = keyword.value.toLowerCase()
  return records.value.filter((r) => {
    const title = (r.cells && Object.values(r.cells)[0]?.valueText) || ''
    return title.toLowerCase().includes(kw)
  })
})

function handleSelectionChange(rows: BitableRecord[]) {
  selectedRows.value = rows
}

function handleConfirm() {
  const ids = selectedRows.value.map((r) => r.id)
  emit('confirm', ids)
  internalVisible.value = false
}

function handleClose() {
  emit('close')
  internalVisible.value = false
}
</script>

<style scoped lang="scss">
.link-selector {
  width: 100%;
}
</style>