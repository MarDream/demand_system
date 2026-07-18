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
        ref="tableRef"
        :data="filteredRecords"
        row-key="id"
        style="width: 100%"
        max-height="400"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="50" reserve-selection />
        <el-table-column label="记录标题">
          <template #default="{ row }">
            {{ recordTitle(row) }}
          </template>
        </el-table-column>
      </el-table>
    </div>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" @click="handleConfirm">确认关联</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { Search } from '@element-plus/icons-vue'
import type { BitableRecord } from '@/types/bitable'
import { listLinkableRecords } from '@/api/modules/bitable'

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
const tableRef = ref<any>(null)

watch(() => props.visible, (v) => {
  internalVisible.value = v
  if (v && props.targetTableId) {
    loadRecords()
  } else {
    selectedRows.value = []
  }
})

watch(() => props.selectedIds, () => {
  if (internalVisible.value) syncSelectedRows()
})

watch(keyword, () => {
  if (internalVisible.value && props.targetTableId) loadRecords()
})

async function loadRecords() {
  if (!props.targetTableId) return
  try {
    records.value = await listLinkableRecords(props.targetTableId, { keyword: keyword.value || undefined, pageSize: 100 })
    await syncSelectedRows()
  } catch {
    records.value = []
  }
}

const filteredRecords = computed(() => {
  if (!keyword.value) return records.value
  const kw = keyword.value.toLowerCase()
  return records.value.filter((r) => recordTitle(r).toLowerCase().includes(kw))
})

function recordTitle(record: BitableRecord) {
  const firstCell = record.cells ? Object.values(record.cells)[0] : undefined
  return firstCell?.valueText || String(firstCell?.valueNumber ?? firstCell?.valueDate ?? record.id)
}

async function syncSelectedRows() {
  await nextTick()
  tableRef.value?.clearSelection?.()
  const selected = records.value.filter((record) => props.selectedIds.includes(record.id))
  selectedRows.value = selected
  selected.forEach((record) => tableRef.value?.toggleRowSelection?.(record, true))
}

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