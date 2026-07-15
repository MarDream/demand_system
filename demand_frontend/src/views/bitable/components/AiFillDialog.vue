<template>
  <el-dialog
    :model-value="visible"
    title="AI 智能填充"
    width="500px"
    @close="handleClose"
  >
    <div class="ai-fill-dialog">
      <el-form label-width="80px">
        <el-form-item label="填充字段">
          <el-select
            v-model="selectedFieldId"
            placeholder="选择要 AI 填充的字段"
            style="width: 100%"
          >
            <el-option
              v-for="field in fields"
              :key="field.id"
              :label="field.name"
              :value="field.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="filling" :disabled="!selectedFieldId" @click="handleFillSingle">
        填充当前行
      </el-button>
      <el-button type="warning" :loading="batchFilling" :disabled="!selectedFieldId" @click="handleFillBatch">
        批量填充
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { resolveErrorMessage } from '@/utils/error'
import { fillCell, fillBatch } from '@/api/modules/bitableAi'
import type { BitableField } from '@/types/bitable'

const props = defineProps<{
  visible: boolean
  tableId: number
  recordId?: number  // 单条填充时需要
  fields: BitableField[]
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'updated'): void
}>()

const selectedFieldId = ref<number | undefined>()
const filling = ref(false)
const batchFilling = ref(false)

async function handleFillSingle() {
  if (!selectedFieldId.value) {
    ElMessage.warning('请选择填充字段')
    return
  }
  if (!props.recordId) {
    ElMessage.warning('未选择记录行')
    return
  }
  filling.value = true
  try {
    const result = await fillCell(props.tableId, props.recordId, selectedFieldId.value)
    ElMessage.success('AI 填充成功')
    emit('updated')
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, 'AI 填充失败，请重试'))
  } finally {
    filling.value = false
  }
}

async function handleFillBatch() {
  if (!selectedFieldId.value) {
    ElMessage.warning('请选择填充字段')
    return
  }
  batchFilling.value = true
  try {
    await fillBatch(props.tableId, selectedFieldId.value)
    ElMessage.success('批量填充任务已提交')
    handleClose()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '任务提交失败，请重试'))
  } finally {
    batchFilling.value = false
  }
}

function handleClose() {
  selectedFieldId.value = undefined
  emit('close')
}
</script>

<style scoped>
.ai-fill-dialog {
  min-height: 80px;
}
</style>
