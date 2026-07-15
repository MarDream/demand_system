<template>
  <el-dialog
    :model-value="visible"
    title="导出数据"
    width="420px"
    @update:model-value="$emit('close')"
  >
    <el-form label-width="80px">
      <el-form-item label="导出格式">
        <el-radio-group v-model="format">
          <el-radio value="excel">Excel (.xlsx)</el-radio>
          <el-radio value="csv">CSV (.csv)</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="$emit('close')">取消</el-button>
      <el-button type="primary" :loading="exporting" @click="handleExport">
        导出
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { resolveErrorMessage } from '@/utils/error'
import { exportExcel, exportCsv } from '@/api/modules/bitableTemplate'

const props = defineProps<{
  visible: boolean
  tableId: number
  tableName: string
}>()

const emit = defineEmits<{
  (e: 'close'): void
}>()

const format = ref<'excel' | 'csv'>('excel')
const exporting = ref(false)

async function handleExport() {
  exporting.value = true
  try {
    let blob: Blob
    let fileName: string
    if (format.value === 'excel') {
      blob = await exportExcel(props.tableId)
      fileName = props.tableName + '.xlsx'
    } else {
      blob = await exportCsv(props.tableId)
      fileName = props.tableName + '.csv'
    }
    downloadBlob(blob, fileName)
    ElMessage.success('导出成功')
    emit('close')
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '导出失败'))
  } finally {
    exporting.value = false
  }
}

function downloadBlob(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.click()
  URL.revokeObjectURL(url)
}
</script>

<style scoped lang="scss">
</style>