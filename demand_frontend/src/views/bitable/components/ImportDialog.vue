<template>
  <el-dialog
    :model-value="visible"
    title="导入数据"
    width="520px"
    @update:model-value="$emit('close')"
  >
    <el-form label-width="100px">
      <el-form-item label="导入格式">
        <el-radio-group v-model="format">
          <el-radio value="excel">Excel (.xlsx)</el-radio>
          <el-radio value="csv">CSV (.csv)</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="选择文件">
        <el-upload
          ref="uploadRef"
          :auto-upload="false"
          :limit="1"
          :accept="acceptStr"
          :on-change="handleFileChange"
          :on-remove="handleFileRemove"
          drag
        >
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">
            拖拽文件到此处，或<em>点击上传</em>
          </div>
          <template #tip>
            <div class="el-upload__tip">
              {{ format === 'excel' ? '仅支持 .xlsx 格式文件，第一行为字段名' : '仅支持 .csv 格式文件（UTF-8 编码），第一行为字段名' }}
            </div>
          </template>
        </el-upload>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="$emit('close')">取消</el-button>
      <el-button type="primary" :loading="importing" :disabled="!selectedFile" @click="handleImport">
        导入
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { resolveErrorMessage } from '@/utils/error'
import { UploadFilled } from '@element-plus/icons-vue'
import { importExcel, importCsv } from '@/api/modules/bitableTemplate'

const props = defineProps<{
  visible: boolean
  tableId: number
}>()

const emit = defineEmits<{
  (e: 'imported', recordIds: number[]): void
  (e: 'close'): void
}>()

const format = ref<'excel' | 'csv'>('excel')
const selectedFile = ref<File | null>(null)
const importing = ref(false)

const acceptStr = computed(() =>
  format.value === 'excel' ? '.xlsx' : '.csv'
)

function handleFileChange(file: any) {
  if (file.raw) {
    selectedFile.value = file.raw
  }
}

function handleFileRemove() {
  selectedFile.value = null
}

async function handleImport() {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择文件')
    return
  }

  importing.value = true
  try {
    let recordIds: number[]
    if (format.value === 'excel') {
      recordIds = await importExcel(props.tableId, selectedFile.value)
    } else {
      recordIds = await importCsv(props.tableId, selectedFile.value)
    }
    ElMessage.success(`成功导入 ${recordIds.length} 条记录`)
    emit('imported', recordIds)
    selectedFile.value = null
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '导入失败'))
  } finally {
    importing.value = false
  }
}
</script>

<style scoped lang="scss">
.el-upload__tip {
  color: var(--color-muted-text);
  font-size: var(--font-size-xs);
}
</style>
